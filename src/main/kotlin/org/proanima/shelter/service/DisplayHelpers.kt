package org.proanima.shelter.service

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.CatLocation
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import java.time.LocalDate

fun displayCatName(name: LocalizedText?, locale: AppLocale): String {
    return name?.forLocale(locale)?.ifBlank { null } ?: messagesFor(locale).t("cat.name.unnamed")
}

fun displayPhotoUrls(photoUrls: List<String>): List<String> {
    return photoUrls.ifEmpty { listOf("/images/default-cat.jpg") }
}

// Урл кошки строится из имени, а не из id, чтобы он был человекочитаемым. Вызывающий
// передаёт английский вариант имени (Cat.name.en): он латиницей и не зависит от языка
// страницы — транслитерация кириллицы не сделана. Если имя не задано или после чистки
// не осталось символов, откатываемся на голый id — как адресовалась кошка до этого изменения.
fun catSlug(name: String?, id: Int): String {
    val slug = name.orEmpty()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return slug.ifBlank { id.toString() }
}

// У приютских кошек редко известна точная дата рождения — обычно только год,
// иногда месяц. Возраст считаем с той точностью, что есть: без месяца — по годам,
// с месяцем — по месяцам, пока не наберётся полный год.
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

fun displayVisitAvailability(status: VisitStatus, freePlaces: Int?, locale: AppLocale): String {
    val messages = messagesFor(locale)
    return if (status == VisitStatus.CANCELLED) {
        messages.t("visit.availability.cancelled")
    } else if (status == VisitStatus.TENTATIVE) {
        messages.t("visit.availability.tentative")
    } else if (status == VisitStatus.COMPLETED) {
        messages.t("visit.availability.completed")
    } else if (status == VisitStatus.FULL) {
        messages.t("visit.availability.full")
    } else if (freePlaces == null) {
        messages.t("visit.availability.unknown")
    } else if (freePlaces > 0) {
        messages.t("visit.availability.free", freePlaces)
    } else {
        messages.t("visit.availability.full")
    }
}

fun displayVisitStatus(status: VisitStatus, locale: AppLocale): String {
    val key = when (status) {
        VisitStatus.OPEN -> "visit.status.open"
        VisitStatus.FULL -> "visit.status.full"
        VisitStatus.TENTATIVE -> "visit.status.tentative"
        VisitStatus.CANCELLED -> "visit.status.cancelled"
        VisitStatus.COMPLETED -> "visit.status.completed"
    }
    return messagesFor(locale).t(key)
}

fun displayVisitDirection(direction: VolunteerDirection, locale: AppLocale): String {
    val key = when (direction) {
        VolunteerDirection.CATS -> "visit.direction.cats"
        VolunteerDirection.DOGS -> "visit.direction.dogs"
    }
    return messagesFor(locale).t(key)
}
