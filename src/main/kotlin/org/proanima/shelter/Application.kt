package org.proanima.shelter

import io.ktor.http.ContentType
import io.ktor.http.content.CachingOptions
import io.ktor.http.CacheControl
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.proanima.shelter.admin.AdminSetup
import org.proanima.shelter.admin.adminConfigFromEnv
import org.proanima.shelter.admin.adminRoutes
import org.proanima.shelter.admin.installAdminSessions
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.repository.EditableGuideRepository
import org.proanima.shelter.repository.InMemorySiteContentRepository
import org.proanima.shelter.repository.JsonCatRepository
import org.proanima.shelter.repository.JsonDogRepository
import org.proanima.shelter.repository.JsonVisitOverridesRepository
import org.proanima.shelter.repository.MarkdownGuideRepository
import org.proanima.shelter.repository.NoSiteContent
import org.proanima.shelter.repository.PgCatRepository
import org.proanima.shelter.repository.PgDogRepository
import org.proanima.shelter.repository.PgPhotoStore
import org.proanima.shelter.repository.PgSiteContentRepository
import org.proanima.shelter.repository.PhotoStore
import org.proanima.shelter.repository.SITE_CONTENT_FILE_NAME
import org.proanima.shelter.repository.SiteContentRepository
import org.proanima.shelter.repository.createDataSource
import org.proanima.shelter.repository.createSchema
import org.proanima.shelter.repository.loadSeedData
import org.proanima.shelter.repository.readSiteContentFile
import org.proanima.shelter.repository.parseDatabaseUrl
import org.proanima.shelter.repository.seedIfNeeded
import org.proanima.shelter.repository.syncSiteAssets
import org.proanima.shelter.routes.catRoutes
import org.proanima.shelter.routes.dogRoutes
import org.proanima.shelter.routes.healthRoutes
import org.proanima.shelter.routes.helpRoutes
import org.proanima.shelter.routes.imageRoutes
import org.proanima.shelter.routes.visitRoutes
import org.proanima.shelter.routes.guideRoutes
import org.proanima.shelter.routes.homeRoutes
import org.proanima.shelter.service.CatService
import org.proanima.shelter.service.DogService
import org.proanima.shelter.service.GuideService
import org.proanima.shelter.service.VisitOverrides
import org.proanima.shelter.service.validatePets
import java.io.File
import java.time.Clock
import java.time.ZoneId

// The visit schedule follows Belgrade time, not the server's time zone.
private val SHELTER_ZONE = ZoneId.of("Europe/Belgrade")

