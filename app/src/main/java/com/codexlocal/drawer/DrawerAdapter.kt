package com.codexlocal.drawer

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.R
import com.codexlocal.databinding.ItemDrawerBinding

class DrawerAdapter(
    private val items: MutableList<DrawerItem>,
    private val onLigne: (DrawerItem) -> Unit,
    private val onAction: (DrawerItem) -> Unit
) : RecyclerView.Adapter<DrawerAdapter.Holder>() {

    class Holder(val binding: ItemDrawerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    /** Remplace tout le contenu du tiroir. */
    fun remplacer(nouveaux: List<DrawerItem>) {
        items.clear()
        items.addAll(nouveaux)
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val b = holder.binding

        // remise a zero
        b.badge.isVisible = false
        b.sousTitre.isVisible = false
        b.action.isVisible = false
        b.titre.setTextColor(0xFFECECEC.toInt())
        b.titre.textSize = 14f
        b.titre.setTypeface(null, android.graphics.Typeface.NORMAL)
        b.root.setOnClickListener(null)
        b.action.setOnClickListener(null)

        when (item) {
            is DrawerItem.Section -> {
                b.titre.text = item.titre.uppercase()
                b.titre.setTextColor(0xFFFF7A00.toInt())
                b.titre.textSize = 12f
                b.titre.setTypeface(null, android.graphics.Typeface.BOLD)
                if (item.action != null) {
                    b.action.text = item.action
                    b.action.isVisible = true
                    b.action.setOnClickListener { onAction(item) }
                }
            }

            is DrawerItem.LigneSession -> {
                b.titre.text = item.titre
                b.sousTitre.text = item.sous
                b.sousTitre.isVisible = true
                if (item.actif) b.titre.setTextColor(0xFFFF7A00.toInt())
                b.action.text = "✕"
                b.action.isVisible = true
                b.action.setOnClickListener { onAction(item) }
                b.root.setOnClickListener { onLigne(item) }
            }

            is DrawerItem.LigneProjet -> {
                b.titre.text = item.nom
                b.sousTitre.text = item.sous
                b.sousTitre.isVisible = true
                if (item.actif) b.titre.setTextColor(0xFFFF7A00.toInt())
                b.action.text = "✕"
                b.action.isVisible = true
                b.action.setOnClickListener { onAction(item) }
                b.root.setOnClickListener { onLigne(item) }
            }

            is DrawerItem.LigneFichier -> {
                b.badge.isVisible = true
                b.badge.text = item.langage.etiquette
                (b.badge.background?.mutate() as? GradientDrawable)
                    ?.setTint(item.langage.couleur)
                b.titre.text = item.nom
                b.sousTitre.text = item.chemin
                b.sousTitre.isVisible = true
                b.root.setOnClickListener { onLigne(item) }
            }

            is DrawerItem.Info -> {
                b.titre.text = item.texte
                b.titre.setTextColor(0xFF8C8C8C.toInt())
                b.titre.textSize = 12f
                b.titre.setPadding(dp(b, 6), 0, 0, 0)
            }

            is DrawerItem.Bouton -> {
                b.titre.text = item.libelle
                b.titre.setTextColor(0xFFFF7A00.toInt())
                b.root.setOnClickListener { onAction(item) }
            }
        }
    }

    private fun dp(b: ItemDrawerBinding, valeur: Int): Int =
        (valeur * b.root.resources.displayMetrics.density).toInt()

    /** Raccourci : identifiant stable pour retrouver une ligne. */
    fun positionDe(predicat: (DrawerItem) -> Boolean): Int = items.indexOfFirst(predicat)
}
