package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.views.dogsPage

fun Route.dogRoutes(locale: AppLocale) {
    get("/dogs") {
        call.respondHtml {
            dogsPage(locale, call.request.path())
        }
    }
}
