package com.codexlocal.studio.apk

import android.content.Context
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import com.codexlocal.studio.CleStudio
import com.codexlocal.studio.SignateurApk
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.CRC32

/**
 * Fabrique une vraie appli Android a partir d'un projet, directement sur le telephone :
 *  1. on part du modele (module :shell, un WebView sans permission) livre dans les assets ;
 *  2. on y met les fichiers du projet (assets/www), son nom, son identifiant, son icone ;
 *  3. on signe avec la cle du Studio.
 * L'appli obtenue s'installe a cote de CodexLocal et fonctionne hors-ligne.
 */
object ConstructeurApk {

    const val MODELE = "modele-shell.apk"
    const val REPERE_ID = "com.codexlocal.app.p00000000"
    const val REPERE_NOM = "CXLNOMAPPPLACEHOLDER0000"
    const val PAGE_ACCUEIL = "index.html"
    private const val WWW = "assets/www/"
    private const val NOM_MAX = 30

    class Resultat(
        val fichier: File,
        val identifiant: String,
        val nom: String,
        val versionCode: Int,
        val empreinte: String,
        val nbFichiers: Int
    )

    /** Identifiant stable d'un projet : le meme projet met a jour la meme appli installee. */
    fun identifiantPour(projet: Projet): String {
        val crc = CRC32().apply { update(projet.uri.toByteArray(Charsets.UTF_8)) }.value
        return "com.codexlocal.app.p" + String.format("%08x", crc)
    }

    /** Nom affiche sous l'icone : celui du projet, sans caracteres de controle, 30 caracteres au plus. */
    fun nomAppli(nomProjet: String): String {
        val propre = nomProjet.filter { !Character.isISOControl(it) }.replace('_', ' ').trim().take(NOM_MAX)
        return propre.ifBlank { "Mon appli" }
    }

    /** Numero de version qui grandit a chaque construction (minutes depuis 1970). */
    fun versionCodeMaintenant(): Int = (System.currentTimeMillis() / 60_000L).toInt()

    /** Construit, signe et verifie l'APK du projet. A appeler hors du fil principal. */
    fun construire(
        context: Context,
        projet: Projet,
        journal: (String) -> Unit = {}
    ): Resultat {
        val modele = try {
            context.assets.open(MODELE).use { it.readBytes() }
        } catch (e: FileNotFoundException) {
            error("Cette version de CodexLocal ne contient pas le modèle d'application.")
        }

        journal("📂 Lecture des fichiers du projet…")
        val fichiers = ProjectStore.lireTousLesFichiers(context, projet)
        journal("   ${fichiers.size} fichier(s)")

        val identifiant = identifiantPour(projet)
        val nom = nomAppli(projet.nom)
        val version = versionCodeMaintenant()

        journal("🧱 Assemblage de « $nom » ($identifiant)…")
        val nonSigne = assembler(modele, fichiers, nom, identifiant, version) { journal("   $it") }

        val dossier = File(context.cacheDir, "studio_apk").apply { mkdirs() }
        val brut = File(dossier, "$identifiant-brut.apk")
        val signe = File(dossier, "$identifiant.apk")
        try {
            brut.writeBytes(nonSigne)
            if (!CleStudio.existe(context)) {
                journal("🔑 Création de ta clé de signature (une seule fois)…")
                CleStudio.creer(context, "Studio CodexLocal")
            }
            journal("✍️ Signature de l'APK…")
            val empreinte = SignateurApk.signer(context, brut, signe)
            journal("✅ APK prêt : ${signe.length() / 1024} Ko, signature vérifiée")
            return Resultat(signe, identifiant, nom, version, empreinte, fichiers.size)
        } finally {
            brut.delete()
        }
    }

    /**
     * Assemble l'APK non signe : modele + projet. Separe de [construire] pour pouvoir le tester
     * sans cle ni telephone.
     */
    internal fun assembler(
        modele: ByteArray,
        fichiers: List<ProjectStore.EntreeFichier>,
        nom: String,
        identifiant: String,
        version: Int,
        journal: (String) -> Unit = {}
    ): ByteArray {
        require(fichiers.any { it.chemin == PAGE_ACCUEIL }) {
            "Il manque le fichier index.html à la racine du projet : c'est la page d'accueil de l'appli. " +
                "Demande à l'IA de le créer, ou ajoute-le avec ＋."
        }

        val entrees = ApkZip.lire(modele)
        val manifeste = entrees.firstOrNull { it.nom == "AndroidManifest.xml" }
            ?: error("Le modèle d'application est incomplet (manifeste absent).")
        val retouche = ManifesteBinaire.retoucher(
            manifeste.octets, mapOf(REPERE_ID to identifiant, REPERE_NOM to nom), version
        )
        check(REPERE_ID in retouche.remplacees && REPERE_NOM in retouche.remplacees) {
            "Le modèle d'application ne contient pas les repères attendus : reconstruis CodexLocal."
        }

        // Icone : une image du projet (icone.png…), sinon celle du modele.
        val cheminIcone = IconesApk.trouver(fichiers.map { it.chemin })
        val imageIcone = cheminIcone?.let { c -> fichiers.first { it.chemin == c }.octets }
        val icones = HashMap<String, ByteArray>()
        if (imageIcone != null) {
            for ((densite, taille) in IconesApk.TAILLES) {
                IconesApk.versPng(imageIcone, taille)?.let { icones[densite] = it }
            }
            if (icones.isEmpty()) journal("⚠️ Image d'icône illisible, icône par défaut gardée")
            else journal("🖼️ Icône : $cheminIcone")
        }

        val sortie = ArrayList<ApkZip.Entree>()
        for (e in entrees) {
            when {
                e.nom == "AndroidManifest.xml" ->
                    sortie.add(ApkZip.Entree(e.nom, retouche.octets, true))
                // Signatures et metadonnees du modele : la nouvelle signature les remplace.
                e.nom.startsWith("META-INF/") -> Unit
                // La page d'exemple du modele cede la place au projet.
                e.nom.startsWith(WWW) -> Unit
                else -> {
                    val remplacement = IconesApk.densite(e.nom)?.let { icones[it] }
                    sortie.add(if (remplacement != null) ApkZip.Entree(e.nom, remplacement, false) else e)
                }
            }
        }
        for (f in fichiers) {
            sortie.add(ApkZip.Entree(WWW + f.chemin, f.octets, ApkZip.doitEtreCompresse(f.chemin)))
        }

        val resultat = java.io.ByteArrayOutputStream()
        ApkZip.ecrire(resultat, sortie)
        return resultat.toByteArray()
    }
}
