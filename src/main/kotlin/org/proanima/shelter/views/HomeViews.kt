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
import kotlinx.html.unsafe
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.service.displayVisitAvailability
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import java.util.ResourceBundle

private const val INSTAGRAM_URL = "https://www.instagram.com/proanima_beograd"
private const val NATA_TELEGRAM = "Nata_Rogava"
private const val ALINA_TELEGRAM = "Trikcsy"

// Иконки плиток «Чем занимаются волонтёры»: только контур, цвет задаёт CSS (.task-icon svg).
private const val ICON_CLEANING = """<path d="M14 3l7 7"/><path d="M12.5 8.5L4 17l3 3 8.5-8.5"/><path d="M6 15l3 3"/>"""
private const val ICON_FEEDING = """<path d="M3 11h18a8 8 0 0 1-8 8h-2a8 8 0 0 1-8-8z"/><path d="M8 7c0-1.5 1.5-1.5 1.5-3M13 7c0-1.5 1.5-1.5 1.5-3"/>"""
private const val ICON_SOCIALIZATION = """<path d="M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"/>"""

fun HTML.homePage(cats: List<Cat>, nextVisit: VolunteerVisit?, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("home.title"), currentPath = currentPath, mainClass = "home") {
        heroSection(locale, messages)
        nextVisitSection(nextVisit, locale, messages)
        volunteersSection(locale, messages)
        joinSection(locale, messages)
        catsSection(cats, locale, messages)
        helpSection(messages)
        aboutSection(messages)
    }
}

private fun FlowContent.heroSection(locale: AppLocale, messages: ResourceBundle) {
    val prefix = "/${locale.code}"

    section(classes = "hero") {
        div(classes = "wrap hero-grid") {
            div(classes = "hero-text") {
                p(classes = "eyebrow") { +messages.t("home.title") }
                h1 { +messages.t("home.hero.headline") }
                p(classes = "lead") { +messages.t("home.hero.lead") }
                div(classes = "hero-actions") {
                    a(href = "#join", classes = "button") { +messages.t("home.hero.cta.join") }
                    a(href = "$prefix/cats", classes = "button button-secondary") { +messages.t("home.hero.cta.cats") }
                }
                ul(classes = "facts") {
                    fact("2010", messages.t("home.hero.fact.founded"))
                    fact("40+", messages.t("home.hero.fact.cats"))
                    fact("2", messages.t("home.hero.fact.visits"))
                }
            }
            img(src = "/images/home-hero.jpg", alt = messages.t("home.hero.photo.alt"), classes = "hero-photo")
        }
    }
}

private fun UL.fact(value: String, label: String) {
    li {
        strong { +value }
        span { +label }
    }
}

private fun FlowContent.nextVisitSection(visit: VolunteerVisit?, locale: AppLocale, messages: ResourceBundle) {
    val visitsHref = "/${locale.code}/visits"

    section(classes = "next-visit") {
        div(classes = "wrap") {
            div(classes = "visit-strip") {
                if (visit == null) {
                    div(classes = "visit-body") {
                        h2 { +messages.t("home.nextVisit.title") }
                        p { +messages.t("home.nextVisit.empty") }
                    }
                    a(href = visitsHref, classes = "button button-secondary") { +messages.t("home.link.visits") }
                } else {
                    visitDateBadge(visit, locale)
                    div(classes = "visit-body") {
                        span(classes = "pill") {
                            +displayVisitAvailability(visit.status, visit.freePlaces, locale)
                        }
                        h2 { +visit.title.forLocale(locale) }
                        p { +"${messages.t("visit.label.time")} ${visit.time}" }
                    }
                    div(classes = "visit-actions") {
                        a(href = "#join", classes = "button") { +messages.t("home.nextVisit.cta") }
                        a(href = visitsHref) { +messages.t("home.link.visits") }
                    }
                }
            }
        }
    }
}

// Дату разбираем мягко: если в данных формат неожиданный, лучше показать карточку без
// плашки с числом, чем уронить всю главную.
private fun FlowContent.visitDateBadge(visit: VolunteerVisit, locale: AppLocale) {
    val date = runCatching { LocalDate.parse(visit.date) }.getOrNull() ?: return
    // Для sr берём латиницу, иначе Java отдаст названия месяцев кириллицей.
    val javaLocale = Locale.forLanguageTag(if (locale == AppLocale.SR) "sr-Latn" else locale.code)
    val month = date.month.getDisplayName(TextStyle.SHORT_STANDALONE, javaLocale).trimEnd('.')

    div(classes = "visit-date") {
        strong { +date.dayOfMonth.toString() }
        span { +month }
    }
}

