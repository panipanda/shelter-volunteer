package org.proanima.shelter.views

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.module
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticAssetsTest {

    @Test
    fun `a stylesheet address carries the fingerprint of the file, the same on every call`() {
        val address = StaticAssets.versioned("/styles/main.css")

        assertTrue(Regex("/styles/main\\.css\\?v=[0-9a-f]{8}").matches(address), address)
        assertEquals(address, StaticAssets.versioned("/styles/main.css"))
        // Different files have different fingerprints.
        assertTrue(StaticAssets.versioned("/styles/admin.css") != address.replace("main", "admin"))
    }

    @Test
    fun `a file that does not exist keeps its plain address`() {
        assertEquals("/styles/nothing-here.css", StaticAssets.versioned("/styles/nothing-here.css"))
    }

    @Test
    fun `the pages link the versioned stylesheet, and the server still serves it under that address`() = testApplication {
        application { module() }

        val html = client.get("/ru").bodyAsText()
        assertContains(html, StaticAssets.versioned("/styles/main.css"))

        val css = client.get(StaticAssets.versioned("/styles/main.css"))
        assertEquals(HttpStatusCode.OK, css.status)
        assertEquals("max-age=3600", css.headers["Cache-Control"])
    }
}
