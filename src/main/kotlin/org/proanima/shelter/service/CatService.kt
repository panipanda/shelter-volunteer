package org.proanima.shelter.service

import org.proanima.shelter.model.Cat

fun getAvailableCats(cats: List<Cat>): List<Cat> {
    return cats.filter { it.isAvailable }
}

fun findCatById(cats: List<Cat>, id: Int): Cat? {
    return cats.firstOrNull { it.id == id }
}