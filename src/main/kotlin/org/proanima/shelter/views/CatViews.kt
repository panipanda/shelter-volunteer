package org.proanima.shelter.views

import kotlinx.html.DL
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.petSlug
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.service.cardPhotoUrl
import org.proanima.shelter.service.displayAdoptionStage
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrls
import java.util.ResourceBundle

fun HTML.catsListPage(cats: List<Cat>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(locale, pageTitle = messages.t("cat.page.title"), currentPath = currentPath) {
        h1 { +messages.t("cat.page.title") }

        val (adopted, others) = cats.partition { it.adoptionStage == AdoptionStage.ADOPTED }
        catList(others, locale)

        if (adopted.isNotEmpty()) {
            h2 { +messages.t("cat.adopted.title") }
            catList(adopted, locale)
        }
    }
}

// Card: photo, name, one meta line. The status line is only for a stage that differs from the group the
// cat is listed in; of the stages shown in the lists (not adopted) that is only RESERVED.
internal fun FlowContent.catList(cats: List<Cat>, locale: AppLocale) {
    val prefix = "/${locale.code}"

    div(classes = "cat-list") {
        cats.forEachIndexed { index, cat ->
            val name = displayCatName(cat.name, locale)
            a(href = "$prefix/cats/${petSlug(cat.name?.en, cat.id)}", classes = "cat-card") {
                cardPhoto(displayPhotoUrls(cat.photoUrls).first(), name, index)
                span(classes = "cat-card-name") { +name }
                val meta = listOfNotNull(
                    cat.birthYear?.let { displayCatAge(it, cat.birthMonth, locale) },
                    displayCatLocation(cat.location, locale).takeIf { cat.location != CatLocation.IN_SHELTER }
                )
                if (meta.isNotEmpty()) {
                    span(classes = "cat-card-meta") { +meta.joinToString(" · ") }
                }
                if (cat.adoptionStage == AdoptionStage.RESERVED) {
                    adoptionStatus(cat.adoptionStage, locale, "cat-card-meta")
                }
            }
        }
    }
}

// The first row of a list is on the first screen and loads at once; the rest waits until it is near the viewport.
private const val CARDS_IN_FIRST_ROW = 3

internal fun FlowContent.cardPhoto(photoUrl: String, alt: String, position: Int) {
    img(src = cardPhotoUrl(photoUrl), alt = alt, classes = "cat-card-photo") {
        attributes["decoding"] = "async"
        if (position >= CARDS_IN_FIRST_ROW) {
            attributes["loading"] = "lazy"
        }
    }
}

// The marker shape (CSS, ::before) carries the meaning, the text always comes with it.
internal fun FlowContent.adoptionStatus(stage: AdoptionStage, locale: AppLocale, extraClass: String = "") {
    if (stage == AdoptionStage.NONE) {
        return
    }
    span(classes = "status status-${stage.name.lowercase().replace('_', '-')} $extraClass".trim()) {
        +displayAdoptionStage(stage, locale)
    }
}

private val TELEGRAM_HANDLE = Regex("@([A-Za-z0-9_]{5,32})")

// The adoption instruction is free text that names the coordinator ("... @Nata_Rogava"); the link
// for the button is taken from it so the contact lives in one place, the data.
private fun telegramHandle(instruction: String?): String? =
    instruction?.let { TELEGRAM_HANDLE.find(it)?.groupValues?.get(1) }

fun HTML.catDetailsPage(cat: Cat, locale: AppLocale, currentPath: String) {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)
    val name = displayCatName(cat.name, locale)
    val age = displayCatAge(cat.birthYear, cat.birthMonth, locale)
    val location = displayCatLocation(cat.location, locale)
    val photoUrls = displayPhotoUrls(cat.photoUrls)
    val description = cat.description.forLocale(locale)
    val forAdoption = cat.adoptionStage == AdoptionStage.FOR_ADOPTION
    val instruction = cat.adoptionInstruction?.forLocale(locale)
        ?: messages.t("cat.adoptionInstruction.unknown")
    val coordinator = telegramHandle(instruction).takeIf { forAdoption }

    pageLayout(
        locale,
        pageTitle = name,
        currentPath = currentPath,
        mainClass = "page cat-page",
        description = description,
        ogImage = photoUrls.first()
    ) {
        p(classes = "back") { a(href = "$prefix/cats") { +messages.t("cat.backToCats") } }

        div(classes = "cat-head") {
            h1 { +name }
            if (coordinator != null) {
                a(href = "https://t.me/$coordinator", classes = "button") { +messages.t("cat.contact.telegram") }
            }
        }
        if (cat.adoptionStage != AdoptionStage.NONE) {
            p(classes = "status-line") { adoptionStatus(cat.adoptionStage, locale) }
        }

        div(classes = "cat-profile") {
            photoGallery(photoUrls, name, messages)

            div(classes = "cat-info") {
                p { +description }
                dl(classes = "facts-list") {
                    fact(messages.t("cat.label.age"), age)
                    fact(messages.t("cat.label.location"), location)
                    cat.medicalStatus?.let { fact(messages.t("cat.label.medicalStatus"), it.forLocale(locale)) }
                }
                // Without a Telegram contact in the instruction there is no button, so the text itself is shown.
                if (forAdoption && coordinator == null) {
                    aside(classes = "callout") {
                        p { +instruction }
                    }
                }
            }
        }
    }
}

// Labels in the messages end with a colon, which a definition list does not need.
internal fun DL.fact(label: String, value: String) {
    dt { +label.removeSuffix(":") }
    dd { +value }
}

internal fun FlowContent.photoGallery(photoUrls: List<String>, name: String, messages: ResourceBundle) {
    // CSS lightbox: clicking a photo navigates to #photo-N, :target shows the
    // matching div over the page; href="#" closes it by resetting the target. Works
    // without JS; cat-gallery.js layers Esc, the arrow keys and focus handling on top.
    div(classes = "cat-gallery") {
        a(href = "#photo-0", classes = "cat-gallery-main") {
            img(src = photoUrls.first(), alt = name, classes = "cat-gallery-photo")
        }
        if (photoUrls.size > 1) {
            div(classes = "cat-gallery-thumbs") {
                photoUrls.forEachIndexed { index, url ->
                    a(href = "#photo-$index") {
                        img(src = cardPhotoUrl(url), alt = name, classes = "cat-gallery-thumb") {
                            attributes["loading"] = "lazy"
                            attributes["decoding"] = "async"
                        }
                    }
                }
            }
        }
        photoUrls.forEachIndexed { index, url ->
            div(classes = "cat-gallery-lightbox") {
                id = "photo-$index"
                attributes["role"] = "dialog"
                attributes["aria-modal"] = "true"
                attributes["aria-label"] = name
                attributes["tabindex"] = "-1"
                a(href = "#", classes = "cat-gallery-lightbox-close") {
                    attributes["aria-label"] = messages.t("cat.gallery.close")
                }
                // Lazy: the lightbox is hidden until opened, so its full-size photo is not fetched with the page.
                img(src = url, alt = name) { attributes["loading"] = "lazy" }
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
    script(src = "/scripts/cat-gallery.js") {
        attributes["defer"] = "defer"
    }
}
