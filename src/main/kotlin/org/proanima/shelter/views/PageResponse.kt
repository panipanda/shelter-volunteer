package org.proanima.shelter.views

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.html.HTML
import kotlinx.html.TagConsumer
import kotlinx.html.html
import kotlinx.html.stream.appendHTML
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.typographicText

// Runs every text node through typographicText. Attribute values, comments and unsafe blocks (scripts)
// are not text nodes, so alt, aria-label and the theme script stay as written. Doing it here covers the
// UI strings, the cat and dog descriptions and the guide at once, without a call at every place that prints text.
private class TypographicConsumer<R>(
    private val downstream: TagConsumer<R>,
    private val locale: AppLocale
) : TagConsumer<R> by downstream {
    override fun onTagContent(content: CharSequence) {
        downstream.onTagContent(typographicText(content.toString(), locale))
    }
}

// The same as ktor's respondHtml, with the typographic consumer in front of the writer.
suspend fun ApplicationCall.respondPage(
    locale: AppLocale,
    status: HttpStatusCode? = null,
    block: HTML.() -> Unit
) {
    val page = buildString {
        append("<!DOCTYPE html>\n")
        TypographicConsumer(appendHTML(), locale).html(block = block)
    }
    respond(TextContent(page, ContentType.Text.Html.withCharset(Charsets.UTF_8), status))
}
