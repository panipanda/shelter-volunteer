package org.proanima.shelter.views

import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayCatAge
import org.proanima.shelter.service.displayCatAvailability
import org.proanima.shelter.service.displayCatName
import org.proanima.shelter.service.displayPhotoUrl

fun renderCatsListPage(cats: List<Cat>): String {
    val catItems = cats.joinToString(separator = "\n") { cat ->
        "<li><a href=\"/cats/${cat.id}\">${displayCatName(cat.name)}</a></li>"
    }

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Cats</title>
        </head>
        <body>
            <h1>Cats</h1>
            <ul>
                $catItems
            </ul>
        </body>
        </html>
    """.trimIndent()
}

fun renderCatDetailsPage(cat: Cat): String {
    val name = displayCatName(cat.name)
    val age = displayCatAge(cat.age)
    val availability = displayCatAvailability(cat.isAvailable)
    val photoUrl = displayPhotoUrl(cat.photoUrl)

    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>$name</title>
        </head>
        <body>
            <p><a href="/cats">Back to cats</a></p>

            <h1>$name</h1>

            <img src="$photoUrl" alt="$name" width="300">

            <p><strong>Age:</strong> $age</p>
            <p><strong>Status:</strong> $availability</p>
            <p><strong>Description:</strong> ${cat.description}</p>
        </body>
        </html>
    """.trimIndent()
}