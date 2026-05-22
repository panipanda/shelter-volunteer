package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
data class VolunteerVisit(
    val id: Int,
    val direction: VolunteerDirection,
    val date: String,
    val time: String,
    val timezone: String,
    val title: String,
    val capacity: Int?,
    val freePlaces: Int?,
    val status: VisitStatus,
    val signupInstruction: String?,
    val publicSummary: String?,
    val lastUpdatedAt: String,
    val createdAt: String,
    val updatedAt: String
)