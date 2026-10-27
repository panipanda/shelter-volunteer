package org.proanima.shelter.routes

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText

// Pages join short words to the next one with a no-break space (see typographicText); the route tests
// compare the words, so they read the body with those spaces turned back into plain ones.
suspend fun HttpResponse.plainText(): String = bodyAsText().replace('\u00A0', ' ')

// The order of attributes in the output is the builder's business, so a tag is found by what it contains.
fun hasTag(body: String, name: String, vararg attributes: String): Boolean =
    Regex("<$name [^>]*>").findAll(body).any { tag -> attributes.all { it in tag.value } }
