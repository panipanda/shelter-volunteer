package org.proanima.shelter.repository

import org.proanima.shelter.model.Cat

interface CatRepository {
    fun findAll(): List<Cat>

    fun findById(id: Int): Cat?
}

// Pages only read; the admin panel is the one that writes. A cat with id 0 is new: the database gives it
// the real id, and save returns the cat with that id.
interface MutableCatRepository : CatRepository {
    fun save(cat: Cat): Cat

    fun delete(id: Int): Boolean
}
