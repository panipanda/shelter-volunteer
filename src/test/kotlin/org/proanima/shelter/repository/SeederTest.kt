package org.proanima.shelter.repository

import org.proanima.shelter.service.validatePets
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeederTest {

    private val dataSource = newTestDataSource()
    private val clock = TestClock()
    private val cats = PgCatRepository(dataSource, clock)
    private val dogs = PgDogRepository(dataSource, clock)
    private val photos = PgPhotoStore(dataSource, clock)

    private fun realData() = loadSeedData(File("data/cats.json"), File("data/dogs.json"), File("data/images"))

    @Test
    fun `the first start copies the real content into the database and it is sound there`() {
        assertTrue(seedIfNeeded(dataSource, clock, ::realData))

        val fromFiles = realData()
        assertEquals(fromFiles.cats.sortedBy { it.id }, cats.findAll())
        assertEquals(fromFiles.dogs.sortedBy { it.id }, dogs.findAll())
        assertEquals(fromFiles.photos.map { it.name }.toSet(), photos.names())

        val names = photos.names()
        assertEquals(emptyList(), validatePets(cats.findAll(), dogs.findAll(), "the database") { it in names })
    }

    @Test
    fun `page photos and placeholders are not copied as pet photos`() {
        seedIfNeeded(dataSource, clock, ::realData)

        assertTrue(photos.names().none { isSiteAsset(it) }, photos.names().toString())
    }

    @Test
    fun `the second start does nothing, not even read the files`() {
        seedIfNeeded(dataSource, clock, ::realData)

        assertFalse(seedIfNeeded(dataSource, clock) { error("must not be called") })
    }

    @Test
    fun `removing every dog does not bring them back on the next start`() {
        seedIfNeeded(dataSource, clock, ::realData)
        dogs.findAll().forEach { dogs.delete(it.id) }

        assertFalse(seedIfNeeded(dataSource, clock, ::realData))
        assertEquals(emptyList(), dogs.findAll())
    }

    @Test
    fun `new pets get ids after the largest one that came from the files`() {
        seedIfNeeded(dataSource, clock, ::realData)
        val largestCat = cats.findAll().maxOf { it.id }
        val largestDog = dogs.findAll().maxOf { it.id }

        val newCat = cats.save(cats.findAll().first().copy(id = 0))
        val newDog = dogs.save(dogs.findAll().first().copy(id = 0))

        assertEquals(largestCat + 1, newCat.id)
        assertEquals(largestDog + 1, newDog.id)
    }

    @Test
    fun `a seeding that fails leaves the database empty so that the next start tries again`() {
        val broken = realData().let { SeedData(it.cats + it.cats.first(), it.dogs, it.photos) }

        assertFailsWith<Exception> { seedIfNeeded(dataSource, clock) { broken } }

        assertEquals(emptyList(), cats.findAll())
        assertEquals(emptySet(), photos.names())
        assertTrue(seedIfNeeded(dataSource, clock, ::realData))
    }

    @Test
    fun `site pictures are added, left alone when unchanged, and replaced when the file changes`() {
        val dir = createTempDirectory("site-assets").toFile().also { it.deleteOnExit() }
        File(dir, "home-hero.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(dir, "default-cat.svg").writeText("<svg/>")
        File(dir, "felix.jpg").writeBytes(byteArrayOf(9))
        File(dir, "notes.txt").writeText("not a picture")

        assertEquals(listOf("default-cat.svg", "home-hero.jpg"), syncSiteAssets(photos, dir))
        assertEquals("image/svg+xml", photos.get("default-cat.svg")?.contentType)
        assertEquals(emptyList(), syncSiteAssets(photos, dir))

        File(dir, "home-hero.jpg").writeBytes(byteArrayOf(4, 5, 6))
        assertEquals(listOf("home-hero.jpg"), syncSiteAssets(photos, dir))
        assertEquals(setOf("default-cat.svg", "home-hero.jpg"), photos.names())
    }

    @Test
    fun `content types follow the file extension`() {
        assertEquals("image/jpeg", contentTypeFor("a.JPG"))
        assertEquals("image/png", contentTypeFor("a.png"))
        assertEquals("image/svg+xml", contentTypeFor("a.svg"))
        assertEquals(null, contentTypeFor("a.txt"))
        assertEquals(null, contentTypeFor("noextension"))
    }
}
