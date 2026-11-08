package org.proanima.shelter.admin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.testing.ApplicationTestBuilder
import org.proanima.shelter.configureRoutes
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.EditableGuideRepository
import org.proanima.shelter.repository.GuideContent
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.PgCatRepository
import org.proanima.shelter.repository.PgDogRepository
import org.proanima.shelter.repository.PgMenuRepository
import org.proanima.shelter.repository.PgPhotoStore
import org.proanima.shelter.repository.PgSectionRepository
import org.proanima.shelter.repository.PgSiteContentRepository
import org.proanima.shelter.repository.TestClock
import org.proanima.shelter.repository.newTestDataSource
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import java.time.Duration
import kotlin.test.assertEquals

// What every admin test needs: an empty in-memory database, the site with the admin panel on it, a browser
// that keeps cookies and does not follow redirects, and the way to log in.
abstract class AdminTestBase {

    protected val dataSource = newTestDataSource()
    protected val clock = TestClock()
    protected val cats = PgCatRepository(dataSource, clock, Duration.ofSeconds(30))
    protected val dogs = PgDogRepository(dataSource, clock, Duration.ofSeconds(30))
    protected val photos = PgPhotoStore(dataSource, clock)
    protected val siteContent = PgSiteContentRepository(dataSource, clock, Duration.ofSeconds(30))
    protected val sections = PgSectionRepository(dataSource, clock, Duration.ofSeconds(30))
    protected val menu = PgMenuRepository(dataSource, clock, Duration.ofSeconds(30))
    protected val password = "correct horse battery"

    protected val guideRepository = object : GuideRepository {
        override fun findGuideMarkdown(locale: AppLocale): GuideContent = GuideContent("# Guide", locale.code)
    }

    protected fun adminSetup(secureCookie: Boolean = false) = AdminSetup(
        AdminConfig(password, "s".repeat(40), secureCookie),
        cats, dogs, photos, clock,
        FailedLoginThrottle(penaltyMillis = 0, maxWaiting = 5),
        content = siteContent,
        shippedGuide = guideRepository,
        sections = sections,
        menu = menu
    )

    protected fun ApplicationTestBuilder.start(admin: AdminSetup? = adminSetup()) {
        application {
            configureRoutes(
                CatService(cats), GuideService(EditableGuideRepository(guideRepository, siteContent)),
                dogService = DogService(dogs), photoStore = photos, admin = admin
            )
        }
    }

    protected fun ApplicationTestBuilder.browser(): HttpClient = createClient {
        followRedirects = false
        install(HttpCookies)
    }

    protected suspend fun HttpClient.post(path: String, fields: Map<String, String>, csrf: String? = null): HttpResponse =
        submitForm(path, parameters {
            fields.forEach { (name, value) -> append(name, value) }
            if (csrf != null) append("_csrf", csrf)
        })

    // Logs in and returns the token that the forms of the logged-in person carry.
    protected suspend fun HttpClient.logIn(): String {
        assertEquals(HttpStatusCode.SeeOther, post("/admin/login", mapOf("password" to password)).status)
        return csrfOf(get("/admin").bodyAsText())
    }

    protected fun csrfOf(html: String): String =
        Regex("""<input[^>]*name="_csrf"[^>]*value="([^"]+)"""").find(html)?.groupValues?.get(1) ?: error("no form token in:\n$html")
}
