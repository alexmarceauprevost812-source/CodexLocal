#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <iomanip>
#include <cmath>
#include <string>
#include <vector>
#include <unistd.h>
#include <sampling.h>

#include "logging.h"
#include "chat.h"
#include "common.h"
#include "llama.h"

template<class T>
static std::string join(const std::vector<T> &values, const std::string &delim) {
    std::ostringstream str;
    for (size_t i = 0; i < values.size(); i++) {
        str << values[i];
        if (i < values.size() - 1) { str << delim; }
    }
    return str.str();
}

/**
 * LLama resources: context, model, batch and sampler
 */
constexpr int   N_THREADS_MIN           = 2;
constexpr int   N_THREADS_MAX           = 4;
constexpr int   N_THREADS_HEADROOM      = 2;

constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;
constexpr int   MIN_CONTEXT_SIZE        = 4096;
constexpr int   MIN_PROMPT_ROOM         = 512;     // place minimale gardee a la conversation
constexpr int   OVERFLOW_HEADROOM       = 4;
constexpr int   BATCH_SIZE              = 512;
constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;

// Taille de contexte demandee par l'appli (avant prepare()) et celle reellement obtenue.
static int g_n_ctx_requested = DEFAULT_CONTEXT_SIZE;
static int g_n_ctx           = DEFAULT_CONTEXT_SIZE;

static llama_model                      * g_model;
static llama_context                    * g_context;
static common_batch                       g_batch;
static common_chat_templates_ptr          g_chat_templates;
static common_sampler                   * g_sampler;

extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_init(JNIEnv *env, jobject /*unused*/, jstring nativeLibDir) {
    // Set llama log handler to Android
    llama_log_set(aichat_android_log_callback, nullptr);

    // Loading all CPU backend variants
    const auto *path_to_backend = env->GetStringUTFChars(nativeLibDir, 0);
    LOGi("Loading backends from %s", path_to_backend);
    ggml_backend_load_all_from_path(path_to_backend);
    env->ReleaseStringUTFChars(nativeLibDir, path_to_backend);

    // Initialize backends
    llama_backend_init();
    LOGi("Backend initiated; Log handler set.");
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_load(JNIEnv *env, jobject, jstring jmodel_path) {
    llama_model_params model_params = llama_model_default_params();

    const auto *model_path = env->GetStringUTFChars(jmodel_path, 0);
    LOGd("%s: Loading model from: \n%s\n", __func__, model_path);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jmodel_path, model_path);
    if (!model) {
        return 1;
    }
    g_model = model;
    return 0;
}

static llama_context *init_context(llama_model *model, const int n_ctx = DEFAULT_CONTEXT_SIZE) {
    if (!model) {
        LOGe("%s: model cannot be null", __func__);
        return nullptr;
    }

    // Multi-threading setup
    const int n_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX,
                                                     (int) sysconf(_SC_NPROCESSORS_ONLN) -
                                                     N_THREADS_HEADROOM));
    LOGi("%s: Using %d threads", __func__, n_threads);

    // Context parameters setup
    llama_context_params ctx_params = llama_context_default_params();
    const int trained_context_size = llama_model_n_ctx_train(model);
    if (n_ctx > trained_context_size) {
        LOGw("%s: Model was trained with only %d context size! Enforcing %d context size...",
             __func__, trained_context_size, n_ctx);
    }
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = BATCH_SIZE;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    auto *context = llama_init_from_model(g_model, ctx_params);
    if (context == nullptr) {
        LOGe("%s: llama_new_context_with_model() returned null)", __func__);
    }
    return context;
}

/**
 * Taille de contexte a demander : celle voulue par l'appli, plafonnee a ce pour quoi le modele a ete
 * entraine (au-dela, les reponses deviennent incoherentes), sans descendre sous MIN_CONTEXT_SIZE.
 */
