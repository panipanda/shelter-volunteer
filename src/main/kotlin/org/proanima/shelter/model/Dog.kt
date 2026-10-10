package org.proanima.shelter.model

import kotlinx.serialization.Serializable

// Dogs have a card on the dogs page and their own profile page; there are no adoption stages for them yet.
@Serializable
data class Dog(
    val id: Int,
    val name: LocalizedText,
    val age: LocalizedText? = null,
    val description: LocalizedText,
    val photoUrls: List<String> = emptyList(),
    val location: CatLocation = CatLocation.IN_SHELTER
)
