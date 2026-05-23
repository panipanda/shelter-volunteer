package org.proanima.shelter.repository

import kotlinx.serialization.json.Json
import org.proanima.shelter.model.VolunteerVisit
import java.io.File

class JsonVisitRepository(
    private val filePath: String = "data/visits.json"
) : VisitRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun findAll(): List<VolunteerVisit> {
        val fileContent = File(filePath).readText()
        return json.decodeFromString<List<VolunteerVisit>>(fileContent)
    }

    override fun findById(id: Int): VolunteerVisit? {
        return findAll().firstOrNull { it.id == id }
    }
}