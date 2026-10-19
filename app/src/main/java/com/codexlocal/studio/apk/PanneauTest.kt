package com.codexlocal.studio.apk

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.codexlocal.R
import com.codexlocal.databinding.ActivityStudioBinding
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Onglet « Test » du Studio : l'apercu du projet se met a jour tout seul pendant que l'IA
 * ecrit les fichiers, et un bouton fabrique la vraie appli (APK signe) pour l'installer et
 * l'essayer sur le telephone. Les applis fabriquees sont independantes de CodexLocal.
 */
class PanneauTest(
    private val activite: AppCompatActivity,
    private val binding: ActivityStudioBinding,
    private val projetCourant: () -> Projet?,
    private val enregistrerApk: (File, String) -> Unit
) {

    private var construit: ConstructeurApk.Resultat? = null
    private var surveillance: Job? = null
    private var instantane: Map<String, ProjectStore.EtatFichier>? = null
    private var occupe = false
    private val lignes = ArrayDeque<String>()

    @SuppressLint("SetJavaScriptEnabled")
    fun configurer() {
        binding.apercuTest.setBackgroundColor(Color.BLACK)
        binding.apercuTest.webChromeClient = WebChromeClient()
        binding.apercuTest.settings.apply {
            // C'est le code du projet de l'utilisateur : le JavaScript doit tourner comme dans l'appli.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }
        binding.btnTestConstruire.setOnClickListener { construire() }
        binding.btnTestInstaller.setOnClickListener { installer() }
        binding.btnTestOuvrir.setOnClickListener { ouvrir() }
        binding.btnTestEnregistrer.setOnClickListener { garder() }
        binding.btnTestDesinstaller.setOnClickListener { retirer() }
        majEtat()
    }

    /** Appele quand l'onglet apparait ou disparait : la surveillance ne tourne que s'il est visible. */
    fun afficher(visible: Boolean) {
        if (!visible) {
            surveillance?.cancel()
            surveillance = null
            return
        }
        if (surveillance?.isActive == true) return
        instantane = null
        surveillance = activite.lifecycleScope.launch {
            activite.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    verifier()
                    delay(INTERVALLE_MS)
                }
            }
        }
    }

    /** Un autre projet a ete choisi : tout repart de zero. */
    fun projetChange() {
        construit = null
        instantane = null
        lignes.clear()
        binding.testJournal.text = ""
        val p = projetCourant()
        if (p != null) binding.apercuTest.webViewClient = ApercuProjet(activite, p)
        majEtat()
        if (binding.panneauTest.isVisible) activite.lifecycleScope.launch { verifier() }
    }

    fun fermer() {
        surveillance?.cancel()
        (binding.apercuTest.parent as? android.view.ViewGroup)?.removeView(binding.apercuTest)
        binding.apercuTest.destroy()
    }

    /** Retour de l'ecran d'installation d'Android : l'etat de l'appli a peut-etre change. */
    fun auRetour() {
        InstallateurApk.reprendre(activite)
        majEtat()
    }

    // ----------------------------------------------------- apercu en direct

    private suspend fun verifier() {
        val p = projetCourant() ?: return
        val maintenant = withContext(Dispatchers.IO) {
            runCatching { ProjectStore.instantane(activite, p) }.getOrNull()
        } ?: return
        if (projetCourant()?.uri != p.uri) return
        val avant = instantane
        if (avant == maintenant) return
        instantane = maintenant

        if (avant != null) {
            val changes = decrireChangements(avant, maintenant)
            changes.take(MAX_LIGNES_CHANGEMENT).forEach { journal(it) }
            if (changes.size > MAX_LIGNES_CHANGEMENT) {
                journal("   … et ${changes.size - MAX_LIGNES_CHANGEMENT} autre(s)")
            }
        }
        if (ConstructeurApk.PAGE_ACCUEIL in maintenant) {
            if (binding.apercuTest.webViewClient !is ApercuProjet) {
                binding.apercuTest.webViewClient = ApercuProjet(activite, p)
            }
            binding.apercuTest.clearCache(true)
            binding.apercuTest.loadUrl(ApercuProjet.adresseAccueil())
            if (avant != null) journal("↻ Aperçu mis à jour")
        } else {
            val page = "<html><body style='background:#000;color:#ff9a3d;font-family:sans-serif;" +
                "padding:24px'>" + activite.getString(R.string.test_sans_page).replace("\n", "<br>") +
                "</body></html>"
            binding.apercuTest.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
        }
    }

    // ------------------------------------------------------------ APK

    private fun construire() {
        val p = projetCourant()
        if (p == null) {
            Toast.makeText(activite, R.string.test_aucun_projet, Toast.LENGTH_SHORT).show()
            return
        }
        if (occupe) return
        definirOccupe(true)
        lignes.clear()
        binding.testJournal.text = ""
        activite.lifecycleScope.launch {
            try {
                val resultat = withContext(Dispatchers.IO) {
                    ConstructeurApk.construire(activite, p) { ligne ->
                        activite.runOnUiThread { journal(ligne) }
                    }
                }
                construit = resultat
                journal("👉 Appuie sur 📲 Installer pour l'essayer sur ton téléphone.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                construit = null
                journal("❌ " + (e.message ?: e.toString()))
            } finally {
                definirOccupe(false)
                majEtat()
            }
        }
    }

    private fun installer() {
        val resultat = construit
        if (resultat == null) {
            Toast.makeText(activite, R.string.test_construire_d_abord, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (InstallateurApk.installer(activite, resultat.fichier)) {
                journal(activite.getString(R.string.test_installation_lancee))
                journal(activite.getString(R.string.test_conflit_cle))
            } else {
                journal(activite.getString(R.string.test_permission))
            }
        } catch (e: Exception) {
            journal("❌ " + (e.message ?: activite.getString(R.string.test_echec_install)))
        }
    }

    private fun ouvrir() {
        val id = construit?.identifiant ?: projetCourant()?.let { ConstructeurApk.identifiantPour(it) } ?: return
        if (!InstallateurApk.ouvrir(activite, id)) {
            Toast.makeText(activite, R.string.test_pas_installee, Toast.LENGTH_SHORT).show()
        }
    }

    private fun garder() {
        val resultat = construit
        if (resultat == null) {
            Toast.makeText(activite, R.string.test_construire_d_abord, Toast.LENGTH_SHORT).show()
            return
        }
        enregistrerApk(resultat.fichier, resultat.nom.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk")
    }

    private fun retirer() {
        val id = projetCourant()?.let { ConstructeurApk.identifiantPour(it) } ?: return
        if (InstallateurApk.versionInstallee(activite, id) == null) {
            Toast.makeText(activite, R.string.test_pas_installee, Toast.LENGTH_SHORT).show()
            return
        }
        InstallateurApk.desinstaller(activite, id)
    }

    // ------------------------------------------------------------ etat

    private fun definirOccupe(valeur: Boolean) {
        occupe = valeur
        binding.btnTestConstruire.isEnabled = !valeur
        binding.btnTestConstruire.text = activite.getString(
            if (valeur) R.string.test_en_cours else R.string.test_construire
        )
    }

    /** Nom, identifiant et etat d'installation de l'appli du projet courant. */
    fun majEtat() {
        val p = projetCourant()
        if (p == null) {
            binding.testInfo.text = activite.getString(R.string.test_aucun_projet)
            return
        }
        val id = ConstructeurApk.identifiantPour(p)
        val installee = InstallateurApk.versionInstallee(activite, id)
        val etat = when {
            installee == null -> activite.getString(R.string.test_etat_pas_installee)
            construit != null && installee >= construit!!.versionCode -> activite.getString(R.string.test_etat_a_jour)
            construit != null -> activite.getString(R.string.test_etat_ancienne)
            else -> activite.getString(R.string.test_etat_installee, installee.toInt())
        }
        binding.testInfo.text = activite.getString(R.string.test_info, ConstructeurApk.nomAppli(p.nom), id, etat)
    }

    private fun journal(ligne: String) {
        lignes.addLast(ligne)
        while (lignes.size > MAX_LIGNES_JOURNAL) lignes.removeFirst()
        binding.testJournal.text = lignes.joinToString("\n")
    }

    companion object {
        private const val INTERVALLE_MS = 1500L
        private const val MAX_LIGNES_JOURNAL = 40
        private const val MAX_LIGNES_CHANGEMENT = 4

        /** « ➕ a.html », « ✏️ b.js », « ➖ c.css » : ce qui a bouge entre deux photos du projet. */
        fun decrireChangements(
            avant: Map<String, ProjectStore.EtatFichier>,
            apres: Map<String, ProjectStore.EtatFichier>
        ): List<String> {
            val lignes = ArrayList<String>()
            apres.forEach { (chemin, etat) ->
                val ancien = avant[chemin]
                if (ancien == null) lignes.add("➕ $chemin") else if (ancien != etat) lignes.add("✏️ $chemin")
            }
            avant.keys.filter { it !in apres }.forEach { lignes.add("➖ $it") }
            return lignes
        }
    }
}
