package com.codexlocal

import android.view.View
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Barre de progression du modele (telechargement, copie, chargement).
 *  - [pct] de 0 a 99 : barre qui avance ;
 *  - null : on ne connait pas l'avancement (barre qui defile) ;
 *  - 100 ou plus : c'est fini, la barre disparait.
 */
fun LinearProgressIndicator.montrerProgres(pct: Int?) {
    if (pct != null && pct >= 100) {
        cacherProgres()
        return
    }
    val indetermine = pct == null
    if (isIndeterminate != indetermine) {
        // Material refuse de changer de mode pendant que la barre est visible.
        visibility = View.INVISIBLE
        isIndeterminate = indetermine
    }
    if (pct != null) setProgressCompat(pct.coerceIn(0, 99), true)
    visibility = View.VISIBLE
}

fun LinearProgressIndicator.cacherProgres() {
    visibility = View.GONE
}
