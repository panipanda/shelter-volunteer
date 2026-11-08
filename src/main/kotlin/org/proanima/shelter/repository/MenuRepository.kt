package org.proanima.shelter.repository

import kotlinx.serialization.json.Json
import org.proanima.shelter.model.MenuTree
import java.io.File

interface MenuRepository {
    // The tree the admin panel saved; null while it has never been saved (the menu is then the default one).
    fun load(): MenuTree?
}

// Pages only read; the admin panel is the one that writes. The tree is one document that is saved whole.
interface MutableMenuRepository : MenuRepository {
    fun save(tree: MenuTree)
}

// No saved tree: what a site that never touched its menu behaves like.
object NoMenu : MenuRepository {
    override fun load(): MenuTree? = null
}

// The tree kept in memory: what the site shows when it runs on the files of data/ (where nothing can edit it), and what
// tests use in place of a database.
class InMemoryMenuRepository(tree: MenuTree? = null) : MutableMenuRepository {

    @Volatile
    private var tree: MenuTree? = tree

    override fun load(): MenuTree? = tree

    override fun save(tree: MenuTree) {
        this.tree = tree
    }
}

// The tree as a file, for the backup and for a site that runs without a database (data/menu.json, in the layout the
// site starts from). A missing file means the menu was never saved; a broken one stops the start rather than quietly
// putting the menu back to the default.
const val MENU_FILE_NAME = "menu.json"

private val fileJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    // As in the database: a default that changes in the code later must not change what was saved before.
    encodeDefaults = true
}

fun menuToJson(tree: MenuTree): String = fileJson.encodeToString(MenuTree.serializer(), tree)

fun readMenuFile(file: File): MenuTree? =
    if (file.exists()) fileJson.decodeFromString(MenuTree.serializer(), file.readText()) else null
