package org.proanima.shelter.admin

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlinx.html.HTML
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import org.proanima.shelter.repository.GuideRepository
import org.proanima.shelter.repository.InMemorySectionRepository
import org.proanima.shelter.repository.InMemorySiteContentRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.repository.MutableCatRepository
import org.proanima.shelter.repository.MutableDogRepository
import org.proanima.shelter.repository.MutableSectionRepository
import org.proanima.shelter.repository.MutableSiteContentRepository
import org.proanima.shelter.repository.PhotoStore
import org.proanima.shelter.repository.isSiteAsset
import org.proanima.shelter.service.cardPhotoUrl
import org.proanima.shelter.service.petSlug
import org.proanima.shelter.views.PagePreview
import org.proanima.shelter.views.catDetailsPage
import org.proanima.shelter.views.dogDetailsPage
import org.proanima.shelter.views.respondPage
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

private val SHELTER_ZONE = ZoneId.of("Europe/Belgrade")

// A login form is a few bytes and an edit form a few kilobytes. A bigger or length-less body is refused
// before it is read, so nobody can fill the memory by posting to the admin addresses.
private const val MAX_LOGIN_BYTES = 4_096L
internal const val MAX_FORM_BYTES = 200_000L

private const val IMAGES_PREFIX = "/images/"

// Everything the admin panel works with. With no AdminSetup the site has no /admin at all.
class AdminSetup(
    val config: AdminConfig,
    val cats: MutableCatRepository,
    val dogs: MutableDogRepository,
    val photos: PhotoStore,
    val clock: Clock = Clock.systemUTC(),
    val throttle: FailedLoginThrottle = FailedLoginThrottle(),
    // The texts and photos of the home, guide, visits and help pages, and the guide that comes with the code (what
    // the guide's form shows until someone edits it).
    val content: MutableSiteContentRepository = InMemorySiteContentRepository(),
    val shippedGuide: GuideRepository = MarkdownGuideRepository(),
    // The sections made in the admin panel and their entries.
    val sections: MutableSectionRepository = InMemorySectionRepository()
) {
    // One backup at a time: it reads every photo, and the free plan has a tenth of a CPU.
    internal val exportRunning = AtomicBoolean(false)

    internal fun nowText(): String =
        ZonedDateTime.ofInstant(clock.instant(), SHELTER_ZONE).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    internal fun today(): LocalDate = LocalDate.ofInstant(clock.instant(), SHELTER_ZONE)

    internal fun currentSession(call: ApplicationCall): AdminSession? {
        val session = call.sessions.get<AdminSession>() ?: return null
        val age = clock.instant().epochSecond - session.issuedAtEpochSecond
        return session.takeIf { age in 0..SESSION_LIFETIME.seconds }
    }
}

// What differs between cats and dogs: their data, their form and their page. The routes are the same.
internal class PetKind<T : Any>(
    val urlName: String,
    // The start of the names of this kind's photo files: "cat" -> /images/cat-12-3fa9c1d2.jpg
    val filePrefix: String,
    val id: (T) -> Int,
    val title: (T) -> String,
    val slug: (T) -> String,
    val photoUrls: (T) -> List<String>,
    val withPhotoUrls: (T, List<String>) -> T,
    val published: (T) -> Boolean,
    val all: () -> List<T>,
    val save: (T) -> T,
    val delete: (Int) -> Boolean,
    val newValues: () -> Map<String, String>,
    val valuesOf: (T) -> Map<String, String>,
    val validate: (Map<String, String>, T?, List<T>) -> FormResult<T>,
    val formPage: HTML.(FormPage<T>) -> Unit,
    val previewPage: HTML.(T, AppLocale, String, PagePreview) -> Unit,
    val deleteQuestion: (String) -> String,
    // The list this kind's records are in: where "to the list" and a deletion lead.
    val listHref: String = "/admin",
    // The biggest form this kind's records are saved with.
    val maxFormBytes: Long = MAX_FORM_BYTES
)

