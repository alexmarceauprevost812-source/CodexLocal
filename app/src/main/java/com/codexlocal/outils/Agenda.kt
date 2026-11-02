package com.codexlocal.outils

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codexlocal.Message
import com.codexlocal.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Un rendez-vous de l'agenda. [rappelMin] : minutes avant le début, -1 sans rappel. */
data class Evenement(val id: Long, val titre: String, val debut: Long, val rappelMin: Int, val note: String = "")

/** Agenda local (sur le téléphone seulement), avec rappels par notification. */
object AgendaStore {

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("agenda", Context.MODE_PRIVATE)

    fun tous(c: Context): List<Evenement> = runCatching {
        val json = JSONArray(sp(c).getString("evenements", "[]"))
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            Evenement(o.getLong("id"), o.getString("titre"), o.getLong("debut"), o.optInt("rappel", -1), o.optString("note"))
        }.sortedBy { it.debut }
    }.getOrDefault(emptyList())

    private fun sauver(c: Context, liste: List<Evenement>) {
        val json = JSONArray()
        liste.forEach {
            json.put(JSONObject().put("id", it.id).put("titre", it.titre).put("debut", it.debut).put("rappel", it.rappelMin).put("note", it.note))
        }
        sp(c).edit().putString("evenements", json.toString()).apply()
    }

    fun ajouter(c: Context, e: Evenement) {
        sauver(c, tous(c).filter { it.id != e.id } + e)
        programmerRappel(c, e)
    }

    fun supprimer(c: Context, e: Evenement) {
        sauver(c, tous(c).filter { it.id != e.id })
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(intentionRappel(c, e))
    }

    fun duJour(c: Context, jour: Calendar): List<Evenement> = tous(c).filter {
        val x = Calendar.getInstance().apply { timeInMillis = it.debut }
        x.get(Calendar.YEAR) == jour.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == jour.get(Calendar.DAY_OF_YEAR)
    }

    fun prochains(c: Context, jours: Int = 14): List<Evenement> {
        val maintenant = System.currentTimeMillis() - 3_600_000
        return tous(c).filter { it.debut in maintenant..(maintenant + jours * 86_400_000L) }
    }

    private fun intentionRappel(c: Context, e: Evenement): PendingIntent = PendingIntent.getBroadcast(
        c, (e.id % Int.MAX_VALUE).toInt(),
        Intent(c, RappelAgenda::class.java).putExtra("titre", e.titre).putExtra("debut", e.debut).putExtra("id", e.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun programmerRappel(c: Context, e: Evenement) {
        if (e.rappelMin < 0) return
        val quand = e.debut - e.rappelMin * 60_000L
        if (quand < System.currentTimeMillis()) return
        // Rappel « à peu près à l'heure » : aucune permission d'alarme exacte nécessaire.
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand, intentionRappel(c, e))
    }

    // ------------------------------------------------------- lien avec le chat

    private val format = SimpleDateFormat("EEEE d MMMM yyyy 'à' HH:mm", Locale.CANADA_FRENCH)
    private val motsAgenda = Regex("(?iU)\\b(agenda|calendrier|rendez-vous|rdv|planifi\\w*|planning|horaire|mon temps|ma semaine|cette semaine|demain|organise\\w*)\\b")
    private val ligneAjout = Regex("(?m)^\\s*AGENDA\\+\\s*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s*\\|\\s*(.+?)\\s*$")

    private val ligneSuggestion = Regex("(?m)^\\s*AGENDA\\?\\s*(\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})\\s*\\|\\s*(.+?)\\s*$")

    val SCHEME: String get() = com.codexlocal.BuildConfig.APPLICATION_ID + ".agenda"

    fun concerne(message: String) = motsAgenda.containsMatchIn(message)

    /** Lien « ajouter en 1 clic » vers l'agenda. */
    fun lienAjout(date: String, heure: String, titre: String): String {
        // Uri.encode garde « ( ) » : ils couperaient le lien Markdown, on les encode aussi.
        fun enc(v: String) = android.net.Uri.encode(v).replace("(", "%28").replace(")", "%29")
        return "$SCHEME://ajout?d=" + enc(date) + "&h=" + enc(heure) + "&t=" + enc(titre)
    }

    /** Les suggestions de l'IA (« AGENDA? … ») deviennent des éléments à ajouter en un clic. */
    fun suggestions(message: Message) {
        if (message.isUser || !ligneSuggestion.containsMatchIn(message.content)) return
        val lecture = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        val affiche = SimpleDateFormat("EEE d MMM · HH:mm", Locale.CANADA_FRENCH)
        surLaReponse(message) { reponse ->
            ligneSuggestion.replace(reponse) { m ->
                val heure = m.groupValues[2].padStart(5, '0')
                val quand = runCatching { lecture.parse(m.groupValues[1] + " " + heure)!! }.getOrNull() ?: return@replace m.value
                val titre = m.groupValues[3].take(80).replace("[", "(").replace("]", ")")
                "📅 **${affiche.format(quand)}** — $titre · ➕ [Ajouter en 1 clic](${lienAjout(m.groupValues[1], heure, titre)})"
            }
        }
    }

    /** Consigne + agenda actuel donnés à l'IA, pour qu'elle aide à planifier et puisse ajouter des rendez-vous. */
    fun contexte(c: Context): String {
        val auj = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm", Locale.CANADA_FRENCH).format(Date())
        val liste = prochains(c).joinToString("\n") { "- " + format.format(Date(it.debut)) + " : " + it.titre }
            .ifBlank { "(aucun rendez-vous dans les 14 prochains jours)" }
        return "Agenda de l'utilisateur (nous sommes $auj) :\n$liste\n" +
            "Pour AJOUTER un rendez-vous que l'utilisateur demande clairement, écris une ligne seule exactement ainsi : " +
            "AGENDA+ AAAA-MM-JJ HH:MM | Titre. Pour PROPOSER des éléments d'un plan (il choisira), écris plutôt " +
            "AGENDA? AAAA-MM-JJ HH:MM | Titre : chacun aura un bouton « Ajouter en 1 clic »."
    }

    fun carte(c: Context): String {
        val liste = prochains(c, 7)
        val heure = SimpleDateFormat("EEE d MMM · HH:mm", Locale.CANADA_FRENCH)
        return buildString {
            append("📅 **Ton agenda · 7 prochains jours**\n")
            if (liste.isEmpty()) append("Rien de prévu 🎉")
            liste.take(8).forEach { append("\n").append(com.codexlocal.chat.EmojisChat.pourSujet(it.titre, "🕒")).append(" **").append(heure.format(Date(it.debut))).append("** · ").append(it.titre.replace("*", "")) }
        }
    }

    /**
     * Applique [transformer] seulement à la vraie réponse, jamais au raisonnement caché
     * (`<think>…</think>`) ni à une réflexion interrompue : un brouillon n'ajoute rien.
     */
    private fun surLaReponse(message: Message, transformer: (String) -> String) {
        val decoupe = com.codexlocal.agent.Reflexion.separer(message.content)
        if (decoupe.enCours) return
        val nouvelle = transformer(decoupe.reponse)
        if (nouvelle == decoupe.reponse) return
        if (message.envoi == null) message.envoi = message.content
        message.content = message.content.removeSuffix(decoupe.reponse) + nouvelle
    }

    /** Ajoute les rendez-vous écrits par l'IA (« AGENDA+ … ») et les remplace par une confirmation. */
    fun appliquer(c: Context, message: Message) {
        if (message.isUser || !ligneAjout.containsMatchIn(message.content)) return
        val lecture = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
        val affiche = SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.CANADA_FRENCH)
        surLaReponse(message) { reponse ->
            ligneAjout.replace(reponse) { m ->
                val debut = runCatching { lecture.parse(m.groupValues[1] + " " + m.groupValues[2].padStart(5, '0'))!!.time }.getOrNull()
                    ?: return@replace m.value
                val titre = m.groupValues[3].take(80)
                // Même rendez-vous déjà là (réponse rejouée) : on ne le double pas.
                if (tous(c).none { it.debut == debut && it.titre == titre }) ajouter(c, Evenement(System.nanoTime(), titre, debut, 30))
                "✅ **Ajouté à l'agenda** : ${affiche.format(Date(debut))} — $titre (rappel 30 min avant)"
            }
        }
    }
}

