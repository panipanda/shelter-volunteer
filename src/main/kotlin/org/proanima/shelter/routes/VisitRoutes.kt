package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.views.visitArchivePage
import org.proanima.shelter.views.visitsPage

fun Route.visitRoutes(locale: AppLocale) {
    get("/visits") {
        call.respondHtml {
            visitsPage(locale, call.request.path())
        }
    }

    get("/visits/archive") {
        call.respondHtml {
            visitArchivePage(locale, call.request.path())
        }
    }
}
