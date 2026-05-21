package org.proanima.shelter.model

data class Cat(
    val id: Int,
    val name: String?,
    val age: Int?,
    val description: String,
    val photoUrl: String?,
    val isAvailable: Boolean,
    val createdAt: String,
    val updatedAt: String
)