private val IMAGES_DIR = File("data/images")
private const val ONE_HOUR_SECONDS = 3600
private const val ONE_DAY_SECONDS = 24 * 3600

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(
        factory = Netty,
        port = port,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

// DATABASE_URL decides where the cats, dogs and their photos live. Without it they are the files in data/, as
// they always were (local runs, tests and the Docker build need no database). With it they live in the database.
fun Application.module() {
    val databaseUrl = System.getenv("DATABASE_URL")?.trim().orEmpty()
    if (databaseUrl.isEmpty()) {
        moduleWithFiles()
    } else {
        moduleWithDatabase(databaseUrl)
    }
}

internal fun Application.moduleWithFiles() {
    val catRepository = JsonCatRepository()
    val dogRepository = JsonDogRepository()
    // Fail at startup, not on the first request to the broken page.
    failOnProblems(validatePets(catRepository.findAll(), dogRepository.findAll(), IMAGES_DIR))

    // Page texts edited in the admin panel and put back from a backup (data/site-content.json). There is no admin
    // panel without a database, so here the file is only read.
    val siteContent = InMemorySiteContentRepository(readSiteContentFile(File("data/$SITE_CONTENT_FILE_NAME")))

    configureRoutes(
        catService = CatService(catRepository),
        guideService = GuideService(EditableGuideRepository(MarkdownGuideRepository(), siteContent)),
        dogService = DogService(dogRepository),
        visitOverrides = JsonVisitOverridesRepository().load(),
        siteContent = siteContent
    )
}

internal fun Application.moduleWithDatabase(databaseUrl: String) {
    val dataSource = createDataSource(parseDatabaseUrl(databaseUrl))
    environment.monitor.subscribe(ApplicationStopped) { dataSource.close() }
    createSchema(dataSource)

    val clock = Clock.systemUTC()
    val seeded = seedIfNeeded(dataSource, clock) {
        // The first start copies data/ into the database, so it must be sound; after that the database is the truth.
        loadSeedData(File("data/cats.json"), File("data/dogs.json"), IMAGES_DIR, File("data/$SITE_CONTENT_FILE_NAME")).also { seed ->
            val photoNames = seed.photos.map { it.name }.toSet()
            failOnProblems(validatePets(seed.cats, seed.dogs, IMAGES_DIR.path) { it in photoNames })
        }
    }
    if (seeded) {
        environment.log.info("The database was empty: cats, dogs and photos were copied into it from data/")
    }

    val photoStore = PgPhotoStore(dataSource, clock)
    syncSiteAssets(photoStore, IMAGES_DIR).takeIf { it.isNotEmpty() }?.let { environment.log.info("Site pictures refreshed in the database: {}", it) }

    val catRepository = PgCatRepository(dataSource, clock)
    val dogRepository = PgDogRepository(dataSource, clock)
    // A mistake made in the admin panel must not take the whole site down, so here problems are only reported.
    val photoNames = photoStore.names()
    validatePets(catRepository.findAll(), dogRepository.findAll(), "the database") { it in photoNames }
        .forEach { environment.log.warn("Content problem: {}", it) }

    // The texts of the pages (home, guide, visits, help) as edited in the admin panel; the pages show the texts that
    // come with the code wherever nothing was edited.
    val siteContent = PgSiteContentRepository(dataSource, clock)
    val shippedGuide = MarkdownGuideRepository()

    // The admin panel needs ADMIN_PASSWORD and SESSION_SECRET; without them the site runs and /admin does not exist.
    val admin = adminConfigFromEnv()?.let {
        AdminSetup(it, catRepository, dogRepository, photoStore, clock, content = siteContent, shippedGuide = shippedGuide)
    }
    if (admin == null) {
        environment.log.warn("ADMIN_PASSWORD and SESSION_SECRET are not set: the admin panel is off")
    }

    configureRoutes(
        catService = CatService(catRepository),
        guideService = GuideService(EditableGuideRepository(shippedGuide, siteContent)),
        dogService = DogService(dogRepository),
        visitOverrides = JsonVisitOverridesRepository().load(),
        photoStore = photoStore,
        admin = admin,
        siteContent = siteContent
    )
}

private fun failOnProblems(problems: List<String>) {
    check(problems.isEmpty()) { "Invalid content data:\n" + problems.joinToString("\n") { "  - $it" } }
}

// With a photoStore the pictures come from it; without one they are served from data/images.
// With an AdminSetup the admin panel is served under /admin; without one there is no such address.
fun Application.configureRoutes(
    catService: CatService,
    guideService: GuideService,
    clock: Clock = Clock.system(SHELTER_ZONE),
    dogService: DogService = DogService(JsonDogRepository()),
    visitOverrides: VisitOverrides = VisitOverrides(),
    photoStore: PhotoStore? = null,
    admin: AdminSetup? = null,
    // The edited texts of the pages. With an admin panel they are the ones it edits.
    siteContent: SiteContentRepository = admin?.content ?: NoSiteContent
) {
    configureErrorPages()
    if (admin != null) {
        installAdminSessions(admin.config)
    }
    // A HEAD request (link previews, uptime monitors, some crawlers) is answered by the GET route of the same
    // address, without the body. Without this only files served from a folder understood HEAD.
    install(AutoHeadResponse)
    // "/ru/cats/" works the same as "/ru/cats".
    install(IgnoreTrailingSlash)
    // Text only: images and fonts are already compressed.
    install(Compression) {
        gzip {
            matchContentType(ContentType.Text.Any, ContentType.Application.JavaScript)
        }
    }
    install(CachingHeaders) {
        options { _, content ->
            when (content.contentType?.withoutParameters()?.contentType) {
                // Files are served under fixed names, so a replaced photo or stylesheet
                // shows up for visitors only after the cache expires: keep the lifetime short.
                "image", "font" -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = ONE_DAY_SECONDS))
                else -> when (content.contentType?.withoutParameters()) {
                    ContentType.Text.CSS, ContentType.Application.JavaScript ->
                        CachingOptions(CacheControl.MaxAge(maxAgeSeconds = ONE_HOUR_SECONDS))
                    else -> null
                }
            }
        }
    }

    routing {
        get("/") {
            call.respondRedirect("/${AppLocale.default.code}")
        }

        healthRoutes()
        if (admin != null) {
            adminRoutes(admin)
        }
        if (photoStore == null) {
            staticFiles("/images", IMAGES_DIR)
        } else {
            imageRoutes(photoStore)
        }
        staticResources("/icons", "static/icons")
        staticResources("/fonts", "static/fonts")
        staticResources("/styles", "static/styles")
        staticResources("/scripts", "static/scripts")

        AppLocale.entries.forEach { locale ->
            route("/${locale.code}") {
                homeRoutes(catService, locale, clock, visitOverrides, siteContent)
                catRoutes(catService, locale, siteContent)
                dogRoutes(dogService, locale, siteContent)
                visitRoutes(locale, clock, visitOverrides, siteContent)
                guideRoutes(guideService, locale, siteContent)
                helpRoutes(locale, siteContent)
            }
        }
    }
}