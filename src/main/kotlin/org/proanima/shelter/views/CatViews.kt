package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import kotlinx.html.strong
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.catSlug
import org.proanima.shelter.service.displayAdoptionStage
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrls

fun HTML.catsListPage(cats: List<Cat>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("cat.page.title"), currentPath = currentPath) {
        h1 { +messages.t("cat.page.title") }
        catList(cats, locale)
    }
}

internal fun FlowContent.catList(cats: List<Cat>, locale: AppLocale) {
    val prefix = "/${locale.code}"

    div(classes = "cat-list") {
        cats.forEach { cat ->
            val name = displayCatName(cat.name, locale)
            a(href = "$prefix/cats/${catSlug(cat.name?.en, cat.id)}", classes = "cat-card") {
                img(src = displayPhotoUrls(cat.photoUrls).first(), alt = name, classes = "cat-card-photo")
                span { +name }
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
    val photoUrls = displayPhotoUrls(cat.photoUrls)
    val description = cat.description.forLocale(locale)

    pageLayout(locale, pageTitle = name, currentPath = currentPath) {
        p { a(href = "$prefix/cats") { +messages.t("cat.backToCats") } }

        h1 { +name }

        // CSS lightbox: clicking a thumbnail navigates to #photo-N, :target shows the
        // matching div over the page; href="#" closes it by resetting the target. Works
        // without JS; cat-gallery.js layers keyboard arrow support on top where it helps.
        div(classes = "cat-gallery") {
            photoUrls.forEachIndexed { index, url ->
                a(href = "#photo-$index") {
                    img(src = url, alt = name, classes = "cat-gallery-thumb")
                }
            }
            photoUrls.forEachIndexed { index, url ->
                div(classes = "cat-gallery-lightbox") {
                    id = "photo-$index"
                    a(href = "#", classes = "cat-gallery-lightbox-close")
                    img(src = url, alt = name)
                    if (photoUrls.size > 1) {
                        val prevIndex = (index - 1 + photoUrls.size) % photoUrls.size
                        val nextIndex = (index + 1) % photoUrls.size
                        a(href = "#photo-$prevIndex", classes = "cat-gallery-lightbox-nav cat-gallery-lightbox-prev") {
                            attributes["aria-label"] = messages.t("cat.gallery.prevPhoto")
                            +"‹"
                        }
                        a(href = "#photo-$nextIndex", classes = "cat-gallery-lightbox-nav cat-gallery-lightbox-next") {
                            attributes["aria-label"] = messages.t("cat.gallery.nextPhoto")
                            +"›"
                        }
                    }
                }
            }
        }
        if (photoUrls.size > 1) {
            script(src = "/scripts/cat-gallery.js") {
                attributes["defer"] = "defer"
            }
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
