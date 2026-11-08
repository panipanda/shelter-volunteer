package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class Cat(
    val id: Int,
    val name: LocalizedText?,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val description: LocalizedText,
    val medicalStatus: LocalizedText? = null,
    val photoUrls: List<String> = emptyList(),
    val location: CatLocation,
    val adoptionStage: AdoptionStage,
    val adoptionInstruction: LocalizedText? = null,
    val createdAt: String,
    val updatedAt: String,
    // A draft is seen only in the admin panel: the public pages leave it out. Older records have no such
    // field and are published.
    val published: Boolean = true
)