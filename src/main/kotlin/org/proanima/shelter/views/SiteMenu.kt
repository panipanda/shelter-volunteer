package org.proanima.shelter.views

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.NavMenu
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.proanima.shelter.views.SiteMenu")

// Where the application keeps the way to ask which menu to draw in a language (set by configureRoutes).
val SiteMenuKey = AttributeKey<(AppLocale) -> NavMenu>("SiteMenu")

// The menu of the page being drawn. Every page is drawn by respondPage in one go on one thread, and its views are
// plain functions that are called from many places, so the menu is handed to the navigation here rather than
// through the parameters of every view. A view drawn on its own (a test of a view, the admin panel's preview) gets
// the menu of the five pages.
object SiteMenu {
    private val current = ThreadLocal<NavMenu>()

    fun current(locale: AppLocale): NavMenu = current.get() ?: NavMenu.fixed(locale)

    // Whether the menu being drawn has a drop-down: the page then needs the script that opens it.
    fun hasDropdown(): Boolean = current.get()?.items?.any { it.children.isNotEmpty() } == true

    internal fun <T> showing(menu: NavMenu, block: () -> T): T {
        val before = current.get()
        current.set(menu)
        try {
            return block()
        } finally {
            if (before == null) current.remove() else current.set(before)
        }
    }
}

// A menu that cannot be read (the database is asleep or down) is the menu of the five pages, not a page that fails.
internal fun ApplicationCall.siteMenu(locale: AppLocale): NavMenu {
    val source = application.attributes.getOrNull(SiteMenuKey) ?: return NavMenu.fixed(locale)
    return try {
        source(locale)
    } catch (e: Exception) {
        log.warn("The menu could not be built: {}", e.message)
        NavMenu.fixed(locale)
    }
}
