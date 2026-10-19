package com.codexlocal.studio.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Retouche d'un AndroidManifest.xml binaire (AXML) deja compile, sans aapt :
 *  - remplace des chaines entieres de la table des chaines (identifiant, nom de l'appli) ;
 *    les longueurs peuvent changer, la table est reconstruite et les tailles recalculees ;
 *  - ecrit le numero de version (attribut android:versionCode de <manifest>).
 * Le reste du fichier (elements, attributs) reference les chaines par numero : il ne change pas.
 */
object ManifesteBinaire {

    private const val TYPE_XML = 0x0003
    private const val TYPE_TABLE_CHAINES = 0x0001
    private const val TYPE_DEBUT_ELEMENT = 0x0102
    private const val DRAPEAU_TRIE = 0x00000001
    private const val DRAPEAU_UTF8 = 0x00000100
    private const val TYPE_ENTIER_DEC = 0x10

    class Resultat(val octets: ByteArray, val remplacees: Set<String>)

    private class Table(val chaines: List<String>, val utf8: Boolean, val drapeaux: Int, val taille: Int)

    /** Toutes les chaines du manifeste (utile pour verifier un resultat). */
    fun lireChaines(manifeste: ByteArray): List<String> = lireTable(manifeste).chaines

    fun retoucher(
        manifeste: ByteArray,
        remplacements: Map<String, String>,
        versionCode: Int? = null
    ): Resultat {
        val table = lireTable(manifeste)
        val remplacees = HashSet<String>()
        val nouvelles = table.chaines.map { chaine ->
            val autre = remplacements[chaine]
            if (autre != null) remplacees.add(chaine)
            autre ?: chaine
        }

        val tableOctets = ecrireTable(nouvelles, table.utf8, table.drapeaux and DRAPEAU_TRIE.inv())
        val reste = manifeste.copyOfRange(8 + table.taille, manifeste.size)

        val sortie = ByteArrayOutputStream(8 + tableOctets.size + reste.size)
        val entete = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        entete.putShort(TYPE_XML.toShort())
        entete.putShort(8)
        entete.putInt(8 + tableOctets.size + reste.size)
        sortie.write(entete.array())
        sortie.write(tableOctets)
        sortie.write(reste)
        val resultat = sortie.toByteArray()

        if (versionCode != null) {
            ecrireVersion(resultat, 8 + tableOctets.size, nouvelles, versionCode)
        }
        return Resultat(resultat, remplacees)
    }

    // ----------------------------------------------------------- lecture

    private fun lireTable(octets: ByteArray): Table {
        val b = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN)
        require(octets.size > 40 && (b.getShort(0).toInt() and 0xFFFF) == TYPE_XML) {
            "Ce n'est pas un manifeste Android binaire"
        }
        val debut = 8
        require((b.getShort(debut).toInt() and 0xFFFF) == TYPE_TABLE_CHAINES) {
            "Table des chaines introuvable dans le manifeste"
        }
        val tailleTable = b.getInt(debut + 4)
        val nombre = b.getInt(debut + 8)
        val nombreStyles = b.getInt(debut + 12)
        val drapeaux = b.getInt(debut + 16)
        val debutChaines = b.getInt(debut + 20)
        require(nombreStyles == 0) { "Manifeste avec styles : non pris en charge" }
        require(tailleTable in 28..(octets.size - 8)) { "Table des chaines abimee" }
        val utf8 = drapeaux and DRAPEAU_UTF8 != 0

        val chaines = ArrayList<String>(nombre)
        for (i in 0 until nombre) {
            val decalage = b.getInt(debut + 28 + 4 * i)
            chaines.add(if (utf8) lireUtf8(b, debut + debutChaines + decalage) else lireUtf16(b, debut + debutChaines + decalage))
        }
        return Table(chaines, utf8, drapeaux, tailleTable)
    }

    private fun lireUtf16(b: ByteBuffer, position: Int): String {
        var p = position
        var longueur = b.getShort(p).toInt() and 0xFFFF
        p += 2
        if (longueur and 0x8000 != 0) {
            longueur = ((longueur and 0x7FFF) shl 16) or (b.getShort(p).toInt() and 0xFFFF)
            p += 2
        }
        val tableau = CharArray(longueur) { b.getShort(p + 2 * it).toInt().toChar() }
        return String(tableau)
    }

    private fun lireUtf8(b: ByteBuffer, position: Int): String {
        var p = position
        // Premiere longueur : nombre de caracteres (ignoree), seconde : nombre d'octets.
        p += if (b.get(p).toInt() and 0x80 != 0) 2 else 1
        var octets = b.get(p).toInt() and 0xFF
        p += 1
        if (octets and 0x80 != 0) {
            octets = ((octets and 0x7F) shl 8) or (b.get(p).toInt() and 0xFF)
            p += 1
        }
        val tableau = ByteArray(octets) { b.get(p + it) }
        return String(tableau, Charsets.UTF_8)
    }

    // ---------------------------------------------------------- ecriture

    private fun ecrireTable(chaines: List<String>, utf8: Boolean, drapeaux: Int): ByteArray {
        val donnees = ByteArrayOutputStream()
        val decalages = IntArray(chaines.size)
        chaines.forEachIndexed { i, chaine ->
            decalages[i] = donnees.size()
            if (utf8) ecrireUtf8(donnees, chaine) else ecrireUtf16(donnees, chaine)
        }
        while (donnees.size() % 4 != 0) donnees.write(0)

        val debutChaines = 28 + 4 * chaines.size
        val taille = debutChaines + donnees.size()
        val b = ByteBuffer.allocate(taille).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(TYPE_TABLE_CHAINES.toShort())
        b.putShort(28)
        b.putInt(taille)
        b.putInt(chaines.size)
        b.putInt(0)                 // styles
        b.putInt(drapeaux)
        b.putInt(debutChaines)
        b.putInt(0)                 // debut des styles
        decalages.forEach { b.putInt(it) }
        b.put(donnees.toByteArray())
        return b.array()
    }

    private fun ecrireUtf16(sortie: ByteArrayOutputStream, chaine: String) {
        val longueur = chaine.length
        require(longueur < 0x7FFF) { "Chaine trop longue pour le manifeste" }
        sortie.write(longueur and 0xFF)
        sortie.write(longueur shr 8)
        for (c in chaine) {
            sortie.write(c.code and 0xFF)
            sortie.write(c.code shr 8)
        }
        sortie.write(0)
        sortie.write(0)
    }

    private fun ecrireUtf8(sortie: ByteArrayOutputStream, chaine: String) {
        val octets = chaine.toByteArray(Charsets.UTF_8)
        require(octets.size < 0x7FFF && chaine.length < 0x7FFF) { "Chaine trop longue pour le manifeste" }
        ecrireLongueurUtf8(sortie, chaine.length)
        ecrireLongueurUtf8(sortie, octets.size)
        sortie.write(octets)
        sortie.write(0)
    }

    private fun ecrireLongueurUtf8(sortie: ByteArrayOutputStream, valeur: Int) {
        if (valeur > 0x7F) {
            sortie.write((valeur shr 8) or 0x80)
            sortie.write(valeur and 0xFF)
        } else {
            sortie.write(valeur)
        }
    }

    /** Cherche l'element <manifest> et ecrit la valeur de son attribut versionCode. */
    private fun ecrireVersion(octets: ByteArray, apresTable: Int, chaines: List<String>, version: Int) {
        val b = ByteBuffer.wrap(octets).order(ByteOrder.LITTLE_ENDIAN)
        var p = apresTable
        while (p + 8 <= octets.size) {
            val type = b.getShort(p).toInt() and 0xFFFF
            val taille = b.getInt(p + 4)
            require(taille >= 8 && p + taille <= octets.size) { "Manifeste abime" }
            if (type == TYPE_DEBUT_ELEMENT) {
                val nom = b.getInt(p + 20)
                if (nom in chaines.indices && chaines[nom] == "manifest") {
                    val debutAttributs = p + 16 + (b.getShort(p + 24).toInt() and 0xFFFF)
                    val tailleAttribut = b.getShort(p + 26).toInt() and 0xFFFF
                    val nombre = b.getShort(p + 28).toInt() and 0xFFFF
                    for (i in 0 until nombre) {
                        val a = debutAttributs + i * tailleAttribut
                        val nomAttribut = b.getInt(a + 4)
                        if (nomAttribut in chaines.indices && chaines[nomAttribut] == "versionCode") {
                            b.putInt(a + 8, -1)                         // pas de texte brut
                            b.put(a + 15, TYPE_ENTIER_DEC.toByte())     // type de la valeur
                            b.putInt(a + 16, version)
                            return
                        }
                    }
                    return
                }
            }
            p += taille
        }
    }
}
