package org.proanima.shelter.views

import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.h1
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.strong
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatAvailability
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrl

fun HTML.catsListPage(cats: List<Cat>, locale: AppLocale) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("cat.page.title")) {
        h1 { +messages.t("cat.page.title") }
        ul(classes = "cat-list") {
            cats.forEach { cat ->
                li {
                    a(href = "$prefix/cats/${cat.id}") { +displayCatName(cat.name, locale) }
                }
            }
        }
    }
}

fun HTML.catDetailsPage(cat: Cat, locale: AppLocale) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)
    val name = displayCatName(cat.name, locale)
    val age = displayCatAge(cat.age, locale)
    val availability = displayCatAvailability(cat.isAvailable, locale)
    val photoUrl = displayPhotoUrl(cat.photoUrl)
    val description = cat.description.forLocale(locale)

    pageLayout(locale, pageTitle = name) {
        p { a(href = "$prefix/cats") { +messages.t("cat.backToCats") } }

        h1 { +name }

        img(src = photoUrl, alt = name) {
            width = "300"
        }

        p { strong { +messages.t("cat.label.age") }; +" $age" }
        p { strong { +messages.t("cat.label.status") }; +" $availability" }
        p { strong { +messages.t("cat.label.description") }; +" $description" }
    }
}
