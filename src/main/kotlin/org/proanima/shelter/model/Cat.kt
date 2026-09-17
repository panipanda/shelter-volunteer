package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class Cat(
    val id: Int,
    val name: String?,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val description: LocalizedText,
    val photoUrl: String?,
    val location: CatLocation,
    val adoptionStage: AdoptionStage,
    val adoptionInstruction: LocalizedText? = null,
    val createdAt: String,
    val updatedAt: String
)