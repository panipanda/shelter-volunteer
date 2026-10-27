package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpRoutesTest {

    private val headings = mapOf(
        "ru" to "Как ещё помочь",
        "en" to "Other ways to help",
        "sr" to "Kako još možete pomoći"
    )

    @Test
    fun `GET help lists the ways to help, the donation link and a link to the coordinators in every locale`() =
        testApplication {
            application { module() }

            headings.forEach { (code, heading) ->
                val response = client.get("/$code/help")
                val body = response.plainText()

                assertEquals(HttpStatusCode.OK, response.status, code)
                assertTrue(body.contains("<h1>$heading</h1>"), code)
                assertEquals(5, Regex("<li>").findAll(body.substringAfter("help-list").substringBefore("</ul>")).count(), code)
                assertTrue(hasTag(body, "a", """href="https://linktr.ee/svora.volunteers""""), code)
                assertTrue(hasTag(body, "a", """href="/$code#join""""), code)
            }
        }

    @Test
    fun `GET help says who Svora is, below the ways to help`() = testApplication {
        application { module() }

        val svora = mapOf("ru" to "Кто такая Свора", "en" to "Who is Svora.Volunteers", "sr" to "Ko je Svora.Volunteers")
        svora.forEach { (code, heading) ->
            val body = client.get("/$code/help").plainText()

            assertTrue(body.contains("<h2>$heading</h2>"), code)
            assertEquals(2, Regex("<ul").findAll(body).count(), "$code: the ways to help and what Svora does")
            assertTrue(body.indexOf("linktr.ee") < body.indexOf("<h2>$heading</h2>"), code)
        }
    }

    @Test
    fun `the help page is in the main menu`() = testApplication {
        application { module() }

        mapOf("ru" to "Как помочь", "en" to "How to help", "sr" to "Kako pomoći").forEach { (code, label) ->
            val menu = client.get("/$code").plainText().substringAfter("""class="main-nav"""").substringBefore("</nav>")

            assertTrue(menu.contains("""<a href="/$code/help">$label</a>"""), code)
        }
        // The menu item is the current page on /help.
        assertTrue(client.get("/ru/help").plainText().contains("""<a href="/ru/help" aria-current="page">Как помочь</a>"""))
    }

    @Test
    fun `every page links to the help page from the footer`() = testApplication {
        application { module() }

        listOf("/ru", "/en/cats", "/sr/guide", "/ru/dogs", "/en/help").forEach { path ->
            val code = path.substring(1, 3)
            val footer = client.get(path).plainText().substringAfter("<footer")

            assertTrue(hasTag(footer, "a", """href="/$code/help""""), path)
        }
    }

    @Test
    fun `the contact is not repeated on the help page`() = testApplication {
        application { module() }

        val body = client.get("/ru/help").plainText()

        assertEquals(0, Regex("https://t.me/Nata_Rogava").findAll(body).count())
        assertEquals(0, Regex("https://t.me/Trikcsy").findAll(body).count())
    }
}
