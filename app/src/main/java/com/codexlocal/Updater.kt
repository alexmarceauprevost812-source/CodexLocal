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
        if (!context.packageManager.canRequestPackageInstalls()) {
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