private fun FlowContent.volunteersSection(locale: AppLocale, messages: ResourceBundle) {
    section {
        div(classes = "wrap") {
            div(classes = "head") {
                h2 { +messages.t("home.volunteers.title") }
                p { +messages.t("home.volunteers.intro") }
            }
            div(classes = "tasks") {
                task(ICON_CLEANING, messages.t("home.volunteers.cleaning.title"), messages.t("home.volunteers.cleaning.text"))
                task(ICON_FEEDING, messages.t("home.volunteers.feeding.title"), messages.t("home.volunteers.feeding.text"))
                task(
                    ICON_SOCIALIZATION,
                    messages.t("home.volunteers.socialization.title"),
                    messages.t("home.volunteers.socialization.text")
                )
            }
            p(classes = "more") {
                +"${messages.t("home.volunteers.more")} "
                a(href = "/${locale.code}/guide") { +messages.t("home.link.guide") }
            }
        }
    }
}

private fun FlowContent.task(iconPaths: String, title: String, text: String) {
    div(classes = "task") {
        div(classes = "task-icon") {
            unsafe { +"""<svg viewBox="0 0 24 24" aria-hidden="true">$iconPaths</svg>""" }
        }
        h3 { +title }
        p { +text }
    }
}

private fun FlowContent.joinSection(locale: AppLocale, messages: ResourceBundle) {
    val prefix = "/${locale.code}"

    // Шаги — настоящая последовательность, поэтому нумерация честная (счётчик в CSS).
    div(classes = "band") {
        id = "join"
        div(classes = "wrap") {
            div(classes = "band-top") {
                div(classes = "head") {
                    h2 { +messages.t("home.join.title") }
                    p { +messages.t("home.join.intro") }
                }
                img(src = "/images/home-volunteers.jpg", alt = messages.t("home.join.photo.alt"), classes = "band-photo")
            }
            ol(classes = "steps") {
                li {
                    p { +messages.t("home.join.step1") }
                    a(href = "$prefix/guide", classes = "inline-link") { +messages.t("home.join.step1.link") }
                }
                li {
                    p { +messages.t("home.join.step2") }
                    div(classes = "step-actions") {
                        coordinatorButtons(messages, "button button-light", "button button-light")
                    }
                }
                li { p { +messages.t("home.join.step3") } }
            }
            div(classes = "schedule") {
                p { +messages.t("home.join.schedule") }
                p { +messages.t("home.join.places") }
            }
        }
    }
}

private fun FlowContent.catsSection(cats: List<Cat>, locale: AppLocale, messages: ResourceBundle) {
    section {
        div(classes = "wrap") {
            div(classes = "head") {
                h2 { +messages.t("home.cats.title") }
                if (cats.isNotEmpty()) {
                    p { +messages.t("home.cats.intro") }
                }
            }
            if (cats.isNotEmpty()) {
                catList(cats, locale)
            }
            p(classes = "section-foot") {
                a(href = "/${locale.code}/cats", classes = "button button-secondary") { +messages.t("home.link.cats") }
            }
        }
    }
}

private fun FlowContent.helpSection(messages: ResourceBundle) {
    section {
        div(classes = "wrap help-layout") {
            div(classes = "help-intro") {
                h2 { +messages.t("home.help.title") }
                p { +messages.t("home.help.intro") }
                p { +messages.t("home.help.contact") }
                div(classes = "contact-buttons") {
                    coordinatorButtons(messages, "button", "button button-secondary")
                }
            }
            ul(classes = "help-list") {
                li { +messages.t("home.help.item.donations") }
                li { +messages.t("home.help.item.food") }
                li { +messages.t("home.help.item.catThings") }
                li { +messages.t("home.help.item.supplies") }
                li { +messages.t("home.help.item.foster") }
            }
        }
    }
}

private fun FlowContent.aboutSection(messages: ResourceBundle) {
    section {
        div(classes = "wrap about-layout") {
            div(classes = "about-text") {
                h2 { +messages.t("home.about.title") }
                p { +messages.t("home.about.text") }
                p {
                    +"${messages.t("home.about.instagram")} "
                    a(href = INSTAGRAM_URL) { +"@proanima_beograd" }
                }
            }
            blockQuote {
                p { +messages.t("home.about.quote") }
                p(classes = "quote-author") { +"— ${messages.t("home.about.quote.author")}" }
            }
        }
    }
}

private fun FlowContent.coordinatorButtons(messages: ResourceBundle, natalyaClasses: String, alinaClasses: String) {
    a(href = "https://t.me/$NATA_TELEGRAM", classes = natalyaClasses) {
        +"${messages.t("home.contact.nata")} (@$NATA_TELEGRAM)"
    }
    a(href = "https://t.me/$ALINA_TELEGRAM", classes = alinaClasses) {
        +"${messages.t("home.contact.alina")} (@$ALINA_TELEGRAM)"
    }
}