static int choose_context_size(const llama_model *model, const int requested) {
    int n = requested;
    const int trained = llama_model_n_ctx_train(model);
    if (trained > 0 && n > trained) {
        LOGw("%s: Model was trained with only %d context size, asked %d", __func__, trained, n);
        n = trained;
    }
    return std::max(n, MIN_CONTEXT_SIZE);
}

/**
 * Cree le contexte de conversation. Si la memoire manque, on retente avec un contexte deux fois plus
 * petit (jusqu'a MIN_CONTEXT_SIZE) plutot que d'echouer.
 */
static llama_context *create_chat_context(llama_model *model) {
    int n = choose_context_size(model, g_n_ctx_requested);
    while (true) {
        auto *context = init_context(model, n);
        if (context != nullptr) {
            g_n_ctx = (int) llama_n_ctx(context);
            LOGi("%s: Context size is %d tokens", __func__, g_n_ctx);
            return context;
        }
        if (n <= MIN_CONTEXT_SIZE) return nullptr;
        n = std::max(n / 2, MIN_CONTEXT_SIZE);
        LOGw("%s: Not enough memory, retrying with %d tokens", __func__, n);
    }
}

// Parametres d'echantillonnage configurables depuis Kotlin
static float g_temp  = DEFAULT_SAMPLER_TEMP;
static float g_top_p = 0.95f;
static int   g_top_k = 40;

