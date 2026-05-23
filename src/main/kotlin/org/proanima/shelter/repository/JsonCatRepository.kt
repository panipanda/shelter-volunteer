package org.proanima.shelter.repository

import kotlinx.serialization.json.Json
import org.proanima.shelter.model.Cat
import java.io.File

class JsonCatRepository(
    private val filePath: String = "data/cats.json"
) : CatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun findAll(): List<Cat> {
        val fileContent = File(filePath).readText()
        return json.decodeFromString<List<Cat>>(fileContent)
    }

    override fun findById(id: Int): Cat? {
        return findAll().firstOrNull { it.id == id }
    }
}