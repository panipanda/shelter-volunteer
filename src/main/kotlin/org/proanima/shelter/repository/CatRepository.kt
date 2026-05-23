package org.proanima.shelter.repository

import org.proanima.shelter.model.Cat

interface CatRepository {
    fun findAll(): List<Cat>

    fun findById(id: Int): Cat?
}