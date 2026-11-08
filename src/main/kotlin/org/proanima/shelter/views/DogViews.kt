package org.proanima.shelter.views

import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.dl
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.span
import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.Dog
import org.proanima.shelter.service.displayCatLocation
import org.proanima.shelter.service.displayDogAge
import org.proanima.shelter.service.petSlug
import java.util.ResourceBundle

private const val DEFAULT_PHOTO = "/images/default-dog.svg"

fun HTML.dogsPage(dogs: List<Dog>, locale: AppLocale, currentPath: String, messages: ResourceBundle = messagesFor(locale)) {
    pageLayout(
        locale,
        pageTitle = messages.t("dogs.page.title"),
        currentPath = currentPath,
        description = messages.t("dogs.page.description"),
        messages = messages
    ) {
        // Just like the list of cats: the heading and the cards. How to sign up for the dog trips is on the
        // home page, who Svora is is on the help page.
        h1 { +messages.t("nav.dogs") }
        p(classes = "list-note") { +messages.t("dogs.list.note") }

        // Dogs that found a home go to their own section at the bottom, as the cats do. With none, the page is
        // only the heading, the note and the cards.
        val (adopted, others) = dogs.partition { it.adoptionStage == AdoptionStage.ADOPTED }
        dogList(others, locale)
        if (adopted.isNotEmpty()) {
            h2 { +messages.t("dog.adopted.title") }
            dogList(adopted, locale)
        }
    }
}

// Same card look as the cats: the whole card links to the dog's page. The status line is only for a stage that
// differs from the group the dog is listed in; of the stages shown in the lists (not adopted) that is only RESERVED.
private fun FlowContent.dogList(dogs: List<Dog>, locale: AppLocale) {
    div(classes = "cat-list") {
        dogs.forEachIndexed { index, dog ->
            val name = dog.name.forLocale(locale)
            a(href = "/${locale.code}/dogs/${petSlug(dog.name.en, dog.id)}", classes = "cat-card") {
                cardPhoto(dog.photoUrls.firstOrNull() ?: DEFAULT_PHOTO, name, index)
                span(classes = "cat-card-name") { +name }
                val meta = listOfNotNull(
                    displayDogAge(dog, locale),
                    displayCatLocation(dog.location, locale).takeIf { dog.location != CatLocation.IN_SHELTER }
                )
                if (meta.isNotEmpty()) {
                    span(classes = "cat-card-meta") { +meta.joinToString(" · ") }
                }
                if (dog.adoptionStage == AdoptionStage.RESERVED) {
                    adoptionStatus(dog.adoptionStage, locale, "cat-card-meta", forDog = true)
                }
            }
        }
    }
}

fun HTML.dogDetailsPage(
    dog: Dog,
    locale: AppLocale,
    currentPath: String,
    preview: PagePreview? = null,
    messages: ResourceBundle = messagesFor(locale)
) {
    val name = dog.name.forLocale(locale)
    val photoUrls = dog.photoUrls.ifEmpty { listOf(DEFAULT_PHOTO) }
    val description = dog.description.forLocale(locale)
    val age = displayDogAge(dog, locale)
    val medicalStatus = dog.medicalStatus?.forLocale(locale)?.takeIf { it.isNotBlank() }
    val forAdoption = dog.adoptionStage == AdoptionStage.FOR_ADOPTION
    // Unlike a cat, a dog has no stock sentence for "how to take it": only what the admin wrote is shown.
    val instruction = dog.adoptionInstruction?.forLocale(locale)?.takeIf { it.isNotBlank() }
    val coordinator = telegramHandle(instruction).takeIf { forAdoption }

    // The same page as a cat's: same title rule, same head block, the status and the contact button when they
    // are known, and the same page class (the phone layout in main.css hangs on it).
    pageLayout(
        locale,
        pageTitle = name,
        currentPath = currentPath,
        mainClass = "page cat-page",
        description = description,
        ogImage = photoUrls.first(),
        preview = preview,
        messages = messages
    ) {
        p(classes = "back") { a(href = "/${locale.code}/dogs") { +messages.t("dog.backToDogs") } }

        div(classes = "cat-head") {
            h1 { +name }
            if (coordinator != null) {
                a(href = "https://t.me/$coordinator", classes = "button") { +messages.t("cat.contact.telegram") }
            }
        }
        if (dog.adoptionStage != AdoptionStage.NONE) {
            p(classes = "status-line") { adoptionStatus(dog.adoptionStage, locale, forDog = true) }
        }

        div(classes = "cat-profile") {
            photoGallery(photoUrls, name, messages)

            div(classes = "cat-info") {
                p { +description }
                dl(classes = "facts-list") {
                    age?.let { fact(messages.t("cat.label.age"), it) }
                    fact(messages.t("cat.label.location"), displayCatLocation(dog.location, locale))
                    medicalStatus?.let { fact(messages.t("cat.label.medicalStatus"), it) }
                }
                // Without a Telegram contact in the instruction there is no button, so the text itself is shown.
                if (forAdoption && coordinator == null && instruction != null) {
                    aside(classes = "callout") {
                        p { +instruction }
                    }
                }
            }
        }
    }
}
