package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.NoSiteContent
import org.proanima.shelter.repository.SiteContentRepository
import org.proanima.shelter.views.helpPage
import org.proanima.shelter.views.respondPage

fun Route.helpRoutes(locale: AppLocale, siteContent: SiteContentRepository = NoSiteContent) {
    get("/help") {
        val messages = siteContent.load().bundle(locale)

        call.respondPage(locale) {
            helpPage(locale, call.request.path(), messages)
        }
    }
}
