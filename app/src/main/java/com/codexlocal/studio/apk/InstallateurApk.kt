package com.codexlocal.studio.apk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** Installe, ouvre et desinstalle les applis fabriquees par le Studio (sur le telephone, sans PC). */
object InstallateurApk {

    private const val PREFS = "studio_installation"
    private const val EN_ATTENTE = "apk_en_attente"

    /**
     * Ouvre l'installateur d'Android sur l'APK.
     * @return false si CodexLocal n'a pas encore le droit d'installer des applis : l'ecran de
     * reglage s'ouvre, et l'installation reprend toute seule au retour (voir [reprendre]).
     */
    fun installer(context: Context, apk: File): Boolean {
        require(apk.isFile) { "APK introuvable : refais la construction." }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(EN_ATTENTE, apk.absolutePath).apply()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intention = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intention); true }.getOrDefault(false)
    }

    /** A appeler au retour de l'ecran de reglage : relance l'installation demandee avant. */
    fun reprendre(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val chemin = prefs.getString(EN_ATTENTE, null) ?: return
        if (!context.packageManager.canRequestPackageInstalls()) return
        prefs.edit().remove(EN_ATTENTE).apply()
        val apk = File(chemin)
        if (apk.isFile) runCatching { installer(context, apk) }
    }

    /** Version de l'appli installee, ou null si elle ne l'est pas. */
    fun versionInstallee(context: Context, identifiant: String): Long? =
        try {
            context.packageManager.getPackageInfo(identifiant, 0).longVersionCode
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

    /** Lance l'appli installee ; faux si elle n'est pas installee. */
    fun ouvrir(context: Context, identifiant: String): Boolean {
        val intention = context.packageManager.getLaunchIntentForPackage(identifiant) ?: return false
        return runCatching {
            context.startActivity(intention.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    /** Demande a Android de desinstaller l'appli (utile si la cle de signature a change). */
    fun desinstaller(context: Context, identifiant: String): Boolean =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$identifiant"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
}
