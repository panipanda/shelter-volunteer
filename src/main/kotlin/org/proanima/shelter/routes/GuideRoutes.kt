package org.proanima.shelter.routes

import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.views.guidePage
import org.proanima.shelter.views.respondPage

fun Route.guideRoutes(guideService: GuideService, locale: AppLocale) {
    get("/guide") {
        val content = guideService.getGuideBlocks(locale)

        call.respondPage(locale) {
            guidePage(locale, content.blocks, content.lang, call.request.path())
        }
    }
}
