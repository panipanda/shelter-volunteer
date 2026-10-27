package org.proanima.shelter

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.views.notFoundPage
import org.proanima.shelter.views.respondPage
import org.proanima.shelter.views.serverErrorPage
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.proanima.shelter.ErrorPages")

// Routes just answer 404 (or throw); the page itself is rendered here, in the language of the URL's prefix.
fun Application.configureErrorPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, _ ->
            val locale = call.localeFromPath()
            call.respondPage(locale, HttpStatusCode.NotFound) { notFoundPage(locale) }
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled error on ${call.request.path()}", cause)
            val locale = call.localeFromPath()
            call.respondPage(locale, HttpStatusCode.InternalServerError) { serverErrorPage(locale) }
        }
    }
}

// "/en/cats/x" -> EN. Anything without a known locale prefix (e.g. "/foo") gets the default locale.
private fun ApplicationCall.localeFromPath(): AppLocale {
    val path = request.path()
    return AppLocale.entries.firstOrNull { path == "/${it.code}" || path.startsWith("/${it.code}/") }
        ?: AppLocale.default
}
