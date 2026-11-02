package com.codexlocal.outils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.codexlocal.R
import com.codexlocal.web.RechercheRiche

/**
 * 📰 Notifications des nouvelles (APK perso) : environ toutes les 3 heures, les manchettes
 * à la une (Google Actualités, Québec/Canada) arrivent en notification. Seulement les
 * nouvelles manchettes : une même nouvelle n'est jamais notifiée deux fois.
 */
object NotifNouvelles {

    const val CANAL = "nouvelles"
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("outils", Context.MODE_PRIVATE)

    fun actives(c: Context) = sp(c).getBoolean("notifNouvelles", true)

    fun definir(c: Context, actif: Boolean) {
        sp(c).edit().putBoolean("notifNouvelles", actif).apply()
        if (actif) programmer(c) else annuler(c)
    }

    private fun intention(c: Context) = PendingIntent.getBroadcast(
        c, 4242, Intent(c, RecepteurNouvelles::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** À appeler au démarrage de l'appli : (re)programme la tournée des nouvelles. */
    fun programmer(c: Context) {
        if (!actives(c)) return
        // Déjà programmée : on garde la prochaine heure (rouvrir l'appli ne la repousse pas).
        val existante = PendingIntent.getBroadcast(
            c, 4242, Intent(c, RecepteurNouvelles::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existante != null) return
        val intervalle = AlarmManager.INTERVAL_HOUR * 3
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setInexactRepeating(
            AlarmManager.RTC, System.currentTimeMillis() + intervalle, intervalle, intention(c)
        )
    }

    fun annuler(c: Context) {
        val pi = intention(c)
        (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
    }

    /** Télécharge et notifie (bloquant, hors du fil principal). */
    fun verifier(c: Context) {
        if (!actives(c)) return
        if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val deja = sp(c).getStringSet("vues", emptySet()).orEmpty()
        val nouvelles = runCatching { RechercheRiche.manchettes(5) }.getOrDefault(emptyList())
            .filter { it.first !in deja }.take(3)
        if (nouvelles.isEmpty()) return
        sp(c).edit().putStringSet("vues", (deja + nouvelles.map { it.first }).toList().takeLast(60).toSet()).apply()
        val gestion = c.getSystemService(NotificationManager::class.java)
        gestion.createNotificationChannel(NotificationChannel(CANAL, "Nouvelles du jour", NotificationManager.IMPORTANCE_DEFAULT))
        nouvelles.forEachIndexed { i, (titre, lien, source) ->
            val ouvrir = PendingIntent.getActivity(
                c, 5000 + i, Intent(Intent.ACTION_VIEW, Uri.parse(lien)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(c, CANAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(com.codexlocal.chat.EmojisChat.pourSujet(titre, "📰") + " " + source.ifBlank { "Nouvelles" })
                .setContentText(titre)
                .setStyle(NotificationCompat.BigTextStyle().bigText(titre))
                .setGroup("nouvelles")
                .setAutoCancel(true)
                .setContentIntent(ouvrir)
                .build()
            NotificationManagerCompat.from(c).notify(9000 + (titre.hashCode() and 0xFFF), notif)
        }
    }
}

class RecepteurNouvelles : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fin = goAsync()
        Thread {
            try { NotifNouvelles.verifier(context.applicationContext) } finally { fin.finish() }
        }.start()
    }
}
