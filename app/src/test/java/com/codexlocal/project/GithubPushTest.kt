package com.codexlocal.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GithubPushTest {

    @Test
    fun nomSeul() {
        assertEquals(GithubPush.Depot(null, "mon-projet"), GithubPush.analyserDepot(" mon-projet "))
    }

    @Test
    fun proprietaireEtNom() {
        assertEquals(GithubPush.Depot("alex", "app"), GithubPush.analyserDepot("alex/app"))
    }

    @Test
    fun adresseGithub() {
        assertEquals(
            GithubPush.Depot("alex", "app"),
            GithubPush.analyserDepot("https://github.com/alex/app.git")
        )
    }

    @Test
    fun saisiesInvalides() {
        assertNull(GithubPush.analyserDepot(""))
        assertNull(GithubPush.analyserDepot("a/b/c"))
        assertNull(GithubPush.analyserDepot("mon projet"))
        assertNull(GithubPush.analyserDepot(".."))
    }
}
