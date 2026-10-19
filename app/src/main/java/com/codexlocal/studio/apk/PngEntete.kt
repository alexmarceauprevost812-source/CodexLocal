package com.codexlocal.studio.apk

/** Lecture des dimensions d'un PNG a partir de son en-tete (sans le decoder). */
object PngEntete {

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** (largeur, hauteur) d'un PNG, ou null si ces octets n'en sont pas un. */
    fun dimensions(octets: ByteArray): Pair<Int, Int>? {
        // 8 octets de signature, puis le bloc IHDR : longueur (4), « IHDR » (4), largeur (4), hauteur (4).
        if (octets.size < 24) return null
        for (i in SIGNATURE.indices) if (octets[i] != SIGNATURE[i]) return null
        if (String(octets, 12, 4, Charsets.US_ASCII) != "IHDR") return null
        val largeur = entier(octets, 16)
        val hauteur = entier(octets, 20)
        return if (largeur > 0 && hauteur > 0) largeur to hauteur else null
    }

    private fun entier(o: ByteArray, p: Int): Int =
        ((o[p].toInt() and 0xFF) shl 24) or ((o[p + 1].toInt() and 0xFF) shl 16) or
            ((o[p + 2].toInt() and 0xFF) shl 8) or (o[p + 3].toInt() and 0xFF)
}
