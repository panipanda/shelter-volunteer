package org.proanima.shelter.routes

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.moduleWithDatabase
import org.proanima.shelter.moduleWithFiles
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.JsonDogRepository
import org.proanima.shelter.service.petSlug
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The whole site started from an empty database must look the same as the site started from the data/ folder:
// that is the promise of the move to the database ("the site looks as before, the data come from elsewhere").
class DatabaseModeTest {

    private val databaseUrl = "jdbc:h2:mem:site-mode-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

    private val pagePaths: List<String> = buildList {
        val cats = JsonCatRepository().findAll()
        val dogs = JsonDogRepository().findAll()
        AppLocale.entries.forEach { locale ->
            val base = "/${locale.code}"
            addAll(listOf(base, "$base/cats", "$base/dogs", "$base/guide", "$base/visits", "$base/help"))
            cats.forEach { add("$base/cats/${petSlug(it.name?.en, it.id)}") }
            dogs.forEach { add("$base/dogs/${petSlug(it.name.en, it.id)}") }
        }
    }

    private val imageNames: List<String> =
        File("data/images").listFiles { file -> file.isFile }.orEmpty().map { it.name }.sorted()

    private class Snapshot(val pages: Map<String, String>, val images: Map<String, Image>)

    private class Image(val status: HttpStatusCode, val contentType: String?, val cacheControl: String?, val bytes: ByteArray)

    private suspend fun ApplicationTestBuilder.snapshot(): Snapshot {
        val pages = pagePaths.associateWith { path ->
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            response.plainText()
        }
        val images = imageNames.associateWith { name ->
            val response = client.get("/images/$name")
            Image(response.status, response.headers[HttpHeaders.ContentType], response.headers[HttpHeaders.CacheControl], response.body())
        }
        return Snapshot(pages, images)
    }

    @Test
    fun `a site started on an empty database serves the same pages and the same pictures as one started on files`() {
        lateinit var fromFiles: Snapshot
        testApplication {
            application { moduleWithFiles() }
            fromFiles = snapshot()
        }

        lateinit var fromDatabase: Snapshot
        testApplication {
            application { moduleWithDatabase(databaseUrl) }
            fromDatabase = snapshot()
        }

        assertTrue(pagePaths.size > 20 && imageNames.size > 20)
        pagePaths.forEach { path -> assertEquals(fromFiles.pages.getValue(path), fromDatabase.pages.getValue(path), path) }
        imageNames.forEach { name ->
            val expected = fromFiles.images.getValue(name)
            val actual = fromDatabase.images.getValue(name)
            assertEquals(HttpStatusCode.OK, actual.status, name)
            assertEquals(expected.contentType, actual.contentType, name)
            assertEquals(expected.cacheControl, actual.cacheControl, name)
            assertContentEquals(expected.bytes, actual.bytes, name)
        }
    }

    @Test
    fun `a second start on the same database finds the content in place and serves it`() {
        val url = "jdbc:h2:mem:site-mode-restart;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        testApplication {
            application { moduleWithDatabase(url) }
            assertEquals(HttpStatusCode.OK, client.get("/ru/cats").status)
        }

        testApplication {
            application { moduleWithDatabase(url) }
            assertEquals(HttpStatusCode.OK, client.get("/ru/cats").status)
            assertEquals(HttpStatusCode.OK, client.get("/images/nami.jpg").status)
        }
    }
}
