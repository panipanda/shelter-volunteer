package org.proanima.shelter.service

import org.proanima.shelter.model.AdoptionStage
import org.proanima.shelter.model.Cat
import org.proanima.shelter.repository.CatRepository

class CatService(private val repository: CatRepository) {

    fun getAllCats(): List<Cat> {
        return repository.findAll()
    }

    fun getAvailableCats(): List<Cat> {
        return repository.findAll().filter { it.adoptionStage == AdoptionStage.FOR_ADOPTION }
    }

    fun getCatById(id: Int): Cat? {
        return repository.findById(id)
    }

    fun getCatBySlug(slug: String): Cat? {
        return repository.findAll().firstOrNull { catSlug(it.name, it.id) == slug }
    }
}