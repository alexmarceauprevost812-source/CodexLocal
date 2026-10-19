package com.codexlocal.coloration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Le code qu'on colore est ecrit en direct, donc souvent incomplet : guillemet jamais ferme, accolade en trop,
 * commentaire coupe en plein milieu... L'analyseur ne doit jamais planter ni rendre des jetons incoherents.
 */
class AnalyseurRobustesseTest {

    private val langages = EXEMPLES.map { it.first }

    @Test fun lesExemplesDonnentDesJetonsCoherents() {
        for ((langage, code) in EXEMPLES) {
            verifierJetons(code, AnalyseurCode.analyserBrut(code, Profils.normaliser(langage)), langage)
        }
    }

    @Test fun chaqueExempleCoupeNImporteOuReste_coherent() {
        // on « tape » chaque exemple lettre par lettre : tous les etats intermediaires doivent passer
        for ((langage, code) in EXEMPLES) {
            val nom = Profils.normaliser(langage)
            for (fin in 0..code.length) {
                val debut = code.substring(0, fin)
                verifierJetons(debut, AnalyseurCode.analyserBrut(debut, nom), "$langage jusqu'a $fin")
            }
        }
    }

    @Test fun chaqueExempleSansSonDebutResteCoherent() {
        // et en partant du milieu (cas d'un bloc de code coupe)
        for ((langage, code) in EXEMPLES) {
            val nom = Profils.normaliser(langage)
            for (debut in code.indices step 3) {
                val reste = code.substring(debut)
                verifierJetons(reste, AnalyseurCode.analyserBrut(reste, nom), "$langage depuis $debut")
            }
        }
    }

    @Test fun duTexteAuHasardNeFaitJamaisPlanter() {
        val alphabet = "\"'`\\/*#<>{}()[]=:;-.,\$@!&|%^~?+_ \n\t\rabcXYZ019eE xx éà😀\u0000"
        val hasard = Random(20261019)
        val noms = langages.map { Profils.normaliser(it) }.distinct()
        repeat(400) { essai ->
            val longueur = hasard.nextInt(160)
            val texte = buildString { repeat(longueur) { append(alphabet[hasard.nextInt(alphabet.length)]) } }
            for (nom in noms) {
                verifierJetons(texte, AnalyseurCode.analyserBrut(texte, nom), "essai $essai en $nom")
            }
        }
    }

    @Test fun deVraisMorceauxMelangesNeFontJamaisPlanter() {
        // des bouts de vrais langages recolles n'importe comment
        val morceaux = EXEMPLES.flatMap { it.second.split('\n') }
        val hasard = Random(7)
        val noms = langages.map { Profils.normaliser(it) }.distinct()
        repeat(150) { essai ->
            val texte = (0 until 1 + hasard.nextInt(8)).joinToString(if (hasard.nextBoolean()) "\n" else " ") {
                morceaux[hasard.nextInt(morceaux.size)]
            }.let { it.substring(0, hasard.nextInt(it.length + 1)) }
            for (nom in noms) {
                verifierJetons(texte, AnalyseurCode.analyserBrut(texte, nom), "melange $essai en $nom")
            }
        }
    }

    @Test fun lesCaracteresDeFinDeLigneWindowsEtMacPassent() {
        for ((langage, code) in EXEMPLES) {
            val windows = code.replace("\n", "\r\n")
            verifierJetons(windows, AnalyseurCode.analyser(windows, langage), "$langage CRLF")
            val mac = code.replace("\n", "\r")
            verifierJetons(mac, AnalyseurCode.analyser(mac, langage), "$langage CR")
        }
    }

    @Test fun lAnalyseEstToujoursLaMemePourUnMemeTexte() {
        for ((langage, code) in EXEMPLES) {
            val a = AnalyseurCode.analyser(code, langage).map { it.toString() + it.couleur }
            val b = AnalyseurCode.analyser(code, langage).map { it.toString() + it.couleur }
            assertEquals(langage, a, b)
        }
    }

    @Test fun uneEntreeVideOuTropGrandeNeDonneRien() {
        assertTrue(AnalyseurCode.analyser("", "kotlin").isEmpty())
        val enorme = "val a = 1\n".repeat(AnalyseurCode.LIMITE / 10 + 10)
        assertTrue(enorme.length > AnalyseurCode.LIMITE)
        assertTrue(AnalyseurCode.analyser(enorme, "kotlin").isEmpty())
    }

    @Test fun unGrosFichierEstAnalyseVite() {
        val unite = EXEMPLES.first { it.first == "kotlin" }.second + "\n"
        val gros = unite.repeat(AnalyseurCode.LIMITE / unite.length)
        assertTrue(gros.length <= AnalyseurCode.LIMITE)
        AnalyseurCode.analyser(gros, "kotlin")                       // chauffe
        val debut = System.nanoTime()
        val jetons = AnalyseurCode.analyser(gros, "kotlin")
        val ms = (System.nanoTime() - debut) / 1_000_000
        assertTrue("aucun jeton sur un gros fichier", jetons.isNotEmpty())
        verifierJetons(gros, jetons, "gros fichier")
        // large marge : sert a reperer un calcul qui explose, pas a mesurer
        assertTrue("trop lent : $ms ms pour ${gros.length} caracteres", ms < 3_000)
    }

    @Test fun unGrosFichierSansRetourALaLigneNeBloquePas() {
        val gros = "x = \"abc\" + 12; ".repeat(AnalyseurCode.LIMITE / 16)
        val debut = System.nanoTime()
        val jetons = AnalyseurCode.analyser(gros, "javascript")
        val ms = (System.nanoTime() - debut) / 1_000_000
        verifierJetons(gros, jetons, "ligne unique")
        assertTrue("trop lent : $ms ms", ms < 3_000)
    }

    @Test fun desParenthesesTresImbriqueesPassent() {
        val profond = "(".repeat(5_000) + ")".repeat(5_000)
        verifierJetons(profond, AnalyseurCode.analyser(profond, "kotlin"), "parentheses")
        val balises = "<a>".repeat(3_000)
        verifierJetons(balises, AnalyseurCode.analyser(balises, "html"), "balises")
    }
}
