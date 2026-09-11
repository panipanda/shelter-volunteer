package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.VisitService
import org.proanima.shelter.views.visitArchivePage
import org.proanima.shelter.views.visitsPage

fun Route.visitRoutes(visitService: VisitService, locale: AppLocale) {
    get("/visits") {
        val visits = visitService.getUpcomingVisits()

        call.respondHtml {
            visitsPage(visits, locale, call.request.path())
        }
    }

    get("/visits/archive") {
        val visits = visitService.getArchivedVisits()

        call.respondHtml {
            visitArchivePage(visits, locale, call.request.path())
        }
    }
}
