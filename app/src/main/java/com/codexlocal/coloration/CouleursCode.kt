package com.codexlocal.coloration

/** Calculs sur les couleurs ecrites dans le code. */
object CouleursCode {

    private const val FOND = 0x07

    /**
     * Couleur opaque d'un code hexadecimal sans le « # » (3, 4, 6 ou 8 chiffres), ou null.
     * A 8 chiffres : AARRGGBB si [alphaEnPremier] (Android), sinon RRGGBBAA (CSS).
     * Une couleur transparente est melangee au fond noir de l'editeur : c'est ce qu'on verrait.
     */
    fun depuisHex(hex: String, alphaEnPremier: Boolean): Int? {
        if (hex.any { Character.digit(it, 16) < 0 }) return null
        val complet = when (hex.length) {
            3 -> hex.map { "$it$it" }.joinToString("") + "ff"
            4 -> hex.map { "$it$it" }.joinToString("")
            6 -> hex + "ff"
            8 -> hex
            else -> return null
        }
        val octets = IntArray(4) { complet.substring(2 * it, 2 * it + 2).toInt(16) }
        val (r, g, b, a) = if (hex.length == 8 && alphaEnPremier) {
            listOf(octets[1], octets[2], octets[3], octets[0])
        } else {
            listOf(octets[0], octets[1], octets[2], octets[3])
        }
        return opaque(r, g, b, a)
    }

    fun opaque(r: Int, g: Int, b: Int, a: Int = 255): Int {
        fun melange(v: Int) = (v * a + FOND * (255 - a)) / 255
        return (0xFF shl 24) or (melange(r) shl 16) or (melange(g) shl 8) or melange(b)
    }

    /** Clair (> 150) ou sombre : sert a choisir noir ou blanc pour lire un texte sur cette couleur. */
    fun luminance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    fun texteSur(argb: Int): Int = if (luminance(argb) > 150) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}
