package com.codexlocal

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

/**
 * La mise a jour est signee avec une autre cle que l'appli installee : Android refuserait de
 * l'installer par-dessus (« application non installee »). Le message est fait pour etre lu tel quel.
 */
class SignatureDifferenteException(message: String) : Exception(message)

/** Verifie, avant de lancer l'installation, que la mise a jour porte la meme signature que l'appli installee. */
object SignatureMiseAJour {

    const val MESSAGE =
        "Cette version est signée avec une autre clé de sécurité que celle de ton CodexLocal. " +
            "Android refuse alors de l'installer par-dessus l'ancienne : c'est ce qui fait échouer la mise à jour.\n\n" +
            "Pour passer à cette version, il faut désinstaller CodexLocal puis installer la nouvelle " +
            "(tes modèles, projets et sessions seront effacés). Les mises à jour suivantes se feront normalement."

    fun empreinte(certificat: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(certificat).joinToString("") { "%02x".format(it) }

    /**
     * Android accepte la mise a jour quand l'APK est signe par la cle de l'appli installee (ou par une cle
     * qui l'a officiellement remplacee : l'« historique » de la nouvelle signature la contient).
     * Si une des deux signatures est illisible, on ne bloque rien : c'est Android qui tranchera.
     */
    fun compatibles(installees: Set<String>, nouvelles: Set<String>): Boolean =
        installees.isEmpty() || nouvelles.isEmpty() || installees.any { it in nouvelles }

    /** @throws SignatureDifferenteException si Android refuserait cette mise a jour pour cause de signature. */
    fun verifier(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return
        val installee = runCatching { pm.getPackageInfo(context.packageName, flags) }.getOrNull() ?: return
        if (!compatibles(empreintes(installee, avecHistorique = false), empreintes(archive, avecHistorique = true))) {
            throw SignatureDifferenteException(MESSAGE)
        }
    }

    private fun empreintes(info: PackageInfo, avecHistorique: Boolean): Set<String> {
        val signature = info.signingInfo ?: return emptySet()
        val certificats = if (avecHistorique) {
            signature.signingCertificateHistory ?: signature.apkContentsSigners
        } else {
            signature.apkContentsSigners
        }
        return certificats.orEmpty().map { empreinte(it.toByteArray()) }.toSet()
    }
}