static common_sampler *new_sampler() {
    common_params_sampling sparams;
    sparams.temp  = g_temp;
    sparams.top_p = g_top_p;
    sparams.top_k = g_top_k;
    return common_sampler_init(g_model, sparams);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_prepare(JNIEnv * /*env*/, jobject /*unused*/) {
    auto *context = create_chat_context(g_model);
    if (!context) { return 1; }
    g_context = context;
    g_batch = common_batch(context);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = new_sampler();
    return 0;
}

static std::string get_backend() {
    std::vector<std::string> backends;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        auto *reg = ggml_backend_reg_get(i);
        std::string name = ggml_backend_reg_name(reg);
        if (name != "CPU") {
            backends.push_back(ggml_backend_reg_name(reg));
        }
    }
    return backends.empty() ? "CPU" : join(backends, ",");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_systemInfo(JNIEnv *env, jobject /*unused*/) {
    return env->NewStringUTF(llama_print_system_info());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_benchModel(JNIEnv *env, jobject /*unused*/, jint pp, jint tg,
                                                      jint pl, jint nr) {
    auto *context = init_context(g_model, pp);
    if (!context) {
        const auto *const err_msg = "Fail to init_context! Bench aborted.";
        LOGe(err_msg);
        return env->NewStringUTF(err_msg);
    }

    auto pp_avg = 0.0;
    auto tg_avg = 0.0;
    auto pp_std = 0.0;
    auto tg_std = 0.0;

    const uint32_t n_ctx = llama_n_ctx(context);
    LOGi("n_ctx = %d", n_ctx);

    int i, j;
    int nri;
    for (nri = 0; nri < nr; nri++) {
        LOGi("Benchmark prompt processing (pp = %d)", pp);

        common_batch batch(context);

        const int n_tokens = pp;
        for (i = 0; i < n_tokens; i++) {
            batch.add(0, i, 0, false);
        }

        batch.set_output(batch.size() - 1, true);
        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp_start = ggml_time_us();
        if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
            LOGe("llama_decode() failed during prompt processing");
        }
        const auto t_pp_end = ggml_time_us();

        // bench text generation

        LOGi("Benchmark text generation (tg = %d)", tg);

        llama_memory_clear(llama_get_memory(context), false);
        const auto t_tg_start = ggml_time_us();
        for (i = 0; i < tg; i++) {
            batch.clear();
            for (j = 0; j < pl; j++) {
                batch.add(0, i, j, true);
            }

            if (llama_process(context, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) {
                LOGe("llama_decode() failed during text generation");
            }
        }
        const auto t_tg_end = ggml_time_us();

        llama_memory_clear(llama_get_memory(context), false);

        const auto t_pp = double(t_pp_end - t_pp_start) / 1000000.0;
        const auto t_tg = double(t_tg_end - t_tg_start) / 1000000.0;

        const auto speed_pp = double(pp) / t_pp;
        const auto speed_tg = double(pl * tg) / t_tg;

        pp_avg += speed_pp;
        tg_avg += speed_tg;

        pp_std += speed_pp * speed_pp;
        tg_std += speed_tg * speed_tg;

        LOGi("pp %f t/s, tg %f t/s", speed_pp, speed_tg);
    }

    llama_free(context);

    pp_avg /= double(nr);
    tg_avg /= double(nr);

    if (nr > 1) {
        pp_std = sqrt(pp_std / double(nr - 1) - pp_avg * pp_avg * double(nr) / double(nr - 1));
        tg_std = sqrt(tg_std / double(nr - 1) - tg_avg * tg_avg * double(nr) / double(nr - 1));
    } else {
        pp_std = 0;
        tg_std = 0;
    }

    char model_desc[128];
    llama_model_desc(g_model, model_desc, sizeof(model_desc));

    const auto model_size = double(llama_model_size(g_model)) / 1024.0 / 1024.0 / 1024.0;
    const auto model_n_params = double(llama_model_n_params(g_model)) / 1e9;

    const auto backend = get_backend();
    std::stringstream result;
    result << std::setprecision(3);
    result << "| model | size | params | backend | test | t/s |\n";
    result << "| --- | --- | --- | --- | --- | --- |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | pp " << pp << " | " << pp_avg << " ± " << pp_std << " |\n";
    result << "| " << model_desc << " | " << model_size << "GiB | " << model_n_params << "B | "
           << backend << " | tg " << tg << " | " << tg_avg << " ± " << tg_std << " |\n";
    return env->NewStringUTF(result.str().c_str());
}


/**
 * Completion loop's long-term states:
 * - chat management
 * - position tracking
 */
constexpr const char *ROLE_SYSTEM       = "system";
constexpr const char *ROLE_USER         = "user";
constexpr const char *ROLE_ASSISTANT    = "assistant";

static std::vector<common_chat_msg> chat_msgs;
static size_t chat_msgs_base;           // messages du prompt systeme (0 ou 1), toujours gardes
static llama_pos system_prompt_position;
static llama_pos current_position;

/**
 * Copie de ce que contient le cache KV : kv_tokens[i] est le jeton a la position i, donc
 * kv_tokens.size() == current_position. Elle permet de decaler le cache en toute securite et,
 * pour les modeles qui ne savent pas decaler, de le reconstruire sans l'historique oublie.
 */
static llama_tokens kv_tokens;

/** Positions (dans le cache) ou commence chaque message de l'utilisateur encore present. */
static std::vector<llama_pos> turn_starts;

static void reset_long_term_states(const bool clear_kv_cache = true) {
    chat_msgs.clear();
    chat_msgs_base = 0;
    kv_tokens.clear();
    turn_starts.clear();
    system_prompt_position = 0;
    current_position = 0;

    if (clear_kv_cache)
        llama_memory_clear(llama_get_memory(g_context), false);
}

static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), chat_msgs, new_msg, role == ROLE_USER, /* use_jinja */ false);
    chat_msgs.push_back(new_msg);
    LOGi("%s: Formatted and added %s message: \n%s\n", __func__, role.c_str(), formatted.c_str());
    return formatted;
}

/**
 * Completion loop's short-term states:
 * - number of tokens the current answer may still use
 * - token chars caching
 * - current assistant message being generated
 */
static int g_tokens_left;
static std::string cached_token_chars;
static std::ostringstream assistant_ss;

static void reset_short_term_states() {
    g_tokens_left = 0;
    cached_token_chars.clear();
    assistant_ss.str("");
}

/** Nombre de jetons que le cache peut contenir (un petit reste est garde de cote). */
static int usable_context() {
    return g_n_ctx - OVERFLOW_HEADROOM;
}

