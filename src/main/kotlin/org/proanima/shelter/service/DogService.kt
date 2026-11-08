package org.proanima.shelter.service

import org.proanima.shelter.model.Dog
import org.proanima.shelter.repository.DogRepository

// What the public pages may show: drafts stay out of every answer here.
class DogService(private val repository: DogRepository) {

    fun getAllDogs(): List<Dog> {
        return published()
    }

    fun getDogBySlug(slug: String): Dog? {
        return published().firstOrNull { petSlug(it.name.en, it.id) == slug }
    }

    private fun published(): List<Dog> = repository.findAll().filter { it.published }
}
