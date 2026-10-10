package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

// A text-only page for now: there is no dog catalogue yet, the dog volunteering is run by Svora.Volunteers.
fun HTML.dogsPage(locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(
        locale,
        pageTitle = messages.t("dogs.page.title"),
        currentPath = currentPath,
        description = messages.t("dogs.page.description")
    ) {
        h1 { +messages.t("nav.dogs") }
        p { +messages.t("dogs.intro") }
        p { +messages.t("dogs.shelter") }

        h2 { +messages.t("dogs.svora.title") }
        p { +messages.t("dogs.svora.text") }
        ul {
            li { +messages.t("dogs.svora.item.trips") }
            li { +messages.t("dogs.svora.item.supplies") }
            li { +messages.t("dogs.svora.item.donations") }
            li { +messages.t("dogs.svora.item.events") }
        }

        h2 { +messages.t("dogs.join.title") }
        p { +messages.t("dogs.join.text") }
        div(classes = "contact-buttons") {
            a(href = SVORA_INSTAGRAM_URL, classes = "button") { +messages.t("dogs.link.instagram") }
            a(href = SVORA_LINKTREE_URL, classes = "button button-secondary") { +messages.t("dogs.link.linktree") }
        }
        p(classes = "more") {
            +"${messages.t("dogs.guide.more")} "
            a(href = "/${locale.code}/guide#general") { +messages.t("dogs.guide.link") }
        }
    }
}