/**
 * Decode `tokens` a la suite du cache (la place doit deja exister) en suivant les positions reelles.
 * Renvoie 0 si tout va bien. Seuls les lots decodes avec succes entrent dans la copie `kv_tokens`.
 */
static int decode_append(const llama_tokens &tokens, const bool compute_last_logit = false) {
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(), current_position);
    for (int i = 0; i < (int) tokens.size(); i += BATCH_SIZE) {
        const int cur_batch_size = std::min((int) tokens.size() - i, BATCH_SIZE);
        g_batch.clear();
        for (int j = 0; j < cur_batch_size; j++) {
            const bool want_logit = compute_last_logit && (i + j == (int) tokens.size() - 1);
            g_batch.add(tokens[i + j], current_position + j, 0, want_logit);
        }
        const int decode_result = llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get());
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
        kv_tokens.insert(kv_tokens.end(), tokens.begin() + i, tokens.begin() + i + cur_batch_size);
        current_position += cur_batch_size;
    }
    return 0;
}

/** Vide le cache puis le remplit avec `keep` (dernier jeton avec ses logits, pour pouvoir continuer). */
static bool rebuild_cache(const llama_tokens &keep) {
    llama_memory_clear(llama_get_memory(g_context), false);
    kv_tokens.clear();
    current_position = 0;
    return decode_append(keep, true) == 0;
}

static int g_discards = 0;       // combien de fois de vieux messages ont ete retires de la memoire

/** Les positions des messages apres que [from, to) a ete retire du cache. */
static void forget_turn_starts(const llama_pos from, const llama_pos to) {
    const llama_pos n = to - from;
    std::vector<llama_pos> kept;
    for (const llama_pos p: turn_starts) {
        if (p < from) kept.push_back(p);
        else if (p >= to) kept.push_back(p - n);
        // sinon le message commencait dans la partie oubliee
    }
    turn_starts.swap(kept);
}

/** Retire [from, to) du cache : on le decale si le modele sait le faire, sinon on le reconstruit. */
static bool discard_range(const llama_pos from, const llama_pos to) {
    const llama_pos n_discard = to - from;
    if (n_discard <= 0) return true;
    LOGi("%s: Discarding %d tokens [%d, %d)", __func__, (int) n_discard, (int) from, (int) to);
    g_discards++;

    auto *mem = llama_get_memory(g_context);
    if (llama_memory_can_shift(mem) && llama_memory_seq_rm(mem, 0, from, to)) {
        llama_memory_seq_add(mem, 0, to, current_position, -n_discard);
        kv_tokens.erase(kv_tokens.begin() + from, kv_tokens.begin() + to);
        current_position -= n_discard;
        forget_turn_starts(from, to);
        LOGi("%s: Context shifting done! Current position: %d", __func__, (int) current_position);
        return true;
    }

    // Ce modele ne sait pas decaler son cache (memoire a fenetre glissante, recurrente...) :
    // on le reconstruit avec ce qu'on garde.
    LOGw("%s: This model cannot shift its context, rebuilding it", __func__);
    llama_tokens keep;
    keep.reserve(kv_tokens.size() - n_discard);
    keep.insert(keep.end(), kv_tokens.begin(), kv_tokens.begin() + from);
    keep.insert(keep.end(), kv_tokens.begin() + to, kv_tokens.end());
    forget_turn_starts(from, to);
    return rebuild_cache(keep);
}

/**
 * Combien de jetons jeter apres le prompt systeme pour liberer au moins `libre_min` places.
 * On jette plutot des messages entiers (le cache recommence alors sur un debut de message) et assez
 * pour ne pas recommencer a chaque tour : environ la moitie, comme la methode classique de llama.cpp.
 */
