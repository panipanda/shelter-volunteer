package org.proanima.shelter.service

import org.proanima.shelter.model.Cat
import org.proanima.shelter.repository.CatRepository

class CatService(private val repository: CatRepository) {

    fun getAllCats(): List<Cat> {
        return repository.findAll()
    }

    fun getAvailableCats(): List<Cat> {
        return repository.findAll().filter { it.isAvailable }
    }

    fun getCatById(id: Int): Cat? {
        return repository.findById(id)
    }
}