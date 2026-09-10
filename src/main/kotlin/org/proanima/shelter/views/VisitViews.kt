package org.proanima.shelter.views

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.service.displayVisitAvailability
import org.proanima.shelter.service.displayVisitDirection
import org.proanima.shelter.service.displayVisitStatus

fun renderVisitsPage(visits: List<VolunteerVisit>, locale: AppLocale): String {
    val messages = messagesFor(locale)

    return """
        <!DOCTYPE html>
        <html lang="${locale.code}">
        <head>
            <meta charset="UTF-8">
            <title>${messages.t("visit.page.upcoming.title")}</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <h1>${messages.t("visit.page.upcoming.title")}</h1>
            <p>${messages.t("visit.page.upcoming.intro")}</p>

            ${renderVisitList(visits, locale, emptyMessage = messages.t("visit.page.upcoming.empty"))}
        </body>
        </html>
    """.trimIndent()
}

fun renderVisitArchivePage(visits: List<VolunteerVisit>, locale: AppLocale): String {
    val messages = messagesFor(locale)

    return """
        <!DOCTYPE html>
        <html lang="${locale.code}">
        <head>
            <meta charset="UTF-8">
            <title>${messages.t("visit.page.archive.title")}</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <h1>${messages.t("visit.page.archive.title")}</h1>
            <p>${messages.t("visit.page.archive.intro")}</p>

            ${renderVisitList(visits, locale, emptyMessage = messages.t("visit.page.archive.empty"))}
        </body>
        </html>
    """.trimIndent()
}

private fun renderVisitList(
    visits: List<VolunteerVisit>,
    locale: AppLocale,
    emptyMessage: String
): String {
    if (visits.isEmpty()) {
        return "<p>$emptyMessage</p>"
    }

    return visits.joinToString(separator = "\n") { visit ->
        renderVisitCard(visit, locale)
    }
}

private fun renderVisitCard(visit: VolunteerVisit, locale: AppLocale): String {
    val messages = messagesFor(locale)
    val title = escapeHtml(visit.title.forLocale(locale))
    val date = escapeHtml(visit.date)
    val time = escapeHtml(visit.time)
    val timezone = escapeHtml(visit.timezone)
    val direction = displayVisitDirection(visit.direction, locale)
    val status = displayVisitStatus(visit.status, locale)
    val availability = displayVisitAvailability(visit.status, visit.freePlaces, locale)
    val capacity = visit.capacity?.toString() ?: messages.t("visit.capacity.unknown")
    val signupInstruction = escapeHtml(
        visit.signupInstruction?.forLocale(locale) ?: messages.t("visit.signup.unknown")
    )
    val publicSummary = escapeHtml(visit.publicSummary?.forLocale(locale) ?: messages.t("visit.summary.unknown"))
    val lastUpdatedAt = escapeHtml(visit.lastUpdatedAt)

    return """
        <article>
            <h2>$title</h2>
            <p><strong>${messages.t("visit.label.date")}</strong> $date</p>
            <p><strong>${messages.t("visit.label.time")}</strong> $time ($timezone)</p>
            <p><strong>${messages.t("visit.label.direction")}</strong> $direction</p>
            <p><strong>${messages.t("visit.label.status")}</strong> $status</p>
            <p><strong>${messages.t("visit.label.availability")}</strong> $availability</p>
            <p><strong>${messages.t("visit.label.capacity")}</strong> $capacity</p>
            <p><strong>${messages.t("visit.label.signup")}</strong> $signupInstruction</p>
            <p><strong>${messages.t("visit.label.summary")}</strong> $publicSummary</p>
            <p><small>${messages.t("visit.label.lastUpdated")} $lastUpdatedAt</small></p>
        </article>
    """.trimIndent()
}
