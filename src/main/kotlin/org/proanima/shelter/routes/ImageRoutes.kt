package org.proanima.shelter.routes

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.proanima.shelter.repository.PhotoStore

private const val ONE_DAY_SECONDS = 24 * 3600

// Serves /images/{name} from the photo store (the database). The content hash is the ETag, so a browser that
// already has the picture asks "has it changed?" and gets a short 304 answer instead of the whole file.
// Cache-Control for the full answer comes from the CachingHeaders plugin, as it does for files from a folder.
fun Route.imageRoutes(store: PhotoStore) {
    get("/images/{name}") {
        val name = call.parameters["name"].orEmpty()
        val sha256 = withContext(Dispatchers.IO) { store.sha256(name) }
        if (sha256 == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        if (etagMatches(call.request.header(HttpHeaders.IfNoneMatch), sha256)) {
            call.response.header(HttpHeaders.ETag, "\"$sha256\"")
            call.response.header(HttpHeaders.CacheControl, "max-age=$ONE_DAY_SECONDS")
            call.respond(HttpStatusCode.NotModified)
            return@get
        }
        // Read the picture again rather than trust the hash from a moment ago: it may have been replaced since.
        val photo = withContext(Dispatchers.IO) { store.get(name) }
        if (photo == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        call.response.header(HttpHeaders.ETag, "\"${photo.sha256}\"")
        call.respondBytes(photo.bytes, responseContentType(photo.contentType))
    }
}

// SVG is text, and files served from a folder carry its charset; the answer from the database does the same.
private fun responseContentType(stored: String): ContentType {
    val type = ContentType.parse(stored)
    return if (type.match(ContentType.Image.SVG)) type.withCharset(Charsets.UTF_8) else type
}

// If-None-Match holds one or more entity tags, possibly weak ("W/"), possibly just "*".
internal fun etagMatches(header: String?, sha256: String): Boolean =
    header != null && header.split(',').map { it.trim().removePrefix("W/").trim('"') }.any { it == sha256 || it == "*" }
