// Banc d'essai sur poste : rejoue de longues conversations contre ai_chat.cpp avec un mini modele.
#include <jni.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>
#include <random>
#include <string>
#include "llama.h"

static bool g_force_no_shift = false;
#define llama_memory_can_shift(mem) (g_force_no_shift ? false : llama_memory_can_shift(mem))
#include "ai_chat.cpp"

static const char *fGet(JNIEnv *, jstring s, jboolean *) { return (const char *) s; }
static void fRel(JNIEnv *, jstring, const char *) {}
static jstring fNew(JNIEnv *, const char *s) { return (jstring) strdup(s); }

static JNINativeInterface_ tbl;
static JNIEnv_ envObj;
static JNIEnv *env = &envObj;

#define J(name) Java_com_codexlocal_engine_internal_InferenceEngineImpl_##name

static int failures = 0;
#define CHECK(cond, ...) do { if (!(cond)) { failures++; printf("  ECHEC [%s:%d] %s : ", __FILE__, __LINE__, #cond); printf(__VA_ARGS__); printf("\n"); } } while (0)

static llama_tokens sys_copy;

static void invariants(const char *ou) {
    CHECK((int) kv_tokens.size() == (int) current_position, "%s: miroir %d != position %d", ou, (int) kv_tokens.size(), (int) current_position);
    CHECK(current_position <= usable_context(), "%s: position %d > %d", ou, (int) current_position, usable_context());
    auto *mem = llama_get_memory(g_context);
    CHECK(llama_memory_seq_pos_max(mem, 0) == current_position - 1, "%s: pos_max %d vs %d", ou, (int) llama_memory_seq_pos_max(mem, 0), (int) current_position - 1);
    CHECK(llama_memory_seq_pos_min(mem, 0) == 0, "%s: pos_min %d", ou, (int) llama_memory_seq_pos_min(mem, 0));
    for (size_t i = 0; i < turn_starts.size(); i++) {
        CHECK(turn_starts[i] >= system_prompt_position && turn_starts[i] <= current_position, "%s: turn_start %d hors zone", ou, (int) turn_starts[i]);
        if (i) CHECK(turn_starts[i] >= turn_starts[i - 1], "%s: turn_starts pas croissant", ou);
    }
    CHECK((int) sys_copy.size() <= (int) kv_tokens.size() && std::equal(sys_copy.begin(), sys_copy.end(), kv_tokens.begin()), "%s: prompt systeme abime", ou);
}

static std::string rand_text(std::mt19937 &rng, int n) {
    static const char *abc = "abcdefghijklmnopqrstuvwxyz     .,\n(){}0123456789";
    std::string s;
    std::uniform_int_distribution<int> d(0, (int) strlen(abc) - 1);
    for (int i = 0; i < n; i++) s += abc[d(rng)];
    return s;
}

static int load_model(const char *path, int n_ctx) {
    llama_backend_init();
    int r = J(load)(env, nullptr, (jstring) path);
    if (r) { printf("load a echoue\n"); return r; }
    J(nativeSetContextSize)(env, nullptr, n_ctx);
    r = J(prepare)(env, nullptr);
    if (r) { printf("prepare a echoue\n"); return r; }
    return 0;
}

static void scenario_long(const char *nom, bool template_expected, int turns, int pmin, int pmax, int nmin, int nmax, unsigned seed) {
    printf("== %s (tours=%d, ctx=%d, decalage=%s)\n", nom, turns, g_n_ctx, g_force_no_shift ? "interdit" : "permis");
    std::mt19937 rng(seed);
    CHECK(J(processSystemPrompt)(env, nullptr, (jstring) "Tu es un assistant de programmation. Reponds en francais.") == 0, "systeme");
    sys_copy.assign(kv_tokens.begin(), kv_tokens.end());
    CHECK(template_expected == common_chat_templates_was_explicit(g_chat_templates.get()), "gabarit explicite attendu=%d", (int) template_expected);
    invariants("apres systeme");
    int rc_bad = 0, discards = 0, max_gen = 0;
    llama_pos prev_total = 0;
    for (int t = 0; t < turns; t++) {
        std::uniform_int_distribution<int> dp(pmin, pmax), dn(nmin, nmax);
        std::string prompt = rand_text(rng, dp(rng));
        int n_predict = dn(rng);
        const int before = current_position;
        int r = J(processUserPrompt)(env, nullptr, (jstring) prompt.c_str(), n_predict);
        if (r != 0) { rc_bad++; printf("  tour %d: processUserPrompt a renvoye %d\n", t, r); invariants("apres echec"); continue; }
        // la fin du message doit etre dans le cache
        if (!template_expected) {
            auto expect = common_tokenize(g_context, prompt, false, false);
            const int k = std::min<int>(16, expect.size());
            CHECK(std::equal(expect.end() - k, expect.end(), kv_tokens.end() - k), "tour %d: fin du message absente du cache", t);
        }
        invariants("apres message");
        int gen = 0;
        while (true) {
            jstring out = J(generateNextToken)(env, nullptr);
            if (!out) break;
            gen++;
            free((void *) out);
        }
        max_gen = std::max(max_gen, gen);
        CHECK(gen <= n_predict, "tour %d: %d jetons generes > %d demandes", t, gen, n_predict);
        invariants("apres reponse");
        if ((int) current_position < before + 1) discards++;
        prev_total = current_position;
    }
    printf("   fin: position=%d/%d, messages gardes=%zu, tours qui ont oublie du vieux=%d, echecs=%d\n", (int) current_position, usable_context(), turn_starts.size(), discards, rc_bad);
    CHECK(rc_bad == 0, "des messages ont echoue");
    (void) prev_total;
}