/** Repère une date / heure simple dans un texte en français : « demain 14h », « 12 novembre à 9h30 », « 2026-11-05 ». */
object DateTexte {
    private val moisFr = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private val joursFr = listOf("dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi")

    fun trouver(texte: String): Long? {
        val t = texte.lowercase(Locale.CANADA_FRENCH).replace("fevrier", "février").replace("aout", "août").replace("decembre", "décembre")
        val c = Calendar.getInstance().apply { set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var trouve = false
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").find(t)?.let {
            c.set(it.groupValues[1].toInt(), it.groupValues[2].toInt() - 1, it.groupValues[3].toInt()); trouve = true
        }
        if (!trouve) Regex("(\\d{1,2})(?:er)?\\s+(" + moisFr.joinToString("|") + ")").find(t)?.let {
            val m = moisFr.indexOf(it.groupValues[2])
            c.set(Calendar.MONTH, m); c.set(Calendar.DAY_OF_MONTH, it.groupValues[1].toInt().coerceIn(1, 31))
            if (c.timeInMillis < System.currentTimeMillis() - 86_400_000L) c.add(Calendar.YEAR, 1)
            trouve = true
        }
        if (!trouve && t.contains("après-demain")) { c.add(Calendar.DAY_OF_YEAR, 2); trouve = true }
        if (!trouve && t.contains("demain")) { c.add(Calendar.DAY_OF_YEAR, 1); trouve = true }
        if (!trouve) joursFr.indexOfFirst { Regex("\\b$it\\b").containsMatchIn(t) }.takeIf { it >= 0 }?.let { j ->
            var ecart = (j + 1) - c.get(Calendar.DAY_OF_WEEK)
            if (ecart <= 0) ecart += 7
            c.add(Calendar.DAY_OF_YEAR, ecart); trouve = true
        }
        val h = Regex("\\b(\\d{1,2})\\s*(?:h|:)\\s*(\\d{2})?\\b").find(t)
        if (h != null && h.groupValues[1].toInt() in 0..23) {
            c.set(Calendar.HOUR_OF_DAY, h.groupValues[1].toInt()); c.set(Calendar.MINUTE, h.groupValues[2].toIntOrNull() ?: 0)
            trouve = true
        } else if (trouve) {
            c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0)
        }
        return if (trouve) c.timeInMillis else null
    }
}

