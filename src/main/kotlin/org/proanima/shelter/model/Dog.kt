package org.proanima.shelter.model

import kotlinx.serialization.Serializable

// Dogs are only shown as cards on the dogs page for now: no profile pages, no adoption stages.
@Serializable
data class Dog(
    val id: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    val photoUrls: List<String> = emptyList()
)
