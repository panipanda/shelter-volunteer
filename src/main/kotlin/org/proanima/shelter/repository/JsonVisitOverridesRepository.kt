package org.proanima.shelter.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.proanima.shelter.service.NextVisit
import org.proanima.shelter.service.VisitOverrides
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class ExtraVisitFile(val date: String, val departure: String)

@Serializable
private data class VisitOverridesFile(
    val cancelled: List<String> = emptyList(),
    val extra: List<ExtraVisitFile> = emptyList()
)

// Optional file: without it the regular schedule applies. Dates are ISO (2026-10-24), times are 24-hour (09:00).
// A malformed file throws, so the mistake is seen at startup and not as a wrong date on the home page.
class JsonVisitOverridesRepository(
    private val filePath: String = "data/visit-overrides.json"
) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun load(): VisitOverrides {
        val file = File(filePath)
        if (!file.exists()) {
            return VisitOverrides()
        }

        val content = json.decodeFromString<VisitOverridesFile>(file.readText())
        return VisitOverrides(
            cancelled = content.cancelled.map(LocalDate::parse).toSet(),
            extra = content.extra.map { NextVisit(LocalDate.parse(it.date), LocalTime.parse(it.departure)) }
        )
    }
}
