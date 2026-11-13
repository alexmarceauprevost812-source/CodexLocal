package com.codexlocal.session

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/** Pourquoi l'appli s'est fermée la dernière fois, d'après Android (rien n'est envoyé nulle part). */
object DernierArret {
    enum class Cause { MEMOIRE, MOTEUR, BLOQUEE, ERREUR }

    data class Arret(val cause: Cause, val quand: Long, val detail: String)

    /** null : fermeture normale (l'utilisateur, une mise à jour…), rien à signaler. */
    fun cause(reason: Int, status: Int): Cause? = when (reason) {
        ApplicationExitInfo.REASON_LOW_MEMORY -> Cause.MEMOIRE
        // Beaucoup de téléphones ne disent pas « mémoire » : la fermeture apparaît comme un signal 9.
        ApplicationExitInfo.REASON_SIGNALED -> if (status == 9) Cause.MEMOIRE else null
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> Cause.MEMOIRE
        ApplicationExitInfo.REASON_CRASH_NATIVE -> Cause.MOTEUR
        ApplicationExitInfo.REASON_ANR -> Cause.BLOQUEE
        ApplicationExitInfo.REASON_CRASH -> Cause.ERREUR
        else -> null
    }

    fun message(cause: Cause): String = when (cause) {
        Cause.MEMOIRE -> "La dernière fois, Android a fermé l'appli par manque de mémoire. " +
            "La mémoire du modèle est réduite ; un modèle plus petit évite ces fermetures."
        Cause.MOTEUR -> "La dernière fois, le moteur local a planté. La mémoire du modèle est réduite par précaution."
        Cause.BLOQUEE -> "La dernière fois, l'appli ne répondait plus et a été fermée."
        Cause.ERREUR -> "La dernière fois, une erreur a fermé l'appli."
    }

    /** Mémoire ou moteur : on ne prend plus la grande mémoire de conversation. */
    fun reduitLaMemoire(cause: Cause): Boolean = cause == Cause.MEMOIRE || cause == Cause.MOTEUR

    /** Dernière fermeture anormale plus récente que [dejaVu], ou null. */
    fun lire(context: Context, dejaVu: Long): Arret? = runCatching {
        val service = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = service.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull()
            ?: return null
        if (info.timestamp <= dejaVu) return null
        val c = cause(info.reason, info.status) ?: return null
        Arret(c, info.timestamp, "raison=${info.reason} statut=${info.status} " +
            "importance=${info.importance} pss=${info.pss / 1024}Mo rss=${info.rss / 1024}Mo " +
            (info.description ?: ""))
    }.getOrNull()
}
