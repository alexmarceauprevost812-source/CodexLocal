package com.codexlocal.studio

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

/** Ce qu'on affiche d'une clé : à qui elle est, son empreinte, sa date d'expiration. */
data class InfoCle(val nom: String, val empreinte: String, val expire: Date)

/**
 * Clé de signature du Studio, créée et gardée sur le téléphone (PKCS12).
 * Le fichier et son mot de passe restent dans l'espace privé de l'appli et sont
 * exclus des sauvegardes cloud. L'exportation sert de copie de secours : sans la
 * clé d'origine, Android refuse les mises à jour d'une appli déjà signée.
 */
object CleStudio {

    const val ALIAS = "studio"

    private const val PREFS = "codexlocal_cles"
    private const val CLE_MDP = "mdp"
    private const val JOURS_VALIDITE = 30L * 365
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"

    private fun dossier(context: Context) = File(context.filesDir, "cles").apply { mkdirs() }
    private fun fichier(context: Context) = File(dossier(context), "studio.p12")
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun motDePasse(context: Context): String? = prefs(context).getString(CLE_MDP, null)

    fun existe(context: Context): Boolean =
        fichier(context).isFile && motDePasse(context) != null

    /** Garde lettres, chiffres, espace, tiret, souligné et point : rien qui casse le nom X.500. */
    fun nomValide(brut: String): String {
        val propre = brut.filter {
            it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '.'
        }.trim().take(60)
        require(propre.isNotEmpty()) { "Nom de clé invalide" }
        return propre
    }

    /** Crée une nouvelle clé RSA 2048 bits (valide 30 ans) et remplace l'ancienne. */
    fun creer(context: Context, nomCommun: String): InfoCle {
        val cn = nomValide(nomCommun)
        val paire = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048, SecureRandom()) }
            .generateKeyPair()

        val sujet = X500Name("CN=$cn, O=CodexLocal")
        val debut = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
        val fin = Date(debut.time + JOURS_VALIDITE * 24L * 3600 * 1000)
        val serie = BigInteger(64, SecureRandom()).add(BigInteger.ONE)
        val constructeur =
            JcaX509v3CertificateBuilder(sujet, serie, debut, fin, sujet, paire.public)
        val signataire = JcaContentSignerBuilder("SHA256withRSA").build(paire.private)
        val certificat =
            JcaX509CertificateConverter().getCertificate(constructeur.build(signataire))

        val mdp = motDePasseAleatoire()
        val magasin = KeyStore.getInstance("PKCS12")
        magasin.load(null, null)
        magasin.setKeyEntry(ALIAS, paire.private, mdp.toCharArray(), arrayOf(certificat))

        val temporaire = File(dossier(context), "studio.p12.tmp")
        try {
            temporaire.outputStream().use { magasin.store(it, mdp.toCharArray()) }
            temporaire.copyTo(fichier(context), overwrite = true)
        } finally {
            temporaire.delete()
        }
        check(prefs(context).edit().putString(CLE_MDP, mdp).commit()) {
            "Impossible d'enregistrer le mot de passe de la clé"
        }
        return decrire(certificat)
    }

    fun charger(context: Context): Pair<PrivateKey, X509Certificate> {
        val mdp = motDePasse(context)?.toCharArray() ?: error("Aucune clé : crée-en une d'abord.")
        val magasin = KeyStore.getInstance("PKCS12")
        fichier(context).inputStream().use { magasin.load(it, mdp) }
        val cle = magasin.getKey(ALIAS, mdp) as? PrivateKey ?: error("Clé illisible")
        val certificat = magasin.getCertificate(ALIAS) as? X509Certificate
            ?: error("Certificat illisible")
        return Pair(cle, certificat)
    }

    fun info(context: Context): InfoCle? =
        if (!existe(context)) null
        else runCatching { decrire(charger(context).second) }.getOrNull()

    /** Copie le fichier .p12 vers une destination choisie par l'utilisateur (sauvegarde). */
    fun copierVers(context: Context, sortie: OutputStream) {
        fichier(context).inputStream().use { it.copyTo(sortie) }
    }

    fun empreinte(certificat: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificat.encoded)
            .joinToString(":") { "%02X".format(it) }

    private fun decrire(certificat: X509Certificate) =
        InfoCle(certificat.subjectX500Principal.name, empreinte(certificat), certificat.notAfter)

    private fun motDePasseAleatoire(): String {
        val hasard = SecureRandom()
        return (1..24).map { ALPHABET[hasard.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
