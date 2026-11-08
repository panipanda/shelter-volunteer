package org.proanima.shelter.admin

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
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
import org.proanima.shelter.repository.MutableCatRepository
import org.proanima.shelter.repository.MutableDogRepository
import org.proanima.shelter.repository.PhotoStore
import org.proanima.shelter.repository.isSiteAsset
import org.proanima.shelter.service.cardPhotoUrl
import org.proanima.shelter.service.petSlug
import org.proanima.shelter.views.catDetailsPage
import org.proanima.shelter.views.dogDetailsPage
import org.proanima.shelter.views.respondPage
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val SHELTER_ZONE = ZoneId.of("Europe/Belgrade")

// A login form is a few bytes and an edit form a few kilobytes. A bigger or length-less body is refused
// before it is read, so nobody can fill the memory by posting to the admin addresses.
private const val MAX_LOGIN_BYTES = 4_096L
private const val MAX_FORM_BYTES = 200_000L

private const val IMAGES_PREFIX = "/images/"

// Everything the admin panel works with. With no AdminSetup the site has no /admin at all.
class AdminSetup(
    val config: AdminConfig,
    val cats: MutableCatRepository,
    val dogs: MutableDogRepository,
    val photos: PhotoStore,
    val clock: Clock = Clock.systemUTC(),
    val throttle: FailedLoginThrottle = FailedLoginThrottle()
) {
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
    val id: (T) -> Int,
    val title: (T) -> String,
    val slug: (T) -> String,
    val photoUrls: (T) -> List<String>,
    val all: () -> List<T>,
    val save: (T) -> T,
    val delete: (Int) -> Boolean,
    val newValues: () -> Map<String, String>,
    val valuesOf: (T) -> Map<String, String>,
    val validate: (Map<String, String>, T?, List<T>) -> FormResult<T>,
    val formPage: HTML.(FormPage<T>) -> Unit,
    val previewPage: HTML.(T, AppLocale, String) -> Unit,
    val deleteQuestion: (String) -> String
)

private fun catKind(admin: AdminSetup) = PetKind<Cat>(
    urlName = "cats",
    id = Cat::id,
    title = ::catTitle,
    slug = { petSlug(it.name?.en, it.id) },
    photoUrls = Cat::photoUrls,
    all = admin.cats::findAll,
    save = admin.cats::save,
    delete = admin.cats::delete,
    newValues = CatForm::newValues,
    valuesOf = CatForm::valuesOf,
    validate = { values, existing, others -> CatForm.validate(values, existing, others, admin.nowText(), admin.today()) },
    formPage = { page -> catFormPage(page) },
    previewPage = { cat, locale, path -> catDetailsPage(cat, locale, path) },
    deleteQuestion = { "Удалить кошку «$it»?" }
)

private fun dogKind(admin: AdminSetup) = PetKind<Dog>(
    urlName = "dogs",
    id = Dog::id,
    title = ::dogTitle,
    slug = { petSlug(it.name.en, it.id) },
    photoUrls = Dog::photoUrls,
    all = admin.dogs::findAll,
    save = admin.dogs::save,
    delete = admin.dogs::delete,
    newValues = DogForm::newValues,
    valuesOf = DogForm::valuesOf,
    validate = { values, existing, others -> DogForm.validate(values, existing, others) },
    formPage = { page -> dogFormPage(page) },
    previewPage = { dog, locale, path -> dogDetailsPage(dog, locale, path) },
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
                    adminDashboardPage(admin.cats.findAll(), admin.dogs.findAll(), session.csrf, call.request.queryParameters["msg"])
                }
            }
        }

        petRoutes(admin, catKind(admin))
        petRoutes(admin, dogKind(admin))
    }
}

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

private fun <T : Any> Route.petRoutes(admin: AdminSetup, kind: PetKind<T>) {
    route("/${kind.urlName}") {
        get("/new") {
            admin.withSession(call) { session ->
                call.respondHtml { kind.formPage(this, FormPage(null, kind.newValues(), emptyMap(), session.csrf)) }
            }
        }

        post {
            admin.withForm(call) { session, values -> savePet(admin, kind, call, session, existing = null, values) }
        }

        route("/{id}") {
            get {
                admin.withSession(call) { session ->
                    withPet(call, kind) { pet ->
                        val page = FormPage(pet, kind.valuesOf(pet), emptyMap(), session.csrf, call.request.queryParameters["msg"])
                        call.respondHtml { kind.formPage(this, page) }
                    }
                }
            }

            post {
                admin.withForm(call) { session, values ->
                    withPet(call, kind) { pet -> savePet(admin, kind, call, session, existing = pet, values) }
                }
            }

            get("/delete") {
                admin.withSession(call) { session ->
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
                admin.withForm(call) { _, _ ->
                    withPet(call, kind) { pet ->
                        val photoUrls = kind.photoUrls(pet)
                        kind.delete(kind.id(pet))
                        removeUnusedPhotos(admin, photoUrls)
                        call.seeOther("/admin?msg=deleted")
                    }
                }
            }

            // The page as visitors will see it, in the chosen language, whatever the status: a draft has no
            // address on the site, so this is the only way to look at it.
            get("/preview") {
                admin.withSession(call) { _ ->
                    withPet(call, kind) { pet ->
                        val locale = AppLocale.entries.firstOrNull { it.code == call.request.queryParameters["lang"] } ?: AppLocale.default
                        val path = "/${locale.code}/${kind.urlName}/${kind.slug(pet)}"
                        call.respondPage(locale) { kind.previewPage(this, pet, locale, path) }
                    }
                }
            }
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

// A photo is deleted with its pet unless another cat or dog still uses it. The pictures of the site's own
// pages are never touched.
private fun removeUnusedPhotos(admin: AdminSetup, urls: List<String>) {
    val stillUsed = (admin.cats.findAll().flatMap { it.photoUrls } + admin.dogs.findAll().flatMap { it.photoUrls }).toSet()
    urls.filterNot { it in stillUsed }.forEach { url ->
        val original = url.removePrefix(IMAGES_PREFIX)
        val card = cardPhotoUrl(url).removePrefix(IMAGES_PREFIX)
        listOf(original, card).distinct().filterNot(::isSiteAsset).forEach { admin.photos.delete(it) }
    }
}

private suspend inline fun AdminSetup.withSession(call: ApplicationCall, block: (AdminSession) -> Unit) {
    val session = currentSession(call)
    if (session == null) {
        call.seeOther("/admin/login")
    } else {
        block(session)
    }
}

// Every change goes through here: a logged-in person, a body of sane size, and the form's own token, which a
// page on another site cannot know.
private suspend inline fun AdminSetup.withForm(call: ApplicationCall, block: (AdminSession, Map<String, String>) -> Unit) {
    val session = currentSession(call)
    if (session == null) {
        call.seeOther("/admin/login")
        return
    }
    val length = call.request.contentLength()
    if (length == null || length > MAX_FORM_BYTES) {
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

private suspend fun ApplicationCall.seeOther(location: String) {
    response.header(HttpHeaders.Location, location)
    respond(HttpStatusCode.SeeOther)
}
