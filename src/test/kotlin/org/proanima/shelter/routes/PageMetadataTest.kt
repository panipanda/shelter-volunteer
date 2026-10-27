package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.module
import org.proanima.shelter.views.PUBLIC_BASE_URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageMetadataTest {

    private fun title(body: String): String =
        Regex("<title>\\s*(.*?)\\s*</title>", RegexOption.DOT_MATCHES_ALL).find(body)!!.groupValues[1]

    @Test
    fun `every page title carries the site name once`() = testApplication {
        application { module() }

        mapOf(
            "/ru/cats" to "Кошки · Pro Anima",
            "/ru/dogs" to "Собаки · Pro Anima",
            "/ru/guide" to "Гайд волонтёра · Pro Anima",
            "/en/dogs" to "Dogs · Pro Anima",
            "/sr/dogs" to "Psi · Pro Anima"
        ).forEach { (path, expected) ->
            assertEquals(expected, title(client.get(path).plainText()), path)
        }
        assertEquals(1, Regex("Pro Anima").findAll(title(client.get("/ru").plainText())).count())
    }

    @Test
    fun `a page has a canonical address, language alternates and a link preview`() = testApplication {
        application { module() }

        val body = client.get("/ru/cats").plainText()

        assertTrue(hasTag(body, "link", """rel="canonical"""", """href="$PUBLIC_BASE_URL/ru/cats""""))
        listOf("ru", "en", "sr", "x-default").forEach { code ->
            assertTrue(hasTag(body, "link", """rel="alternate"""", """hreflang="$code""""), code)
        }
        assertTrue(hasTag(body, "meta", """property="og:title""""))
        assertTrue(hasTag(body, "meta", """property="og:url"""", """content="$PUBLIC_BASE_URL/ru/cats""""))
        assertTrue(hasTag(body, "meta", """name="twitter:card""""))
    }

    @Test
    fun `a pet page previews with its own first photo as an absolute address`() = testApplication {
        application { module() }

        val body = client.get("/ru/cats/felix").plainText()

        assertTrue(hasTag(body, "meta", """property="og:image"""", """content="$PUBLIC_BASE_URL/images/"""))
        assertTrue(hasTag(body, "meta", """property="og:description""""))
    }

    @Test
    fun `a page without a photo of its own previews with the home photo`() = testApplication {
        application { module() }

        val body = client.get("/en/guide").plainText()

        assertTrue(
            hasTag(body, "meta", """property="og:image"""", """content="$PUBLIC_BASE_URL/images/home-hero.jpg"""")
        )
    }

    @Test
    fun `an error page is not indexed and has no address of its own`() = testApplication {
        application { module() }

        val response = client.get("/ru/cats/nobody")
        val body = response.plainText()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(hasTag(body, "meta", """name="robots"""", """content="noindex""""))
        assertFalse(hasTag(body, "link", """rel="canonical""""))
        assertFalse(hasTag(body, "link", """rel="alternate""""))
    }
}
