package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.proanima.shelter.module

class GuideRoutesTest {

    @Test
    fun `GET guide returns volunteer guide page`() = testApplication {
        application {
            module()
        }

        val response = client.get("/guide")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)

        assertTrue(body.contains("Volunteer guide"))
        assertTrue(body.contains("Before the visit"))
        assertTrue(body.contains("What to bring"))
        assertTrue(body.contains("During the visit"))
        assertTrue(body.contains("How signup works"))

        assertTrue(body.contains("""href="/cats""""))
        assertTrue(body.contains("""href="/visits""""))
        assertTrue(body.contains("""href="/visits/archive""""))
    }
}