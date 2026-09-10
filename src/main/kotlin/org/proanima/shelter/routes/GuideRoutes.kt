package org.proanima.shelter.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.application.call
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.views.renderGuidePage

fun Route.guideRoutes(locale: AppLocale) {
    get("/guide") {
        call.respondText(
            text = renderGuidePage(locale),
            contentType = ContentType.Text.Html
        )
    }
}