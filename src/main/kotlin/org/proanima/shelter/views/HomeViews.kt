package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.UL
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
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.NextVisit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.ResourceBundle

private const val INSTAGRAM_URL = "https://www.instagram.com/proanima_beograd"
private const val NATA_TELEGRAM = "Nata_Rogava"
private const val ALINA_TELEGRAM = "Trikcsy"

fun HTML.homePage(cats: List<Cat>, nextVisit: NextVisit, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(
        locale,
        pageTitle = messages.t("home.page.title"),
        currentPath = currentPath,
        description = messages.t("home.page.description")
    ) {
        heroSection(locale, messages)
        nextVisitSection(nextVisit, locale, messages)
        img(src = "/images/home-hero.jpg", alt = messages.t("home.hero.photo.alt"), classes = "hero-photo")
        catsSection(cats, locale, messages)
        joinSection(locale, messages)
        volunteersSection(locale, messages)
        dogsSection(locale, messages)
        helpSection(messages)
        aboutSection(messages)
    }
}

// The primary button of the page is in the next-visit block, so the hero only points to the two places.
private fun FlowContent.heroSection(locale: AppLocale, messages: ResourceBundle) {
    section(classes = "hero") {
        h1 { +messages.t("home.hero.headline") }
        p(classes = "lead") { +messages.t("home.hero.lead") }
        p(classes = "links") {
            a(href = "#join") { +messages.t("home.hero.cta.join") }
            a(href = "/${locale.code}/cats") { +messages.t("home.hero.cta.cats") }
        }
        ul(classes = "facts") {
            fact("2010", messages.t("home.hero.fact.founded"))
            fact("40+", messages.t("home.hero.fact.cats"))
            fact("2", messages.t("home.hero.fact.visits"))
        }
    }
}

private fun UL.fact(value: String, label: String) {
    li {
        strong { +value }
        span { +label }
    }
}

private fun FlowContent.nextVisitSection(visit: NextVisit, locale: AppLocale, messages: ResourceBundle) {
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
            p { +messages.t("home.cats.intro") }
            catList(cats, locale)
        }
        p { a(href = "/${locale.code}/cats") { +messages.t("home.link.cats") } }
    }
}

private fun FlowContent.joinSection(locale: AppLocale, messages: ResourceBundle) {
    val prefix = "/${locale.code}"

    // The steps are a real sequence, so it is a numbered list.
    section {
        id = "join"
        h2 { +messages.t("home.join.title") }
        p { +messages.t("home.join.intro") }
        ol(classes = "steps") {
            li {
                +messages.t("home.join.step1")
                p(classes = "links") { a(href = "$prefix/guide") { +messages.t("home.join.step1.link") } }
            }
            li {
                +messages.t("home.join.step2")
                coordinatorLinks(messages)
            }
            li { +messages.t("home.join.step3") }
        }
        p { +messages.t("home.join.schedule") }
        p { +messages.t("home.join.places") }
    }
}

private fun FlowContent.volunteersSection(locale: AppLocale, messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.volunteers.title") }
        p { +messages.t("home.volunteers.intro") }
        div(classes = "tasks") {
            task(messages.t("home.volunteers.cleaning.title"), messages.t("home.volunteers.cleaning.text"))
            task(messages.t("home.volunteers.feeding.title"), messages.t("home.volunteers.feeding.text"))
            task(messages.t("home.volunteers.socialization.title"), messages.t("home.volunteers.socialization.text"))
        }
        p {
            +"${messages.t("home.volunteers.more")} "
            a(href = "/${locale.code}/guide") { +messages.t("home.link.guide") }
            +" · "
            a(href = "/${locale.code}/dogs") { +messages.t("home.volunteers.dogs") }
        }
    }
}

private fun FlowContent.task(title: String, text: String) {
    div(classes = "task") {
        h3 { +title }
        p { +text }
    }
}

private fun FlowContent.dogsSection(locale: AppLocale, messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.dogs.title") }
        p { +messages.t("home.dogs.text") }
        p(classes = "links") {
            a(href = "/${locale.code}/dogs") { +messages.t("home.dogs.cta.page") }
            a(href = SVORA_INSTAGRAM_URL) { +messages.t("dogs.link.instagram") }
        }
    }
}

private fun FlowContent.helpSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.help.title") }
        p { +messages.t("home.help.intro") }
        ul(classes = "help-list") {
            li { +messages.t("home.help.item.donations") }
            li { +messages.t("home.help.item.food") }
            li { +messages.t("home.help.item.catThings") }
            li { +messages.t("home.help.item.supplies") }
            li { +messages.t("home.help.item.foster") }
        }
        p { +messages.t("home.help.contact") }
        coordinatorLinks(messages)
    }
}

private fun FlowContent.aboutSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.about.title") }
        p { +messages.t("home.about.text") }
        p {
            +"${messages.t("home.about.instagram")} "
            a(href = INSTAGRAM_URL) { +"@proanima_beograd" }
        }
        p {
            +"${messages.t("home.about.telegram")} "
            a(href = TELEGRAM_CHANNEL_URL) { +"@proanima_belgrade" }
        }
        p {
            +"${messages.t("home.about.svora")} "
            a(href = SVORA_INSTAGRAM_URL) { +"@svora.volunteers" }
        }
        blockQuote {
            p { +messages.t("home.about.quote") }
            p(classes = "quote-author") { +"— ${messages.t("home.about.quote.author")}" }
        }
    }
}

private fun FlowContent.coordinatorLinks(messages: ResourceBundle) {
    p(classes = "links") {
        a(href = "https://t.me/$NATA_TELEGRAM") { +"${messages.t("home.contact.nata")} (@$NATA_TELEGRAM)" }
        a(href = "https://t.me/$ALINA_TELEGRAM") { +"${messages.t("home.contact.alina")} (@$ALINA_TELEGRAM)" }
    }
}
