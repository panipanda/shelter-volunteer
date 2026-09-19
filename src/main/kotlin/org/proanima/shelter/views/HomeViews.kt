package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.blockQuote
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.ol
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.VolunteerVisit
import java.util.ResourceBundle

private const val INSTAGRAM_URL = "https://www.instagram.com/proanima_beograd"
private const val NATA_TELEGRAM = "Nata_Rogava"
private const val ALINA_TELEGRAM = "Trikcsy"

fun HTML.homePage(cats: List<Cat>, nextVisit: VolunteerVisit?, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("home.title"), currentPath = currentPath) {
        heroSection(locale, messages)
        aboutSection(messages)
        volunteersSection(locale, messages)
        joinSection(locale, messages)
        nextVisitSection(nextVisit, locale, messages)
        catsSection(cats, locale, messages)
        helpSection(messages)
    }
}

private fun FlowContent.heroSection(locale: AppLocale, messages: ResourceBundle) {
    val prefix = "/${locale.code}"

    section(classes = "hero") {
        h1 { +messages.t("home.title") }
        p(classes = "lead") { +messages.t("home.hero.lead") }
        div(classes = "hero-actions") {
            a(href = "#join", classes = "button") { +messages.t("home.hero.cta.join") }
            a(href = "$prefix/cats", classes = "button button-secondary") { +messages.t("home.hero.cta.cats") }
        }
        img(src = "/images/home-hero.jpg", alt = messages.t("home.hero.photo.alt"), classes = "hero-photo")
    }
}

private fun FlowContent.aboutSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.about.title") }
        p { +messages.t("home.about.text") }
        blockQuote {
            p { +messages.t("home.about.quote") }
            p(classes = "quote-author") { +"— ${messages.t("home.about.quote.author")}" }
        }
        p {
            +"${messages.t("home.about.instagram")} "
            a(href = INSTAGRAM_URL) { +"@proanima_beograd" }
        }
    }
}

private fun FlowContent.volunteersSection(locale: AppLocale, messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.volunteers.title") }
        p { +messages.t("home.volunteers.intro") }
        ul {
            li { +messages.t("home.volunteers.item.cleaning") }
            li { +messages.t("home.volunteers.item.feeding") }
            li { +messages.t("home.volunteers.item.socialization") }
        }
        p {
            +"${messages.t("home.volunteers.more")} "
            a(href = "/${locale.code}/guide") { +messages.t("home.link.guide") }
        }
    }
}

private fun FlowContent.joinSection(locale: AppLocale, messages: ResourceBundle) {
    val prefix = "/${locale.code}"

    section(classes = "join") {
        id = "join"
        h2 { +messages.t("home.join.title") }
        div(classes = "join-layout") {
            div(classes = "join-text") {
                p { +messages.t("home.join.intro") }
                ol {
                    li {
                        +"${messages.t("home.join.step1")} "
                        a(href = "$prefix/guide") { +messages.t("home.join.step1.link") }
                    }
                    li {
                        +"${messages.t("home.join.step2")} "
                        coordinatorLinks(messages)
                    }
                    li { +messages.t("home.join.step3") }
                }
                p { +messages.t("home.join.schedule") }
                p { +messages.t("home.join.places") }
            }
            img(src = "/images/home-volunteers.jpg", alt = messages.t("home.join.photo.alt"), classes = "join-photo")
        }
    }
}

private fun FlowContent.nextVisitSection(visit: VolunteerVisit?, locale: AppLocale, messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.nextVisit.title") }
        if (visit == null) {
            p { +messages.t("home.nextVisit.empty") }
        } else {
            visitCard(visit, locale)
        }
        p { a(href = "/${locale.code}/visits") { +messages.t("home.link.visits") } }
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

private fun FlowContent.helpSection(messages: ResourceBundle) {
    section {
        h2 { +messages.t("home.help.title") }
        p { +messages.t("home.help.intro") }
        ul {
            li { +messages.t("home.help.item.donations") }
            li { +messages.t("home.help.item.food") }
            li { +messages.t("home.help.item.catThings") }
            li { +messages.t("home.help.item.supplies") }
            li { +messages.t("home.help.item.foster") }
        }
        p {
            +"${messages.t("home.help.contact")} "
            coordinatorLinks(messages)
        }
    }
}

private fun FlowContent.coordinatorLinks(messages: ResourceBundle) {
    a(href = "https://t.me/$NATA_TELEGRAM") { +"${messages.t("home.contact.nata")} (@$NATA_TELEGRAM)" }
    +" ${messages.t("home.contact.or")} "
    a(href = "https://t.me/$ALINA_TELEGRAM") { +"${messages.t("home.contact.alina")} (@$ALINA_TELEGRAM)" }
}
