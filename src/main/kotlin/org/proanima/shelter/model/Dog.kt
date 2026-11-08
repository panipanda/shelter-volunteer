package org.proanima.shelter.model

import kotlinx.serialization.Serializable

// A dog has the same facts as a cat: age, health, where it lives, the adoption stage and how to take it. They are
// optional and a page shows only what is filled in, so a record that has none of them (every dog saved before they
// existed) reads and looks as before.
@Serializable
data class Dog(
    val id: Int,
    val name: LocalizedText,
    // The age is counted from the birth year (and month, when known) like a cat's. `age` is the same thing in words
    // ("About 3 years") for a dog whose birth year nobody knows; the birth year wins when both are given.
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val age: LocalizedText? = null,
    val description: LocalizedText,
    val medicalStatus: LocalizedText? = null,
    val photoUrls: List<String> = emptyList(),
    val location: CatLocation = CatLocation.IN_SHELTER,
    val adoptionStage: AdoptionStage = AdoptionStage.NONE,
    val adoptionInstruction: LocalizedText? = null,
    // A draft is seen only in the admin panel: the public pages leave it out.
    val published: Boolean = true
)
