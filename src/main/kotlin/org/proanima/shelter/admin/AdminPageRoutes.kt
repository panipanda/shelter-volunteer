package org.proanima.shelter.admin

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.SiteContent
import java.security.SecureRandom

// A form with the three languages of the whole guide in it is much bigger than a cat's: Cyrillic letters take six
// bytes each in a form. Still a limit, so that nobody can fill the memory by posting to the admin addresses.
private const val MAX_PAGE_FORM_BYTES = 900_000L

// One photo and the form's own fields.
private const val MAX_PAGE_PHOTO_REQUEST_BYTES = PhotoLimits.MAX_FILE_BYTES + 1024L * 1024

private const val IMAGES_PREFIX = "/images/"

private val random = SecureRandom()

internal fun Route.pageRoutes(admin: AdminSetup) {
    val defaults = ContentDefaults(admin.shippedGuide)

    route("/pages") {
        get {
            admin.withSession(call) { session ->
                val content = admin.content.load()
                val summaries = ContentCatalog.pages.map { page -> PageSummary(page, page.storageKeys.count { it in content.entries }) }
                call.respondHtml { adminPagesIndexPage(summaries, session.csrf) }
            }
        }

        route("/{page}") {
            get {
                admin.withSession(call) { session ->
                    withContentPage(call) { page ->
                        val content = admin.content.load()
                        val view = formView(page, content, defaults, PageForm.valuesOf(page, content, defaults), emptyMap(), session, call.request.queryParameters["msg"])
                        call.respondHtml { adminPageFormPage(view) }
                    }
                }
            }

            post {
                admin.withForm(call, MAX_PAGE_FORM_BYTES) { session, typed ->
                    withContentPage(call) { page ->
                        val content = admin.content.load()
                        val result = PageForm.validate(page, typed, defaults, content)
                        if (result.ok) {
                            admin.content.update(result.changes)
                            call.seeOther("/admin/pages/${page.id}?msg=saved")
                        } else {
                            // The form comes back with everything as it was typed.
                            val view = formView(page, content, defaults, typed, result.errors, session, flashCode = null)
                            call.respondHtml(HttpStatusCode.UnprocessableEntity) { adminPageFormPage(view) }
                        }
                    }
                }
            }

            // The photo has a page of its own: changing it reloads the page, which would throw away the text typed
            // into the form but not saved yet.
            route("/photo") {
                get {
                    admin.withSession(call) { session ->
                        withContentPage(call) { page ->
                            if (page.photo == null) {
                                call.respond(HttpStatusCode.NotFound)
                            } else {
                                val view = photoView(admin, page, page.photo, session, call.request.queryParameters["msg"])
                                call.respondHtml { adminPagePhotoPage(view) }
                            }
                        }
                    }
                }

                post {
                    admin.withSession(call) { session ->
                        withContentPage(call) { page ->
                            if (page.photo == null) call.respond(HttpStatusCode.NotFound) else uploadPagePhoto(admin, call, session, page, page.photo)
                        }
                    }
                }

                post("/reset") {
                    admin.withForm(call) { _, _ ->
                        withContentPage(call) { page ->
                            if (page.photo == null) {
                                call.respond(HttpStatusCode.NotFound)
                            } else {
                                val previous = customPhotoUrl(page.photo, admin.content.load())
                                admin.content.update(mapOf(ContentKey(page.photo.setting.key) to null))
                                previous?.let { removePagePhoto(admin, it) }
                                call.seeOther("/admin/pages/${page.id}/photo?msg=reset")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend inline fun withContentPage(call: ApplicationCall, block: (ContentPage) -> Unit) {
    val page = ContentCatalog.find(call.parameters["page"])
    if (page == null) {
        call.respond(HttpStatusCode.NotFound)
    } else {
        block(page)
    }
}

private fun formView(
    page: ContentPage,
    content: SiteContent,
    defaults: ContentDefaults,
    values: Map<String, String>,
    errors: Map<String, String>,
    session: AdminSession,
    flashCode: String?
) = PageFormView(
    page = page,
    values = values,
    errors = errors,
    edited = PageForm.editedNames(page, content),
    defaults = defaults,
    photoUrl = page.photo?.let { content.plain(it.setting.key) ?: it.setting.default },
    photoEdited = page.photo?.let { customPhotoUrl(it, content) != null } ?: false,
    csrf = session.csrf,
    flashCode = flashCode
)

// The address of a photo that was uploaded through the admin panel; null while the page has the committed one.
private fun customPhotoUrl(photo: PagePhoto, content: SiteContent): String? =
    content.plain(photo.setting.key)?.takeIf { it.startsWith("$IMAGES_PREFIX${photo.namePrefix}-") }

private fun photoView(
    admin: AdminSetup,
    page: ContentPage,
    photo: PagePhoto,
    session: AdminSession,
    flashCode: String?,
    problems: List<String> = emptyList()
): PagePhotoView {
    val content = admin.content.load()
    val custom = customPhotoUrl(photo, content)
    return PagePhotoView(
        page = page,
        photo = photo,
        photoUrl = custom ?: photo.setting.default,
        custom = custom != null,
        csrf = session.csrf,
        flashCode = flashCode,
        problems = problems,
        usedBytes = admin.photos.totalBytes()
    )
}

// Takes the file of an upload (the form's token must come before it: a file that comes earlier is not read at
// all), turns it into the page's JPEG, stores it under a new address and points the page at it. The picture it
// replaces is deleted afterwards, and only if it was one uploaded here: the committed file is never touched.
private suspend fun uploadPagePhoto(admin: AdminSetup, call: ApplicationCall, session: AdminSession, page: ContentPage, photo: PagePhoto) {
    val length = call.request.contentLength()
    if (length == null || length > MAX_PAGE_PHOTO_REQUEST_BYTES) {
        call.respond(HttpStatusCode.PayloadTooLarge)
        return
    }
    var tokenOk = false
    var fileBeforeToken = false
    var bytes: ByteArray? = null
    var extraFiles = false

    call.receiveMultipart().forEachPart { part ->
        try {
            if (part is PartData.FormItem && part.name == "_csrf") {
                tokenOk = tokensMatch(part.value, session.csrf)
            } else if (part is PartData.FileItem && !part.originalFileName.isNullOrBlank()) {
                if (!tokenOk) {
                    fileBeforeToken = true
                } else if (bytes == null) {
                    bytes = part.streamProvider().use { it.readNBytes(PhotoLimits.MAX_FILE_BYTES + 1) }
                } else {
                    extraFiles = true
                }
            }
        } finally {
            part.dispose()
        }
    }

    if (!tokenOk || fileBeforeToken) {
        call.respondHtml(HttpStatusCode.Forbidden) {
            adminMessagePage(
                "Страница устарела",
                "Форма была открыта слишком давно, и ничего не загрузилось. Откройте страницу фото заново.",
                "/admin/pages/${page.id}/photo",
                "К фото",
                session.csrf
            )
        }
        return
    }

    val problems = mutableListOf<String>()
    val picture = bytes
    if (picture == null) {
        problems += "Выберите файл."
    } else {
        try {
            val processed = ImageProcessing.processForPage(picture)
            val name = newPageImageName(admin, photo.namePrefix)
            val previous = customPhotoUrl(photo, admin.content.load())
            admin.photos.put("$name.jpg", "image/jpeg", processed)
            admin.content.update(mapOf(ContentKey(photo.setting.key) to "$IMAGES_PREFIX$name.jpg"))
            previous?.let { removePagePhoto(admin, it) }
            call.seeOther("/admin/pages/${page.id}/photo?msg=uploaded")
            return
        } catch (e: PhotoRejected) {
            problems += e.message.orEmpty()
        }
    }
    if (extraFiles) {
        problems += "Загружается один файл: остальные пропущены."
    }
    call.respondHtml(HttpStatusCode.UnprocessableEntity) {
        adminPagePhotoPage(photoView(admin, page, photo, session, flashCode = null, problems = problems))
    }
}

// /images/page-home-hero-3fa9c1d2.jpg: the random part makes the address new for every photo, so a browser never
// shows an old picture under a new one. The name of the uploaded file is not used.
private fun newPageImageName(admin: AdminSetup, prefix: String): String {
    while (true) {
        val name = "$prefix-" + ByteArray(4).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        if (admin.photos.sha256("$name.jpg") == null) return name
    }
}

private fun removePagePhoto(admin: AdminSetup, url: String) {
    admin.photos.delete(url.removePrefix(IMAGES_PREFIX))
}
