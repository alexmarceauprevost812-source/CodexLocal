package com.codexlocal.web

import com.codexlocal.Prefs
import com.codexlocal.Message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RechercheWebTest {
    @Test fun decideLocalementSansEnvoyerLeMessageNiLesSecrets() {
        assertNull(RechercheWeb.requete("Salut mon chum"))
        assertNull(RechercheWeb.requete("Écris une fonction addition"))
        assertNull(RechercheWeb.requete("Sans internet, vérifie React"))
        assertEquals("https://docs.python.org/3/library/pathlib.html",
            RechercheWeb.requete("Avec pathlib, lis mon fichier secret_token_123"))
        val requete = RechercheWeb.requete("Vérifie la dernière version de React, clé personnelle abc123")!!
        assertEquals("react latest version official documentation", requete)
        assertFalse(requete.contains("abc123"))
    }

    @Test fun rechercheGeneraleSeulementQuandOnLaDemandeEtSansSecrets() {
        assertNull(RechercheWeb.requeteGenerale("Salut mon chum"))
        assertNull(RechercheWeb.requeteGenerale("Corrige mon code"))
        assertNull(RechercheWeb.requeteGenerale("Sans internet, cherche sur le web React"))
        assertEquals("comment faire un bouton copier en JavaScript",
            RechercheWeb.requeteGenerale("Cherche sur le web comment faire un bouton copier en JavaScript"))
        assertNotNull(RechercheWeb.requeteGenerale("Donne-moi un exemple de code Python pour lire un CSV"))
        val q = RechercheWeb.requeteGenerale("Peux-tu chercher sur internet la version de Ollama, clé sk-ant-abc123456789xyz ghp_abcdefgh12345678")!!
        assertFalse(q.contains("sk-ant"))
        assertFalse(q.contains("ghp_"))
        // le code en ligne et les mots de passe ne partent jamais
        val enLigne = RechercheWeb.requeteGenerale("Cherche sur le web pourquoi `login('alice', 'hunter2')` échoue, mot de passe: hunter3")!!
        assertFalse(enLigne.contains("hunter2"))
        assertFalse(enLigne.contains("hunter3"))
        assertFalse(enLigne.contains("login("))
        // un bloc de code colle n'est jamais envoye
        val avecCode = RechercheWeb.requeteGenerale("Cherche sur le web cette erreur\n```\nmot_de_passe = 'secret'\n```")!!
        assertFalse(avecCode.contains("secret"))
    }

    @Test fun rechercheGeneraleLitLePremierResultatEtEcarteLesAdressesPrivees() = runBlocking {
        val resultat = RechercheWeb.rechercherGeneral("bouton copier javascript",
            chercher = { listOf(SourceWeb("http://192.168.0.1/admin", "routeur"), SourceWeb("https://localhost/x", "local"),
                SourceWeb("https://developer.mozilla.org/clipboard", "MDN — Clipboard API"), SourceWeb("https://stackoverflow.com/q/1", "SO")) },
            lire = { url -> if (url.contains("mozilla")) "navigator.clipboard.writeText(texte)" else "" })
        assertEquals(listOf("https://developer.mozilla.org/clipboard", "https://stackoverflow.com/q/1"), resultat.sources.map { it.url })
        assertTrue(resultat.contexte().contains("navigator.clipboard.writeText"))
        assertFalse(RechercheWeb.urlPublique("https://10.0.0.2/"))
        assertTrue(RechercheWeb.urlPublique("https://fr.wikipedia.org/wiki/Bitcoin"))
    }

    @Test fun refuseLesDestinationsLocalesEtLesFauxDomaines() {
        for (url in listOf("http://docs.python.org/", "https://127.0.0.1/", "file:///secret",
            "https://docs.python.org.evil.test/", "https://user:password@docs.python.org/", "https://docs.python.org:8080/")) {
            assertFalse(url, RechercheWeb.urlAutorisee(url))
        }
        assertTrue(RechercheWeb.urlAutorisee("https://docs.python.org/3/library/pathlib.html"))
    }

    @Test fun consulteLaDocumentationEtFournitUnContexteBorne() = runBlocking {
        val requete = RechercheWeb.requete("Avec pathlib lis un fichier")!!
        var urlVue = ""
        val resultat = RechercheWeb.rechercher(requete) { url ->
            urlVue = url
            "<html><script>secret()</script><main>Path.read_text lit un fichier UTF-8.</main></html>"
        }
        assertEquals(requete, urlVue)
        assertEquals(1, resultat.sources.size)
        assertTrue(resultat.contexte().contains("Path.read_text"))
        assertFalse(resultat.contexte().contains("secret()"))
        assertTrue(resultat.resume().contains(requete))
        val long = RechercheWeb.rechercher(requete) { "<p>" + "x".repeat(12000) + "</p>" }
        assertEquals(4000, long.sources.single().extrait.length)
    }

    @Test fun ecarteLesResultatsHorsSujetEtLesUrlsNonOfficielles() {
        val rss = """<rss><channel>
            <item><link>https://example.com/react</link><description>React</description></item>
            <item><link>https://docs.python.org/3/</link><description>Python</description></item>
            <item><link>https://react.dev/blog</link><description>React official versions</description></item>
            </channel></rss>"""
        val sources = RechercheWeb.lireRss(rss, "react latest version official documentation")
        assertEquals(listOf("https://react.dev/blog"), sources.map { it.url })
    }

    @Test fun refuseXmlAvecEntitesEtPropageLesAnnulations() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            RechercheWeb.lireRss("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///secret'>]><rss/>", "react")
        }
        try {
            RechercheWeb.rechercher("https://docs.python.org/3/") { throw CancellationException("stop") }
            fail("Annulation ignorée")
        } catch (_: CancellationException) { }
    }

    @Test fun echecNePretendPasAvoirVerifieDesSources() {
        val echec = ResultatWeb("react", emptyList(), indisponible = true)
        assertTrue(echec.contexte().contains("ne prétends pas"))
        assertTrue(echec.resume().contains("indisponible"))
    }

    @Test fun afficheLesSourcesSansAjouterDePrefillNiPolluerLaReponseTransmise() {
        val resultat = ResultatWeb("python", listOf(SourceWeb("https://docs.python.org/3/", "Documentation")))
        val message = Message.assistant("def addition(a, b): return a + b")
        resultat.annoterReponse(message)
        assertTrue(message.content.contains("Sources Internet consultées"))
        assertEquals("def addition(a, b): return a + b", message.envoi)
        assertThrows(IllegalArgumentException::class.java) { resultat.annoterReponse(Message.user("Question")) }
    }

    @Test fun preferencePersistantePeutDesactiverLeReseau() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("codexlocal", 0).edit().clear().commit()
        val prefs = Prefs(context)
        assertTrue(prefs.rechercheWebAuto)
        prefs.rechercheWebAuto = false
        assertFalse(Prefs(context).rechercheWebAuto)
    }

    @Test
    fun leCodeColleNeDeclenchePasLaRecherche() {
        assertNull(RechercheWeb.requeteGenerale("Explique ce fichier client Acme confidentiel\n```js\nconst snippet = 'bonjour';\n```"))
        assertNull(RechercheWeb.requeteGenerale("Que fait `snippet` ici ?"))
        assertNotNull(RechercheWeb.requeteGenerale("Trouve un exemple de code pour lire un fichier en Kotlin\n```kotlin\nval x = 1\n```"))
    }
}
