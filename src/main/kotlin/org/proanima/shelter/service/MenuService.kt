package org.proanima.shelter.service

import org.proanima.shelter.i18n.messagesFor
import org.proanima.shelter.i18n.t
import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SystemPage
import org.proanima.shelter.model.sectionNodeId
import org.proanima.shelter.repository.MenuRepository
import org.proanima.shelter.repository.SectionRepository
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.proanima.shelter.service.MenuService")

// One entry of the menu as the navigation draws it.
class NavItem(
    val id: String,
    val label: String,
    // Null where the entry has no page of its own (a group; a section that has nothing to show in this language but
    // has a drop-down worth showing): such an entry only opens its drop-down.
    val href: String?,
    // A page below this one counts as being on the page (a cat's card inside /cats).
    val matchChildren: Boolean,
    val children: List<NavItem> = emptyList()
)

// What the navigation needs for one page: the entries, and whether they need two rows.
class NavMenu(val items: List<NavItem>, val twoRows: Boolean) {
    companion object {
        // The menu of a site with no sections and no saved tree. It is what the navigation draws when it is given no
        // menu at all (a view drawn on its own), so the two cannot differ.
        fun fixed(locale: AppLocale, label: (SystemPage, AppLocale) -> String = ::pageLabel): NavMenu =
            NavMenu(
                SystemPage.entries.map { page -> pageItem(page, locale, label, emptyList()) },
                twoRows = SystemPage.entries.size > MAX_ONE_ROW_ITEMS
            )
    }
}

private fun pageLabel(page: SystemPage, locale: AppLocale): String = messagesFor(locale).t(page.labelKey)

private fun pageItem(page: SystemPage, locale: AppLocale, label: (SystemPage, AppLocale) -> String, children: List<NavItem>) =
    NavItem(page.nodeId, label(page, locale), "/${locale.code}/${page.path}", page.matchChildren, children)

// The menu visitors see: the saved tree (or the default one), cut down to what exists in the language of the page.
class MenuService(
    private val sections: SectionRepository,
    private val menu: MenuRepository,
    private val label: (SystemPage, AppLocale) -> String = ::pageLabel
) {

    fun build(locale: AppLocale): NavMenu {
        val sectionList = guarded("sections", emptyList()) { sections.findSections() }
        val itemList = guarded("entries of the sections", emptyList()) { sections.findItems() }
        val tree = settleMenuTree(guarded("tree", null) { menu.load() }, sectionList)

        val byLanguage = AppLocale.entries.associateWith { language -> NavBuilder(tree, sectionList, itemList, language, label).top() }
        // One decision for all languages, so that the header does not change height when the language is switched.
        val twoRows = byLanguage.values.any { it.size > MAX_ONE_ROW_ITEMS }
        return NavMenu(byLanguage.getValue(locale), twoRows)
    }

    // A part of the menu that cannot be read (the database is asleep or down) is that part missing, not a page that fails.
    private fun <T> guarded(what: String, fallback: T, read: () -> T): T = try {
        read()
    } catch (e: Exception) {
        log.warn("The menu could not read the {}: {}", what, e.message)
        fallback
    }
}

// The tree for one language. The rules are in the order they are applied:
//  - a hidden entry is out, and so is everything below it (the page itself stays reachable by its address);
//  - a page is always shown;
//  - a section is shown when it has a published entry written in this language;
//  - a group is shown when one of its entries is;
//  - an entry with a shown entry below it is shown anyway; if it has no page of its own here it only opens its drop-down.
private class NavBuilder(
    private val tree: MenuTree,
    sections: List<Section>,
    private val items: List<SectionItem>,
    private val locale: AppLocale,
    private val label: (SystemPage, AppLocale) -> String
) {
    private val sectionsById = sections.associateBy { sectionNodeId(it.id) }

    fun top(): List<NavItem> = below(null)

    private fun below(parent: String?): List<NavItem> = tree.childrenOf(parent).mapNotNull { item(it) }

    private fun item(node: MenuNode): NavItem? {
        if (node.hidden) {
            return null
        }
        val children = below(node.id)
        return when {
            node.isPage -> SystemPage.entries.firstOrNull { it.nodeId == node.id }?.let { pageItem(it, locale, label, children) }
            node.isSection -> sectionsById[node.id]?.let { section ->
                val shown = items.any { it.sectionId == section.id && it.isShownIn(locale) }
                if (!shown && children.isEmpty()) {
                    null
                } else {
                    NavItem(node.id, section.menuLabel(locale), if (shown) "/${locale.code}/${section.slug}" else null, true, children)
                }
            }
            node.isGroup -> if (children.isEmpty()) null else NavItem(node.id, node.menuLabel(locale), null, false, children)
            else -> null
        }
    }
}
