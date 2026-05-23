package org.proanima.shelter.views

import org.proanima.shelter.model.Cat
import org.proanima.shelter.service.displayCatName

fun renderCatsListPage(cats: List<Cat>): String {
    val catItems = cats.joinToString(separator = "\n") { cat ->
        "<li>${displayCatName(cat.name)}</li>"
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