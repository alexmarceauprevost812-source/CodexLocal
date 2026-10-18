package com.codexlocal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Telecharge l'APK d'une nouvelle version et lance son installation. */
object Updater {

    private const val PENDING = "installation_en_attente"

    /** Android verifies the signing certificate before replacing the installed app. */
    private fun valider(context: Context, apk: File) {
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        require(archive?.packageName == context.packageName) { "Cet APK n’est pas une mise à jour de CodexLocal." }
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        require(archive.longVersionCode > installed.longVersionCode) {
            "Choisissez un APK plus récent que la version installée."
        }
    }

    suspend fun importer(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val destination = File(context.cacheDir, "mise-a-jour.apk")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Impossible d’ouvrir cet APK." }
            destination.outputStream().use { input.copyTo(it) }
        }
        valider(context, destination)
        destination
    }

    /** Resume after the user grants Android's installation permission. */
    fun reprendreInstallation(context: Context) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getBoolean(PENDING, false) && context.packageManager.canRequestPackageInstalls()) {
            prefs.edit().remove(PENDING).apply()
            val apk = File(context.cacheDir, "mise-a-jour.apk")
            if (apk.exists()) runCatching { installer(context, apk) }
        }
    }

    suspend fun telecharger(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destination = File(context.cacheDir, "mise-a-jour.apk")
        if (destination.exists()) destination.delete()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CodexLocal")
        }
        try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code" }
            val total = conn.contentLengthLong
            var fait = 0L
            var dernier = -1
            conn.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val tampon = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(tampon)
                        if (n <= 0) break
                        output.write(tampon, 0, n)
                        fait += n
                        if (total > 0) {
                            val pct = ((fait * 100) / total).toInt()
                            if (pct != dernier) {
                                dernier = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        destination
    }

    /**
     * Ouvre le gestionnaire de paquets sur l'APK telecharge.
     * @return false si l'appli n'a pas encore le droit d'installer des applis
     *         (auquel cas l'ecran de reglage est ouvert).
     */
    fun installer(context: Context, apk: File): Boolean {
        valider(context, apk)
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.getSharedPreferences("updates", Context.MODE_PRIVATE)
                .edit().putBoolean(PENDING, true).apply()
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk
        )
        val intention = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intention)
            true
        }.getOrDefault(false)
    }
}
