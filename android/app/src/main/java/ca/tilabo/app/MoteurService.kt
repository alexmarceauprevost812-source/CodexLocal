package ca.tilabo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Garde le moteur gguf et son serveur (127.0.0.1:8081) en vie,
 * meme quand l'appli est en arriere-plan (Termux s'y branche).
 */
class MoteurService : Service() {

    companion object {
        private const val CANAL = "moteur"
        private const val NOTIF = 1
        const val ACTION_ARRETER = "ca.tilabo.app.ARRETER"

        @Volatile var actif: MoteurService? = null
            private set

        fun demarrer(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, MoteurService::class.java))
        }

        fun majNotification(ctx: Context) {
            val s = actif ?: return
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF, s.notification())
        }
    }

    private var serveur: ServeurLocal? = null
    private var reveil: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        actif = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Moteur d'IA", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Le moteur gguf du Ti-Labo roule sur le téléphone"
            }
        )
        startForeground(NOTIF, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        reveil = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TiLabo:moteur").apply { acquire() }
        runCatching { Rappels.canal(this); Rappels.planifier(applicationContext) }
        serveur = ServeurLocal(applicationContext).also {
            try {
                it.demarrer()
            } catch (e: Exception) {
                Log.e("TiLabo", "Port 8081 deja pris (llama-server dans Termux ?)", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ARRETER) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serveur?.arreter()
        serveur = null
        Thread { kotlinx.coroutines.runBlocking { Moteur.decharger() } }.start()
        runCatching { reveil?.release() }
        actif = null
        super.onDestroy()
    }

    fun notification(): Notification {
        val ouvrir = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val arreter = PendingIntent.getService(
            this, 1, Intent(this, MoteurService::class.java).setAction(ACTION_ARRETER),
            PendingIntent.FLAG_IMMUTABLE
        )
        val texte = when {
            Moteur.enChargement != null -> "Chargement de ${Moteur.enChargement}…"
            Moteur.nom != null -> "IA prête : ${Moteur.nom}"
            else -> "Moteur gguf prêt — aucune IA chargée"
        }
        return Notification.Builder(this, CANAL)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Ti-Labo")
            .setContentText(texte)
            .setContentIntent(ouvrir)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null as android.graphics.drawable.Icon?, "Arrêter", arreter).build())
            .build()
    }
}