static int choose_discard_count(const int libre_min) {
    const int zone = current_position - system_prompt_position;
    const int target = std::min(zone, std::max(libre_min, zone / 2));

    int best_within = -1;   // le plus grand debut de message dans [libre_min, target]
    int best_above = -1;    // sinon, le plus petit debut de message au-dela de la cible
    for (const llama_pos p: turn_starts) {
        const int n = (int) (p - system_prompt_position);
        if (n >= libre_min && n <= target) best_within = std::max(best_within, n);
        else if (n > target && n <= zone) best_above = (best_above < 0) ? n : std::min(best_above, n);
    }
    if (best_within >= 0) return best_within;
    if (best_above >= 0) return best_above;
    return target;      // un seul message enorme : on coupe au milieu, faute de mieux
}

/**
 * Garantit `needed` places libres a la suite du cache en oubliant les plus anciens echanges
 * (jamais le prompt systeme). Faux si c'est impossible ou si la reconstruction echoue.
 */
static bool ensure_room(const int needed) {
    const int usable = usable_context();
    if (current_position + needed <= usable) return true;
    if (system_prompt_position + needed > usable) return false;

    const int libre_min = current_position + needed - usable;
    const int n_discard = choose_discard_count(libre_min);
    if (n_discard < libre_min) return false;
    return discard_range(system_prompt_position, system_prompt_position + n_discard);
}

/**
 * Apres un echec de decodage le cache est dans un etat incertain : on le ramene au seul prompt
 * systeme. La conversation affichee reste intacte, c'est l'appli qui redonne un rappel au modele.
 */
static int g_recoveries = 0;     // combien de fois la memoire a du etre remise au prompt systeme

