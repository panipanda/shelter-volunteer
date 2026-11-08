package org.proanima.shelter.service

import org.proanima.shelter.model.AppLocale
import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.model.SystemPage
import org.proanima.shelter.repository.InMemoryMenuRepository
import org.proanima.shelter.repository.InMemorySectionRepository
import org.proanima.shelter.repository.MenuRepository
import org.proanima.shelter.repository.SectionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// What a visitor gets in the menu: the tree cut down to what exists in the language of the page.
class MenuServiceTest {

    private fun text(ru: String, en: String? = null, sr: String? = null) = LocalizedText(ru, en, sr)

    private val archive = Section(id = 1, slug = "archive", title = text("Архив визитов", "Visit archive"))
    private val articles = Section(id = 2, slug = "articles", title = text("Статьи", "Articles", "Članci"), menuTitle = text("Ст.", "Art.", "Čl."))

    private fun published(sectionId: Int, id: Int, title: LocalizedText) =
        SectionItem(id = id, sectionId = sectionId, title = title, published = true)

    private val archiveItem = published(1, 10, text("Выезд", "Trip"))

    // The label of a page is its key here, so the tests do not depend on the wording.
    private fun service(
        tree: MenuTree?,
        sections: List<Section> = listOf(archive, articles),
        items: List<SectionItem> = listOf(archiveItem, published(2, 11, text("Совет", "Advice", "Savet")))
    ) = MenuService(InMemorySectionRepository(sections, items), InMemoryMenuRepository(tree), label = { page, _ -> page.key })

    // The entries of the top level as "label" or "label[child, child]"; a '-' marks an entry with no page of its own.
    private fun NavMenu.outline(): List<String> = items.map { item ->
        val own = (if (item.href == null) "-" else "") + item.label
        if (item.children.isEmpty()) own else own + item.children.joinToString(prefix = "[", postfix = "]") { it.label }
    }

    private fun group(number: Int, ru: String, parent: String? = null, position: Int = 0, en: String? = ru, hidden: Boolean = false) =
        MenuNode("group:g$number", parent = parent, position = position, title = text(ru, en), hidden = hidden)

    // ---- the menu a site starts with

