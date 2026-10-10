package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.strong
import kotlinx.html.ul
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Dog
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.petSlug

private const val DEFAULT_PHOTO = "/images/default-cat.jpg"

fun HTML.dogsPage(dogs: List<Dog>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(
        locale,
        pageTitle = messages.t("dogs.page.title"),
        currentPath = currentPath,
        mainClass = "page page-wide",
        description = messages.t("dogs.page.description")
    ) {
        h1 { +messages.t("nav.dogs") }
        p { +messages.t("dogs.intro") }
        p { +messages.t("dogs.shelter") }

        if (dogs.isNotEmpty()) {
            h2 { +messages.t("dogs.list.title") }
            p { +messages.t("dogs.list.intro") }
            dogList(dogs, locale)
        }

        h2 { +messages.t("dogs.svora.title") }
        p { +messages.t("dogs.svora.text") }
        ul {
            li { +messages.t("dogs.svora.item.trips") }
            li { +messages.t("dogs.svora.item.supplies") }
            li { +messages.t("dogs.svora.item.donations") }
            li { +messages.t("dogs.svora.item.events") }
        }

        h2 { +messages.t("dogs.join.title") }
        p { +messages.t("dogs.join.text") }
        div(classes = "contact-buttons") {
            a(href = "https://t.me/$DASHA_TELEGRAM", classes = "button") {
                +"${messages.t("home.contact.dasha")} (@$DASHA_TELEGRAM)"
            }
            a(href = SVORA_INSTAGRAM_URL, classes = "button button-secondary") { +messages.t("dogs.link.instagram") }
            a(href = SVORA_LINKTREE_URL, classes = "button button-secondary") { +messages.t("dogs.link.linktree") }
        }
        p(classes = "more") {
            +"${messages.t("dogs.guide.more")} "
            a(href = "/${locale.code}/guide#general") { +messages.t("dogs.guide.link") }
        }
    }
}

// Same card look as the cats: the whole card links to the dog's page.
private fun FlowContent.dogList(dogs: List<Dog>, locale: AppLocale) {
    div(classes = "cat-list") {
        dogs.forEach { dog ->
            val name = dog.name.forLocale(locale)
            a(href = "/${locale.code}/dogs/${petSlug(dog.name.en, dog.id)}", classes = "cat-card") {
                img(src = dog.photoUrls.firstOrNull() ?: DEFAULT_PHOTO, alt = name, classes = "cat-card-photo")
                div(classes = "cat-card-body") {
                    span(classes = "cat-card-name") { +name }
                    dog.age?.let { age -> span(classes = "tag") { +age.forLocale(locale) } }
                }
            }
        }
    }
}

fun HTML.dogDetailsPage(dog: Dog, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)
    val name = dog.name.forLocale(locale)

    pageLayout(locale, pageTitle = "$name — ${messages.t("nav.dogs")}", currentPath = currentPath) {
        p { a(href = "/${locale.code}/dogs") { +messages.t("dog.backToDogs") } }

        h1 { +name }

        photoGallery(dog.photoUrls.ifEmpty { listOf(DEFAULT_PHOTO) }, name, messages)

        dog.age?.let { age -> p { strong { +messages.t("cat.label.age") }; +" ${age.forLocale(locale)}" } }
        p { strong { +messages.t("cat.label.location") }; +" ${displayCatLocation(dog.location, locale)}" }
        p { strong { +messages.t("cat.label.description") }; +" ${dog.description.forLocale(locale)}" }
    }
}
