package org.proanima.shelter.repository

import org.proanima.shelter.model.Dog

interface DogRepository {
    fun findAll(): List<Dog>
}

// A dog with id 0 is new: the database gives it the real id, and save returns the dog with that id.
interface MutableDogRepository : DogRepository {
    fun save(dog: Dog): Dog

    fun delete(id: Int): Boolean
}
