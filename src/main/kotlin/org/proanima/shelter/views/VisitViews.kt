package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.p
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.NextVisit

// There is no list of visits yet, so the page gives what the list would: the next trip and how to join it.
// Both blocks are the ones the home page shows, so the answer is the same wherever the volunteer looks.
fun HTML.visitsPage(nextVisit: NextVisit, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.upcoming.title"), currentPath = currentPath) {
        h1 { +messages.t("visit.page.upcoming.title") }
        nextVisitSection(nextVisit, locale, messages)
        joinSection(locale, messages)
    }
}

fun HTML.visitArchivePage(locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.archive.title"), currentPath = currentPath) {
        visitStub(messages.t("visit.page.archive.title"), messages.t("visit.page.archive.stub"))
        p { a(href = "/${locale.code}/visits") { +messages.t("visit.page.upcoming.title") } }
    }
}

private fun FlowContent.visitStub(title: String, text: String) {
    h1 { +title }
    p { +text }
}
