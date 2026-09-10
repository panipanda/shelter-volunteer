package org.proanima.shelter.views

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatAvailability
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrl

fun renderCatsListPage(cats: List<Cat>, locale: AppLocale): String {
    val prefix = "/${locale.code}"
    val catItems = cats.joinToString(separator = "\n") { cat ->
        "<li><a href=\"$prefix/cats/${cat.id}\">${escapeHtml(displayCatName(cat.name))}</a></li>"
    }

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Cats</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <h1>Cats</h1>
            <ul>
                $catItems
            </ul>
        </body>
        </html>
    """.trimIndent()
}

fun renderCatDetailsPage(cat: Cat, locale: AppLocale): String {
    val prefix = "/${locale.code}"
    val name = escapeHtml(displayCatName(cat.name))
    val age = displayCatAge(cat.age)
    val availability = displayCatAvailability(cat.isAvailable)
    val photoUrl = escapeHtml(displayPhotoUrl(cat.photoUrl))
    val description = escapeHtml(cat.description.forLocale(locale))

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>$name</title>
        </head>
        <body>
            ${renderNavigation(locale)}
            <p><a href="$prefix/cats">Back to cats</a></p>

            <h1>$name</h1>

            <img src="$photoUrl" alt="$name" width="300">

            <p><strong>Age:</strong> $age</p>
            <p><strong>Status:</strong> $availability</p>
            <p><strong>Description:</strong> $description</p>
        </body>
        </html>
    """.trimIndent()
}