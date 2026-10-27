package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale

// Ways to help that are not visits. They are for a different reader than the volunteer sign-up, so they
// are not on the home page; the home page and the footer link here.
fun HTML.helpPage(locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(
        locale,
        pageTitle = messages.t("help.title"),
        currentPath = currentPath,
        description = messages.t("help.page.description")
    ) {
        h1 { +messages.t("help.title") }
        p { +messages.t("help.intro") }
        ul(classes = "help-list") {
            li { +messages.t("help.item.donations") }
            li { +messages.t("help.item.food") }
            li { +messages.t("help.item.catThings") }
            li { +messages.t("help.item.supplies") }
            li { +messages.t("help.item.foster") }
        }
        p { a(href = SVORA_LINKTREE_URL) { +messages.t("help.link.donate") } }
        // The coordinators' contacts live in the "join" section of the home page; here they are only a link to it.
        p {
            +"${messages.t("help.contact")} "
            a(href = "/${locale.code}#join") { +messages.t("help.contact.link") }
            +"."
        }

        // The donation link above is Svora's, so the page says who they are.
        section {
            h2 { +messages.t("help.svora.title") }
            p { +messages.t("help.svora.text") }
            ul {
                li { +messages.t("help.svora.item.trips") }
                li { +messages.t("help.svora.item.supplies") }
                li { +messages.t("help.svora.item.donations") }
                li { +messages.t("help.svora.item.events") }
            }
        }
    }
}
