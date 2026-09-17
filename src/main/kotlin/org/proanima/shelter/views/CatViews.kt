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
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayAdoptionStage
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrl

fun HTML.catsListPage(cats: List<Cat>, locale: AppLocale, currentPath: String) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("cat.page.title"), currentPath = currentPath) {
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

fun HTML.catDetailsPage(cat: Cat, locale: AppLocale, currentPath: String) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)
    val name = displayCatName(cat.name, locale)
    val age = displayCatAge(cat.birthYear, cat.birthMonth, locale)
    val location = displayCatLocation(cat.location, locale)
    val adoptionStage = displayAdoptionStage(cat.adoptionStage, locale)
    val photoUrl = displayPhotoUrl(cat.photoUrl)
    val description = cat.description.forLocale(locale)

    pageLayout(locale, pageTitle = name, currentPath = currentPath) {
        p { a(href = "$prefix/cats") { +messages.t("cat.backToCats") } }

        h1 { +name }

        img(src = photoUrl, alt = name) {
            width = "300"
        }

        p { strong { +messages.t("cat.label.age") }; +" $age" }
        p { strong { +messages.t("cat.label.location") }; +" $location" }
        p { strong { +messages.t("cat.label.status") }; +" $adoptionStage" }
        p { strong { +messages.t("cat.label.description") }; +" $description" }

        cat.medicalStatus?.let { medicalStatus ->
            p { strong { +messages.t("cat.label.medicalStatus") }; +" ${medicalStatus.forLocale(locale)}" }
        }

        if (cat.adoptionStage == AdoptionStage.FOR_ADOPTION) {
            val instruction = cat.adoptionInstruction?.forLocale(locale)
                ?: messages.t("cat.adoptionInstruction.unknown")
            p { strong { +messages.t("cat.label.howToAdopt") }; +" $instruction" }
        }
    }
}
