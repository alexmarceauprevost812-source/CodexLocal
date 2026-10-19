package com.codexlocal.projet

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.format.Formatter
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.R
import com.codexlocal.bureau.Favoris
import com.codexlocal.databinding.ActivityProjetBinding
import com.codexlocal.editor.CodeEditorActivity
import com.codexlocal.project.Dialogues
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.ImagesProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import com.codexlocal.studio.StudioActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vue « projet » comme sur GitHub, mais locale : le chemin cliquable, la barre des langages,
 * les dossiers puis les fichiers (nom exact + pastille du langage), ⭐ pour le mettre en
 * favori, ＋ pour ajouter un fichier ou un dossier, et un appui sur un fichier l'ouvre dans
 * l'editeur pour taper et modifier le code. Tout reste sur le telephone.
 */
class ProjetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProjetBinding
    private lateinit var favoris: Favoris
    private lateinit var adapter: LignesAdapter
    private lateinit var boutonEtoile: TextView

    private var projet: Projet? = null
    private var dossierCourant = ""
    private var fichiers: List<FichierProjet> = emptyList()
    private var dossiers: List<String> = emptyList()

    private val choisirImage =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) ajouterImage(uri, commeIcone = false)
        }

    private val choisirIcone =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) ajouterImage(uri, commeIcone = true)
        }

    /** Le bouton retour remonte d'un dossier avant de quitter le projet. */
    private val retour = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = ouvrirDossier(Arborescence.parent(dossierCourant))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProjetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(barres.left, barres.top, barres.right, barres.bottom)
            insets
        }
        favoris = Favoris(this)

        val uri = intent.getStringExtra(EXTRA_PROJET_URI)
        projet = ProjectStore.lister(this).firstOrNull { it.uri == uri }
        val p = projet
        if (p == null) {
            Toast.makeText(this, R.string.projet_introuvable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        dossierCourant = savedInstanceState?.getString(ETAT_DOSSIER)
            ?: intent.getStringExtra(EXTRA_DOSSIER) ?: ""

        binding.toolbar.title = p.nom
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.projet)
        configurerBoutons()
        onBackPressedDispatcher.addCallback(this, retour)

        adapter = LignesAdapter(::surClic, ::surAppuiLong)
        binding.listeProjet.layoutManager = LinearLayoutManager(this)
        binding.listeProjet.adapter = adapter
        binding.cheminProjet.movementMethod = LinkMovementMethod.getInstance()
        afficher()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ETAT_DOSSIER, dossierCourant)
    }

    /** On recharge au retour de l'editeur : le fichier a pu changer. */
    override fun onResume() {
        super.onResume()
        charger()
    }

    private fun configurerBoutons() {
        val menu = binding.toolbar.menu
        boutonEtoile = menu.findItem(R.id.action_etoile).actionView as TextView
        val boutonAjouter = menu.findItem(R.id.action_ajouter).actionView as TextView
        val boutonParametres = menu.findItem(R.id.action_parametres_projet).actionView as TextView
        boutonAjouter.text = "＋"
        boutonAjouter.contentDescription = getString(R.string.projet_ajouter)
        boutonParametres.text = "⚙️"
        boutonParametres.contentDescription = getString(R.string.projet_parametres)
        listOf(boutonEtoile, boutonAjouter, boutonParametres)
            .forEach { it.setTextColor(getColor(R.color.white)) }
        boutonEtoile.setOnClickListener { basculerFavori() }
        boutonAjouter.setOnClickListener { menuAjouter() }
        boutonParametres.setOnClickListener { menuParametres() }
        majEtoile()
    }

    private fun majEtoile() {
        val p = projet ?: return
        val actif = favoris.estFavori(Favoris.cleProjet(p.uri))
        boutonEtoile.text = if (actif) "⭐" else "☆"
        boutonEtoile.contentDescription = getString(R.string.projet_favori)
    }

    private fun basculerFavori() {
        val p = projet ?: return
        val maintenant = favoris.basculer(Favoris.cleProjet(p.uri))
        majEtoile()
        Toast.makeText(
            this,
            if (maintenant) R.string.bureau_favori_ajoute else R.string.bureau_favori_retire,
            Toast.LENGTH_SHORT
        ).show()
    }

    // ------------------------------------------------------------ contenu

    private fun charger() {
        val p = projet ?: return
        lifecycleScope.launch {
            val (listeFichiers, listeDossiers) = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.listerFichiers(this@ProjetActivity, p) }
                    .getOrDefault(emptyList()) to
                    runCatching { ProjectStore.listerDossiers(this@ProjetActivity, p) }
                        .getOrDefault(emptyList())
            }
            // Comme sur GitHub, on voit aussi les images du projet (elles partent dans l'APK).
            val images = withContext(Dispatchers.IO) {
                runCatching {
                    ProjectStore.instantane(this@ProjetActivity, p)
                        .filterKeys { ImagesProjet.estImage(it) }
                        .map { (chemin, etat) ->
                            FichierProjet(chemin.substringAfterLast('/'), chemin, "", etat.taille, p.interne)
                        }
                }.getOrDefault(emptyList())
            }
            fichiers = listeFichiers + images
            dossiers = listeDossiers
            // Si le dossier courant a disparu, on revient a la racine.
            if (dossierCourant.isNotEmpty() &&
                Arborescence.contenu(dossierCourant, fichiers, dossiers).let { it.dossiers.isEmpty() && it.fichiers.isEmpty() } &&
                dossierCourant !in dossiers
            ) dossierCourant = ""
            afficher()
        }
    }

    private fun ouvrirDossier(chemin: String) {
        dossierCourant = chemin
        afficher()
    }

    private fun afficher() {
        val p = projet ?: return
        val contenu = Arborescence.contenu(dossierCourant, fichiers, dossiers)
        val lignes = ArrayList<Ligne>()
        if (dossierCourant.isNotEmpty()) lignes.add(Ligne.Haut)
        contenu.dossiers.forEach { lignes.add(Ligne.DossierLigne(it)) }
        contenu.fichiers.forEach {
            lignes.add(Ligne.FichierLigne(it, favoris.estFavori(Favoris.cleFichier(p.uri, it.chemin))))
        }
        if (contenu.dossiers.isEmpty() && contenu.fichiers.isEmpty()) {
            lignes.add(Ligne.Info(getString(R.string.projet_dossier_vide)))
        }
        adapter.remplacer(lignes)
        retour.isEnabled = dossierCourant.isNotEmpty()
        binding.toolbar.subtitle = getString(R.string.projet_sous_titre, fichiers.size)
        afficherChemin(p)
        afficherLangages()
    }

    private fun afficherChemin(p: Projet) {
        val orange = getColor(R.color.orange)
        val blanc = getColor(R.color.white)
        val texte = SpannableStringBuilder()
        fun etape(libelle: String, chemin: String, dernier: Boolean) {
            val debut = texte.length
            texte.append(libelle)
            texte.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = ouvrirDossier(chemin)
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = if (dernier) blanc else orange
                    ds.isUnderlineText = false
                    ds.isFakeBoldText = dernier
                }
            }, debut, texte.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val etapes = Arborescence.segments(dossierCourant)
        etape("📁 " + p.nom, "", etapes.isEmpty())
        etapes.forEachIndexed { i, (nom, chemin) ->
            texte.append("  /  ")
            etape(nom, chemin, i == etapes.lastIndex)
        }
        binding.cheminProjet.text = texte
    }

    /** Barre colorée des langages du projet, avec la part de chacun. */
    private fun afficherLangages() {
        // Comme sur GitHub, les images ne comptent pas comme du code dans la barre des langages.
        val parts = StatsLangages.calculer(fichiers.filterNot { ImagesProjet.estImage(it.chemin) })
        binding.barreLangages.removeAllViews()
        binding.barreLangages.isVisible = parts.isNotEmpty()
        binding.legendeLangages.isVisible = parts.isNotEmpty()
        if (parts.isEmpty()) return
        val rayon = 4f * resources.displayMetrics.density
        binding.barreLangages.background = GradientDrawable().apply {
            cornerRadius = rayon
            setColor(Color.BLACK)
        }
        binding.barreLangages.clipToOutline = true
        parts.forEach { part ->
            binding.barreLangages.addView(
                View(this).apply {
                    setBackgroundColor(part.couleur)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, part.fraction)
                }
            )
        }
        val legende = SpannableStringBuilder()
        parts.forEachIndexed { i, part ->
            if (i > 0) legende.append("   ")
            val debut = legende.length
            legende.append("●")
            legende.setSpan(ForegroundColorSpan(part.couleur), debut, legende.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            legende.append(" ${part.nom} ${part.pourcent} %")
        }
        binding.legendeLangages.text = legende
    }

    // ------------------------------------------------------------ actions

    private fun surClic(ligne: Ligne) {
        when (ligne) {
            is Ligne.Haut -> ouvrirDossier(Arborescence.parent(dossierCourant))
            is Ligne.DossierLigne -> ouvrirDossier(ligne.dossier.chemin)
            is Ligne.FichierLigne ->
                if (ImagesProjet.estImage(ligne.fichier.chemin)) voirImage(ligne.fichier)
                else ouvrirEditeur(ligne.fichier)
            is Ligne.Info -> Unit
        }
    }

    private fun surAppuiLong(ligne: Ligne) {
        val fichier = (ligne as? Ligne.FichierLigne)?.fichier ?: return
        val p = projet ?: return
        val cle = Favoris.cleFichier(p.uri, fichier.chemin)
        val libelleFavori = getString(
            if (favoris.estFavori(cle)) R.string.bureau_favori_retire else R.string.bureau_favori_ajoute
        )
        val image = ImagesProjet.estImage(fichier.chemin)
        // Une image n'est pas du code : pas de favori (il s'ouvrirait dans l'editeur de texte), juste la suppression.
        val libelles = if (image) {
            arrayOf(getString(R.string.projet_supprimer_fichier))
        } else {
            arrayOf(libelleFavori, getString(R.string.projet_modifier), getString(R.string.projet_supprimer_fichier))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(fichier.chemin)
            .setItems(libelles) { _, choix ->
                when {
                    image -> confirmerSuppression(fichier)
                    choix == 0 -> {
                        favoris.basculer(cle)
                        afficher()
                    }
                    choix == 1 -> ouvrirEditeur(fichier)
                    else -> confirmerSuppression(fichier)
                }
            }
            .show()
    }

    private fun ouvrirEditeur(f: FichierProjet) {
        startActivity(
            Intent(this, CodeEditorActivity::class.java)
                .putExtra(CodeEditorActivity.EXTRA_CHEMIN, f.uri)
                .putExtra(CodeEditorActivity.EXTRA_NOM, f.chemin)
                .putExtra(CodeEditorActivity.EXTRA_INTERNE, f.interne)
        )
    }

    /** Montre l'image du projet dans une fenetre (reduite pour ne pas saturer la memoire). */
    private fun voirImage(f: FichierProjet) {
        val p = projet ?: return
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ProjectStore.ouvrirFlux(this@ProjetActivity, p, f.chemin)?.use {
                        BitmapFactory.decodeStream(it, null, bornes)
                    }
                    var echantillon = 1
                    while (bornes.outWidth / (echantillon * 2) >= 1200 || bornes.outHeight / (echantillon * 2) >= 1200) {
                        echantillon *= 2
                    }
                    val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
                    ProjectStore.ouvrirFlux(this@ProjetActivity, p, f.chemin)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }.getOrNull()
            }
            if (bitmap == null) {
                Toast.makeText(this@ProjetActivity, R.string.projet_image_illisible, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val vue = ImageView(this@ProjetActivity).apply {
                setImageBitmap(bitmap)
                adjustViewBounds = true
                setPadding(24, 24, 24, 24)
            }
            MaterialAlertDialogBuilder(this@ProjetActivity)
                .setTitle(f.chemin)
                .setMessage(getString(R.string.projet_image_usage, f.chemin))
                .setView(vue)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun confirmerSuppression(f: FichierProjet) {
        MaterialAlertDialogBuilder(this)
            .setMessage(getString(R.string.projet_confirmer_suppression, f.chemin))
            .setPositiveButton(R.string.projet_supprimer_fichier) { _, _ ->
                lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val reel = if (f.uri.isEmpty()) {
                                projet?.let { ProjectStore.trouverFichier(this@ProjetActivity, it, f.chemin) }
                            } else f
                            reel != null && ProjectStore.supprimerFichier(this@ProjetActivity, reel)
                        }.getOrDefault(false)
                    }
                    Toast.makeText(
                        this@ProjetActivity,
                        if (ok) R.string.projet_fichier_supprime else R.string.projet_echec_suppression,
                        Toast.LENGTH_SHORT
                    ).show()
                    charger()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun menuAjouter() {
        MaterialAlertDialogBuilder(this)
            .setItems(
                arrayOf(
                    getString(R.string.projet_nouveau_fichier),
                    getString(R.string.projet_nouveau_dossier),
                    getString(R.string.plus_image),
                    getString(R.string.plus_icone)
                )
            ) { _, choix ->
                when (choix) {
                    0 -> nouveauFichier()
                    1 -> nouveauDossier()
                    2 -> choisirImage.launch("image/*")
                    else -> choisirIcone.launch("image/*")
                }
            }
            .show()
    }

    private fun prefixer(nom: String) =
        if (dossierCourant.isEmpty()) nom else "$dossierCourant/$nom"

    private fun nouveauFichier() {
        val p = projet ?: return
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.drawer_nouveau_fichier),
            message = getString(R.string.msg_creer_fichier),
            hint = getString(R.string.hint_nom_fichier),
            initial = if (fichiers.isEmpty()) "index.html" else "",
            valider = { if (it.isBlank()) getString(R.string.hint_nom_fichier) else Dialogues.erreurChemin(prefixer(it)) }
        ) { nom ->
            lifecycleScope.launch {
                try {
                    val cree = withContext(Dispatchers.IO) {
                        ProjectStore.creerFichier(this@ProjetActivity, p, prefixer(nom))
                    }
                    Toast.makeText(
                        this@ProjetActivity, getString(R.string.msg_fichier_cree, cree.chemin), Toast.LENGTH_SHORT
                    ).show()
                    // Un seul geste : le fichier s'ouvre tout de suite pour taper le code.
                    ouvrirEditeur(cree)
                    charger()
                } catch (e: Exception) {
                    erreur(R.string.msg_echec_creation, e)
                }
            }
        }
    }

    private fun nouveauDossier() {
        val p = projet ?: return
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.drawer_nouveau_dossier),
            message = getString(R.string.msg_creer_dossier),
            hint = getString(R.string.hint_nom_dossier),
            valider = { if (it.isBlank()) getString(R.string.hint_nom_dossier) else Dialogues.erreurChemin(prefixer(it)) }
        ) { nom ->
            lifecycleScope.launch {
                try {
                    val cree = withContext(Dispatchers.IO) {
                        ProjectStore.creerDossier(this@ProjetActivity, p, prefixer(nom))
                    }
                    Toast.makeText(
                        this@ProjetActivity, getString(R.string.msg_dossier_cree, cree), Toast.LENGTH_SHORT
                    ).show()
                    charger()
                } catch (e: Exception) {
                    erreur(R.string.msg_echec_dossier, e)
                }
            }
        }
    }

    private fun ajouterImage(uri: android.net.Uri, commeIcone: Boolean) {
        val p = projet ?: return
        lifecycleScope.launch {
            try {
                val chemin = withContext(Dispatchers.IO) {
                    if (commeIcone) {
                        ImagesProjet.definirIcone(this@ProjetActivity, p, uri)
                    } else {
                        val nom = contentResolver.query(
                            uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { if (it.moveToFirst()) it.getString(0) else null }
                        ImagesProjet.importer(this@ProjetActivity, p, uri, nom)
                    }
                }
                Toast.makeText(
                    this@ProjetActivity,
                    getString(if (commeIcone) R.string.plus_icone_definie else R.string.plus_image_ajoutee, chemin),
                    Toast.LENGTH_LONG
                ).show()
                charger()
            } catch (e: Exception) {
                erreur(R.string.plus_echec_titre, e)
            }
        }
    }

    private fun menuParametres() {
        val p = projet ?: return
        val stockage = getString(
            R.string.projet_info_stockage,
            getString(if (p.interne) R.string.projet_stockage_interne else R.string.projet_stockage_externe)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(p.nom + "\n" + stockage)
            .setItems(
                arrayOf(getString(R.string.projet_ouvrir_studio), getString(R.string.projet_retirer))
            ) { _, choix ->
                if (choix == 0) {
                    startActivity(
                        Intent(this, StudioActivity::class.java)
                            .putExtra(StudioActivity.EXTRA_PROJET_URI, p.uri)
                    )
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.projet_retirer)
                        .setMessage(R.string.projet_retirer_msg)
                        .setPositiveButton(R.string.projet_retirer) { _, _ ->
                            ProjectStore.supprimer(this, p.uri)
                            Toast.makeText(this, R.string.projet_retire, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
                }
            }
            .show()
    }

    private fun erreur(titre: Int, e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titre)
            .setMessage(e.message ?: e.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ------------------------------------------------------------- liste

    private sealed interface Ligne {
        data object Haut : Ligne
        data class DossierLigne(val dossier: Arborescence.Dossier) : Ligne
        data class FichierLigne(val fichier: FichierProjet, val favori: Boolean) : Ligne
        data class Info(val texte: String) : Ligne
    }

    private class LignesAdapter(
        private val surClic: (Ligne) -> Unit,
        private val surAppuiLong: (Ligne) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var lignes: List<Ligne> = emptyList()

        fun remplacer(nouvelles: List<Ligne>) {
            lignes = nouvelles
            notifyDataSetChanged()
        }

        class LigneVue(vue: View) : RecyclerView.ViewHolder(vue) {
            val icone: TextView = vue.findViewById(R.id.repoIcone)
            val nom: TextView = vue.findViewById(R.id.repoNom)
            val sous: TextView = vue.findViewById(R.id.repoSous)
            val action: TextView = vue.findViewById(R.id.repoAction)
        }

        class InfoVue(vue: View) : RecyclerView.ViewHolder(vue) {
            val texte: TextView = vue.findViewById(R.id.enteteTexte)
        }

        override fun getItemViewType(position: Int) = if (lignes[position] is Ligne.Info) 1 else 0

        override fun getItemCount() = lignes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                InfoVue(inflater.inflate(R.layout.item_bureau_entete, parent, false))
            } else {
                LigneVue(inflater.inflate(R.layout.item_repo, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val ligne = lignes[position]
            if (holder is InfoVue && ligne is Ligne.Info) {
                holder.texte.text = ligne.texte
                holder.texte.setTextColor(holder.itemView.context.getColor(R.color.grey))
                holder.texte.textSize = 13f
                holder.texte.setTypeface(null, Typeface.NORMAL)
                return
            }
            val h = holder as LigneVue
            val ctx = h.itemView.context
            h.itemView.setOnClickListener { surClic(ligne) }
            h.itemView.setOnLongClickListener { surAppuiLong(ligne); true }
            h.icone.background = null
            h.icone.setTypeface(null, Typeface.NORMAL)
            h.icone.textSize = 22f
            h.icone.setTextColor(ctx.getColor(R.color.white))
            when (ligne) {
                is Ligne.Haut -> {
                    h.icone.text = "📂"
                    h.nom.text = ".."
                    h.sous.text = ctx.getString(R.string.projet_remonter)
                    h.action.text = ""
                }
                is Ligne.DossierLigne -> {
                    h.icone.text = "📁"
                    h.nom.text = ligne.dossier.nom
                    h.sous.text = ctx.getString(R.string.projet_elements, ligne.dossier.elements)
                    h.action.text = "›"
                }
                is Ligne.FichierLigne -> {
                    // Pastille aux couleurs du langage + nom exact du fichier.
                    val langage = Langages.pour(ligne.fichier.nom)
                    val image = ImagesProjet.estImage(ligne.fichier.chemin)
                    h.icone.text = if (image) "IMG" else langage.etiquette
                    h.icone.textSize = if (image || langage.etiquette.length <= 3) 14f else 11f
                    h.icone.setTypeface(null, Typeface.BOLD)
                    h.icone.setBackgroundResource(R.drawable.bg_badge)
                    val couleur = if (image) 0xFF7AA2F7.toInt() else langage.couleur
                    (h.icone.background.mutate() as? GradientDrawable)?.setColor(couleur)
                    h.icone.setTextColor(if (luminance(couleur) > 150) Color.BLACK else Color.WHITE)
                    h.nom.text = (if (ligne.favori) "⭐ " else "") + ligne.fichier.nom
                    h.sous.text = (if (image) ctx.getString(R.string.projet_image) else langage.nom) +
                        " · " + Formatter.formatShortFileSize(ctx, ligne.fichier.taille)
                    h.action.text = if (image) "🖼️" else "✏️"
                }
                is Ligne.Info -> Unit
            }
        }

        private fun luminance(couleur: Int): Int =
            (Color.red(couleur) * 299 + Color.green(couleur) * 587 + Color.blue(couleur) * 114) / 1000
    }

    companion object {
        const val EXTRA_PROJET_URI = "projet_uri"
        const val EXTRA_DOSSIER = "projet_dossier"
        private const val ETAT_DOSSIER = "dossier"
    }
}