private fun catKind(admin: AdminSetup) = PetKind<Cat>(
    urlName = "cats",
    filePrefix = "cat",
    id = Cat::id,
    title = ::catTitle,
    slug = { petSlug(it.name?.en, it.id) },
    photoUrls = Cat::photoUrls,
    withPhotoUrls = { cat, urls -> cat.copy(photoUrls = urls, updatedAt = admin.nowText()) },
    published = Cat::published,
    all = admin.cats::findAll,
    save = admin.cats::save,
    delete = admin.cats::delete,
    newValues = CatForm::newValues,
    valuesOf = CatForm::valuesOf,
    validate = { values, existing, others -> CatForm.validate(values, existing, others, admin.nowText(), admin.today()) },
    formPage = { page -> catFormPage(page) },
    previewPage = { cat, locale, path, preview -> catDetailsPage(cat, locale, path, preview) },
    deleteQuestion = { "Удалить кошку «$it»?" }
)

private fun dogKind(admin: AdminSetup) = PetKind<Dog>(
    urlName = "dogs",
    filePrefix = "dog",
    id = Dog::id,
    title = ::dogTitle,
    slug = { petSlug(it.name.en, it.id) },
    photoUrls = Dog::photoUrls,
    withPhotoUrls = { dog, urls -> dog.copy(photoUrls = urls) },
    published = Dog::published,
    all = admin.dogs::findAll,
    save = admin.dogs::save,
    delete = admin.dogs::delete,
    newValues = DogForm::newValues,
    valuesOf = DogForm::valuesOf,
    validate = { values, existing, others -> DogForm.validate(values, existing, others, admin.today()) },
    formPage = { page -> dogFormPage(page) },
    previewPage = { dog, locale, path, preview -> dogDetailsPage(dog, locale, path, preview) },
    deleteQuestion = { "Удалить собаку «$it»?" }
)

fun Route.adminRoutes(admin: AdminSetup) {
    route("/admin") {
        // Nothing here is for search engines, caches or frames.
        intercept(ApplicationCallPipeline.Plugins) {
            call.response.header("X-Robots-Tag", "noindex, nofollow")
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.response.header("X-Frame-Options", "DENY")
            call.response.header("Referrer-Policy", "same-origin")
        }

        loginRoutes(admin)

        get {
            admin.withSession(call) { session ->
                call.respondHtml {
                    adminDashboardPage(admin.cats.findAll(), admin.dogs.findAll(), session.csrf, call.request.queryParameters["msg"], admin.photos.totalBytes())
                }
            }
        }

        // A POST with the form's token, not a plain link: it makes the server read every photo, so a page on another
        // site must not be able to start it by sending the person's browser to a link.
        post("/export") {
            admin.withForm(call) { session, _ -> sendBackup(admin, call, session) }
        }

        val cats = catKind(admin)
        val dogs = dogKind(admin)
        petRoutes(admin, "/${cats.urlName}") { cats }
        petRoutes(admin, "/${dogs.urlName}") { dogs }
        pageRoutes(admin)
        sectionAdminRoutes(admin)
    }
}

private suspend fun sendBackup(admin: AdminSetup, call: ApplicationCall, session: AdminSession) {
    if (!admin.exportRunning.compareAndSet(false, true)) {
        call.respondHtml(HttpStatusCode.TooManyRequests) {
            adminMessagePage(
                "Копия уже собирается",
                "Кто-то уже скачивает копию. Подождите минуту и нажмите кнопку снова.",
                "/admin",
                "К списку",
                session.csrf
            )
        }
        return
    }
    try {
        // Read before the answer starts: a database that cannot be reached becomes an error page, not a broken file.
        val cats = admin.cats.findAll()
        val dogs = admin.dogs.findAll()
        val names = admin.photos.names()
        val content = admin.content.load()
        val sections = admin.sections.findSections()
        val sectionItems = admin.sections.findItems()
        val today = admin.today()
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "proanima-backup-$today.zip").toString()
        )
        call.respondOutputStream(ContentType.Application.Zip) {
            val report = writeBackup(this, cats, dogs, names, admin.photos, today, content, sections, sectionItems)
            backupLog.info(
                "Backup sent: {} cats, {} dogs, {} sections with {} entries, {} photo files, {} missing",
                report.cats, report.dogs, report.sections, report.sectionItems, report.photos, report.missingPhotos.size
            )
        }
    } finally {
        admin.exportRunning.set(false)
    }
}

private val backupLog = LoggerFactory.getLogger("org.proanima.shelter.admin.Backup")

