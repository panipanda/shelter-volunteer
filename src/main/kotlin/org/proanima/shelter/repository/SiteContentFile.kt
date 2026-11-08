package org.proanima.shelter.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.proanima.shelter.service.ContentKey
import org.proanima.shelter.service.SiteContent
import java.io.File

// The edited texts of the pages as a file, for the backup (data/site-content.json in the layout the site can start
// from). Written sorted, so that two backups of the same site differ only where the site does.
@Serializable
private class SiteContentEntry(val key: String, val language: String, val text: String)

@Serializable
private class SiteContentFile(val texts: List<SiteContentEntry>)

private val fileJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

const val SITE_CONTENT_FILE_NAME = "site-content.json"

fun siteContentToJson(content: SiteContent): String {
    val entries = content.entries.entries
        .sortedWith(compareBy({ it.key.key }, { it.key.language }))
        .map { SiteContentEntry(it.key.key, it.key.language, it.value) }
    return fileJson.encodeToString(SiteContentFile.serializer(), SiteContentFile(entries))
}

// A missing file is not an error: a site that never had its pages edited has none. A broken one is, and it stops
// the first start rather than leaving the pages quietly without the texts that were backed up.
fun readSiteContentFile(file: File): Map<ContentKey, String> {
    if (!file.exists()) {
        return emptyMap()
    }
    val parsed = fileJson.decodeFromString(SiteContentFile.serializer(), file.readText())
    return parsed.texts.associate { ContentKey(it.key, it.language) to it.text }
}