static void recover_after_failure() {
    g_recoveries++;
    llama_tokens system_only(kv_tokens.begin(),
                             kv_tokens.begin() + std::min((size_t) system_prompt_position, kv_tokens.size()));
    turn_starts.clear();
    if (chat_msgs.size() > chat_msgs_base) chat_msgs.erase(chat_msgs.begin() + chat_msgs_base, chat_msgs.end());
    system_prompt_position = (llama_pos) system_only.size();
    if (!rebuild_cache(system_only)) {
        // Meme cela echoue : on repart d'un cache vide, sans prompt systeme.
        llama_memory_clear(llama_get_memory(g_context), false);
        kv_tokens.clear();
        current_position = 0;
        system_prompt_position = 0;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_processSystemPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring jsystem_prompt
) {
    // Reset long-term & short-term states
    reset_long_term_states();
    reset_short_term_states();

    // Obtain system prompt from JEnv
    const auto *system_prompt = env->GetStringUTFChars(jsystem_prompt, nullptr);
    LOGd("%s: System prompt received: \n%s", __func__, system_prompt);
    std::string formatted_system_prompt(system_prompt);

    // Format system prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_system_prompt = chat_add_and_format(ROLE_SYSTEM, system_prompt);
    }
    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);

    // Tokenize system prompt
    const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,
                                               has_chat_template, has_chat_template);
    for (auto id: system_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Le prompt systeme doit laisser de la place a la conversation
    const int max_system_size = usable_context() - MIN_PROMPT_ROOM;
    if ((int) system_tokens.size() > max_system_size) {
        LOGe("%s: System prompt too long for context! %d tokens, max: %d",
             __func__, (int) system_tokens.size(), max_system_size);
        return 1;
    }

    // Decode system tokens in batches
    if (decode_append(system_tokens)) {
        LOGe("%s: llama_decode() failed!", __func__);
        return 2;
    }

    // Update position
    system_prompt_position = current_position;
    chat_msgs_base = chat_msgs.size();
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_processUserPrompt(
        JNIEnv *env,
        jobject /*unused*/,
        jstring juser_prompt,
        jint n_predict
) {
    // Reset short-term states
    reset_short_term_states();

    // Obtain and tokenize user prompt
    const auto *const user_prompt = env->GetStringUTFChars(juser_prompt, nullptr);
    LOGd("%s: User prompt received: \n%s", __func__, user_prompt);
    std::string formatted_user_prompt(user_prompt);

    // Format user prompt if applicable
    const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());
    if (has_chat_template) {
        formatted_user_prompt = chat_add_and_format(ROLE_USER, user_prompt);
    }
    env->ReleaseStringUTFChars(juser_prompt, user_prompt);

    // Decode formatted user prompts
    auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);
    for (auto id: user_tokens) {
        LOGv("token: `%s`\t -> `%d`", common_token_to_piece(g_context, id).c_str(), id);
    }

    // Un peu de place gardee pour la reponse : les reponses courtes n'obligent ainsi pas a decaler le
    // cache en pleine ecriture. Pour une longue reponse, le decalage se fait quand meme en cours de route.
    const int usable = usable_context();
    const int room = usable - (int) system_prompt_position;
    const int reserve = std::max(0, std::min({(int) n_predict, usable / 16, room - MIN_PROMPT_ROOM}));
    const int max_prompt = room - reserve;
    if (max_prompt < 16) {
        LOGe("%s: No room left for the user prompt (system prompt: %d tokens)", __func__, (int) system_prompt_position);
        return 1;
    }

    // Message trop long pour le contexte : on garde son debut et surtout sa fin (la question et la
    // fin du gabarit), et on coupe au milieu.
    const int user_prompt_size = (int) user_tokens.size();
    if (user_prompt_size > max_prompt) {
        const auto marker = common_tokenize(g_context, "\n[...]\n", false, false);
        const int head = max_prompt / 4;
        const int tail = std::max(0, max_prompt - head - (int) marker.size());
        llama_tokens cut;
        cut.reserve(max_prompt);
        cut.insert(cut.end(), user_tokens.begin(), user_tokens.begin() + head);
        cut.insert(cut.end(), marker.begin(), marker.end());
        cut.insert(cut.end(), user_tokens.end() - tail, user_tokens.end());
        LOGw("%s: User prompt too long! Skipped %d tokens!", __func__, user_prompt_size - (int) cut.size());
        user_tokens.swap(cut);
    }

    // Oublie les plus anciens echanges si besoin, puis lit le message
    turn_starts.push_back(current_position);
    if (!ensure_room((int) user_tokens.size() + reserve)) {
        LOGe("%s: Could not make room for the user prompt", __func__);
        recover_after_failure();
        return 3;
    }
    if (decode_append(user_tokens, true)) {
        LOGe("%s: llama_decode() failed!", __func__);
        recover_after_failure();
        return 2;
    }

    g_tokens_left = (int) n_predict;
    return 0;
}

