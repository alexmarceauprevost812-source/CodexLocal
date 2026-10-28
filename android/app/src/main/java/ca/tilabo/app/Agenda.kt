package ca.tilabo.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Agenda du Ti-Labo : TiLabo/agenda.json sur le telephone, donnees/agenda.json sur la cle USB-C
 * (le meme format que le Ti-Labo de la cle). Les deux se fusionnent quand la cle est branchee :
 * pour chaque evenement, la version modifiee le plus recemment gagne (les suppressions aussi).
 */
object Agenda {

    private val TEL = File("/storage/emulated/0/TiLabo/agenda.json")
    private val verrou = Any()

    private fun lire(f: File): MutableList<JSONObject> = runCatching {
        val a = JSONObject(f.readText()).optJSONArray("evenements") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
    }.getOrDefault(mutableListOf())

    private fun ecrire(f: File, evts: List<JSONObject>) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(JSONObject().put("version", 1).put("evenements", JSONArray(evts)).toString(1))
        if (!tmp.renameTo(f)) { f.writeText(tmp.readText()); tmp.delete() }
    }

    /** Le fichier d'agenda du Ti-Labo de la cle (le dossier qui contient app/server.py). */
    fun fichierCle(): File? {
        File("/storage").listFiles()?.sortedBy { it.name }?.forEach { cle ->
            if (cle.name == "emulated" || cle.name == "self" || !cle.isDirectory) return@forEach
            for (r in listOf("TiLabo/TiLabo", "TiLabo")) {
                val racine = File(cle, r)
                if (File(racine, "app/server.py").isFile) return File(racine, "donnees/agenda.json")
            }
        }
        return null
    }

    fun lister(ctx: Context): JSONObject {
        val cle = fichierCle()
        // Cle branchee : on fusionne tout de suite (le Ti-Labo du PC a peut-etre ajoute des evenements)
        if (cle != null) runCatching { synchroniser(ctx) }
        val evts = synchronized(verrou) { lire(TEL) }.filter { !it.optBoolean("supprime") }
        return JSONObject().put("evenements", JSONArray(evts))
            .put("cle", JSONObject().put("branchee", cle != null))
    }

    fun modifier(ctx: Context, d: JSONObject): JSONObject {
        synchronized(verrou) {
            val evts = lire(TEL)
            val maintenant = System.currentTimeMillis()
            val id = d.optString("supprimer")
            if (id.isNotEmpty()) {
                evts.filter { it.optString("id") == id }.forEach { it.put("supprime", true).put("modifie", maintenant) }
            } else {
                val ev = d.optJSONObject("evenement") ?: throw IllegalArgumentException("Événement manquant")
                require(ev.optString("id").isNotEmpty() && ev.optString("debut").isNotEmpty()) { "Événement invalide" }
                val propre = JSONObject()
                for (k in listOf("id", "titre", "debut", "fin", "journee", "couleur", "lieu", "note", "rappel", "repete")) {
                    propre.put(k, ev.opt(k) ?: JSONObject.NULL)
                }
                propre.put("modifie", maintenant).put("supprime", false)
                evts.removeAll { it.optString("id") == propre.optString("id") }
                evts.add(propre)
            }
            ecrire(TEL, evts)
        }
        // Cle branchee : on la garde a jour tout de suite
        if (fichierCle() != null) runCatching { synchroniser(ctx) }
        Rappels.planifier(ctx)
        return JSONObject().put("ok", true)
    }

    fun synchroniser(ctx: Context): JSONObject {
        val cle = fichierCle() ?: throw IllegalStateException("Branche la clé USB-C (avec le Ti-Labo) pour synchroniser l'agenda.")
        synchronized(verrou) {
            val tous = HashMap<String, JSONObject>()
            for (e in lire(cle) + lire(TEL)) {
                val id = e.optString("id"); if (id.isEmpty()) continue
                val avant = tous[id]
                if (avant == null || e.optLong("modifie") > avant.optLong("modifie")) tous[id] = e
            }
            val liste = tous.values.sortedBy { it.optString("debut") }
            ecrire(TEL, liste)
            ecrire(cle, liste)
            Rappels.planifier(ctx)
            return JSONObject().put("ok", true).put("total", liste.count { !it.optBoolean("supprime") })
        }
    }

    /** Les evenements actifs (pour les rappels). */
    fun actifs(): List<JSONObject> = synchronized(verrou) { lire(TEL) }.filter { !it.optBoolean("supprime") }
}

