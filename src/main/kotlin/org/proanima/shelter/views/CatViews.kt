package org.proanima.shelter.views

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatAvailability
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrl

fun renderCatsListPage(cats: List<Cat>, locale: AppLocale): String {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)
    val catItems = cats.joinToString(separator = "\n") { cat ->
        "<li><a href=\"$prefix/cats/${cat.id}\">${escapeHtml(displayCatName(cat.name, locale))}</a></li>"
    }

    return """
        <!DOCTYPE html>
        <html lang="${locale.code}">
        <head>
            <meta charset="UTF-8">
            <title>${messages.t("cat.page.title")}</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <h1>${messages.t("cat.page.title")}</h1>
            <ul>
                $catItems
            </ul>
        </body>
        </html>
    """.trimIndent()
}

fun renderCatDetailsPage(cat: Cat, locale: AppLocale): String {
    val prefix = "/${locale.code}"
    val messages = messagesFor(locale)
    val name = escapeHtml(displayCatName(cat.name, locale))
    val age = displayCatAge(cat.age, locale)
    val availability = displayCatAvailability(cat.isAvailable, locale)
    val photoUrl = escapeHtml(displayPhotoUrl(cat.photoUrl))
    val description = escapeHtml(cat.description.forLocale(locale))

    return """
        <!DOCTYPE html>
        <html lang="${locale.code}">
        <head>
            <meta charset="UTF-8">
            <title>$name</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <p><a href="$prefix/cats">${messages.t("cat.backToCats")}</a></p>

            <h1>$name</h1>

            <img src="$photoUrl" alt="$name" width="300">

            <p><strong>${messages.t("cat.label.age")}</strong> $age</p>
            <p><strong>${messages.t("cat.label.status")}</strong> $availability</p>
            <p><strong>${messages.t("cat.label.description")}</strong> $description</p>
        </body>
        </html>
    """.trimIndent()
}
