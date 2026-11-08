package org.proanima.shelter.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.NoSiteContent
import org.proanima.shelter.repository.SiteContentRepository
import org.proanima.shelter.service.SectionService
import org.proanima.shelter.service.itemSlug
import org.proanima.shelter.service.shownLocales
import org.proanima.shelter.views.respondPage
import org.proanima.shelter.views.sectionItemPage
import org.proanima.shelter.views.sectionPage

// The sections made in the admin panel live at /<language>/<address of the section>. The fixed pages (cats, dogs,
// guide, visits, help) have routes of their own, which win over these; the admin panel does not let a section
// take one of those addresses anyway. An address that is no section's gets the ordinary 404.
fun Route.sectionRoutes(sectionService: SectionService, locale: AppLocale, siteContent: SiteContentRepository = NoSiteContent) {
    get("/{section}") {
        val section = sectionService.findSection(call.parameters["section"]!!, locale)

        if (section == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val messages = siteContent.load().bundle(locale)
            val alternates = sectionService.localesOf(section)

            call.respondPage(locale) {
                sectionPage(
                    section,
                    sectionService.publishedItems(section, locale),
                    locale,
                    call.request.path(),
                    activeTag = call.request.queryParameters["tag"],
                    messages = messages,
                    alternates = alternates,
                    localeHref = { other -> if (other in alternates) "/${other.code}/${section.slug}" else "/${other.code}" }
                )
            }
        }
    }

    get("/{section}/{slug}") {
        val section = sectionService.findSection(call.parameters["section"]!!, locale)
        val item = section?.let { sectionService.findItem(it, call.parameters["slug"]!!, locale) }

        if (section == null || item == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val messages = siteContent.load().bundle(locale)
            val alternates = item.shownLocales()

            call.respondPage(locale) {
                sectionItemPage(
                    section,
                    item,
                    locale,
                    call.request.path(),
                    messages = messages,
                    alternates = alternates,
                    localeHref = { other -> if (other in alternates) "/${other.code}/${section.slug}/${itemSlug(item)}" else "/${other.code}" }
                )
            }
        }
    }
}
