package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DogRoutesTest {

    @Test
    fun `GET dogs links to Svora in every locale`() = testApplication {
        application { module() }

        listOf("ru", "en", "sr").forEach { code ->
            val response = client.get("/$code/dogs")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status, code)
            assertTrue(body.contains("https://www.instagram.com/svora.volunteers/"), code)
            assertTrue(body.contains("https://linktr.ee/svora.volunteers"), code)
            assertTrue(body.contains("Svora.Volunteers"), code)
            assertTrue(body.contains("""href="/$code/dogs""""), code)
        }
    }

    @Test
    fun `home page mentions Svora and links to the dogs page`() = testApplication {
        application { module() }

        val body = client.get("/ru").bodyAsText()

        assertTrue(body.contains("Волонтёрский центр Pro Anima · Svora.Volunteers"))
        assertTrue(body.contains("""href="/ru/dogs""""))
        assertTrue(body.contains("https://www.instagram.com/svora.volunteers/"))
    }
}
