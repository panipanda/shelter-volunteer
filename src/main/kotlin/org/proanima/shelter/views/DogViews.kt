package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.dl
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.p
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.petSlug

private const val DEFAULT_PHOTO = "/images/default-dog.svg"

fun HTML.dogsPage(dogs: List<Dog>, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)

    pageLayout(
        locale,
        pageTitle = messages.t("dogs.page.title"),
        currentPath = currentPath,
        description = messages.t("dogs.page.description")
    ) {
        // Just like the list of cats: the heading and the cards. How to sign up for the dog trips is on the
        // home page, who Svora is is on the help page.
        h1 { +messages.t("nav.dogs") }
        dogList(dogs, locale)
    }
}

// Same card look as the cats: the whole card links to the dog's page.
private fun FlowContent.dogList(dogs: List<Dog>, locale: AppLocale) {
    div(classes = "cat-list") {
        dogs.forEachIndexed { index, dog ->
            val name = dog.name.forLocale(locale)
            a(href = "/${locale.code}/dogs/${petSlug(dog.name.en, dog.id)}", classes = "cat-card") {
                cardPhoto(dog.photoUrls.firstOrNull() ?: DEFAULT_PHOTO, name, index)
                span(classes = "cat-card-name") { +name }
                val meta = listOfNotNull(
                    dog.age?.forLocale(locale),
                    displayCatLocation(dog.location, locale).takeIf { dog.location != CatLocation.IN_SHELTER }
                )
                if (meta.isNotEmpty()) {
                    span(classes = "cat-card-meta") { +meta.joinToString(" · ") }
                }
            }
        }
    }
}

fun HTML.dogDetailsPage(dog: Dog, locale: AppLocale, currentPath: String) {
    val messages = messagesFor(locale)
    val name = dog.name.forLocale(locale)
    val photoUrls = dog.photoUrls.ifEmpty { listOf(DEFAULT_PHOTO) }
    val description = dog.description.forLocale(locale)

    // The same page as a cat's, without the status and the contact button: same title rule, same head
    // block and the same page class (the phone layout in main.css hangs on it).
    pageLayout(
        locale,
        pageTitle = name,
        currentPath = currentPath,
        mainClass = "page cat-page",
        description = description,
        ogImage = photoUrls.first()
    ) {
        p(classes = "back") { a(href = "/${locale.code}/dogs") { +messages.t("dog.backToDogs") } }

        div(classes = "cat-head") {
            h1 { +name }
        }

        div(classes = "cat-profile") {
            photoGallery(photoUrls, name, messages)

            div(classes = "cat-info") {
                p { +description }
                dl(classes = "facts-list") {
                    dog.age?.let { age -> fact(messages.t("cat.label.age"), age.forLocale(locale)) }
                    fact(messages.t("cat.label.location"), displayCatLocation(dog.location, locale))
                }
            }
        }
    }
}
