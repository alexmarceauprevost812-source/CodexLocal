package com.codexlocal.bureau

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.FondEcran
import com.codexlocal.Prefs
import com.codexlocal.R
import com.codexlocal.databinding.ActivityBureauBinding
import com.codexlocal.editor.CodeEditorActivity
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import com.codexlocal.projet.ProjetActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bureau : l'ecran d'accueil de l'appli, comme sur un telephone. Toutes les entrees de menu
 * sont des icones, 4 de large, puis les lignes suivantes vers le bas de l'ecran : favoris,
 * puis les projets. Un projet s'ouvre dans la vue « depot » (ProjetActivity) ; une entree
 * de menu renvoie son choix au chat (voir MainActivity.executerBureau).
 */
class BureauActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBureauBinding
    private lateinit var favoris: Favoris
    private lateinit var adapter: TuilesAdapter

    private var projets: List<Projet> = emptyList()
    private var fichiersFavoris: Map<String, FichierProjet?> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBureauBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barres = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(barres.left, barres.top, barres.right, barres.bottom)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        favoris = Favoris(this)

        // Comme l'ecran d'accueil d'un telephone : meme fond d'ecran que le chat.
        FondEcran.appliquer(this, binding.fondBureau, Prefs(this).fondOpacite)

        adapter = TuilesAdapter(::surClic, ::surAppuiLong)
        val grille = GridLayoutManager(this, COLONNES)
        grille.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = when (adapter.element(position)) {
                is ElementBureau.Entete, is ElementBureau.Info -> COLONNES
                else -> 1
            }
        }
        binding.tuiles.layoutManager = grille
        binding.tuiles.adapter = adapter
        afficher()
    }

    override fun onResume() {
        super.onResume()
        recharger()
    }

    /** Lit les projets (rapide), puis retrouve en arriere-plan les fichiers favoris. */
    private fun recharger() {
        projets = ProjectStore.lister(this)
        afficher()
        val aResoudre = favoris.toutes().mapNotNull { cle ->
            (Favoris.decoder(cle) as? Favoris.Cible.DeFichier)?.let { cle to it }
        }
        if (aResoudre.isEmpty()) return
        lifecycleScope.launch {
            val trouves = withContext(Dispatchers.IO) {
                aResoudre.associate { (cle, cible) ->
                    val projet = projets.firstOrNull { it.uri == cible.projetUri }
                    cle to projet?.let {
                        runCatching { ProjectStore.trouverFichier(this@BureauActivity, it, cible.chemin) }
                            .getOrNull()
                    }
                }
            }
            fichiersFavoris = trouves
            afficher()
        }
    }

    private fun afficher() {
        val titres = ConstructeurBureau.Titres(
            favoris = getString(R.string.bureau_favoris),
            projets = getString(R.string.bureau_rubrique_projets),
            aucunProjet = getString(R.string.bureau_aucun_projet)
        )
        adapter.remplacer(
            ConstructeurBureau.construire(
                projets, favoris.toutes(),
                { projet, chemin -> fichiersFavoris[Favoris.cleFichier(projet.uri, chemin)] },
                titres
            )
        )
    }

    private fun surClic(element: ElementBureau) {
        when (element) {
            is ElementBureau.Menu -> {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ACTION, element.action.cle))
                finish()
            }
            is ElementBureau.ProjetTuile ->
                startActivity(
                    Intent(this, ProjetActivity::class.java)
                        .putExtra(ProjetActivity.EXTRA_PROJET_URI, element.projet.uri)
                )
            is ElementBureau.FichierTuile ->
                startActivity(
                    Intent(this, CodeEditorActivity::class.java)
                        .putExtra(CodeEditorActivity.EXTRA_CHEMIN, element.fichier.uri)
                        .putExtra(CodeEditorActivity.EXTRA_NOM, element.fichier.chemin)
                        .putExtra(CodeEditorActivity.EXTRA_INTERNE, element.fichier.interne)
                )
            is ElementBureau.Entete, is ElementBureau.Info -> Unit
        }
    }

    /** Appui long : explication d'une entree de menu, ou ⭐ sur un projet / fichier. */
    private fun surAppuiLong(element: ElementBureau) {
        val cle = when (element) {
            is ElementBureau.Menu -> {
                Toast.makeText(this, element.action.sous, Toast.LENGTH_SHORT).show()
                return
            }
            is ElementBureau.ProjetTuile -> Favoris.cleProjet(element.projet.uri)
            is ElementBureau.FichierTuile ->
                Favoris.cleFichier(element.projet.uri, element.fichier.chemin)
            else -> return
        }
        val maintenant = favoris.basculer(cle)
        Toast.makeText(
            this,
            if (maintenant) R.string.bureau_favori_ajoute else R.string.bureau_favori_retire,
            Toast.LENGTH_SHORT
        ).show()
        afficher()
    }

    private class TuilesAdapter(
        private val surClic: (ElementBureau) -> Unit,
        private val surAppuiLong: (ElementBureau) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var elements: List<ElementBureau> = emptyList()

        fun element(position: Int): ElementBureau = elements[position]

        fun remplacer(nouveaux: List<ElementBureau>) {
            elements = nouveaux
            notifyDataSetChanged()
        }

        class Tuile(vue: View) : RecyclerView.ViewHolder(vue) {
            val emoji: TextView = vue.findViewById(R.id.tuileEmoji)
            val titre: TextView = vue.findViewById(R.id.tuileTitre)
        }

        class Entete(vue: View) : RecyclerView.ViewHolder(vue) {
            val texte: TextView = vue.findViewById(R.id.enteteTexte)
        }

        override fun getItemViewType(position: Int) = when (elements[position]) {
            is ElementBureau.Entete -> TYPE_ENTETE
            is ElementBureau.Info -> TYPE_INFO
            else -> TYPE_TUILE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_TUILE) {
                Tuile(inflater.inflate(R.layout.item_bureau, parent, false))
            } else {
                Entete(inflater.inflate(R.layout.item_bureau_entete, parent, false))
            }
        }

        override fun getItemCount() = elements.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val element = elements[position]
            when (holder) {
                is Entete -> {
                    val ctx = holder.itemView.context
                    val info = element is ElementBureau.Info
                    holder.texte.text = when (element) {
                        is ElementBureau.Entete -> element.texte
                        is ElementBureau.Info -> element.texte
                        else -> ""
                    }
                    holder.texte.setTextColor(ctx.getColor(if (info) R.color.grey else R.color.orange))
                    holder.texte.textSize = if (info) 12f else 14f
                    holder.texte.setTypeface(null, if (info) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD)
                }
                is Tuile -> lier(holder, element)
            }
        }

        private fun lier(h: Tuile, element: ElementBureau) {
            val ctx = h.itemView.context
            val fond = Color.parseColor("#F20C0C0C")
            var texteEmoji = ""
            var tailleEmoji = 28f
            var couleurTexte = ctx.getColor(R.color.white)
            var couleurFond = fond
            var couleurTrait = ctx.getColor(R.color.orange_dark)
            var titre = ""
            var description = ""

            when (element) {
                is ElementBureau.Menu -> {
                    texteEmoji = element.action.emoji
                    titre = ctx.getString(element.action.titre)
                    description = titre + " : " + ctx.getString(element.action.sous)
                }
                is ElementBureau.ProjetTuile -> {
                    texteEmoji = "📁"
                    titre = (if (element.favori) "⭐ " else "") + element.projet.nom
                    description = element.projet.nom
                }
                is ElementBureau.FichierTuile -> {
                    // Pastille aux couleurs du langage, avec son sigle et le nom exact du fichier.
                    val langage = Langages.pour(element.fichier.nom)
                    texteEmoji = langage.etiquette
                    tailleEmoji = if (langage.etiquette.length <= 3) 17f else 12f
                    couleurFond = langage.couleur
                    couleurTrait = langage.couleur
                    couleurTexte = if (luminance(langage.couleur) > 150) Color.BLACK else Color.WHITE
                    titre = "⭐ " + element.fichier.nom
                    description = element.fichier.chemin + " (" + langage.nom + ")"
                }
                else -> Unit
            }

            h.emoji.text = texteEmoji
            h.emoji.textSize = tailleEmoji
            h.emoji.setTextColor(couleurTexte)
            h.emoji.setTypeface(null, if (element is ElementBureau.FichierTuile) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            (h.emoji.background?.mutate() as? GradientDrawable)?.apply {
                setColor(couleurFond)
                setStroke((1.5f * ctx.resources.displayMetrics.density).toInt(), couleurTrait)
            }
            h.titre.text = titre
            h.itemView.contentDescription = description
            h.itemView.setOnClickListener { surClic(element) }
            h.itemView.setOnLongClickListener { surAppuiLong(element); true }
        }

        private fun luminance(couleur: Int): Int =
            (Color.red(couleur) * 299 + Color.green(couleur) * 587 + Color.blue(couleur) * 114) / 1000

        companion object {
            private const val TYPE_TUILE = 0
            private const val TYPE_ENTETE = 1
            private const val TYPE_INFO = 2
        }
    }

    companion object {
        const val EXTRA_ACTION = "bureau_action"

        /** 4 choix de large, puis les lignes suivantes vers le bas de l'ecran. */
        const val COLONNES = 4
    }
}
