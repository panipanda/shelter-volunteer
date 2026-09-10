package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.views.homePage

fun Route.homeRoutes(locale: AppLocale) {
    get {
        call.respondHtml {
            homePage(locale)
        }
    }
}
