package com.codexlocal.studio.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

/** Ce qu'on lit dans le repertoire central d'un zip : de quoi verifier methode et alignement. */
class InfosZip(val nom: String, val methode: Int, val debutDonnees: Int, val tailleStockee: Int)

/** Lecture « a la main » d'un zip, independante de ApkZip : c'est elle qui juge le resultat. */
object OutilsZip {

    fun structure(zip: ByteArray): List<InfosZip> {
        val b = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN)
        var fin = zip.size - 22
        while (fin >= 0 && b.getInt(fin) != 0x06054b50) fin--
        require(fin >= 0) { "pas de fin de repertoire central" }
        val nombre = b.getShort(fin + 10).toInt() and 0xFFFF
        var p = b.getInt(fin + 16)
        val liste = ArrayList<InfosZip>()
        repeat(nombre) {
            require(b.getInt(p) == 0x02014b50) { "entree du repertoire central abimee" }
            val methode = b.getShort(p + 10).toInt() and 0xFFFF
            val tailleStockee = b.getInt(p + 20)
            val longNom = b.getShort(p + 28).toInt() and 0xFFFF
            val longExtra = b.getShort(p + 30).toInt() and 0xFFFF
            val longCommentaire = b.getShort(p + 32).toInt() and 0xFFFF
            val local = b.getInt(p + 42)
            val nom = String(zip, p + 46, longNom, Charsets.UTF_8)
            require(b.getInt(local) == 0x04034b50) { "entete local abime pour $nom" }
            val nomLocal = b.getShort(local + 26).toInt() and 0xFFFF
            val extraLocal = b.getShort(local + 28).toInt() and 0xFFFF
            liste.add(InfosZip(nom, methode, local + 30 + nomLocal + extraLocal, tailleStockee))
            p += 46 + longNom + longExtra + longCommentaire
        }
        return liste
    }
}

/** Lecture « a la main » d'un manifeste binaire : chaines, attributs d'un element, suite des blocs. */
object OutilsAxml {

    class Attribut(val brut: Int, val typeValeur: Int, val donnee: Int)

