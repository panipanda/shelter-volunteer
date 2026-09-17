package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class Cat(
    val id: Int,
    val name: String?,
    val age: Int?,
    val description: LocalizedText,
    val photoUrl: String?,
    val location: CatLocation,
    val adoptionStage: AdoptionStage,
    val createdAt: String,
    val updatedAt: String
)