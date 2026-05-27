package org.proanima.shelter.routes

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.application.call
import org.proanima.shelter.views.renderGuidePage

fun Route.guideRoutes() {
    get("/guide") {
        call.respondText(
            text = renderGuidePage(),
            contentType = ContentType.Text.Html
        )
    }
}