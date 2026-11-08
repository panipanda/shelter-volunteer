package org.proanima.shelter.service

import org.proanima.shelter.model.LocalizedText
import org.proanima.shelter.model.MenuNode
import org.proanima.shelter.model.MenuTree
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SystemPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The rules of the menu tree on plain data: what a stored document becomes, and how the editing functions change it.
class MenuTreeTest {

    private fun text(ru: String, en: String? = null, sr: String? = null) = LocalizedText(ru, en, sr)

    private fun section(id: Int, slug: String = "s$id", position: Int = 0) =
        Section(id = id, slug = slug, title = text("Раздел $id", "Section $id"), position = position)

    private val pages = SystemPage.entries.map { it.nodeId }

    // The tree as the list of ids in the order they are drawn in, a child right after its parent, with a dash per level.
    private fun MenuTree.outline(): List<String> =
        childrenOf(null).flatMap { top -> listOf(top.id) + childrenOf(top.id).map { "-${it.id}" } }

    private fun settle(stored: MenuTree?, vararg sections: Section) = settleMenuTree(stored, sections.toList())

    // ---- what the site starts with

    @Test
    fun `with nothing stored the menu is the five pages in their order, then the sections by position`() {
        val tree = settle(null, section(7, position = 2), section(3, position = 2), section(9, position = 1))

        assertEquals(pages + listOf("section:9", "section:3", "section:7"), tree.outline())
        assertTrue(tree.nodes.none { it.hidden })
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70, 80), tree.childrenOf(null).map { it.position })
    }

    @Test
    fun `a stored tree is read back as it was stored`() {
        val stored = MenuTree(
            listOf(
                MenuNode("page:cats", parent = "group:g1", position = 10),
                MenuNode("page:dogs", parent = "group:g1", position = 20),
                MenuNode("group:g1", position = 10, title = text("Животные")),
                MenuNode("page:guide", position = 20),
                MenuNode("page:visits", position = 30),
                MenuNode("section:1", parent = "page:visits", position = 10),
                MenuNode("page:help", position = 40)
            ),
            nextGroupNumber = 2
        )

        val tree = settle(stored, section(1))

        assertEquals(listOf("group:g1", "-page:cats", "-page:dogs", "page:guide", "page:visits", "-section:1", "page:help"), tree.outline())
        assertEquals(2, tree.nextGroupNumber)
    }

    // ---- a stored tree that no longer fits the site

    @Test
    fun `an entry of a section that is gone, a page that does not exist or a group with no name is dropped`() {
        val stored = MenuTree(
            listOf(
                MenuNode("section:99", position = 10),
                MenuNode("page:nothing", position = 20),
                MenuNode("group:g1", position = 30, title = text(" ")),
                MenuNode("group:g2", position = 40),
                MenuNode("thing:1", position = 50)
            )
        )

        assertEquals(pages, settle(stored).outline())
    }

    @Test
    fun `a page or a section the document does not know goes to the end of the top level`() {
        val stored = MenuTree(listOf(MenuNode("page:help", position = 10), MenuNode("page:cats", position = 20)))

        val tree = settle(stored, section(1))

        assertEquals(listOf("page:help", "page:cats", "page:dogs", "page:guide", "page:visits", "section:1"), tree.outline())
    }

    @Test
    fun `an entry whose parent is gone is put on the top level`() {
        val stored = MenuTree(
            listOf(
                MenuNode("page:cats", parent = "group:g5", position = 10),
                MenuNode("section:1", parent = "section:44", position = 20)
            )
        )

        val tree = settle(stored, section(1))

        assertNull(tree.node("page:cats")!!.parent)
        assertNull(tree.node("section:1")!!.parent)
    }

    @Test
    fun `parents that come back to themselves are put on the top level`() {
        val stored = MenuTree(
            listOf(
                MenuNode("page:cats", parent = "page:dogs", position = 10),
                MenuNode("page:dogs", parent = "page:cats", position = 20),
                MenuNode("page:help", parent = "page:help", position = 30)
            )
        )

        val tree = settle(stored)

        assertTrue(tree.nodes.all { it.parent == null })
        assertEquals(pages.size, tree.nodes.size)
    }

    @Test
    fun `a chain deeper than the menu allows puts the deepest entry on the top level`() {
        val stored = MenuTree(
            listOf(
                MenuNode("page:visits", position = 10),
                MenuNode("section:1", parent = "page:visits", position = 10),
                MenuNode("section:2", parent = "section:1", position = 10)
            )
        )

        val tree = settle(stored, section(1), section(2))

        assertEquals("page:visits", tree.node("section:1")!!.parent)
        assertNull(tree.node("section:2")!!.parent)
        assertTrue(tree.nodes.all { tree.depthOf(it.id) <= MAX_MENU_DEPTH })
    }

    @Test
    fun `two entries with one id count once, the first wins`() {
        val stored = MenuTree(listOf(MenuNode("page:help", position = 10, hidden = true), MenuNode("page:help", position = 20)))

        val tree = settle(stored)

        assertEquals(1, tree.nodes.count { it.id == "page:help" })
        assertTrue(tree.node("page:help")!!.hidden)
    }

    @Test
    fun `the next group number is never lower than a group the document already has`() {
        val stored = MenuTree(listOf(MenuNode("group:g7", position = 10, title = text("Группа"))), nextGroupNumber = 1)

        assertEquals(8, settle(stored).nextGroupNumber)
    }

    @Test
    fun `settling a settled tree changes nothing`() {
        val once = settle(MenuTree(listOf(MenuNode("page:cats", parent = "page:nope", position = 5))), section(1))

        assertEquals(once, settleMenuTree(once, listOf(section(1))))
    }

    // ---- moving along a line

    @Test
    fun `an entry swaps places with the one above or below it, and stays at the end of the line`() {
        val tree = settle(null)

        val down = tree.shifted("page:cats", up = false)
        assertEquals(listOf("page:dogs", "page:cats", "page:guide", "page:visits", "page:help"), down.outline())
        assertEquals(down, down.shifted("page:help", up = false))
        assertEquals(down.outline(), down.shifted("page:dogs", up = true).outline())
        assertEquals(tree.outline(), down.shifted("page:cats", up = true).outline())
    }

    @Test
    fun `shifting an entry that is not in the tree changes nothing`() {
        val tree = settle(null)

        assertEquals(tree, tree.shifted("section:5", up = true))
    }

    // ---- moving under another entry

    @Test
    fun `an entry moves to the end of the list under another entry, and to the top level`() {
        val tree = settle(null, section(1))
            .movedUnder("section:1", "page:visits")!!
            .movedUnder("page:help", "page:visits")!!

        assertEquals(listOf("page:cats", "page:dogs", "page:guide", "page:visits", "-section:1", "-page:help"), tree.outline())

        val back = tree.movedUnder("section:1", null)!!
        assertEquals(listOf("page:cats", "page:dogs", "page:guide", "page:visits", "-page:help", "section:1"), back.outline())
    }

    @Test
    fun `an entry cannot go under itself, under what is below it, or deeper than two levels`() {
        val tree = settle(null, section(1)).movedUnder("section:1", "page:visits")!!

        assertNull(tree.movedUnder("page:visits", "page:visits"))
        assertNull(tree.movedUnder("page:visits", "section:1"))
        // An entry with entries below it can only stand on the top level.
        assertNull(tree.movedUnder("page:visits", "page:help"))
        // Nothing can go under an entry that is already on the second level.
        assertNull(tree.movedUnder("page:help", "section:1"))
        assertNull(tree.movedUnder("page:help", "page:nowhere"))
        assertNull(tree.movedUnder("section:77", null))
    }

    @Test
    fun `moving an entry where it already is changes nothing`() {
        val tree = settle(null, section(1)).movedUnder("section:1", "page:visits")!!

        assertEquals(tree, tree.movedUnder("section:1", "page:visits"))
        assertEquals(tree, tree.movedUnder("page:help", null))
    }

    @Test
    fun `the choices of places are the top level and the entries that keep the tree within two levels`() {
        val tree = settle(null, section(1)).movedUnder("section:1", "page:visits")!!

        // Not itself, and not "section:1": it is already on the second level, so nothing can go under it.
        assertEquals(listOf(null, "page:cats", "page:dogs", "page:guide", "page:visits"), tree.parentChoices("page:help"))
        assertFalse("section:1" in tree.parentChoices("page:help"))
        // The entry with something below it may only go to the top level.
        assertEquals(listOf<String?>(null), tree.parentChoices("page:visits"))
        assertEquals(emptyList(), tree.parentChoices("page:missing"))
    }

    // ---- hiding

    @Test
    fun `an entry is hidden and shown again, and the others stay as they were`() {
        val tree = settle(null)

        val hidden = tree.withHidden("page:guide", true)
        assertTrue(hidden.node("page:guide")!!.hidden)
        assertEquals(1, hidden.nodes.count { it.hidden })
        assertEquals(tree, hidden.withHidden("page:guide", false))
        assertEquals(tree, tree.withHidden("page:missing", true))
    }

    // ---- groups

    @Test
    fun `a new group is added at the end of the top level with a key that is never used again`() {
        val first = settle(null).withNewGroup(text("Животные"), text("Звери"))

        assertEquals("group:g1", first.childrenOf(null).last().id)
        assertEquals(text("Звери"), first.node("group:g1")!!.menuTitle)
        assertEquals(2, first.nextGroupNumber)

        val second = first.without("group:g1").withNewGroup(text("Другая"), null)
        assertEquals("group:g2", second.childrenOf(null).last().id)
        assertNull(second.node("group:g1"))
    }

    @Test
    fun `a group is renamed, and renaming something that is not a group changes nothing`() {
        val tree = settle(null).withNewGroup(text("Животные"), null)

        val renamed = tree.withGroupRenamed("group:g1", text("Питомцы", "Pets"), text("Звери"))

        assertEquals(text("Питомцы", "Pets"), renamed.node("group:g1")!!.title)
        assertEquals(text("Звери"), renamed.node("group:g1")!!.menuTitle)
        assertEquals(tree, tree.withGroupRenamed("page:cats", text("Нет"), null))
    }

    @Test
    fun `taking a group out moves what was in it to its level, to the end, in the same order`() {
        val tree = settle(null)
            .withNewGroup(text("Животные"), null)
            .movedUnder("page:dogs", "group:g1")!!
            .movedUnder("page:cats", "group:g1")!!

        val without = tree.without("group:g1")

        assertEquals(listOf("page:guide", "page:visits", "page:help", "page:dogs", "page:cats"), without.outline())
        assertEquals(listOf(10, 20, 30, 40, 50), without.childrenOf(null).map { it.position })
    }

    @Test
    fun `taking out a section that has entries below it lifts them to its place`() {
        val tree = settle(null, section(1), section(2)).movedUnder("section:2", "section:1")!!

        val without = tree.without("section:1")

        assertEquals(pages + "section:2", without.outline())
    }

    @Test
    fun `a page cannot be taken out of the tree`() {
        val tree = settle(null)

        assertEquals(tree, tree.without("page:cats"))
        assertEquals(tree, tree.without("section:3"))
    }

    @Test
    fun `a section placed from the form lands at the end of the chosen level`() {
        val tree = settle(null, section(1), section(2))

        val placed = tree.movedUnder("section:2", "page:visits")!!

        assertNotNull(placed.node("section:2"))
        assertEquals("page:visits", placed.node("section:2")!!.parent)
        assertEquals(pages + "section:1", placed.childrenOf(null).map { it.id })
    }
}
