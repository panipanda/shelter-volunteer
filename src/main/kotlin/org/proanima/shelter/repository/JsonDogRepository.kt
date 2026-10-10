package org.proanima.shelter.repository

import kotlinx.serialization.json.Json
import org.proanima.shelter.model.Dog
import java.io.File

class JsonDogRepository(
    private val filePath: String = "data/dogs.json"
) : DogRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override fun findAll(): List<Dog> {
        return json.decodeFromString<List<Dog>>(File(filePath).readText())
    }
}
