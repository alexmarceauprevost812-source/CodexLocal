package com.codexlocal.agent

import com.codexlocal.studio.StudioParser

/**
 * Quand l'IA donne du code sans nom de fichier, l'appli lui redemande de choisir ce nom (a part pour
 * un service en ligne ; dans la conversation en cours pour le modele local, dont la memoire reste intacte).
 * Ce fichier prepare la question, lit la reponse (meme avec le raisonnement `<think>` des modeles
 * « reflechis ») et trouve un nom qui ne reprend jamais un fichier existant. Si la reponse est
 * inutilisable, un nom par defaut (index.html, main.py...) est donne. Code pur, sans Android.
 */
object NommageIA {

    /** Consigne de la petite question « quel nom pour ce fichier ? ». */
    const val SYSTEME =
        "Tu donnes des noms de fichiers. Tu reponds uniquement par un nom de fichier court, " +
            "en minuscules, avec la bonne extension, sans aucune phrase."

    /** Combien de blocs au maximum sont nommes par l'IA (les autres recoivent un nom par defaut). */
    const val MAX_QUESTIONS = 4

    private class Langage(val extensions: Set<String>, val nomParDefaut: String)

    private val LANGAGES: Map<String, Langage> = buildMap {
        fun ajouter(noms: List<String>, extensions: Set<String>, defaut: String) {
            val l = Langage(extensions, defaut)
            noms.forEach { put(it, l) }
        }
        ajouter(listOf("html"), setOf("html", "htm"), "index.html")
        ajouter(listOf("css"), setOf("css"), "style.css")
        ajouter(listOf("scss"), setOf("scss"), "style.scss")
        ajouter(listOf("js", "javascript"), setOf("js", "mjs"), "script.js")
        ajouter(listOf("jsx"), setOf("jsx"), "App.jsx")
        ajouter(listOf("ts", "typescript"), setOf("ts"), "main.ts")
        ajouter(listOf("tsx"), setOf("tsx"), "App.tsx")
        ajouter(listOf("json"), setOf("json"), "data.json")
        ajouter(listOf("xml"), setOf("xml"), "data.xml")
        ajouter(listOf("yaml", "yml"), setOf("yml", "yaml"), "config.yml")
        ajouter(listOf("py", "python"), setOf("py"), "main.py")
        ajouter(listOf("kt", "kotlin"), setOf("kt", "kts"), "Main.kt")
        ajouter(listOf("java"), setOf("java"), "Main.java")
        ajouter(listOf("php"), setOf("php"), "index.php")
        ajouter(listOf("c"), setOf("c", "h"), "main.c")
        ajouter(listOf("cpp"), setOf("cpp", "cc", "hpp", "h"), "main.cpp")
        ajouter(listOf("csharp", "cs"), setOf("cs"), "Program.cs")
        ajouter(listOf("go"), setOf("go"), "main.go")
        ajouter(listOf("rust", "rs"), setOf("rs"), "main.rs")
        ajouter(listOf("swift"), setOf("swift"), "main.swift")
        ajouter(listOf("dart"), setOf("dart"), "main.dart")
        ajouter(listOf("ruby", "rb"), setOf("rb"), "main.rb")
        ajouter(listOf("sql"), setOf("sql"), "schema.sql")
        ajouter(listOf("lua"), setOf("lua"), "main.lua")
        ajouter(listOf("md", "markdown"), setOf("md", "markdown"), "notes.md")
        ajouter(listOf("toml"), setOf("toml"), "config.toml")
        ajouter(listOf("ini"), setOf("ini", "cfg", "conf"), "config.ini")
    }

    /** Toutes les extensions acceptees pour un nom choisi par l'IA (verifiees par un test). */
    internal fun extensionsAcceptees(): Set<String> = LANGAGES.values.flatMap { it.extensions }.toSet()

    /**
     * La question posee dans la conversation en cours (modele local : sa memoire n'est pas remise a
     * zero) : le code est deja dans la conversation, on le reconnait a ses premieres lignes.
     */
    fun demandeDansLaConversation(bloc: ActionFichier.SansNom): String {
        val debut = bloc.contenu.lines().filter { it.isNotBlank() }.take(3).joinToString("\n").take(240)
        return "Quel nom de fichier (avec son extension) pour le bloc de code ${bloc.langage} qui commence par :\n" +
            debut + "\nReponds UNIQUEMENT par ce nom de fichier, rien d'autre."
    }

