package com.codexlocal.studio.apk

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipInputStream

/**
 * Lecture et ecriture d'archives APK (zip) : on garde la main sur l'alignement.
 * Android 11+ exige que `resources.arsc` soit non compresse et aligne sur 4 octets ;
 * toutes les entrees non compressees sont donc alignees comme le fait `zipalign`.
 */
object ApkZip {

    class Entree(val nom: String, val octets: ByteArray, val compresse: Boolean)

    private const val ALIGNEMENT = 4

    // Formats deja compresses : les recompresser ne sert a rien.
    private val dejaCompresses = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "mp3", "ogg", "mp4", "webm", "woff", "woff2",
        "zip", "gz", "m4a", "aac", "flac"
    )

    /** Choisit stocke / compresse pour une entree. */
    fun doitEtreCompresse(nom: String): Boolean {
        if (!nomAutoriseCompression(nom)) return false
        val extension = nom.substringAfterLast('.', "").lowercase()
        return extension !in dejaCompresses
    }

    private fun nomAutoriseCompression(nom: String): Boolean = nom != "resources.arsc"

    /** Lit toutes les entrees d'un zip (les dossiers sont ignores). */
    fun lire(octets: ByteArray): List<Entree> {
        val entrees = ArrayList<Entree>()
        ZipInputStream(octets.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                entrees.add(Entree(e.name, zip.readBytes(), doitEtreCompresse(e.name)))
            }
        }
        return entrees
    }

    /** Ecrit un zip : entrees stockees alignees sur 4 octets, les autres compressees. */
    fun ecrire(sortie: OutputStream, entrees: List<Entree>) {
        val compteur = Compteur(sortie)
        val repertoire = ByteArrayOutputStream()
        val vus = HashSet<String>()

        for (entree in entrees) {
            require(vus.add(entree.nom)) { "Fichier en double dans l'APK : ${entree.nom}" }
            val nom = entree.nom.toByteArray(Charsets.UTF_8)
            val crc = CRC32().apply { update(entree.octets) }.value
            // resources.arsc doit rester stocke (Android 11+ refuse l'APK sinon), quoi qu'on demande.
            val veutCompresser = entree.compresse && nomAutoriseCompression(entree.nom)
            val donnees = if (veutCompresser) deflater(entree.octets) else entree.octets
            // Si la compression n'aide pas, on stocke tel quel.
            val compresse = veutCompresser && donnees.size < entree.octets.size
            val contenu = if (compresse) donnees else entree.octets

            val decalageEntete = compteur.position
            val extra = if (compresse) 0 else bourrage(decalageEntete + 30 + nom.size)

            val entete = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
            entete.putInt(0x04034b50)
            entete.putShort(20)                       // version requise
            entete.putShort(0x0800)                   // noms en UTF-8
            entete.putShort(if (compresse) 8 else 0)  // methode
            entete.putShort(0)                        // heure
            entete.putShort(0x21)                     // date : 1980-01-01 (archive reproductible)
            entete.putInt(crc.toInt())
            entete.putInt(contenu.size)
            entete.putInt(entree.octets.size)
            entete.putShort(nom.size.toShort())
            entete.putShort(extra.toShort())
            compteur.write(entete.array())
            compteur.write(nom)
            if (extra > 0) compteur.write(champExtra(extra))
            compteur.write(contenu)

            val central = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
            central.putInt(0x02014b50)
            central.putShort(20)                      // fabrique par
            central.putShort(20)                      // version requise
            central.putShort(0x0800)
            central.putShort(if (compresse) 8 else 0)
            central.putShort(0)
            central.putShort(0x21)
            central.putInt(crc.toInt())
            central.putInt(contenu.size)
            central.putInt(entree.octets.size)
            central.putShort(nom.size.toShort())
            central.putShort(0)                       // extra
            central.putShort(0)                       // commentaire
            central.putShort(0)                       // disque
            central.putShort(0)                       // attributs internes
            central.putInt(0)                         // attributs externes
            central.putInt(decalageEntete.toInt())
            repertoire.write(central.array())
            repertoire.write(nom)
        }

        val debutRepertoire = compteur.position
        compteur.write(repertoire.toByteArray())
        val fin = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        fin.putInt(0x06054b50)
        fin.putShort(0)
        fin.putShort(0)
        fin.putShort(entrees.size.toShort())
        fin.putShort(entrees.size.toShort())
        fin.putInt(repertoire.size())
        fin.putInt(debutRepertoire.toInt())
        fin.putShort(0)
        compteur.write(fin.array())
        sortie.flush()
    }

    /** Octets de remplissage pour que les donnees tombent sur un multiple de 4. */
    internal fun bourrage(positionDonnees: Long): Int {
        var manque = ((ALIGNEMENT - positionDonnees % ALIGNEMENT) % ALIGNEMENT).toInt()
        // Un champ « extra » mesure au moins 4 octets (identifiant + taille).
        if (manque in 1..3) manque += ALIGNEMENT
        return manque
    }

    private fun champExtra(taille: Int): ByteArray {
        val tampon = ByteBuffer.allocate(taille).order(ByteOrder.LITTLE_ENDIAN)
        tampon.putShort(0xD935.toShort())             // identifiant utilise par zipalign
        tampon.putShort((taille - 4).toShort())
        return tampon.array()
    }

    private fun deflater(octets: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION, true)
        d.setInput(octets)
        d.finish()
        val sortie = ByteArrayOutputStream(octets.size / 2 + 64)
        val tampon = ByteArray(8192)
        while (!d.finished()) {
            val n = d.deflate(tampon)
            sortie.write(tampon, 0, n)
        }
        d.end()
        return sortie.toByteArray()
    }

    private class Compteur(private val sortie: OutputStream) : OutputStream() {
        var position = 0L
            private set

        override fun write(b: Int) {
            sortie.write(b)
            position++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            sortie.write(b, off, len)
            position += len
        }
    }
}