/** Notifications de rappel (AlarmManager, sans permission speciale : quelques minutes de marge possibles). */
object Rappels {

    private const val CANAL = "rappels"
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    private fun debut(ev: JSONObject): LocalDateTime? = runCatching {
        val s = ev.optString("debut")
        if (s.length > 10) LocalDateTime.parse(s.take(16), FMT) else LocalDate.parse(s.take(10)).atTime(9, 0)
    }.getOrNull()

    /** Les debuts des 8 prochains jours (repetitions comprises). */
    private fun prochains(ev: JSONObject, jusqua: LocalDateTime): List<LocalDateTime> {
        val d0 = debut(ev) ?: return emptyList()
        val res = mutableListOf<LocalDateTime>()
        var d = d0
        var garde = 0
        while (d <= jusqua && garde++ < 2000) {
            if (d >= LocalDateTime.now().minusDays(1)) res += d
            d = when (ev.optString("repete")) {
                "jour" -> d.plusDays(1)
                "semaine" -> d.plusWeeks(1)
                "mois" -> d0.plusMonths(java.time.temporal.ChronoUnit.MONTHS.between(d0, d) + 1)
                "annee" -> d.plusYears(1)
                else -> return res
            }
        }
        return res
    }

    fun planifier(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences("tilabo-rappels", Context.MODE_PRIVATE)
        // on annule les anciens
        prefs.getStringSet("codes", emptySet())?.forEach { c ->
            c.toIntOrNull()?.let { am.cancel(intention(ctx, it, "", "", "")) }
        }
        val codes = HashSet<String>()
        val maintenant = System.currentTimeMillis()
        val limite = LocalDateTime.now().plusDays(8)
        for (ev in Agenda.actifs()) {
            if (ev.isNull("rappel") || !ev.has("rappel")) continue
            val minutes = ev.optInt("rappel", -1); if (minutes < 0) continue
            for (d in prochains(ev, limite)) {
                val quand = d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - minutes * 60_000L
                if (quand <= maintenant) continue
                val code = (ev.optString("id") + d).hashCode()
                val heure = if (ev.optBoolean("journee")) "Toute la journée" else d.toLocalTime().toString().take(5)
                val texte = (if (minutes == 0) "Maintenant" else if (minutes >= 1440) "Demain" else "Dans $minutes min") +
                    " · $heure" + (ev.optString("lieu").takeIf { it.isNotBlank() && it != "null" }?.let { " · 📍 $it" } ?: "")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quand,
                    intention(ctx, code, ev.optString("titre", "Événement"), texte, ev.optString("couleur")))
                codes += code.toString()
                if (codes.size > 400) break
            }
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }

    private fun intention(ctx: Context, code: Int, titre: String, texte: String, couleur: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, code,
            Intent(ctx, RappelRecepteur::class.java).putExtra("titre", titre).putExtra("texte", texte).putExtra("code", code)
                .putExtra("couleur", couleur),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun canal(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL, "Rappels de l'agenda", NotificationManager.IMPORTANCE_HIGH))
    }

    fun montrer(ctx: Context, i: Intent) {
        canal(ctx)
        val ouvrir = PendingIntent.getActivity(ctx, 7,
            Intent(ctx, MainActivity::class.java).putExtra("page", "agenda"), PendingIntent.FLAG_IMMUTABLE)
        val couleur = runCatching { android.graphics.Color.parseColor(i.getStringExtra("couleur")) }.getOrDefault(0xFFFF7A1A.toInt())
        val n = android.app.Notification.Builder(ctx, CANAL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("🗓️ " + (i.getStringExtra("titre") ?: "Événement"))
            .setContentText(i.getStringExtra("texte"))
            .setColor(couleur)
            .setContentIntent(ouvrir)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(i.getIntExtra("code", 0), n)
    }
}

class RappelRecepteur : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Rappels.planifier(ctx)
            return
        }
        Rappels.montrer(ctx, intent)
    }
}
