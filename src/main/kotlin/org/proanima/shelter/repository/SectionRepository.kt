package org.proanima.shelter.repository

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

interface SectionRepository {
    // From the smallest position up, then in the order the sections were made.
    fun findSections(): List<Section>

    // The entries of every section, drafts included, in the order they were made. The pages leave the drafts out.
    fun findItems(): List<SectionItem>
}

// Pages only read; the admin panel is the one that writes. A section or an entry with id 0 is new: the database
// gives it the real id, and save returns it with that id.
interface MutableSectionRepository : SectionRepository {
    fun saveSection(section: Section): Section

    // A section takes its entries with it. Their photos are for the caller to remove.
    fun deleteSection(id: Int): Boolean

    fun saveItem(item: SectionItem): SectionItem

    fun deleteItem(id: Int): Boolean
}

internal val sectionOrder: Comparator<Section> = compareBy<Section>({ it.position }, { it.id })

// No sections at all: what a site without any behaves like.
object NoSections : SectionRepository {
    override fun findSections(): List<Section> = emptyList()

    override fun findItems(): List<SectionItem> = emptyList()
}

// Sections kept in memory: what the site shows when it runs on the files of data/ (where nothing can edit them), and
// what tests use in place of a database.
class InMemorySectionRepository(
    sections: List<Section> = emptyList(),
    items: List<SectionItem> = emptyList()
) : MutableSectionRepository {

    private val lock = Any()
    private var sections: List<Section> = sections
    private var items: List<SectionItem> = items
    private val nextId = AtomicInteger(maxOf(sections.maxOfOrNull { it.id } ?: 0, items.maxOfOrNull { it.id } ?: 0) + 1)

    override fun findSections(): List<Section> = synchronized(lock) { sections.sortedWith(sectionOrder) }

    override fun findItems(): List<SectionItem> = synchronized(lock) { items.sortedBy { it.id } }

    override fun saveSection(section: Section): Section = synchronized(lock) {
        val saved = if (section.id == 0) section.copy(id = nextId.getAndIncrement()) else section
        if (section.id != 0 && sections.none { it.id == section.id }) {
            throw NoSuchElementException("There is no section ${section.id} to update")
        }
        sections = sections.filterNot { it.id == saved.id } + saved
        saved
    }

    override fun deleteSection(id: Int): Boolean = synchronized(lock) {
        val existed = sections.any { it.id == id }
        sections = sections.filterNot { it.id == id }
        items = items.filterNot { it.sectionId == id }
        existed
    }

    override fun saveItem(item: SectionItem): SectionItem = synchronized(lock) {
        val saved = if (item.id == 0) item.copy(id = nextId.getAndIncrement()) else item
        if (item.id != 0 && items.none { it.id == item.id }) {
            throw NoSuchElementException("There is no entry ${item.id} to update")
        }
        items = items.filterNot { it.id == saved.id } + saved
        saved
    }

    override fun deleteItem(id: Int): Boolean = synchronized(lock) {
        val existed = items.any { it.id == id }
        items = items.filterNot { it.id == id }
        existed
    }
}

// The sections and their entries as files, for the backup and for a site that runs without a database
// (data/sections.json and data/section-items.json, in the layout the site starts from). A missing file means the
// site has none; a broken one stops the start rather than leaving the sections quietly out.
const val SECTIONS_FILE_NAME = "sections.json"
const val SECTION_ITEMS_FILE_NAME = "section-items.json"

private val fileJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    // As in the database: a default that changes in the code later must not change what was saved before.
    encodeDefaults = true
}

fun sectionsToJson(sections: List<Section>): String = fileJson.encodeToString(ListSerializer(Section.serializer()), sections)

fun sectionItemsToJson(items: List<SectionItem>): String = fileJson.encodeToString(ListSerializer(SectionItem.serializer()), items)

fun readSectionsFile(file: File): List<Section> =
    if (file.exists()) fileJson.decodeFromString(ListSerializer(Section.serializer()), file.readText()) else emptyList()

fun readSectionItemsFile(file: File): List<SectionItem> =
    if (file.exists()) fileJson.decodeFromString(ListSerializer(SectionItem.serializer()), file.readText()) else emptyList()
