package org.proanima.shelter.service

import org.proanima.shelter.model.Dog
import org.proanima.shelter.repository.DogRepository

class DogService(private val repository: DogRepository) {

    fun getAllDogs(): List<Dog> {
        return repository.findAll()
    }

    fun getDogBySlug(slug: String): Dog? {
        return repository.findAll().firstOrNull { petSlug(it.name.en, it.id) == slug }
    }
}
