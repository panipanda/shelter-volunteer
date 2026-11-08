package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul
import org.proanima.shelter.i18n.lines
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.plain
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.NextVisit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.ResourceBundle

// messages: the texts of the site with the edits made in the admin panel (the same for every page below).
fun HTML.homePage(
    cats: List<Cat>,
    nextVisit: NextVisit,
    locale: AppLocale,
    currentPath: String,
    messages: ResourceBundle = messagesFor(locale)
) {
    pageLayout(
        locale,
        pageTitle = messages.t("home.page.title"),
        currentPath = currentPath,
        description = messages.t("home.page.description"),
        messages = messages
    ) {
        heroSection(locale, messages)
        nextVisitSection(nextVisit, locale, messages)
        img(src = messages.plain(SiteSettings.HERO_PHOTO), alt = messages.t("home.hero.photo.alt"), classes = "hero-photo")
        // The visit button leads to the join steps, so they come right after the photo, before the cats.
        joinSection(locale, messages) { founderQuote(messages) }
        catsSection(cats, locale, messages)
        volunteersSection(messages)
        aboutSection(messages)
    }
}

// The primary button of the page is in the next-visit block right under the hero, so the hero has no primary button.
// The one secondary button (a whole sentence) is the way in for a reader who wants to help with money or things, not with visits.
private fun FlowContent.heroSection(locale: AppLocale, messages: ResourceBundle) {
    section(classes = "hero") {
        h1 { +messages.t("home.hero.headline") }
        p(classes = "lead") { +messages.t("home.hero.lead") }
        // The whole sentence is the button. It is outlined, so the filled "How to sign up" button stays the page's primary.
        p(classes = "hero-help") {
            a(href = "/${locale.code}/help", classes = "button button-secondary") { +messages.t("home.hero.help") }
        }
    }
}

internal fun FlowContent.nextVisitSection(visit: NextVisit, locale: AppLocale, messages: ResourceBundle) {
    // For sr we use Latin script, otherwise Java returns day and month names in Cyrillic.
    val javaLocale = Locale.forLanguageTag(if (locale == AppLocale.SR) "sr-Latn" else locale.code)
    val weekday = visit.date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, javaLocale)
        .replaceFirstChar { it.titlecase(javaLocale) }
    val month = visit.date.month.getDisplayName(TextStyle.SHORT_STANDALONE, javaLocale).trimEnd('.')
    val departure = visit.departure.format(DateTimeFormatter.ofPattern("H:mm"))

    section(classes = "next-visit") {
        div(classes = "visit-date") {
            strong { +visit.date.dayOfMonth.toString() }
            span { +month }
        }
        div(classes = "visit-body") {
            h2 { +messages.t("home.nextVisit.title") }
            p(classes = "visit-weekday") { +weekday }
            p { +messages.t("home.nextVisit.departure", departure) }
            p { +messages.t("home.nextVisit.chat") }
            p { a(href = "#join", classes = "button") { +messages.t("home.nextVisit.cta") } }
        }
    }
}

private fun FlowContent.catsSection(cats: List<Cat>, locale: AppLocale, messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.cats.title") }
        if (cats.isNotEmpty()) {
            catList(cats, locale)
        }
        p { a(href = "/${locale.code}/cats") { +messages.t("home.link.cats") } }
    }
}

// closing goes at the end of the section: the home page puts the founder's quote there, /visits has nothing.
internal fun FlowContent.joinSection(
    locale: AppLocale,
    messages: ResourceBundle,
    closing: FlowContent.() -> Unit = {}
) {
    val prefix = "/${locale.code}"

    // The steps are a real sequence, so it is a numbered list.
    section {
        id = "join"
        h2 { +messages.t("home.join.title") }
        p { +messages.t("home.join.intro") }
        ol(classes = "steps") {
            li { textWithLink(messages.t("home.join.step1"), "$prefix/guide", messages.t("home.join.step1.link")) }
            li {
                +"${messages.t("home.join.step2")} "
                contactLink(messages.plain(SiteSettings.NATA_TELEGRAM), messages.t("home.contact.nata"))
                +", "
                contactLink(messages.plain(SiteSettings.ALINA_TELEGRAM), messages.t("home.contact.alina"))
            }
            // The trips to the dogs are organised by Svora and have their own chat and moderator.
            li {
                +"${messages.t("home.join.dogs")} "
                contactLink(messages.plain(SiteSettings.DASHA_TELEGRAM_NAME), messages.t("home.contact.dasha"))
            }
            li { +messages.t("home.join.step3") }
        }
        closing()
    }
}

// What volunteers do with the cats and with the dogs. How to sign up for either is in the join section.
private fun FlowContent.volunteersSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.volunteers.title") }
        h3 { +messages.t("home.volunteers.cats.title") }
        ul {
            messages.lines(SiteSettings.VOLUNTEER_CAT_TASKS).forEach { li { +it } }
        }
        h3 { +messages.t("home.volunteers.dogs.title") }
        ul {
            messages.lines(SiteSettings.VOLUNTEER_DOG_TASKS).forEach { li { +it } }
        }
    }
}

// Other ways to help (donations, food, fostering) are for a different reader than a volunteer, so they
// have their own page, which the menu and the footer link to.
private fun FlowContent.aboutSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.about.title") }
        p { +messages.t("home.about.text") }
    }
}

private const val LINK_PLACEHOLDER = "{link}"

// A sentence with a link in the middle: the message holds {link} where the link goes, so the word order
// stays the translator's business and the link text is its own message. The admin panel refuses a text without
// {link}, but a page must not fail because of one, so the sentence is then followed by the link.
private fun FlowContent.textWithLink(pattern: String, href: String, linkText: String) {
    val parts = pattern.split(LINK_PLACEHOLDER, limit = 2)
    +parts.first()
    a(href = href) { +linkText }
    if (parts.size > 1) {
        +parts[1]
    }
}

private fun FlowContent.founderQuote(messages: ResourceBundle) {
    blockQuote {
        p { +messages.t("home.about.quote") }
        p(classes = "quote-author") { +"— ${messages.t("home.about.quote.author")}" }
    }
}

// A contact is part of the sentence that asks to write to it, so it follows the colon in the same line and wraps
// with the text; nowrap only keeps a name together with its handle.
private fun FlowContent.contactLink(handle: String, name: String) {
    a(href = "https://t.me/$handle", classes = "nowrap") { +"$name (@$handle)" }
}