private fun Route.loginRoutes(admin: AdminSetup) {
    get("/login") {
        if (admin.currentSession(call) != null) {
            call.seeOther("/admin")
        } else {
            call.respondHtml { adminLoginPage(error = null) }
        }
    }

    post("/login") {
        val length = call.request.contentLength()
        if (length == null || length > MAX_LOGIN_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return@post
        }
        val entered = call.receiveParameters()["password"].orEmpty()
        if (passwordMatches(entered, admin.config.password)) {
            call.sessions.set(AdminSession(newCsrfToken(), admin.clock.instant().epochSecond))
            call.seeOther("/admin")
        } else if (admin.throttle.penalize()) {
            call.respondHtml(HttpStatusCode.Forbidden) { adminLoginPage("Неверный пароль.") }
        } else {
            call.respondHtml(HttpStatusCode.TooManyRequests) {
                adminLoginPage("Слишком много неудачных попыток. Подождите минуту и попробуйте снова.")
            }
        }
    }

    post("/logout") {
        admin.withForm(call) { _, _ ->
            call.sessions.clear(SESSION_COOKIE)
            call.seeOther("/admin/login")
        }
    }
}

// path: where the routes hang ("/cats"; for the entries of a section "/sections/{sid}/items"). kindOf says which kind
// a request is about: one for the cats and the dogs, and for the entries the one made from the section named in the
// address (null when there is no such section, which is a 404).
internal fun <T : Any> Route.petRoutes(admin: AdminSetup, path: String, kindOf: (ApplicationCall) -> PetKind<T>?) {
    route(path) {
        get("/new") {
            admin.withKindSession(call, kindOf) { kind, session ->
                call.respondHtml { kind.formPage(this, FormPage(null, kind.newValues(), emptyMap(), session.csrf)) }
            }
        }

        post {
            admin.withKindForm(call, kindOf) { kind, session, values -> savePet(admin, kind, call, session, existing = null, values) }
        }

        route("/{id}") {
            get {
                admin.withKindSession(call, kindOf) { kind, session ->
                    withPet(call, kind) { pet ->
                        val page = FormPage(pet, kind.valuesOf(pet), emptyMap(), session.csrf, call.request.queryParameters["msg"])
                        call.respondHtml { kind.formPage(this, page) }
                    }
                }
            }

            post {
                admin.withKindForm(call, kindOf) { kind, session, values ->
                    withPet(call, kind) { pet -> savePet(admin, kind, call, session, existing = pet, values) }
                }
            }

            get("/delete") {
                admin.withKindSession(call, kindOf) { kind, session ->
                    withPet(call, kind) { pet ->
                        call.respondHtml {
                            adminDeleteConfirmPage(
                                question = kind.deleteQuestion(kind.title(pet)),
                                action = "/admin/${kind.urlName}/${kind.id(pet)}/delete",
                                backHref = "/admin/${kind.urlName}/${kind.id(pet)}",
                                csrf = session.csrf
                            )
                        }
                    }
                }
            }

            post("/delete") {
                admin.withKindForm(call, kindOf) { kind, _, _ ->
                    withPet(call, kind) { pet ->
                        val photoUrls = kind.photoUrls(pet)
                        kind.delete(kind.id(pet))
                        removeUnusedPhotos(admin, photoUrls)
                        call.seeOther("${kind.listHref}?msg=deleted")
                    }
                }
            }

            // Photos have a page of their own: adding, moving and removing a photo reloads the page, which would
            // throw away text typed into the form but not saved yet.
            route("/photos") {
                get {
                    admin.withKindSession(call, kindOf) { kind, session ->
                        withPet(call, kind) { pet ->
                            call.respondHtml {
                                adminPhotosPage(photosPage(admin, kind, pet, session, call.request.queryParameters["msg"]))
                            }
                        }
                    }
                }

                post {
                    admin.withKindSession(call, kindOf) { kind, session ->
                        withPet(call, kind) { pet -> uploadPhotos(admin, kind, call, session, pet) }
                    }
                }

                // "Delete" next to a photo leads here first; the deletion itself is the "update" action below.
                // Only a photo of this record can be asked about, so nothing else is ever put on the page.
                get("/delete") {
                    admin.withKindSession(call, kindOf) { kind, session ->
                        withPet(call, kind) { pet ->
                            val urls = kind.photoUrls(pet)
                            val photo = call.request.queryParameters["photo"]
                            if (photo == null || photo !in urls) {
                                call.respond(HttpStatusCode.NotFound)
                            } else {
                                call.respondHtml {
                                    adminPhotoDeleteConfirmPage(
                                        PhotoDeleteAsk(
                                            kindPath = kind.urlName,
                                            id = kind.id(pet),
                                            title = kind.title(pet),
                                            photoUrl = photo,
                                            isCover = urls.first() == photo,
                                            isOnly = urls.size == 1,
                                            csrf = session.csrf
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                post("/update") {
                    admin.withKindForm(call, kindOf) { kind, session, values ->
                        withPet(call, kind) { pet -> changePhotos(admin, kind, call, session, pet, values) }
                    }
                }
            }

            // The page as visitors will see it, in the chosen language, whatever the status: a draft has no
            // address on the site, so this is the only way to look at it.
            get("/preview") {
                admin.withKindSession(call, kindOf) { kind, _ ->
                    withPet(call, kind) { pet ->
                        val locale = AppLocale.entries.firstOrNull { it.code == call.request.queryParameters["lang"] } ?: AppLocale.default
                        val path = "/${locale.code}/${kind.urlName}/${kind.slug(pet)}"
                        val own = "/admin/${kind.urlName}/${kind.id(pet)}"
                        val preview = PagePreview(
                            editHref = own,
                            draft = !kind.published(pet),
                            localeHref = { "$own/preview?lang=${it.code}" }
                        )
                        call.respondPage(locale) { kind.previewPage(this, pet, locale, path, preview) }
                    }
                }
            }
        }
    }
}

// The kind a request is about, found from the request itself. The kind of a cat or a dog never changes; the kind of
// an entry depends on the section in the address.
private suspend inline fun <T : Any> AdminSetup.withKindSession(
    call: ApplicationCall,
    kindOf: (ApplicationCall) -> PetKind<T>?,
    block: (PetKind<T>, AdminSession) -> Unit
) {
    withSession(call) { session ->
        val kind = kindOf(call)
        if (kind == null) call.respond(HttpStatusCode.NotFound) else block(kind, session)
    }
}

private suspend inline fun <T : Any> AdminSetup.withKindForm(
    call: ApplicationCall,
    kindOf: (ApplicationCall) -> PetKind<T>?,
    block: (PetKind<T>, AdminSession, Map<String, String>) -> Unit
) {
    // The kind is found first because the size of form that is allowed depends on it; a request without a login
    // is sent to the login page before anything about the address is said.
    withSession(call) { _ ->
        val kind = kindOf(call)
        if (kind == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            withForm(call, kind.maxFormBytes) { session, values -> block(kind, session, values) }
        }
    }
}

private suspend fun <T : Any> savePet(
    admin: AdminSetup,
    kind: PetKind<T>,
    call: ApplicationCall,
    session: AdminSession,
    existing: T?,
    values: Map<String, String>
) {
    val others = kind.all().filter { existing == null || kind.id(it) != kind.id(existing) }
    val result = kind.validate(values, existing, others)
    val pet = result.value
    if (pet == null || !result.ok) {
        call.respondHtml(HttpStatusCode.UnprocessableEntity) {
            kind.formPage(this, FormPage(existing, values, result.errors, session.csrf))
        }
        return
    }
    val saved = try {
        kind.save(pet)
    } catch (e: NoSuchElementException) {
        // Deleted by someone else while this form was open.
        call.respond(HttpStatusCode.NotFound)
        return
    }
    call.seeOther("/admin/${kind.urlName}/${kind.id(saved)}?msg=${if (existing == null) "created" else "saved"}")
}

private fun <T : Any> photosPage(
    admin: AdminSetup,
    kind: PetKind<T>,
    pet: T,
    session: AdminSession,
    flashCode: String?,
    problems: List<String> = emptyList(),
    addedCount: Int = 0
) = PhotosPage(
    kindPath = kind.urlName,
    id = kind.id(pet),
    title = kind.title(pet),
    photoUrls = kind.photoUrls(pet),
    csrf = session.csrf,
    flashCode = flashCode,
    problems = problems,
    addedCount = addedCount,
    usedBytes = admin.photos.totalBytes()
)

private val random = SecureRandom()

// /images/cat-12-3fa9c1d2.jpg: the kind and the id say whose photo it is, the random part makes the address new for
// every photo, so a browser never shows an old picture under a new one. The name of the uploaded file is not used.
private fun newPhotoBase(admin: AdminSetup, filePrefix: String, id: Int): String {
    while (true) {
        val base = "$filePrefix-$id-" + ByteArray(4).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        if (admin.photos.sha256("$base.jpg") == null) return base
    }
}

// The name as the person knows it, for the message; never used to store anything.
private fun shownFileName(raw: String?): String = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\').take(60)

// Takes the files of an upload one at a time: each is read (never more than the size limit), turned into the two
// JPEG copies and stored, then forgotten, so the memory used does not grow with the number of files. The form's
// token must come before the first file; a file that comes earlier is not read at all.
private suspend fun <T : Any> uploadPhotos(
    admin: AdminSetup,
    kind: PetKind<T>,
    call: ApplicationCall,
    session: AdminSession,
    pet: T
) {
    val length = call.request.contentLength()
    if (length == null || length > PhotoLimits.MAX_UPLOAD_REQUEST_BYTES) {
        call.respond(HttpStatusCode.PayloadTooLarge)
        return
    }
    var tokenOk = false
    var fileBeforeToken = false
    var files = 0
    val problems = mutableListOf<String>()
    val stored = mutableListOf<String>()
    val alreadyThere = kind.photoUrls(pet).size

    call.receiveMultipart().forEachPart { part ->
        try {
            if (part is PartData.FormItem && part.name == "_csrf") {
                tokenOk = tokensMatch(part.value, session.csrf)
            } else if (part is PartData.FileItem && !tokenOk && !part.originalFileName.isNullOrBlank()) {
                fileBeforeToken = true
            } else if (part is PartData.FileItem && tokenOk && !part.originalFileName.isNullOrBlank()) {
                files++
                val name = shownFileName(part.originalFileName)
                if (files > PhotoLimits.MAX_FILES_PER_UPLOAD) {
                    problems += "$name: за один раз можно загрузить не больше ${PhotoLimits.MAX_FILES_PER_UPLOAD} файлов."
                } else if (alreadyThere + stored.size >= PhotoLimits.MAX_PHOTOS_PER_PET) {
                    problems += "$name: у одной записи не больше ${PhotoLimits.MAX_PHOTOS_PER_PET} фото."
                } else {
                    val bytes = part.streamProvider().use { it.readNBytes(PhotoLimits.MAX_FILE_BYTES + 1) }
                    try {
                        val processed = ImageProcessing.process(bytes)
                        val base = newPhotoBase(admin, kind.filePrefix, kind.id(pet))
                        admin.photos.put("$base.jpg", "image/jpeg", processed.original)
                        admin.photos.put("$base-card.jpg", "image/jpeg", processed.card)
                        stored += "/images/$base.jpg"
                    } catch (e: PhotoRejected) {
                        problems += "$name: ${e.message}"
                    }
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
                "Форма была открыта слишком давно, и ничего не загрузилось. Откройте страницу фотографий заново.",
                "/admin/${kind.urlName}/${kind.id(pet)}/photos",
                "К фотографиям",
                session.csrf
            )
        }
        return
    }
    if (files == 0) {
        problems += "Выберите хотя бы один файл."
    }

    // The record is read again: it may have been edited while the files were being processed.
    val fresh = kind.all().firstOrNull { kind.id(it) == kind.id(pet) }
    if (fresh == null) {
        removeUnusedPhotos(admin, stored)
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val current = if (stored.isEmpty()) fresh else kind.save(kind.withPhotoUrls(fresh, kind.photoUrls(fresh) + stored))

    if (problems.isEmpty()) {
        call.seeOther("/admin/${kind.urlName}/${kind.id(current)}/photos?msg=uploaded")
    } else {
        call.respondHtml(if (stored.isEmpty()) HttpStatusCode.UnprocessableEntity else HttpStatusCode.OK) {
            adminPhotosPage(photosPage(admin, kind, current, session, flashCode = null, problems = problems, addedCount = stored.size))
        }
    }
}

// One button of one photo: earlier, later, cover (first), delete. The photo is named by its address, not by a
// position, so a stale page cannot move or delete the wrong one.
private suspend fun <T : Any> changePhotos(
    admin: AdminSetup,
    kind: PetKind<T>,
    call: ApplicationCall,
    session: AdminSession,
    pet: T,
    values: Map<String, String>
) {
    val urls = kind.photoUrls(pet).toMutableList()
    val photo = values["photo"]
    val index = urls.indexOf(photo)
    if (photo == null || index < 0) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    val outcome = when (values["action"]) {
        "left" -> "moved".also { if (index > 0) urls.add(index - 1, urls.removeAt(index)) }
        "right" -> "moved".also { if (index < urls.lastIndex) urls.add(index + 1, urls.removeAt(index)) }
        "cover" -> "moved".also { urls.add(0, urls.removeAt(index)) }
        "delete" -> "removed".also { urls.removeAt(index) }
        else -> {
            call.respond(HttpStatusCode.BadRequest)
            return
        }
    }
    val saved = try {
        kind.save(kind.withPhotoUrls(pet, urls))
    } catch (e: NoSuchElementException) {
        call.respond(HttpStatusCode.NotFound)
        return
    }
    // The record no longer points at the photo, and only then is the picture itself removed.
    if (outcome == "removed") {
        removeUnusedPhotos(admin, listOf(photo))
    }
    call.seeOther("/admin/${kind.urlName}/${kind.id(saved)}/photos?msg=$outcome")
}

// A photo is deleted with its record unless another cat, dog or entry of a section still uses it. The pictures of
// the site's own pages are never touched.
internal fun removeUnusedPhotos(admin: AdminSetup, urls: List<String>) {
    val stillUsed = (
        admin.cats.findAll().flatMap { it.photoUrls } +
            admin.dogs.findAll().flatMap { it.photoUrls } +
            admin.sections.findItems().flatMap { it.photoUrls }
        ).toSet()
    urls.filterNot { it in stillUsed }.forEach { url ->
        val original = url.removePrefix(IMAGES_PREFIX)
        val card = cardPhotoUrl(url).removePrefix(IMAGES_PREFIX)
        listOf(original, card).distinct().filterNot(::isSiteAsset).forEach { admin.photos.delete(it) }
    }
}

internal suspend inline fun AdminSetup.withSession(call: ApplicationCall, block: (AdminSession) -> Unit) {
    val session = currentSession(call)
    if (session == null) {
        call.seeOther("/admin/login")
    } else {
        block(session)
    }
}

// Every change goes through here: a logged-in person, a body of sane size, and the form's own token, which a
// page on another site cannot know.
internal suspend inline fun AdminSetup.withForm(
    call: ApplicationCall,
    maxBytes: Long = MAX_FORM_BYTES,
    block: (AdminSession, Map<String, String>) -> Unit
) {
    val session = currentSession(call)
    if (session == null) {
        call.seeOther("/admin/login")
        return
    }
    val length = call.request.contentLength()
    if (length == null || length > maxBytes) {
        call.respond(HttpStatusCode.PayloadTooLarge)
        return
    }
    val parameters = call.receiveParameters()
    if (!tokensMatch(parameters["_csrf"], session.csrf)) {
        call.respondHtml(HttpStatusCode.Forbidden) {
            adminMessagePage(
                "Страница устарела",
                "Форма была открыта слишком давно или в другой вкладке, и ничего не сохранилось. Откройте её заново.",
                "/admin",
                "К списку",
                session.csrf
            )
        }
        return
    }
    block(session, parameters.entries().associate { (name, values) -> name to values.first() })
}

private suspend inline fun <T : Any> withPet(call: ApplicationCall, kind: PetKind<T>, block: (T) -> Unit) {
    val wanted = call.parameters["id"]?.toIntOrNull()
    val pet = wanted?.let { id -> kind.all().firstOrNull { kind.id(it) == id } }
    if (pet == null) {
        call.respond(HttpStatusCode.NotFound)
    } else {
        block(pet)
    }
}

internal suspend fun ApplicationCall.seeOther(location: String) {
    response.header(HttpHeaders.Location, location)
    respond(HttpStatusCode.SeeOther)
}
