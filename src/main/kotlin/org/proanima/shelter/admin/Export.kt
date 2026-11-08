package org.proanima.shelter.admin

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.proanima.shelter.model.Cat
import org.proanima.shelter.model.Dog
import org.proanima.shelter.model.Section
import org.proanima.shelter.model.SectionItem
import org.proanima.shelter.repository.PhotoStore
import org.proanima.shelter.repository.SECTIONS_FILE_NAME
import org.proanima.shelter.repository.SECTION_ITEMS_FILE_NAME
import org.proanima.shelter.repository.SITE_CONTENT_FILE_NAME
import org.proanima.shelter.repository.isSiteAsset
import org.proanima.shelter.repository.sectionItemsToJson
import org.proanima.shelter.repository.sectionsToJson
import org.proanima.shelter.repository.siteContentToJson
import org.proanima.shelter.service.SiteContent
import org.proanima.shelter.service.cardPhotoUrl
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// The backup is a copy kept by the people, outside the database: Neon keeps a history of its own, but only for
// a short time and only while the account exists. The layout is the one the site starts from without a
// database (data/cats.json, data/dogs.json, data/images/), so an archive can be unpacked into the repository's
// data folder, or given to the seeder as it is.

private val exportJson = Json {
    prettyPrint = true
    // As in the database: a default that changes in the code later must not change a pet that was saved before.
    encodeDefaults = true
}

private const val IMAGES_PREFIX = "/images/"

class BackupReport(
    val cats: Int,
    val dogs: Int,
    val photos: Int,
    val missingPhotos: List<String>,
    val pageTexts: Int = 0,
    val sections: Int = 0,
    val sectionItems: Int = 0
)

// Writes the zip to `out`, one photo at a time: nothing but the photo being copied is held in memory, however
// many photos there are (the server has a 256 MB heap and the photos can add up to a gigabyte).
// The photos are JPEGs already, so they are stored as they are; compressing them again would only cost CPU,
// of which the free plan has a tenth. The zip's index is written at the very end: an archive cut short by a
// broken connection or a database error is reported as damaged by zip programs, instead of opening with photos
// missing.
fun writeBackup(
    out: OutputStream,
    cats: List<Cat>,
    dogs: List<Dog>,
    photoNames: Set<String>,
    photos: PhotoStore,
    today: LocalDate,
    content: SiteContent = SiteContent.EMPTY,
    sections: List<Section> = emptyList(),
    sectionItems: List<SectionItem> = emptyList()
): BackupReport {
    val zip = ZipOutputStream(out)
    zip.putText("cats.json", exportJson.encodeToString(ListSerializer(Cat.serializer()), cats))
    zip.putText("dogs.json", exportJson.encodeToString(ListSerializer(Dog.serializer()), dogs))
    zip.putText(SITE_CONTENT_FILE_NAME, siteContentToJson(content))
    zip.putText(SECTIONS_FILE_NAME, sectionsToJson(sections))
    zip.putText(SECTION_ITEMS_FILE_NAME, sectionItemsToJson(sectionItems))

    var copied = 0
    zip.setLevel(Deflater.NO_COMPRESSION)
    photoNames.filterNot(::isSiteAsset).sorted().forEach { name ->
        // Deleted since the list was made: nothing to copy.
        val photo = photos.get(name) ?: return@forEach
        zip.putNextEntry(ZipEntry("images/$name"))
        zip.write(photo.bytes)
        zip.closeEntry()
        copied++
    }
    zip.setLevel(Deflater.DEFAULT_COMPRESSION)

    // A record that points at a picture the store does not have: the backup is still made, but says so. The
    // photos of the pages are one picture each (no card copy).
    val pagePhotos = content.entries.filterKeys { it.key.startsWith("image.") }.values
    val wanted = (cats.flatMap { it.photoUrls } + dogs.flatMap { it.photoUrls } + sectionItems.flatMap { it.photoUrls })
        .flatMap { listOf(it, cardPhotoUrl(it)) }
        .plus(pagePhotos)
        .filter { it.startsWith(IMAGES_PREFIX) }
        .map { it.removePrefix(IMAGES_PREFIX) }
        .filterNot(::isSiteAsset)
        .distinct()
    val missing = wanted.filterNot { it in photoNames }.sorted()

    val report = BackupReport(cats.size, dogs.size, copied, missing, content.entries.size, sections.size, sectionItems.size)
    zip.putText("README.txt", readme(report, today))
    zip.finish()
    zip.flush()
    return report
}

private fun ZipOutputStream.putText(name: String, text: String) {
    putNextEntry(ZipEntry(name))
    write(text.toByteArray(Charsets.UTF_8))
    closeEntry()
}

// For whoever opens the archive in a year: the volunteers, or someone who was never told how the site works.
private fun readme(report: BackupReport, today: LocalDate): String = buildString {
    appendLine("Резервная копия сайта Pro Anima от $today.")
    appendLine()
    appendLine("Что внутри:")
    appendLine("  cats.json, dogs.json — кошки и собаки (тексты на трёх языках). Черновики тоже здесь: у них \"published\": false.")
    appendLine("  site-content.json — тексты страниц (главная, гайд, визиты, «Как помочь»), которые меняли в админке: только изменённые,")
    appendLine("    остальные такие, как в коде сайта. Гайд, если его правили, лежит там целиком.")
    appendLine("  sections.json, section-items.json — разделы сайта (статьи, архив визитов и другие): их настройки, поля, метки и записи")
    appendLine("    на трёх языках. Черновики тоже здесь: у них \"published\": false.")
    appendLine("  images/ — фото кошек и собак и записей разделов (оригинал и уменьшенная копия для карточки, имя с -card) и фото страниц,")
    appendLine("    которые загружали в админке (имена page-...).")
    appendLine()
    appendLine("Кошек: ${report.cats}. Собак: ${report.dogs}. Изменённых текстов страниц: ${report.pageTexts}. Файлов в images/: ${report.photos}.")
    appendLine("Разделов: ${report.sections}, записей в них: ${report.sectionItems}.")
    if (report.missingPhotos.isNotEmpty()) {
        appendLine()
        appendLine("ВНИМАНИЕ: записи ссылаются на эти файлы, но в базе их не нашлось, в копию они не попали:")
        report.missingPhotos.forEach { appendLine("  $it") }
    }
    appendLine()
    appendLine("Исходные фото страниц и картинки-заглушки (home-*, default-*) в копию не входят: они лежат в репозитории сайта.")
    appendLine()
    appendLine("Как восстановить:")
    appendLine("  Положите все json-файлы (cats, dogs, site-content, sections, section-items) в папку data/ репозитория сайта,")
    appendLine("  а содержимое images/ — в data/images/.")
    appendLine("  Сайт без настройки DATABASE_URL работает прямо с этих файлов. А если база пустая (новая), при первом запуске")
    appendLine("  сайт сам копирует эти файлы в неё.")
}