    /** La question posee a l'IA pour un bloc : un extrait du code et les noms deja pris. */
    fun demande(bloc: ActionFichier.SansNom, existants: List<String>): String {
        val extrait = bloc.contenu.lines().take(40).joinToString("\n").take(1800)
        val liste = existants.take(40).joinToString(", ")
        return buildString {
            append("Choisis le nom de fichier de ce code ").append(bloc.langage).append(". ")
            if (liste.isNotEmpty()) append("Fichiers deja dans le projet : ").append(liste).append(" (prends un autre nom). ")
            append("Reponds UNIQUEMENT par le nom du fichier avec son extension (exemple : ")
            append(LANGAGES[bloc.langage]?.nomParDefaut ?: "index.html").append("), rien d'autre.\n\nCode :\n")
            append(extrait)
        }
    }

    /** Vrai quand [nom] a une extension qui va avec [langage] (ou quand on ne connait pas le langage). */
    fun compatible(nom: String, langage: String): Boolean {
        val l = LANGAGES[langage.lowercase()] ?: return true
        val extension = nom.substringAfterLast('/').substringAfterLast('.', "").lowercase()
        return extension in l.extensions
    }

    private val SEPARATEURS = Regex("""[\s"'`*()\[\]{}<>,;:!?]+""")

    /**
     * Le nom de fichier contenu dans la reponse de l'IA, ou null. On lit d'abord ce qui suit le
     * raisonnement (`</think>`) ; sans reponse, on prend le dernier nom du raisonnement.
     */
    fun lireNom(reponse: String, langage: String): String? {
        val fin = reponse.lastIndexOf("</think>")
        val reponseSeule = when {
            fin >= 0 -> reponse.substring(fin + "</think>".length)
            else -> reponse.replace(Regex("(?s)<think>.*"), "")
        }
        fun candidats(texte: String) = texte.split(SEPARATEURS)
            .map { StudioParser.nettoyerChemin(it.trimEnd('.').trim('/')) }
            .filter { it.isNotEmpty() && NomsFichiers.ressembleAUnFichier(it) && compatible(it, langage) }
        candidats(reponseSeule).firstOrNull()?.let { return it }
        if (reponseSeule.isNotBlank()) return null
        return candidats(reponse).lastOrNull()
    }

    /** Le nom donne a un bloc quand l'IA n'a pas su en choisir un. */
    fun nomParDefaut(langage: String): String =
        LANGAGES[langage.lowercase()]?.nomParDefaut ?: "fichier." + langage.ifBlank { "txt" }

    /**
     * [nom] tel quel s'il est libre, sinon « nom_2.ext », « nom_3.ext »... Un nom est pris quand il
     * est dans [pris] ou quand [existe] le dit (verification directe dans le projet, sans limite de liste).
     */
    fun unique(nom: String, pris: Set<String>, existe: (String) -> Boolean = { false }): String {
        val prisBas = pris.map { it.lowercase() }.toSet()
        if (nom.lowercase() !in prisBas && !existe(nom)) return nom
        val dossier = nom.substringBeforeLast('/', "")
        val fichier = nom.substringAfterLast('/')
        val point = fichier.lastIndexOf('.')
        val base = if (point > 0) fichier.substring(0, point) else fichier
        val extension = if (point > 0) fichier.substring(point) else ""
        var n = 2
        while (true) {
            val essai = (if (dossier.isEmpty()) "" else "$dossier/") + base + "_" + n + extension
            if (essai.lowercase() !in prisBas && !existe(essai)) return essai
            n++
        }
    }

    /**
     * Le nom de chaque bloc sans nom (numero de la ligne d'ouverture -> nom). [reponsesIA] suit
     * l'ordre de [blocs] ; null = l'IA n'a pas repondu. [dejaPris] : fichiers du projet et de la
     * reponse ; [existe] verifie aussi chaque nom choisi directement dans le projet. Un nom n'est jamais donne deux fois et ne reprend jamais un fichier existant.
     */
    fun attribuer(
        blocs: List<ActionFichier.SansNom>,
        reponsesIA: List<String?>,
        dejaPris: Set<String>,
        existe: (String) -> Boolean = { false }
    ): Map<Int, String> {
        val pris = dejaPris.toMutableSet()
        val noms = LinkedHashMap<Int, String>()
        blocs.forEachIndexed { i, bloc ->
            if (bloc.ligneOuverture < 0) return@forEachIndexed
            val choisi = reponsesIA.getOrNull(i)?.let { lireNom(it, bloc.langage) }
                ?: nomParDefaut(bloc.langage)
            val nom = unique(choisi, pris, existe)
            pris.add(nom)
            noms[bloc.ligneOuverture] = nom
        }
        return noms
    }
}
