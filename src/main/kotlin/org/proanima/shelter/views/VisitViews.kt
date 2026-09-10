package org.proanima.shelter.views

import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.service.displayVisitAvailability

fun renderVisitsPage(visits: List<VolunteerVisit>): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Volunteer visits</title>
        </head>
        <body>
            ${renderNavigation()}
            <h1>Volunteer visits</h1>
            <p>Upcoming shelter visits for volunteers.</p>

            ${renderVisitList(visits, emptyMessage = "No upcoming visits are available right now.")}
        </body>
        </html>
    """.trimIndent()
}

fun renderVisitArchivePage(visits: List<VolunteerVisit>): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Visit archive</title>
        </head>
        <body>
            ${renderNavigation()}
            <h1>Visit archive</h1>
            <p>Past shelter visits and public summaries.</p>

            ${renderVisitList(visits, emptyMessage = "No completed visits yet.")}
        </body>
        </html>
    """.trimIndent()
}

private fun renderVisitList(
    visits: List<VolunteerVisit>,
    emptyMessage: String
): String {
    if (visits.isEmpty()) {
        return "<p>$emptyMessage</p>"
    }

    return visits.joinToString(separator = "\n") { visit ->
        renderVisitCard(visit)
    }
}

private fun renderVisitCard(visit: VolunteerVisit): String {
    val title = escapeHtml(visit.title)
    val date = escapeHtml(visit.date)
    val time = escapeHtml(visit.time)
    val timezone = escapeHtml(visit.timezone)
    val availability = displayVisitAvailability(visit.status, visit.freePlaces)
    val capacity = visit.capacity?.toString() ?: "Capacity is being updated"
    val signupInstruction = escapeHtml(visit.signupInstruction ?: "Signup information is being updated.")
    val publicSummary = escapeHtml(visit.publicSummary ?: "No public summary yet.")
    val lastUpdatedAt = escapeHtml(visit.lastUpdatedAt)

    return """
        <article>
            <h2>$title</h2>
            <p><strong>Date:</strong> $date</p>
            <p><strong>Time:</strong> $time ($timezone)</p>
            <p><strong>Direction:</strong> ${visit.direction}</p>
            <p><strong>Status:</strong> ${visit.status}</p>
            <p><strong>Availability:</strong> $availability</p>
            <p><strong>Capacity:</strong> $capacity</p>
            <p><strong>Signup:</strong> $signupInstruction</p>
            <p><strong>Summary:</strong> $publicSummary</p>
            <p><small>Last updated: $lastUpdatedAt</small></p>
        </article>
    """.trimIndent()
}