static void scenario_huge(const char *nom) {
    printf("== %s\n", nom);
    std::mt19937 rng(7);
    CHECK(J(processSystemPrompt)(env, nullptr, (jstring) "Systeme.") == 0, "systeme");
    sys_copy.assign(kv_tokens.begin(), kv_tokens.end());
    std::string gros = rand_text(rng, 30000) + " QUESTION FINALE?";
    int r = J(processUserPrompt)(env, nullptr, (jstring) gros.c_str(), 512);
    CHECK(r == 0, "message enorme: code %d", r);
    invariants("message enorme");
    if (common_chat_templates_was_explicit(g_chat_templates.get())) {
        // avec gabarit : la question et le debut de la reponse (« assistant ») doivent finir le cache
        std::string fin;
        for (size_t i = kv_tokens.size() - 60; i < kv_tokens.size(); i++) fin += common_token_to_piece(g_context, kv_tokens[i]);
        CHECK(fin.find("QUESTION FINALE?") != std::string::npos, "la question finale a ete perdue");
        CHECK(fin.size() >= 10 && fin.compare(fin.size() - 10, 10, "assistant\n") == 0, "le debut de reponse du gabarit manque");
    } else {
        auto expect = common_tokenize(g_context, gros, false, false);
        int k = std::min<int>(16, expect.size());
        CHECK(std::equal(expect.end() - k, expect.end(), kv_tokens.end() - k), "la question finale a ete perdue");
    }
    int gen = 0;
    while (true) { jstring out = J(generateNextToken)(env, nullptr); if (!out) break; gen++; free((void *) out); }
    invariants("apres reponse enorme");
    // et la conversation continue
    r = J(processUserPrompt)(env, nullptr, (jstring) "suite normale", 200);
    CHECK(r == 0, "message suivant: code %d", r);
    invariants("message suivant");
    printf("   ok (position=%d)\n", (int) current_position);
}

static void scenario_reset() {
    printf("== reinitialisation\n");
    J(nativeResetConversation)(env, nullptr);
    CHECK(current_position == 0 && kv_tokens.empty() && turn_starts.empty(), "etat pas remis a zero");
    CHECK(J(processSystemPrompt)(env, nullptr, (jstring) "Nouveau systeme") == 0, "systeme apres reset");
    sys_copy.assign(kv_tokens.begin(), kv_tokens.end());
    invariants("apres reset");
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    memset(&tbl, 0, sizeof tbl);
    tbl.GetStringUTFChars = fGet; tbl.ReleaseStringUTFChars = fRel; tbl.NewStringUTF = fNew;
    envObj.functions = &tbl;

    const char *model = argv[1];
    const bool chatml = argc > 2 && std::string(argv[2]) == "chatml";
    g_force_no_shift = argc > 3 && std::string(argv[3]) == "noshift";
    if (load_model(model, 4096)) return 2;

    scenario_long("conversation longue", chatml, 60, 20, 2500, 50, 1200, 42);
    scenario_reset();
    scenario_long("messages tres courts, reponses longues", chatml, 12, 5, 40, 1500, 3000, 5);
    scenario_reset();
    scenario_huge("message plus grand que le contexte");
    scenario_reset();
    scenario_long("apres reset", chatml, 20, 100, 1800, 100, 900, 99);

    printf(failures ? "\nECHECS: %d\n" : "\nTOUT EST BON\n", failures);
    return failures ? 1 : 0;
}
