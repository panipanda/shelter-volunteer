package org.proanima.shelter.views

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.service.MenuEntry
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.proanima.shelter.views.SiteMenu")

// Where the application keeps the way to ask which sections the menu lists (set by configureRoutes).
val SectionMenuKey = AttributeKey<(AppLocale) -> List<MenuEntry>>("SectionMenu")

// The menu entries made by sections, for the page being drawn. Every page is drawn by respondPage in one go on one
// thread, and its views are plain functions that are called from many places, so the entries are handed to the
// navigation here rather than through the parameters of every view.
object SiteMenu {
    private val current = ThreadLocal<List<MenuEntry>>()

    fun entries(): List<MenuEntry> = current.get().orEmpty()

    internal fun <T> showing(entries: List<MenuEntry>, block: () -> T): T {
        val before = current.get()
        current.set(entries)
        try {
            return block()
        } finally {
            if (before == null) current.remove() else current.set(before)
        }
    }
}

// A menu that cannot be read (the database is asleep or down) is a menu without the sections, not a page that fails.
internal fun ApplicationCall.sectionMenu(locale: AppLocale): List<MenuEntry> {
    val source = application.attributes.getOrNull(SectionMenuKey) ?: return emptyList()
    return try {
        source(locale)
    } catch (e: Exception) {
        log.warn("The menu of the sections could not be read: {}", e.message)
        emptyList()
    }
}
