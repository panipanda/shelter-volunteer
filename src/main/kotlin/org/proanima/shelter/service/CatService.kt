package org.proanima.shelter.service

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.repository.CatRepository

// What the public pages may show: drafts stay out of every answer here. The admin panel reads the
// repository directly, because it has to see them.
class CatService(private val repository: CatRepository) {

    fun getAllCats(): List<Cat> {
        return published()
    }

    fun getAvailableCats(): List<Cat> {
        return published().filter { it.adoptionStage == AdoptionStage.FOR_ADOPTION }
    }

    fun getCatById(id: Int): Cat? {
        return published().firstOrNull { it.id == id }
    }

    fun getCatBySlug(slug: String): Cat? {
        return published().firstOrNull { petSlug(it.name?.en, it.id) == slug }
    }

    private fun published(): List<Cat> = repository.findAll().filter { it.published }
}
