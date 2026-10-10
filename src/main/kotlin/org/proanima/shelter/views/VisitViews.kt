package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.h1
import kotlinx.html.p
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

// Placeholders: the visit list will appear together with the sync with the cat volunteers' chat.
fun HTML.visitsPage(locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.upcoming.title"), currentPath = currentPath) {
        visitStub(messages.t("visit.page.upcoming.title"), messages.t("visit.page.upcoming.stub"))
    }
}

fun HTML.visitArchivePage(locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("visit.page.archive.title"), currentPath = currentPath) {
        visitStub(messages.t("visit.page.archive.title"), messages.t("visit.page.archive.stub"))
    }
}

private fun FlowContent.visitStub(title: String, text: String) {
    h1 { +title }
    p { +text }
}