    @Test
    fun `with no saved tree and no sections the menu is the five pages and fits one row`() {
        val menu = service(null, sections = emptyList(), items = emptyList()).build(AppLocale.RU)

        assertEquals(listOf("cats", "dogs", "guide", "visits", "help"), menu.outline())
        assertEquals(listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/help"), menu.items.map { it.href })
        assertFalse(menu.twoRows)
    }

    @Test
    fun `a section with something published follows the pages, and it makes the menu two rows`() {
        val menu = service(null).build(AppLocale.RU)

        assertEquals(7, menu.items.size)
        assertEquals(listOf("/ru/archive", "/ru/articles"), menu.items.drop(5).map { it.href })
        assertTrue(menu.twoRows)
    }

    @Test
    fun `the label of a section is its menu name, and the title when it has none`() {
        val menu = service(null).build(AppLocale.EN)

        assertEquals(listOf("Visit archive", "Art."), menu.items.drop(5).map { it.label })
    }

    // ---- languages

    @Test
    fun `a section with nothing in a language is not in that language's menu`() {
        val onlyRussian = published(1, 10, text("Выезд"))
        val service = service(null, sections = listOf(archive), items = listOf(onlyRussian))

        assertEquals(6, service.build(AppLocale.RU).items.size)
        assertEquals(5, service.build(AppLocale.EN).items.size)
        assertEquals(5, service.build(AppLocale.SR).items.size)
    }

    @Test
    fun `the rows are decided for all languages together, so the header does not change height with the language`() {
        val onlyRussian = published(1, 10, text("Выезд"))
        val service = service(null, sections = listOf(archive), items = listOf(onlyRussian))

        AppLocale.entries.forEach { assertTrue(service.build(it).twoRows, it.code) }
    }

    @Test
    fun `a draft does not show a section`() {
        val service = service(null, sections = listOf(archive), items = listOf(archiveItem.copy(published = false)))

        assertEquals(5, service.build(AppLocale.RU).items.size)
    }

    // ---- the tree

    @Test
    fun `a section under a page is a drop-down of that page, and the page keeps its own address`() {
        val tree = MenuTree(
            listOf(
                MenuNode("page:cats", position = 10), MenuNode("page:dogs", position = 20), MenuNode("page:guide", position = 30),
                MenuNode("page:visits", position = 40), MenuNode("section:1", parent = "page:visits", position = 10),
                MenuNode("page:help", position = 50)
            )
        )

        val menu = service(tree, sections = listOf(archive), items = listOf(archiveItem)).build(AppLocale.RU)

        assertEquals(listOf("cats", "dogs", "guide", "visits[Архив визитов]", "help"), menu.outline())
        assertEquals("/ru/visits", menu.items[3].href)
        assertEquals("/ru/archive", menu.items[3].children.single().href)
        assertFalse(menu.twoRows, "five at the top level fit one row")
    }

    @Test
    fun `a group is a drop-down with no page of its own`() {
        val tree = MenuTree(
            listOf(
                group(1, "Животные", position = 10), MenuNode("page:cats", parent = "group:g1", position = 10),
                MenuNode("page:dogs", parent = "group:g1", position = 20), MenuNode("page:guide", position = 20),
                MenuNode("page:visits", position = 30), MenuNode("page:help", position = 40)
            ),
            nextGroupNumber = 2
        )

        val menu = service(tree, sections = emptyList(), items = emptyList()).build(AppLocale.RU)

        assertEquals(listOf("-Животные[cats, dogs]", "guide", "visits", "help"), menu.outline())
        assertNull(menu.items[0].href)
        assertEquals(listOf("/ru/cats", "/ru/dogs"), menu.items[0].children.map { it.href })
    }

    @Test
    fun `the menu name of a group is used, and the title when there is none`() {
        val short = group(1, "Животные приюта").copy(menuTitle = text("Звери", "Pets"))
        val plain = group(2, "Другое", position = 20)
        val tree = MenuTree(
            listOf(short, MenuNode("page:cats", parent = "group:g1"), plain, MenuNode("page:dogs", parent = "group:g2")), nextGroupNumber = 3
        )

        val menu = service(tree, sections = emptyList(), items = emptyList()).build(AppLocale.RU)

        assertEquals(listOf("Звери", "Другое"), menu.items.take(2).map { it.label })
        assertEquals("Pets", service(tree, emptyList(), emptyList()).build(AppLocale.EN).items.first().label)
        // A language the group was not named in borrows from the others, like every text of the site.
        assertEquals("Другое", service(tree, emptyList(), emptyList()).build(AppLocale.SR).items[1].label)
    }

    @Test
    fun `a group whose entries are all out of this language is not shown`() {
        val onlyRussian = published(1, 10, text("Выезд"))
        val tree = MenuTree(listOf(group(1, "Архивы"), MenuNode("section:1", parent = "group:g1")), nextGroupNumber = 2)
        val service = service(tree, sections = listOf(archive), items = listOf(onlyRussian))

        assertEquals("-Архивы[Архив визитов]", service.build(AppLocale.RU).outline().first())
        assertEquals(listOf("cats", "dogs", "guide", "visits", "help"), service.build(AppLocale.EN).outline())
    }

    @Test
    fun `a section with no page in this language but with a shown entry below it only opens its drop-down`() {
        val onlyRussian = published(1, 10, text("Выезд"))
        val tree = MenuTree(
            listOf(MenuNode("section:1", position = 10), MenuNode("section:2", parent = "section:1", position = 10)), nextGroupNumber = 1
        )
        val service = service(tree, items = listOf(onlyRussian, published(2, 11, text("Совет", "Advice"))))

        val en = service.build(AppLocale.EN).items.first()
        assertEquals("Visit archive", en.label)
        assertNull(en.href)
        assertEquals(listOf("/en/articles"), en.children.map { it.href })

        val ru = service.build(AppLocale.RU).items.first()
        assertEquals("/ru/archive", ru.href)
    }

    // ---- hiding

    @Test
    fun `a hidden page is out of the menu but its children go with it`() {
        val tree = MenuTree(
            listOf(
                MenuNode("page:visits", position = 10, hidden = true), MenuNode("section:1", parent = "page:visits", position = 10),
                MenuNode("page:guide", position = 20, hidden = true)
            )
        )

        val menu = service(tree, sections = listOf(archive), items = listOf(archiveItem)).build(AppLocale.RU)

        assertEquals(listOf("cats", "dogs", "help"), menu.outline())
    }

    @Test
    fun `a hidden child leaves the others where they are, and a group whose children are all hidden is not shown`() {
        val tree = MenuTree(
            listOf(
                group(1, "Животные", position = 10), MenuNode("page:cats", parent = "group:g1", position = 10, hidden = true),
                MenuNode("page:dogs", parent = "group:g1", position = 20), group(2, "Пусто", position = 20),
                MenuNode("page:guide", parent = "group:g2", hidden = true)
            ),
            nextGroupNumber = 3
        )

        val menu = service(tree, emptyList(), emptyList()).build(AppLocale.RU)

        assertEquals(listOf("-Животные[dogs]", "visits", "help"), menu.outline())
    }

    @Test
    fun `when everything is hidden the menu is empty and nothing fails`() {
        val tree = MenuTree(SystemPage.entries.map { MenuNode(it.nodeId, hidden = true) })

        val menu = service(tree, emptyList(), emptyList()).build(AppLocale.RU)

        assertTrue(menu.items.isEmpty())
        assertFalse(menu.twoRows)
    }

    // ---- faults

    @Test
    fun `a tree that cannot be read gives the default menu, and sections that cannot be read give a menu without them`() {
        val brokenMenu = object : MenuRepository {
            override fun load(): MenuTree = error("the database is down")
        }
        val brokenSections = object : SectionRepository {
            override fun findSections(): List<Section> = error("the database is down")

            override fun findItems(): List<SectionItem> = error("the database is down")
        }

        val noTree = MenuService(InMemorySectionRepository(listOf(archive), listOf(archiveItem)), brokenMenu, label = { page, _ -> page.key })
        val noSections = MenuService(brokenSections, InMemoryMenuRepository(), label = { page, _ -> page.key })

        assertEquals(listOf("cats", "dogs", "guide", "visits", "help", "Архив визитов"), noTree.build(AppLocale.RU).outline())
        assertEquals(listOf("cats", "dogs", "guide", "visits", "help"), noSections.build(AppLocale.RU).outline())
    }

    @Test
    fun `the fixed menu is the five pages with their real names`() {
        val ru = NavMenu.fixed(AppLocale.RU)

        assertEquals(listOf("Кошки", "Собаки", "Гайд", "Визиты", "Как помочь"), ru.items.map { it.label })
        assertEquals(listOf("/ru/cats", "/ru/dogs", "/ru/guide", "/ru/visits", "/ru/help"), ru.items.map { it.href })
        assertEquals(listOf(true, true, false, true, false), ru.items.map { it.matchChildren })
    }
}
