package com.codexlocal.usage

import android.content.Context
import com.codexlocal.ProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Levee quand la limite choisie par l'utilisateur est atteinte : rien n'est envoye au service. */
class LimiteAtteinteException(message: String) : Exception(message)

/**
 * Compteur d'utilisation des cles API (OpenAI, Claude, Gemini…), pour ne pas depasser son
 * forfait. Tout reste sur le telephone. Les jetons sont estimes (≈ 4 caracteres par jeton) :
 * c'est une garde-fou, pas la facture du service. Un compteur par fournisseur, remis a zero
 * automatiquement chaque jour et chaque mois ; les limites (0 = illimite) sont par fournisseur.
 */
class CompteurUsage(
    context: Context,
    private val aujourdhui: () -> LocalDate = { LocalDate.now() }
) {

    data class Periode(val requetes: Long = 0, val jetonsEntree: Long = 0, val jetonsSortie: Long = 0) {
        val jetons: Long get() = jetonsEntree + jetonsSortie
    }

    data class Etat(
        val jour: Periode,
        val mois: Periode,
        val limiteJour: Long,
        val limiteMois: Long
    ) {
        /** Part de la limite deja utilisee (0 a plus de 100), ou null sans limite. */
        val pourcentJour: Int? get() = pourcent(jour.jetons, limiteJour)
        val pourcentMois: Int? get() = pourcent(mois.jetons, limiteMois)

        /** Le plus avance des deux pourcentages, ou null si aucune limite n'est fixee. */
        val pourcentMax: Int? get() = listOfNotNull(pourcentJour, pourcentMois).maxOrNull()

        val limiteAtteinte: Boolean get() = (pourcentMax ?: 0) >= 100

        private fun pourcent(utilise: Long, limite: Long): Int? =
            if (limite <= 0) null else ((utilise * 100) / limite).coerceAtMost(999).toInt()
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun etat(p: ProviderType): Etat {
        val jour = aujourdhui().toString()
        val mois = jour.take(7)
        return Etat(
            jour = lire(p, "j", jour),
            mois = lire(p, "m", mois),
            limiteJour = prefs.getLong("${p.name}_limite_j", 0),
            limiteMois = prefs.getLong("${p.name}_limite_m", 0)
        )
    }

    fun definirLimites(p: ProviderType, parJour: Long, parMois: Long) {
        prefs.edit()
            .putLong("${p.name}_limite_j", parJour.coerceAtLeast(0))
            .putLong("${p.name}_limite_m", parMois.coerceAtLeast(0))
            .apply()
    }

    /** Ajoute une requete terminee au compteur du jour et du mois. */
    fun enregistrer(p: ProviderType, jetonsEntree: Long, jetonsSortie: Long) {
        val jour = aujourdhui().toString()
        val mois = jour.take(7)
        val editeur = prefs.edit()
        ecrire(editeur, p, "j", jour, lire(p, "j", jour), jetonsEntree, jetonsSortie)
        ecrire(editeur, p, "m", mois, lire(p, "m", mois), jetonsEntree, jetonsSortie)
        editeur.apply()
    }

    fun remettreAZero(p: ProviderType) {
        val editeur = prefs.edit()
        for (suffixe in listOf("j", "m")) {
            editeur.remove("${p.name}_${suffixe}_date").remove("${p.name}_${suffixe}_req")
                .remove("${p.name}_${suffixe}_in").remove("${p.name}_${suffixe}_out")
        }
        editeur.apply()
    }

    /** Refuse la requete si une limite est deja atteinte. */
    fun verifier(p: ProviderType) {
        val e = etat(p)
        if (e.limiteJour > 0 && e.jour.jetons >= e.limiteJour) {
            throw LimiteAtteinteException(
                "Limite du jour atteinte pour ${p.label} : ≈ ${nombre(e.jour.jetons)} / " +
                    "${nombre(e.limiteJour)} jetons. Augmente-la dans Réglages ou reviens demain."
            )
        }
        if (e.limiteMois > 0 && e.mois.jetons >= e.limiteMois) {
            throw LimiteAtteinteException(
                "Limite du mois atteinte pour ${p.label} : ≈ ${nombre(e.mois.jetons)} / " +
                    "${nombre(e.limiteMois)} jetons. Augmente-la dans Réglages ou attends le mois prochain."
            )
        }
    }

    /**
     * Enveloppe une reponse en streaming : refuse l'appel si une limite est atteinte, puis compte
     * la requete (jetons d'entree donnes, jetons de sortie estimes sur ce qui est recu).
     * Une requete refusee avant toute reponse (erreur de cle…) ne coute rien et n'est pas comptee.
     */
    fun compter(p: ProviderType, jetonsEntree: Long, source: Flow<String>): Flow<String> = flow {
        verifier(p)
        var caracteres = 0
        var recu = false
        try {
            source.collect {
                recu = true
                caracteres += it.length
                emit(it)
            }
        } finally {
            if (recu) enregistrer(p, jetonsEntree, estimerJetons(caracteres))
        }
    }

    private fun lire(p: ProviderType, suffixe: String, dateCourante: String): Periode {
        val base = "${p.name}_$suffixe"
        // Une autre date en memoire = une nouvelle periode : le compteur repart de zero.
        if (prefs.getString("${base}_date", null) != dateCourante) return Periode()
        return Periode(
            requetes = prefs.getLong("${base}_req", 0),
            jetonsEntree = prefs.getLong("${base}_in", 0),
            jetonsSortie = prefs.getLong("${base}_out", 0)
        )
    }

    private fun ecrire(
        editeur: android.content.SharedPreferences.Editor,
        p: ProviderType, suffixe: String, date: String, avant: Periode,
        jetonsEntree: Long, jetonsSortie: Long
    ) {
        val base = "${p.name}_$suffixe"
        editeur.putString("${base}_date", date)
            .putLong("${base}_req", avant.requetes + 1)
            .putLong("${base}_in", avant.jetonsEntree + jetonsEntree.coerceAtLeast(0))
            .putLong("${base}_out", avant.jetonsSortie + jetonsSortie.coerceAtLeast(0))
    }

    companion object {
        private const val PREFS = "codexlocal_usage"

        /** Estimation grossiere : environ 4 caracteres par jeton. */
        fun estimerJetons(caracteres: Int): Long = ((caracteres + 3) / 4).toLong()

        fun estimerJetons(texte: String): Long = estimerJetons(texte.length)

        fun nombre(n: Long): String = NumberFormat.getIntegerInstance(Locale.FRANCE).format(n)

        /** Resume pour la ligne d'etat : « ≈ 12 340 / 50 000 jetons aujourd'hui ». */
        fun resume(e: Etat): String {
            val utilise = nombre(e.jour.jetons)
            val base = if (e.limiteJour > 0) "≈ $utilise / ${nombre(e.limiteJour)}" else "≈ $utilise"
            val alerte = if ((e.pourcentMax ?: 0) >= 80) "⚠️ " else ""
            return "$alerte$base jetons aujourd'hui"
        }
    }
}
