package com.codexlocal

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Codex REA : mode par défaut au premier lancement et choix de la release de mise à jour. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodexReaTest {
    @Test fun modeParDefautDependDeLaVariante() {
        assertEquals(AssistantMode.REA, ModeParDefaut.choisir(true))
        assertEquals(AssistantMode.CODEX, ModeParDefaut.choisir(false))
    }

    @Test fun prefsReaAppOuvreEnReaSansPcEtUnChoixEnregistreLEmporte() {
        val prefs = Prefs(RuntimeEnvironment.getApplication(), reaApp = true)
        assertEquals(AssistantMode.REA, prefs.assistantMode)
        prefs.assistantMode = AssistantMode.CHAT
        assertEquals(AssistantMode.CHAT, prefs.assistantMode)
    }

    @Test fun prefsStandardNeChangePasDeMode() {
        val prefs = Prefs(RuntimeEnvironment.getApplication(), reaApp = false)
        prefs.reaUrl = "http://127.0.0.1:8766/mcp"
        assertEquals(AssistantMode.CODEX, prefs.assistantMode)
    }

    @Test fun varianteSelonLesOptionsDeCompilation() {
        assertNull(UpdateChecker.variante(perso = false, rea = false))
        assertEquals(UpdateChecker.PERSO, UpdateChecker.variante(perso = true, rea = false))
        assertEquals(UpdateChecker.REA, UpdateChecker.variante(perso = false, rea = true))
    }

    private val releases = JSONArray("""[
      {"tag_name":"v1.6.9","assets":[{"name":"CodexLocal-1.6.9.apk","browser_download_url":"https://x/std.apk"}]},
      {"tag_name":"perso-v1.6.8","assets":[{"name":"TVA-LexOS-1.6.8.apk","browser_download_url":"https://x/perso.apk"}],"body":"p"},
      {"tag_name":"rea-v1.6.7","draft":true,"assets":[{"name":"Codex-REA-1.6.7.apk","browser_download_url":"https://x/brouillon.apk"}]},
      {"tag_name":"rea-v1.6.6","assets":[{"name":"notes.txt","browser_download_url":"https://x/n"}]},
      {"tag_name":"rea-v1.6.5","assets":[{"name":"Codex-REA-1.6.5.apk","browser_download_url":"https://x/rea.apk"}],"body":"r"}
    ]""")

    @Test fun chaqueVarianteTrouveSaPropreRelease() {
        assertEquals(Triple("1.6.8", "https://x/perso.apk", "p"), UpdateChecker.choisirRelease(releases, UpdateChecker.PERSO))
        // brouillon et release sans APK Codex-REA ignorés
        assertEquals(Triple("1.6.5", "https://x/rea.apk", "r"), UpdateChecker.choisirRelease(releases, UpdateChecker.REA))
        assertNull(UpdateChecker.choisirRelease(JSONArray("[]"), UpdateChecker.REA))
    }

    @Test fun standardPrendLePremierApkSansPrefixe() {
        val assets = releases.getJSONObject(0).getJSONArray("assets")
        assertEquals("https://x/std.apk", UpdateChecker.choisirApk(assets, ""))
        assertNull(UpdateChecker.choisirApk(assets, "Codex-REA"))
        assertNull(UpdateChecker.choisirApk(null, ""))
    }
}
