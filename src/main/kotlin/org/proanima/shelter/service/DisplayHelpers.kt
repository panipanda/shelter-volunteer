package org.proanima.shelter.service

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import java.time.LocalDate

fun displayCatName(name: LocalizedText?, locale: AppLocale): String {
    return name?.forLocale(locale)?.ifBlank { null } ?: messagesFor(locale).t("cat.name.unnamed")
}

fun displayPhotoUrls(photoUrls: List<String>): List<String> {
    return photoUrls.ifEmpty { listOf("/images/default-cat.jpg") }
}

// A cat's or dog's URL is built from the name, not the id, so that it is human-readable. The caller
// passes the English name variant (Cat.name.en): it is in Latin script and does not depend on the page
// language — Cyrillic transliteration is not implemented. If the name is not set or nothing remains
// after cleaning, we fall back to the bare id — how the cat was addressed before this change.
fun petSlug(name: String?, id: Int): String {
    val slug = name.orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return slug.ifBlank { id.toString() }
}

// Shelter cats rarely have an exact birth date known — usually only the year,
// sometimes the month. We compute age with whatever precision is available: without a month — in years,
// with a month — in months, until a full year is reached.
fun displayCatAge(birthYear: Int?, birthMonth: Int?, locale: AppLocale, today: LocalDate = LocalDate.now()): String {
    val messages = messagesFor(locale)
    if (birthYear == null) {
        return messages.t("cat.age.unknown")
    }
    if (birthMonth == null) {
        val years = today.year - birthYear
        return if (years == 1) messages.t("cat.age.oneYear") else messages.t("cat.age.years", years)
    }
    val totalMonths = (today.year - birthYear) * 12 + (today.monthValue - birthMonth)
    val years = totalMonths / 12
    val months = totalMonths % 12
    return if (years == 1) {
        messages.t("cat.age.oneYear")
    } else if (years > 1) {
        messages.t("cat.age.years", years)
    } else if (months == 1) {
        messages.t("cat.age.oneMonth")
    } else {
        messages.t("cat.age.months", months)
    }
}

fun displayCatLocation(location: CatLocation, locale: AppLocale): String {
    val key = when (location) {
        CatLocation.IN_SHELTER -> "cat.location.inShelter"
        CatLocation.IN_FOSTERHOME -> "cat.location.inFosterhome"
        CatLocation.AT_HOME -> "cat.location.atHome"
    }
    return messagesFor(locale).t(key)
}

fun displayAdoptionStage(stage: AdoptionStage, locale: AppLocale): String {
    val key = when (stage) {
        AdoptionStage.NONE -> "cat.adoptionStage.none"
        AdoptionStage.FOR_ADOPTION -> "cat.adoptionStage.forAdoption"
        AdoptionStage.RESERVED -> "cat.adoptionStage.reserved"
        AdoptionStage.ADOPTED -> "cat.adoptionStage.adopted"
    }
    return messagesFor(locale).t(key)
}
