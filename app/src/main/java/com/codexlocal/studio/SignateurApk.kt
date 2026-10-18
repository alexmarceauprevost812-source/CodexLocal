package com.codexlocal.studio

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File

/** Signe un APK avec la clé du Studio (schémas v1, v2 et v3) puis vérifie le résultat. */
object SignateurApk {

    /** Renvoie l'empreinte SHA-256 du certificat qui a signé l'APK de sortie. */
    fun signer(context: Context, entree: File, sortie: File): String {
        val (cle, certificat) = CleStudio.charger(context)
        sortie.delete()
        val config = ApkSigner.SignerConfig.Builder(CleStudio.ALIAS, cle, listOf(certificat)).build()
        ApkSigner.Builder(listOf(config))
            .setInputApk(entree)
            .setOutputApk(sortie)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setAlignmentPreserved(true)
            .setCreatedBy("CodexLocal")
            .build()
            .sign()

        val verification = ApkVerifier.Builder(sortie).build().verify()
        check(verification.isVerified) {
            "La signature n'a pas pu être vérifiée : " +
                verification.errors.joinToString { it.toString() }
        }
        return CleStudio.empreinte(certificat)
    }
}
