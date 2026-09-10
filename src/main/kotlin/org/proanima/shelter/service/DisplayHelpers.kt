package org.proanima.shelter.service

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection

fun displayCatName(name: String?, locale: AppLocale): String {
    return name ?: messagesFor(locale).t("cat.name.unnamed")
}

fun displayPhotoUrl(photoUrl: String?): String {
    return photoUrl ?: "/images/default-cat.jpg"
}

fun displayCatAge(age: Int?, locale: AppLocale): String {
    val messages = messagesFor(locale)
    return if (age == null) {
        messages.t("cat.age.unknown")
    } else if (age == 1) {
        messages.t("cat.age.oneYear")
    } else {
        messages.t("cat.age.years", age)
    }
}

fun displayCatAvailability(isAvailable: Boolean, locale: AppLocale): String {
    val messages = messagesFor(locale)
    return if (isAvailable) {
        messages.t("cat.status.available")
    } else {
        messages.t("cat.status.unavailable")
    }
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