/** Android efface les alarmes à l'arrêt : on reprogramme les rappels (et les nouvelles) au démarrage. */
class RedemarrageTelephone : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        AgendaStore.tous(context).forEach { AgendaStore.programmerRappel(context, it) }
        NotifNouvelles.programmer(context)
    }
}

/** Notification de rappel d'un rendez-vous. */
class RappelAgenda : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val titre = intent.getStringExtra("titre") ?: return
        val debut = intent.getLongExtra("debut", 0)
        val gestion = context.getSystemService(NotificationManager::class.java)
        gestion.createNotificationChannel(NotificationChannel(CANAL, "Rappels de l'agenda", NotificationManager.IMPORTANCE_HIGH))
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val ouvrir = PendingIntent.getActivity(
            context, 0, Intent(context, AgendaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(com.codexlocal.chat.EmojisChat.pourSujet(titre, "📅") + " $titre")
            .setContentText("À " + SimpleDateFormat("HH:mm", Locale.CANADA_FRENCH).format(Date(debut)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(ouvrir)
            .build()
        NotificationManagerCompat.from(context).notify((intent.getLongExtra("id", 0) % Int.MAX_VALUE).toInt(), notif)
    }

    companion object { const val CANAL = "agenda" }
}

/** Calendrier mensuel noir/orange : touche un jour, ajoute des rendez-vous avec rappel. */
class AgendaActivity : AppCompatActivity() {

    private val orange = Color.parseColor("#FF7A00")
    private val bleu = Color.parseColor("#38BDF8")
    private val or = Color.parseColor("#FFC107")
    private val mois = Calendar.getInstance()
    private var jour = Calendar.getInstance()
    private lateinit var titreMois: TextView
    private lateinit var grille: LinearLayout
    private lateinit var liste: LinearLayout
    private lateinit var titreJour: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(14), dp(18), dp(14), dp(14))
        }
        racine.addView(TextView(this).apply {
            text = "📅 Agenda"; setTextColor(orange); textSize = 26f; typeface = Typeface.DEFAULT_BOLD
        })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(bouton("◀") { mois.add(Calendar.MONTH, -1); dessiner() })
        titreMois = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        nav.addView(titreMois, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(bouton("▶") { mois.add(Calendar.MONTH, 1); dessiner() })
        racine.addView(nav)
        grille = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        racine.addView(grille)
        val ligneTitre = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(4)) }
        titreJour = TextView(this).apply { setTextColor(bleu); textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
        ligneTitre.addView(titreJour, LinearLayout.LayoutParams(0, -2, 1f))
        ligneTitre.addView(bouton("＋ Ajouter") { dialogAjout() })
        racine.addView(ligneTitre)
        liste = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        racine.addView(ScrollView(this).apply { addView(liste) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(racine)
        dessiner()
        traiterIntention(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        traiterIntention(intent)
    }

    /** Lien « ➕ Ajouter en 1 clic » du chat, ou texte partagé depuis une autre appli. */
    private fun traiterIntention(intent: Intent?) {
        val donnees = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && donnees != null && donnees.scheme == AgendaStore.SCHEME) {
            val debut = runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).parse(donnees.getQueryParameter("d") + " " + donnees.getQueryParameter("h"))!!.time
            }.getOrNull()
            val titre = donnees.getQueryParameter("t")?.take(80)?.trim().orEmpty()
            if (debut != null && titre.isNotEmpty()) {
                val deja = AgendaStore.tous(this).any { it.debut == debut && it.titre == titre }
                if (!deja) AgendaStore.ajouter(this, Evenement(System.currentTimeMillis(), titre, debut, 30))
                Toast.makeText(this, if (deja) "Déjà dans l'agenda" else "✅ Ajouté : $titre", Toast.LENGTH_SHORT).show()
                jour = Calendar.getInstance().apply { timeInMillis = debut }
                mois.timeInMillis = debut
                dessiner()
            }
            setIntent(Intent())
        } else if (intent?.action == Intent.ACTION_SEND) {
            val texte = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val sujet = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
            setIntent(Intent())
            if (texte.isNotEmpty() || sujet.isNotEmpty()) dialogAjout(titreInitial = sujet.ifEmpty { texte.lineSequence().first() }.take(80), noteInitiale = texte.take(300))
        }
    }

    private fun bouton(texte: String, action: () -> Unit) = Button(this).apply {
        text = texte; isAllCaps = false; setTextColor(or); textSize = 15f
        background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#141414")); setStroke(dp(2), orange) }
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { action() }
    }

    private fun dessiner() {
        titreMois.text = SimpleDateFormat("MMMM yyyy", Locale.CANADA_FRENCH).format(mois.time).replaceFirstChar { it.uppercase() }
        grille.removeAllViews()
        val entete = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("D", "L", "M", "M", "J", "V", "S").forEach { j ->
            entete.addView(TextView(this).apply { text = j; gravity = Gravity.CENTER; setTextColor(Color.GRAY) }, LinearLayout.LayoutParams(0, dp(26), 1f))
        }
        grille.addView(entete)
        val premier = (mois.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val decalage = premier.get(Calendar.DAY_OF_WEEK) - 1
        val nbJours = premier.getActualMaximum(Calendar.DAY_OF_MONTH)
        val aujourdHui = Calendar.getInstance()
        val tous = AgendaStore.tous(this)
        var case = 0
        var ligne: LinearLayout? = null
        for (i in 0 until decalage + nbJours) {
            if (case % 7 == 0) {
                ligne = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grille.addView(ligne)
            }
            val numero = i - decalage + 1
            val vue = TextView(this).apply { gravity = Gravity.CENTER; textSize = 15f }
            if (numero >= 1) {
                val ce = (premier.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, numero) }
                val occupe = tous.any {
                    val x = Calendar.getInstance().apply { timeInMillis = it.debut }
                    x.get(Calendar.YEAR) == ce.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == ce.get(Calendar.DAY_OF_YEAR)
                }
                val estAuj = memeJour(ce, aujourdHui)
                val estChoisi = memeJour(ce, jour)
                vue.text = if (occupe) "$numero\n•" else "$numero\n "
                vue.setTextColor(if (estChoisi) Color.BLACK else if (estAuj) or else Color.WHITE)
                vue.background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (estChoisi) orange else Color.TRANSPARENT)
                    if (estAuj && !estChoisi) setStroke(dp(2), or)
                }
                vue.setOnClickListener { jour = ce; dessiner() }
            }
            ligne?.addView(vue, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            case++
        }
        while (case % 7 != 0) { ligne?.addView(View(this), LinearLayout.LayoutParams(0, dp(48), 1f)); case++ }
        titreJour.text = SimpleDateFormat("EEEE d MMMM", Locale.CANADA_FRENCH).format(jour.time).replaceFirstChar { it.uppercase() }
        afficherJour()
    }

    private fun memeJour(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun afficherJour() {
        liste.removeAllViews()
        val evts = AgendaStore.duJour(this, jour)
        if (evts.isEmpty()) {
            liste.addView(TextView(this).apply { text = "Rien de prévu — touche « ＋ Ajouter »"; setTextColor(Color.GRAY); setPadding(0, dp(10), 0, 0) })
        }
        val heure = SimpleDateFormat("HH:mm", Locale.CANADA_FRENCH)
        evts.forEach { e ->
            liste.addView(TextView(this).apply {
                text = "${com.codexlocal.chat.EmojisChat.pourSujet(e.titre + " " + e.note, "🕒")} ${heure.format(Date(e.debut))}  ${e.titre}" +
                    (if (e.rappelMin >= 0) "\n🔔 rappel ${libelleRappel(e.rappelMin)}" else "") +
                    (if (e.note.isNotBlank()) "\n📝 ${e.note}" else "")
                setTextColor(Color.WHITE); textSize = 15f
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Color.parseColor("#111111")); setStroke(dp(1), bleu) }
                setOnClickListener { optionsEvenement(e) }
            }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(6), 0, 0) })
        }
    }

    private val rappels = listOf(-1 to "Aucun rappel", 0 to "À l'heure", 10 to "10 min avant", 30 to "30 min avant", 60 to "1 h avant", 1440 to "1 jour avant")

    private fun libelleRappel(m: Int) = rappels.firstOrNull { it.first == m }?.second ?: "$m min avant"

    private fun optionsEvenement(e: Evenement) {
        AlertDialog.Builder(this)
            .setTitle(e.titre)
            .setItems(arrayOf("📲 Copier dans le calendrier du téléphone", "🗑️ Supprimer")) { _, choix ->
                if (choix == 0) {
                    runCatching {
                        startActivity(
                            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.TITLE, e.titre)
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.debut)
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.debut + 3_600_000)
                                .putExtra(CalendarContract.Events.DESCRIPTION, e.note)
                        )
                    }.onFailure { Toast.makeText(this, "Aucune appli calendrier", Toast.LENGTH_SHORT).show() }
                } else {
                    AgendaStore.supprimer(this, e); dessiner()
                }
            }
            .show()
    }

    private fun dialogAjout(titreInitial: String = "", noteInitiale: String = "") {
        val boite = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val titre = EditText(this).apply { hint = "Titre (ex. : Dentiste)"; setText(titreInitial) }
        val note = EditText(this).apply { hint = "Note (facultatif)"; setText(noteInitiale) }
        val trouve = DateTexte.trouver(titreInitial + " " + noteInitiale)
        val quand = (jour.clone() as Calendar).apply {
            val maintenant = Calendar.getInstance()
            set(Calendar.HOUR_OF_DAY, (maintenant.get(Calendar.HOUR_OF_DAY) + 1).coerceAtMost(23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            // Une date ou une heure dans le texte partagé (« demain 14h », « le 12 novembre ») est reprise.
            if (trouve != null) timeInMillis = trouve
        }
        val choixHeure = Button(this).apply { isAllCaps = false }
        fun majHeure() { choixHeure.text = "🗓️ " + SimpleDateFormat("EEE d MMM · HH:mm", Locale.CANADA_FRENCH).format(quand.time) }
        majHeure()
        choixHeure.setOnClickListener {
            DatePickerDialog(this, { _, a, m, j ->
                quand.set(a, m, j)
                TimePickerDialog(this, { _, h, mi -> quand.set(Calendar.HOUR_OF_DAY, h); quand.set(Calendar.MINUTE, mi); majHeure() },
                    quand.get(Calendar.HOUR_OF_DAY), quand.get(Calendar.MINUTE), true).show()
            }, quand.get(Calendar.YEAR), quand.get(Calendar.MONTH), quand.get(Calendar.DAY_OF_MONTH)).show()
        }
        val rappel = Spinner(this).apply {
            adapter = ArrayAdapter(this@AgendaActivity, android.R.layout.simple_spinner_dropdown_item, rappels.map { it.second })
            setSelection(3)
        }
        boite.addView(titre); boite.addView(choixHeure); boite.addView(rappel); boite.addView(note)
        AlertDialog.Builder(this)
            .setTitle("Nouveau rendez-vous")
            .setView(boite)
            .setPositiveButton("Ajouter") { _, _ ->
                val t = titre.text.toString().trim()
                if (t.isEmpty()) return@setPositiveButton
                AgendaStore.ajouter(this, Evenement(System.currentTimeMillis(), t, quand.timeInMillis, rappels[rappel.selectedItemPosition].first, note.text.toString().trim()))
                jour = (quand.clone() as Calendar); mois.timeInMillis = quand.timeInMillis
                dessiner()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
