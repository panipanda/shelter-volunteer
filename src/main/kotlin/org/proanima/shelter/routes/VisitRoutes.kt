package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.NoSiteContent
import org.proanima.shelter.repository.SiteContentRepository
import org.proanima.shelter.service.VisitOverrides
import org.proanima.shelter.service.nextScheduledVisit
import org.proanima.shelter.views.respondPage
import org.proanima.shelter.views.visitsPage
import java.time.Clock
import java.time.LocalDateTime

fun Route.visitRoutes(
    locale: AppLocale,
    clock: Clock,
    visitOverrides: VisitOverrides,
    siteContent: SiteContentRepository = NoSiteContent
) {
    get("/visits") {
        val nextVisit = nextScheduledVisit(LocalDateTime.now(clock), visitOverrides)
        val messages = siteContent.load().bundle(locale)

        call.respondPage(locale) {
            visitsPage(nextVisit, locale, call.request.path(), messages)
        }
    }
}
