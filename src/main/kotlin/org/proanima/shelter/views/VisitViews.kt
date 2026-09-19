package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.article
import kotlinx.html.h2
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.small
import kotlinx.html.strong
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.service.displayVisitAvailability
import org.proanima.shelter.service.displayVisitDirection
import org.proanima.shelter.service.displayVisitStatus

fun HTML.visitsPage(visits: List<VolunteerVisit>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.upcoming.title"), currentPath = currentPath) {
        h1 { +messages.t("visit.page.upcoming.title") }
        p { +messages.t("visit.page.upcoming.intro") }
        visitList(visits, locale, messages.t("visit.page.upcoming.empty"))
    }
}

fun HTML.visitArchivePage(visits: List<VolunteerVisit>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.archive.title"), currentPath = currentPath) {
        h1 { +messages.t("visit.page.archive.title") }
        p { +messages.t("visit.page.archive.intro") }
        visitList(visits, locale, messages.t("visit.page.archive.empty"))
    }
}

private fun FlowContent.visitList(visits: List<VolunteerVisit>, locale: AppLocale, emptyMessage: String) {
    if (visits.isEmpty()) {
        p { +emptyMessage }
        return
    }

    visits.forEach { visit -> visitCard(visit, locale) }
}

internal fun FlowContent.visitCard(visit: VolunteerVisit, locale: AppLocale) {
    val messages = messagesFor(locale)
    val title = visit.title.forLocale(locale)
    val direction = displayVisitDirection(visit.direction, locale)
    val status = displayVisitStatus(visit.status, locale)
    val availability = displayVisitAvailability(visit.status, visit.freePlaces, locale)
    val capacity = visit.capacity?.toString() ?: messages.t("visit.capacity.unknown")
    val signupInstruction = visit.signupInstruction?.forLocale(locale) ?: messages.t("visit.signup.unknown")
    val publicSummary = visit.publicSummary?.forLocale(locale) ?: messages.t("visit.summary.unknown")

    article {
        h2 { +title }
        p { strong { +messages.t("visit.label.date") }; +" ${visit.date}" }
        p { strong { +messages.t("visit.label.time") }; +" ${visit.time} (${visit.timezone})" }
        p { strong { +messages.t("visit.label.direction") }; +" $direction" }
        p { strong { +messages.t("visit.label.status") }; +" $status" }
        p { strong { +messages.t("visit.label.availability") }; +" $availability" }
        p { strong { +messages.t("visit.label.capacity") }; +" $capacity" }
        p { strong { +messages.t("visit.label.signup") }; +" $signupInstruction" }
        p { strong { +messages.t("visit.label.summary") }; +" $publicSummary" }
        p { small { +"${messages.t("visit.label.lastUpdated")} ${visit.lastUpdatedAt}" } }
    }
}
