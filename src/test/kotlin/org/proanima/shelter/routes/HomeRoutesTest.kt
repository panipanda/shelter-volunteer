package org.proanima.shelter.routes

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.repository.CatRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.GuideService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRoutesTest {

    private fun cat(id: Int, name: String, stage: AdoptionStage) = Cat(
        id = id,
        name = LocalizedText(en = name),
        birthYear = 2025,
        birthMonth = 3,
        description = LocalizedText(en = "Friendly cat"),
        medicalStatus = null,
        photoUrls = emptyList(),
        location = CatLocation.IN_SHELTER,
        adoptionStage = stage,
        adoptionInstruction = null,
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    // The clock is set to UTC so the test does not depend on the machine's time zone; 2026-09-16 is a Wednesday.
    private fun clockAt(dateTime: String): Clock =
        Clock.fixed(Instant.parse("${dateTime}Z"), ZoneId.of("UTC"))

    private fun catRepository(cats: List<Cat>) = object : CatRepository {
        override fun findAll(): List<Cat> = cats
        override fun findById(id: Int): Cat? = cats.firstOrNull { it.id == id }
    }

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent =
            GuideContent("# Guide\n\nStub guide content for tests.", locale.code)
    }

    @Test
    fun `GET home shows about, volunteer info and contacts in every locale`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                guideService = GuideService(guideRepository)
            )
        }

        listOf("ru", "en", "sr").forEach { code ->
            val response = client.get("/$code")
            val body = response.plainText()

            assertEquals(HttpStatusCode.OK, response.status, code)
            assertTrue(body.contains("https://t.me/proanima_belgrade"), code)
            assertTrue(body.contains("https://t.me/Nata_Rogava"), code)
            assertTrue(body.contains("https://t.me/Trikcsy"), code)
            assertTrue(body.contains("Mirica Toma") || body.contains("Мирица Тома"), code)
            assertTrue(body.contains("id=\"join\""), code)
        }
    }

    @Test
    fun `every page has a logo linking to its locale home and serves the icons`() = testApplication {
        application {
            configureRoutes(
                catService = CatService(catRepository(emptyList())),
                guideService = GuideService(guideRepository)
            )
        }

        listOf("ru", "en", "sr").forEach { code ->
            listOf("/$code/cats", "/$code/guide", "/$code/visits").forEach { path ->
                val body = client.get(path).plainText()
                assertTrue(body.contains("<a href=\"/$code\" class=\"logo\">"), path)
                assertTrue(body.contains("/icons/favicon-32.png"), path)
            }
        }
        listOf("logo.png", "favicon-32.png", "favicon-192.png", "apple-touch-icon.png").forEach { icon ->
            assertEquals(HttpStatusCode.OK, client.get("/icons/$icon").status, icon)
        }
    }

    private fun homeAt(dateTime: String, path: String = "/ru") = run {
        var body = ""
        testApplication {
            application {
                configureRoutes(
                    catService = CatService(catRepository(emptyList())),
                    guideService = GuideService(guideRepository),
                    clock = clockAt(dateTime)
                )
            }
            body = client.get(path).plainText()
        }
        body
    }

    @Test
    fun `GET home shows Wednesday when it is the nearest visit`() {
        // Monday, September 14, 2026: the next Wednesday is the 16th, trip at 9:00.
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("<strong>16</strong>"))
        assertTrue(body.contains("Среда"))
        assertTrue(body.contains("Выезд из Врачара (Белград) в 9:00."))
    }

    @Test
    fun `GET home shows Saturday when it is the nearest visit`() {
        // Thursday, September 17, 2026: after Wednesday the next one is Saturday the 19th, trip at 10:00.
        val body = homeAt("2026-09-17T12:00:00")

        assertTrue(body.contains("<strong>19</strong>"))
        assertTrue(body.contains("Суббота"))
        assertTrue(body.contains("Выезд из Врачара (Белград) в 10:00."))
    }

    @Test
    fun `GET home switches to the next visit once today's departure has passed`() {
        assertTrue(homeAt("2026-09-16T08:59:00").contains("<strong>16</strong>"))
        assertTrue(homeAt("2026-09-16T09:01:00").contains("<strong>19</strong>"))
        assertTrue(homeAt("2026-09-19T10:01:00").contains("<strong>23</strong>"))
    }

    @Test
    fun `GET home mentions the chat but no free places`() {
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("Запись, свободные места, отмены и переносы — в чате волонтёров."))
        assertFalse(body.contains("Свободных мест:"))
    }

    @Test
    fun `GET home shows at most three cats that are waiting for adoption`() = testApplication {
        val cats = listOf(
            cat(1, "Alfa", AdoptionStage.FOR_ADOPTION),
            cat(2, "Beta", AdoptionStage.ADOPTED),
            cat(3, "Gamma", AdoptionStage.FOR_ADOPTION),
            cat(4, "Delta", AdoptionStage.FOR_ADOPTION),
            cat(5, "Epsilon", AdoptionStage.FOR_ADOPTION)
        )
        application {
            configureRoutes(
                catService = CatService(catRepository(cats)),
                guideService = GuideService(guideRepository)
            )
        }

        val body = client.get("/en").plainText()

        assertTrue(body.contains("Alfa"))
        assertTrue(body.contains("Gamma"))
        assertTrue(body.contains("Delta"))
        assertFalse(body.contains("Beta"))
        assertFalse(body.contains("Epsilon"))
    }

    @Test
    fun `GET home says the regular days once, in the next-visit block, and has no schedule paragraph in the steps`() {
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(
            body.contains(
                "Выезды по средам и субботам, длятся около трёх часов. " +
                    "Запись, свободные места, отмены и переносы — в чате волонтёров."
            )
        )
        assertFalse(body.contains("зависит от трансфера"))
        assertFalse(body.contains("Визиты проходят по средам"))
    }

    @Test
    fun `GET home shows the Saturday departure time when the next visit is a Saturday`() {
        // Thursday: after Wednesday the next one is Saturday, which leaves at 10:00, not at 9:00.
        val body = homeAt("2026-09-17T12:00:00")

        assertTrue(body.contains("Выезд из Врачара (Белград) в 10:00."))
        assertFalse(body.contains("Выезд из Врачара (Белград) в 9:00."))
    }

    @Test
    fun `GET home step one reads as one sentence with the link inside it`() {
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("""Прочитайте <a href="/ru/guide">гайд волонтёра</a>: что взять с собой и как вести себя в приюте."""))
        assertFalse(body.contains("Открыть гайд"))
        assertFalse(body.contains("короткий гайд"))
    }

    @Test
    fun `GET home ends with the text about the shelter alone, no news links and no help link`() {
        listOf("ru", "en", "sr").forEach { code ->
            val body = homeAt("2026-09-14T10:00:00", "/$code")

            val about = body.substringBeforeLast("</section>").substringAfterLast("<section>")
            assertTrue(about.contains("<h2>"), code)
            assertEquals(1, Regex("<p>").findAll(about).count(), "$code: one paragraph")
            assertFalse(about.contains("<a "), "$code: no links in it")
            assertFalse(body.contains("instagram.com/proanima_beograd"), code)
        }
    }

    @Test
    fun `GET home puts the founder's quote at the end of the join section and links to the help page`() {
        listOf("ru", "en", "sr").forEach { code ->
            val body = homeAt("2026-09-14T10:00:00", "/$code")

            assertEquals(1, Regex("<blockquote").findAll(body).count(), code)
            assertTrue(body.indexOf("""id="join"""") < body.indexOf("<blockquote"), code)
            // The first <h3> is in the volunteers section, which follows the join section.
            assertTrue(body.indexOf("<blockquote") < body.indexOf("<h3>"), code)
            assertTrue(hasTag(body, "a", """href="/$code/help""""), code)
            assertFalse(body.contains("help-list"), "$code: the list of ways to help is on the help page")
        }
    }

    @Test
    fun `GET home has the way to sign up for the dog trips as the third step, once`() {
        listOf("ru", "en", "sr").forEach { code ->
            val body = homeAt("2026-09-14T10:00:00", "/$code")

            assertEquals(1, Regex("https://t.me/dashafirman").findAll(body).count(), code)

            val joinSection = body.substring(body.indexOf("""id="join"""")).substringBefore("</section>")
            val steps = joinSection.substringAfter("<ol").substringBefore("</ol>").split("<li>").drop(1)
            assertEquals(4, steps.size, "$code: guide, cat chat, dog chat, pick a visit")

            // Like the cats' step: a sentence that ends with a colon, and the one link to the moderator in the same line.
            val dogStep = steps[2]
            assertTrue(dogStep.contains("https://t.me/dashafirman"), "$code: the third step is the dogs'")
            assertTrue(dogStep.substringBefore("<a ").trimEnd().endsWith(":"), "$code: $dogStep")
            assertEquals(1, Regex("<a ").findAll(dogStep).count(), "$code: a single link")
            assertFalse(dogStep.contains("<p"), "$code: the link is not set off in a paragraph of its own")
            assertFalse(joinSection.contains("instagram.com"), "$code: no second link in the dogs' sign-up")
            // The quote still closes the section.
            assertTrue(joinSection.indexOf("</ol>") < joinSection.indexOf("<blockquote"), code)
        }
    }

    @Test
    fun `GET home keeps the coordinators' links in the line of the sentence that asks to write to them`() {
        listOf("ru", "en", "sr").forEach { code ->
            val body = homeAt("2026-09-14T10:00:00", "/$code")

            val joinSection = body.substring(body.indexOf("""id="join"""")).substringBefore("</section>")
            val catStep = joinSection.substringAfter("<ol").substringBefore("</ol>").split("<li>")[2]

            assertEquals(2, Regex("<a ").findAll(catStep).count(), code)
            assertTrue(catStep.substringBefore("<a ").trimEnd().endsWith(":"), "$code: $catStep")
            assertFalse(catStep.contains("<p"), "$code: no paragraph of links under the sentence")
        }
    }

    @Test
    fun `GET home lists what volunteers do with the dogs and no longer points to a dogs page of its own`() {
        val body = homeAt("2026-09-14T10:00:00")

        assertTrue(body.contains("Гуляют с собаками."))
        assertTrue(body.contains("Убирают вольеры."))
        assertTrue(body.contains("Общаются и помогают собакам социализироваться."))
        assertFalse(body.contains("О собаках и как помочь"))
        assertFalse(body.contains("организуются отдельно"))
    }

    @Test
    fun `GET visits has no quote, only the two blocks`() {
        assertFalse(homeAt("2026-09-14T10:00:00", "/ru/visits").contains("<blockquote"))
    }

    @Test
    fun `GET home says each contact and fact once`() {
        listOf("/ru", "/en", "/sr").forEach { path ->
            val body = homeAt("2026-09-14T10:00:00", path)

            assertEquals(1, Regex("https://t.me/Nata_Rogava").findAll(body).count(), path)
            assertEquals(1, Regex("https://t.me/Trikcsy").findAll(body).count(), path)
            assertEquals(1, Regex("2010").findAll(body).count(), path)
            assertEquals(5, Regex("<h2>").findAll(body).count(), path)
        }
    }
}
