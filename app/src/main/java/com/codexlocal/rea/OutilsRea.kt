package com.codexlocal.rea

import org.json.JSONObject

/** Source des outils d'analyse du mode REA : le serveur REA du PC, ou le moteur local du téléphone. */
interface OutilsRea {
    /** Prépare les outils et renvoie la consigne (avec le catalogue réel) donnée au modèle. */
    suspend fun connect(): String

    /** Exécute un appel déjà validé par [ReaProtocol]. Une erreur d'outil lève une exception. */
    suspend fun call(call: JSONObject, maxChars: Int = 24_000): String

    suspend fun close()
}
