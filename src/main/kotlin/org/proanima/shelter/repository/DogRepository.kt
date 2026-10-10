package org.proanima.shelter.repository

import org.proanima.shelter.model.Dog

interface DogRepository {
    fun findAll(): List<Dog>

    fun findBySlug(slug: String): Dog?
}
