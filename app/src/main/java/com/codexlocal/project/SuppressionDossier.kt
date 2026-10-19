package com.codexlocal.project

import java.io.File
import java.nio.file.Files

/**
 * Efface le dossier d'un projet de l'espace CodexLocal, avec des garde-fous : on n'efface jamais
 * l'espace des projets lui-meme, ni rien qui en sorte. Code pur, sans Android.
 */
object SuppressionDossier {

    /**
     * Efface [cible] et tout son contenu, seulement si [cible] est un dossier directement dans
     * [racine] (jamais [racine] elle-meme, ni un chemin qui en sort). Un lien symbolique est
     * retire comme un simple lien : on n'entre pas dedans, donc rien en dehors du projet n'est
     * touche. Renvoie vrai si le dossier n'existe plus a la fin.
     */
    fun supprimer(racine: File, cible: File): Boolean {
        val nom = cible.name
        if (nom.isEmpty() || nom == "." || nom == "..") return false
        val parent = cible.absoluteFile.parentFile ?: return false
        if (parent.canonicalFile != racine.canonicalFile) return false
        if (!existe(cible)) return true
        effacer(cible)
        return !existe(cible)
    }

    private fun estLien(f: File): Boolean = Files.isSymbolicLink(f.toPath())

    private fun existe(f: File): Boolean = f.exists() || estLien(f)

    private fun effacer(f: File) {
        if (!estLien(f) && f.isDirectory) f.listFiles()?.forEach { effacer(it) }
        f.delete()
    }
}
