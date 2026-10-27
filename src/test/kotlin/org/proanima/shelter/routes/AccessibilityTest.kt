package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.server.testing.testApplication
import org.proanima.shelter.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccessibilityTest {

    @Test
    fun `every page starts with a skip link to the main content`() = testApplication {
        application { module() }

        listOf("/ru", "/en/cats", "/sr/guide", "/ru/dogs", "/en/visits").forEach { path ->
            val body = client.get(path).plainText()

            assertTrue(hasTag(body, "a", """href="#main"""", """class="skip-link""""), path)
            assertTrue(hasTag(body, "main", """id="main""""), path)
            assertTrue(body.indexOf("skip-link") < body.indexOf("main-nav"), "$path: the skip link comes first")
        }
    }

    @Test
    fun `only the cat page has the cat-page class that reorders it on a phone`() = testApplication {
        application { module() }

        assertTrue(hasTag(client.get("/en/cats/felix").plainText(), "main", """class="page cat-page""""))
        assertTrue(hasTag(client.get("/en/cats").plainText(), "main", """class="page""""))
        assertTrue(!client.get("/en/cats").plainText().contains("cat-page"))
    }

    @Test
    fun `both navigation landmarks have a name`() = testApplication {
        application { module() }

        val body = client.get("/en").plainText()

        assertTrue(hasTag(body, "nav", """class="main-nav"""", """aria-label="Main menu""""))
        assertTrue(hasTag(body, "nav", """class="lang-switch"""", """aria-label="Language""""))
    }

    @Test
    fun `language links say which language they open`() = testApplication {
        application { module() }

        val body = client.get("/ru/cats").plainText()

        assertTrue(hasTag(body, "a", """href="/en/cats"""", """hreflang="en"""", """aria-label="English""""))
        assertTrue(hasTag(body, "a", """href="/sr/cats"""", """hreflang="sr"""", """aria-label="Srpski""""))
        assertTrue(hasTag(body, "span", """aria-current="true"""", """lang="ru""""))
    }

    @Test
    fun `the photo lightbox is a named dialog with a named close link, also for a single photo`() = testApplication {
        application { module() }

        // Sava has one photo: the keyboard script must be there all the same.
        val body = client.get("/en/dogs/sava").plainText()

        assertTrue(hasTag(body, "div", """role="dialog"""", """aria-modal="true"""", """aria-label="Sava""""))
        assertTrue(hasTag(body, "a", """class="cat-gallery-lightbox-close"""", """aria-label="Close photo""""))
        assertEquals(1, Regex("""src="/scripts/cat-gallery.js"""").findAll(body).count())
    }
}
