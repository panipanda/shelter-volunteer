package org.proanima.shelter.routes

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.service.VisitService
import org.proanima.shelter.views.renderVisitArchivePage
import org.proanima.shelter.views.renderVisitsPage

fun Route.visitRoutes(visitService: VisitService) {
    get("/visits") {
        val visits = visitService.getUpcomingVisits()

        call.respondText(
            text = renderVisitsPage(visits),
            contentType = ContentType.Text.Html
        )
    }

    get("/visits/archive") {
        val visits = visitService.getArchivedVisits()

        call.respondText(
            text = renderVisitArchivePage(visits),
            contentType = ContentType.Text.Html
        )
    }
}