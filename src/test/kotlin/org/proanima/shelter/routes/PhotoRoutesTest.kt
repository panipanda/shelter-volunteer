package org.proanima.shelter.routes

import io.ktor.client.request.get
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoRoutesTest {

    private fun cat(id: Int, photoUrls: List<String>) = Cat(
        id = id,
        name = LocalizedText(en = "Cat$id"),
        birthYear = 2025,
        birthMonth = 3,
        description = LocalizedText(en = "Friendly cat"),
        medicalStatus = null,
        photoUrls = photoUrls,
        location = CatLocation.IN_SHELTER,
        adoptionStage = AdoptionStage.FOR_ADOPTION,
        adoptionInstruction = null,
        createdAt = "2026-01-01T00:00:00+01:00",
        updatedAt = "2026-01-01T00:00:00+01:00"
    )

    private fun catRepository(cats: List<Cat>) = object : CatRepository {
        override fun findAll(): List<Cat> = cats
        override fun findById(id: Int): Cat? = cats.firstOrNull { it.id == id }
    }

    private val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent = GuideContent("# Guide", locale.code)
    }

    private fun images(body: String, cssClass: String): List<String> =
        Regex("<img [^>]*class=\"$cssClass\"[^>]*>").findAll(body).map { it.value }.toList()

    @Test
    fun `cards load the card copy, and only the cards after the first row are lazy`() = testApplication {
        val cats = (1..5).map { cat(it, listOf("/images/p$it.jpg")) }
        application {
            configureRoutes(CatService(catRepository(cats)), GuideService(guideRepository))
        }

        val cards = images(client.get("/en/cats").plainText(), "cat-card-photo")

        assertEquals(5, cards.size)
        cards.forEachIndexed { index, tag ->
            assertTrue("""src="/images/p${index + 1}-card.jpg"""" in tag, tag)
            assertEquals(index >= 3, """loading="lazy"""" in tag, tag)
        }
    }

    @Test
    fun `a cat page loads the big photo at once and the thumbnails and the hidden lightbox lazily`() = testApplication {
        val cats = listOf(cat(1, listOf("/images/a.jpg", "/images/b.jpg")))
        application {
            configureRoutes(CatService(catRepository(cats)), GuideService(guideRepository))
        }

        val body = client.get("/en/cats/cat1").plainText()

        val main = images(body, "cat-gallery-photo").single()
        assertTrue("""src="/images/a.jpg"""" in main && "loading" !in main, main)

        val thumbs = images(body, "cat-gallery-thumb")
        assertEquals(listOf("/images/a-card.jpg", "/images/b-card.jpg"), thumbs.map { Regex("""src="([^"]+)"""").find(it)!!.groupValues[1] })
        assertTrue(thumbs.all { """loading="lazy"""" in it })

        // Two lightbox photos plus two thumbnails are lazy; the main photo is the only one that is not.
        assertEquals(4, Regex("""loading="lazy"""").findAll(body).count())
    }

    @Test
    fun `a pet without photos shows the placeholder as it is`() = testApplication {
        application {
            configureRoutes(CatService(catRepository(listOf(cat(1, emptyList())))), GuideService(guideRepository))
        }

        val cards = images(client.get("/en/cats").plainText(), "cat-card-photo")

        assertTrue("""src="/images/default-cat.svg"""" in cards.single(), cards.toString())
    }
}