static bool is_valid_utf8(const char *string) {
    if (!string) { return true; }

    const auto *bytes = (const unsigned char *) string;
    int num;

    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00) {
            // U+0000 to U+007F
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            // U+0080 to U+07FF
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            // U+0800 to U+FFFF
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            // U+10000 to U+10FFFF
            num = 4;
        } else {
            return false;
        }

        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_generateNextToken(
        JNIEnv *env,
        jobject /*unused*/
) {
    // Fin de la reponse : le nombre de jetons demande est atteint
    if (g_tokens_left <= 0) {
        LOGw("%s: STOP: reached the requested answer length", __func__);
        return nullptr;
    }

    // Generation sans fin : quand le cache est plein, on oublie les plus anciens echanges
    if (!ensure_room(1)) {
        LOGe("%s: Context full and nothing left to forget", __func__);
        recover_after_failure();
        return nullptr;
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Populate the batch with new token, then decode
    g_batch.clear();
    g_batch.add(new_token_id, current_position, 0, true);
    if (llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGe("%s: llama_decode() failed for generated token", __func__);
        recover_after_failure();
        return nullptr;
    }

    // Update position
    kv_tokens.push_back(new_token_id);
    current_position++;
    g_tokens_left--;

    // Stop if next token is EOG
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("id: %d,\tIS EOG!\nSTOP.", new_token_id);
        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());
        return nullptr;
    }

    // If not EOG, convert to text
    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    cached_token_chars += new_token_chars;

    // Create and return a valid UTF-8 Java string
    jstring result = nullptr;
    if (is_valid_utf8(cached_token_chars.c_str())) {
        result = env->NewStringUTF(cached_token_chars.c_str());
        LOGv("id: %d,\tcached: `%s`,\tnew: `%s`", new_token_id, cached_token_chars.c_str(), new_token_chars.c_str());

        assistant_ss << cached_token_chars;
        cached_token_chars.clear();
    } else {
        LOGv("id: %d,\tappend to cache", new_token_id);
        result = env->NewStringUTF("");
    }
    return result;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_unload(JNIEnv * /*unused*/, jobject /*unused*/) {
    // Reset long-term & short-term states.
    // Le cache KV n'existe que si le contexte a ete cree (prepare() peut avoir echoue).
    reset_long_term_states(g_context != nullptr);
    reset_short_term_states();

    // Free up resources, puis remise a zero des pointeurs : un second unload()
    // (apres un echec de chargement, par exemple) ne doit jamais liberer deux fois.
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    g_chat_templates.reset();
    g_batch = common_batch();
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_shutdown(JNIEnv *, jobject /*unused*/) {
    llama_backend_free();
}

/**
 * CodexLocal : configuration de l'echantillonnage (temperature, top-p, top-k).
 * A appeler avant prepare() pour etre pris en compte.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeSetSamplingParams(
        JNIEnv * /*unused*/, jobject /*unused*/, jfloat temp, jfloat top_p, jint top_k) {
    g_temp  = temp;
    g_top_p = top_p;
    g_top_k = top_k;
    // si le sampler existe deja, on le reconstruit pour appliquer tout de suite
    if (g_sampler != nullptr) {
        common_sampler_free(g_sampler);
        g_sampler = new_sampler();
    }
    LOGi("%s: temp=%.2f top_p=%.2f top_k=%d", __func__, g_temp, g_top_p, (int) g_top_k);
}

/**
 * CodexLocal : repart de zero (vide le cache KV) sans recharger le modele.
 * Le prompt systeme doit ensuite etre renvoye via processSystemPrompt().
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeResetConversation(
        JNIEnv * /*unused*/, jobject /*unused*/) {
    LOGi("%s: conversation reset", __func__);
    reset_long_term_states();
    reset_short_term_states();
}
/**
 * CodexLocal : taille de contexte voulue (en jetons). A appeler avant prepare() ; le moteur la
 * plafonne a ce pour quoi le modele a ete entraine et la reduit si la memoire manque.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeSetContextSize(
        JNIEnv * /*unused*/, jobject /*unused*/, jint n_ctx) {
    g_n_ctx_requested = std::max((int) n_ctx, MIN_CONTEXT_SIZE);
    LOGi("%s: requested context size = %d", __func__, g_n_ctx_requested);
}

/** CodexLocal : nombre de jetons deja dans la memoire du modele. */
extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeContextUsed(
        JNIEnv * /*unused*/, jobject /*unused*/) {
    return (jint) current_position;
}

/** CodexLocal : taille totale de la memoire du modele, en jetons. */
extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeContextSize(
        JNIEnv * /*unused*/, jobject /*unused*/) {
    return (jint) usable_context();
}

/** CodexLocal : combien de fois de vieux messages ont ete retires de la memoire du modele (conversation trop longue). */
extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeDiscardCount(
        JNIEnv * /*unused*/, jobject /*unused*/) {
    return (jint) g_discards;
}

/** CodexLocal : combien de fois la memoire du modele a du etre remise a son point de depart apres un incident. */
extern "C"
JNIEXPORT jint JNICALL
Java_com_codexlocal_engine_internal_InferenceEngineImpl_nativeRecoveryCount(
        JNIEnv * /*unused*/, jobject /*unused*/) {
    return (jint) g_recoveries;
}
