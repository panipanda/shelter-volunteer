package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import org.proanima.shelter.i18n.lines
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.plain
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import java.util.ResourceBundle

// Ways to help that are not visits. They are for a different reader than the volunteer sign-up, so they
// are not on the home page; the home page and the footer link here.
fun HTML.helpPage(locale: AppLocale, currentPath: String, messages: ResourceBundle = messagesFor(locale)) {
    pageLayout(
        locale,
        pageTitle = messages.t("help.title"),
        currentPath = currentPath,
        description = messages.t("help.page.description"),
        messages = messages
    ) {
        h1 { +messages.t("help.title") }
        p { +messages.t("help.intro") }
        // The donation link is the one thing this page asks for, so it is the page's primary button, above the list.
        p { a(href = messages.plain(SiteSettings.DONATE_URL), classes = "button") { +messages.t("help.link.donate") } }
        p { +messages.t("help.needs") }
        ul(classes = "help-list") {
            messages.lines(SiteSettings.HELP_NEEDS).forEach { li { +it } }
        }
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
                messages.lines(SiteSettings.SVORA_ACTIVITIES).forEach { li { +it } }
            }
        }
    }
}
