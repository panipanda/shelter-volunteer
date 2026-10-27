package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.proanima.shelter.module

class GuideRoutesTest {

    @Test
    fun `GET guide returns volunteer guide page`() = testApplication {
        application {
            module()
        }

        val response = client.get("/ru/guide")
        val body = response.plainText()

        assertEquals(HttpStatusCode.OK, response.status)

        assertTrue(body.contains("Гайд волонтёра"))
        assertTrue(body.contains("Перед визитом"))
        assertTrue(body.contains("Что взять с собой"))
        assertTrue(body.contains("Во время визита"))
        assertTrue(body.contains("""lang="ru""""))

        assertTrue(body.contains("Работа с кошками"))
        assertTrue(body.contains("Работа с собаками"))
        assertTrue(body.contains("""href="#general""""))
        assertTrue(body.contains("""id="cats""""))
        assertTrue(body.contains("""id="dogs""""))

        assertTrue(body.contains("""href="/ru/cats""""))
        assertTrue(body.contains("""href="/ru/visits""""))
    }

    @Test
    fun `the guide does not repeat what the site says elsewhere`() = testApplication {
        application { module() }

        listOf("ru", "en", "sr").forEach { code ->
            val body = client.get("/$code/guide").plainText()

            // The title and the table of contents say what the page is, and sign-up is on the home page.
            val removed = listOf(
                "Сначала общее для всех", "This page is a guide", "Ova stranica je vodič",
                "Как работает запись", "How signup works", "Kako funkcioniše prijava",
                "Рабочие перчатки", "Work gloves", "Radne rukavice"
            )
            removed.forEach { assertFalse(body.contains(it), "$code: \"$it\" is back in the guide") }

            // The dogs page is a list of dogs now, so the text of the guide does not point to it
            // (the menu in the header still does).
            val guideText = body.substringAfter("""<div class="guide">""")
            assertFalse(guideText.contains("""href="/$code/dogs""""), code)
        }
    }

    @Test
    fun `GET guide for sr locale returns Serbian content`() = testApplication {
        application {
            module()
        }

        val response = client.get("/sr/guide")
        val body = response.plainText()

        assertEquals(HttpStatusCode.OK, response.status)

        assertTrue(body.contains("Vodič za volontere"))
        assertTrue(body.contains("Pre posete"))
        assertTrue(body.contains("Šta poneti"))
        assertTrue(body.contains("""lang="sr""""))

        assertTrue(body.contains("""href="/sr/cats""""))
    }
}