    /** Taille de la table des chaines (le bloc qui suit l'entete de 8 octets). */
    fun tailleTable(axml: ByteArray): Int =
        ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN).getInt(8 + 4)

    fun drapeauxTable(axml: ByteArray): Int =
        ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN).getInt(8 + 16)

    /** Parcourt les blocs apres la table et renvoie (type, position). Verifie que ca tombe pile en fin de fichier. */
    fun blocs(axml: ByteArray): List<Pair<Int, Int>> {
        val b = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        check(b.getInt(4) == axml.size) { "taille annoncee ${b.getInt(4)} != ${axml.size}" }
        var p = 8 + tailleTable(axml)
        val liste = ArrayList<Pair<Int, Int>>()
        while (p < axml.size) {
            val type = b.getShort(p).toInt() and 0xFFFF
            val taille = b.getInt(p + 4)
            check(taille >= 8 && p + taille <= axml.size) { "bloc abime en $p" }
            liste.add(type to p)
            p += taille
        }
        check(p == axml.size) { "les blocs ne tombent pas pile en fin de fichier" }
        return liste
    }

    /** Numeros de ressources du bloc « resource map » (0x0180), ou null s'il n'y en a pas. */
    fun cartesRessources(axml: ByteArray): List<Int>? {
        val b = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        val (_, p) = blocs(axml).firstOrNull { it.first == 0x0180 } ?: return null
        val taille = b.getInt(p + 4)
        return (8 until taille step 4).map { b.getInt(p + it) }
    }

    /** Attributs de l'element `element` (premier trouve), par nom. */
    fun attributs(axml: ByteArray, chaines: List<String>, element: String): Map<String, Attribut> {
        val b = ByteBuffer.wrap(axml).order(ByteOrder.LITTLE_ENDIAN)
        for ((type, p) in blocs(axml)) {
            if (type != 0x0102) continue
            if (chaines[b.getInt(p + 20)] != element) continue
            val debut = p + 16 + (b.getShort(p + 24).toInt() and 0xFFFF)
            val taille = b.getShort(p + 26).toInt() and 0xFFFF
            val nombre = b.getShort(p + 28).toInt() and 0xFFFF
            val resultat = LinkedHashMap<String, Attribut>()
            for (i in 0 until nombre) {
                val a = debut + i * taille
                resultat[chaines[b.getInt(a + 4)]] =
                    Attribut(b.getInt(a + 8), b.get(a + 15).toInt() and 0xFF, b.getInt(a + 16))
            }
            return resultat
        }
        error("element $element introuvable")
    }

    // ---------------------------------------------------------------- fabrication

    private const val NS_AUCUN = -1

    /**
     * Petit manifeste binaire comme en fabrique aapt2 :
     * `<manifest versionCode=1 package=ID><application label=NOM/></manifest>`.
     * Chaines : 0 versionCode, 1 label, 2 package, 3 manifest, 4 application, 5 ID, 6 NOM (+ `autres`).
     */
    fun fabriquer(
        utf8: Boolean,
        identifiant: String,
        nom: String,
        autres: List<String> = emptyList(),
        trie: Boolean = false
    ): ByteArray {
        val chaines = listOf("versionCode", "label", "package", "manifest", "application", identifiant, nom) + autres
        val table = table(chaines, utf8, trie)

        val cartes = ByteBuffer.allocate(8 + 8).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0180.toShort()); putShort(8); putInt(16)
            putInt(0x0101021b)      // versionCode
            putInt(0x01010001)      // label
        }.array()

        val sortie = ByteArrayOutputStream()
        sortie.write(table)
        sortie.write(cartes)
        sortie.write(debutElement(3, listOf(
            attribut(0, -1, 0x10, 1),
            attribut(2, 5, 0x03, 5)
        )))
        sortie.write(debutElement(4, listOf(attribut(1, 6, 0x03, 6))))
        sortie.write(finElement(4))
        sortie.write(finElement(3))
        val corps = sortie.toByteArray()

        val entete = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        entete.putShort(0x0003.toShort()); entete.putShort(8); entete.putInt(8 + corps.size)
        return entete.array() + corps
    }

    private fun attribut(nom: Int, brut: Int, type: Int, donnee: Int): ByteArray =
        ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(NS_AUCUN); putInt(nom); putInt(brut)
            putShort(8); put(0); put(type.toByte()); putInt(donnee)
        }.array()

    private fun debutElement(nom: Int, attributs: List<ByteArray>): ByteArray {
        val taille = 36 + 20 * attributs.size
        val b = ByteBuffer.allocate(taille).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0x0102.toShort()); b.putShort(16); b.putInt(taille); b.putInt(1); b.putInt(-1)
        b.putInt(NS_AUCUN); b.putInt(nom)
        b.putShort(20); b.putShort(20); b.putShort(attributs.size.toShort())
        b.putShort(0); b.putShort(0); b.putShort(0)
        attributs.forEach { b.put(it) }
        return b.array()
    }

    private fun finElement(nom: Int): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(0x0103.toShort()); putShort(16); putInt(24); putInt(1); putInt(-1)
            putInt(NS_AUCUN); putInt(nom)
        }.array()

    private fun table(chaines: List<String>, utf8: Boolean, trie: Boolean): ByteArray {
        val donnees = ByteArrayOutputStream()
        val decalages = IntArray(chaines.size)
        chaines.forEachIndexed { i, s ->
            decalages[i] = donnees.size()
            if (utf8) {
                val octets = s.toByteArray(Charsets.UTF_8)
                longueurUtf8(donnees, s.length)
                longueurUtf8(donnees, octets.size)
                donnees.write(octets)
                donnees.write(0)
            } else {
                donnees.write(s.length and 0xFF); donnees.write(s.length shr 8)
                s.forEach { donnees.write(it.code and 0xFF); donnees.write(it.code shr 8) }
                donnees.write(0); donnees.write(0)
            }
        }
        while (donnees.size() % 4 != 0) donnees.write(0)
        val debut = 28 + 4 * chaines.size
        val taille = debut + donnees.size()
        val b = ByteBuffer.allocate(taille).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(0x0001.toShort()); b.putShort(28); b.putInt(taille)
        b.putInt(chaines.size); b.putInt(0)
        b.putInt((if (utf8) 0x100 else 0) or (if (trie) 0x1 else 0))
        b.putInt(debut); b.putInt(0)
        decalages.forEach { b.putInt(it) }
        b.put(donnees.toByteArray())
        return b.array()
    }

    private fun longueurUtf8(sortie: ByteArrayOutputStream, v: Int) {
        if (v > 0x7F) { sortie.write((v shr 8) or 0x80); sortie.write(v and 0xFF) } else sortie.write(v)
    }
}

/** Fabrique de petits PNG unis (sans passer par Android), pour tester les images. */
object PngTest {

    fun uni(largeur: Int, hauteur: Int, argb: Int): ByteArray {
        val lignes = ByteArrayOutputStream()
        repeat(hauteur) {
            lignes.write(0)                              // filtre : aucun
            repeat(largeur) {
                lignes.write((argb shr 16) and 0xFF)     // R
                lignes.write((argb shr 8) and 0xFF)      // G
                lignes.write(argb and 0xFF)              // B
                lignes.write((argb shr 24) and 0xFF)     // A
            }
        }
        val deflater = Deflater()
        deflater.setInput(lignes.toByteArray()); deflater.finish()
        val compresse = ByteArrayOutputStream()
        val tampon = ByteArray(4096)
        while (!deflater.finished()) compresse.write(tampon, 0, deflater.deflate(tampon))
        deflater.end()

        val sortie = ByteArrayOutputStream()
        sortie.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteBuffer.allocate(13).putInt(largeur).putInt(hauteur)
            .put(8.toByte()).put(6.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte()).array()    // 8 bits, RGBA
        bloc(sortie, "IHDR", ihdr)
        bloc(sortie, "IDAT", compresse.toByteArray())
        bloc(sortie, "IEND", ByteArray(0))
        return sortie.toByteArray()
    }

    private fun bloc(sortie: ByteArrayOutputStream, type: String, donnees: ByteArray) {
        sortie.write(ByteBuffer.allocate(4).putInt(donnees.size).array())
        val corps = type.toByteArray(Charsets.US_ASCII) + donnees
        sortie.write(corps)
        sortie.write(ByteBuffer.allocate(4).putInt(CRC32().apply { update(corps) }.value.toInt()).array())
    }
}
