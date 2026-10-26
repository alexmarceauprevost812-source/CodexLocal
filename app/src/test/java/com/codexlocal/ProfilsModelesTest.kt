package com.codexlocal

import org.junit.Assert.*
import org.junit.Test

class ProfilsModelesTest {
    @Test fun reconnaitLesNomsRenommesParUsb() {
        assertEquals(ProfilsModeles.Profil.ALEX,
            ProfilsModeles.reconnaitre("/sdcard/CodexLocal/models/Alex Marceau Prevost.gguf"))
        assertEquals(ProfilsModeles.Profil.LUCKY,
            ProfilsModeles.reconnaitre("/sdcard/CodexLocal/models/Lucky.gguf"))
        assertEquals(ProfilsModeles.Profil.DEMON_HA,
            ProfilsModeles.reconnaitre("/sdcard/CodexLocal/models/demon-HA.gguf"))
    }

    @Test fun accepteLesVariantesSansConfondreLesAutresModeles() {
        assertEquals(ProfilsModeles.Profil.LUCKY,
            ProfilsModeles.reconnaitre("C:\\models\\LUCKY GGUF.GGUF"))
        assertEquals(ProfilsModeles.Profil.DEMON_HA,
            ProfilsModeles.reconnaitre("demon_HA GGUF.gguf"))
        assertNull(ProfilsModeles.reconnaitre("unlucky.gguf"))
        assertNull(ProfilsModeles.reconnaitre("lucky.gguf.part"))
        assertNull(ProfilsModeles.reconnaitre("lucky.txt"))
        assertNull(ProfilsModeles.reconnaitre("qwen3-8b.gguf"))
    }

    @Test fun conserveLesConsignesPersonnellesEtLesContraintesDesModes() {
        for (base in listOf(
            "Consigne personnelle : utilise les bibliotheques du projet.",
            CHAT_SYSTEM_PROMPT,
            DEFAULT_SYSTEM_PROMPT + " Reponds uniquement en JSON."
        )) {
            val resultat = ProfilsModeles.consigne("Lucky.gguf", base)
            assertTrue(resultat.startsWith(base + "\n\n"))
            assertTrue(resultat.contains("ton nom est Lucky GGUF"))
            assertTrue(resultat.contains("format de reponse"))
            assertTrue(resultat.contains("sans outil et resultat reels"))
        }
    }

    @Test fun neModifiePasLesModelesNonPersonnalises() {
        val base = "Tu donnes des noms de fichiers."
        assertEquals(base, ProfilsModeles.consigne("qwen3-8b.gguf", base))
        assertEquals(base, ProfilsModeles.consigne("", base))
    }

    @Test fun leProfilResteStableApresUnNouveauLancementEtUnChangementDeMode() {
        val chemin = "/sdcard/CodexLocal/models/demon-HA.gguf"
        val initial = ProfilsModeles.consigne(chemin, DEFAULT_SYSTEM_PROMPT)
        ProfilsModeles.consigne(chemin, CHAT_SYSTEM_PROMPT)
        assertEquals(initial, ProfilsModeles.consigne(chemin, DEFAULT_SYSTEM_PROMPT))
        assertTrue(initial.contains("court plan de match"))
    }
}
