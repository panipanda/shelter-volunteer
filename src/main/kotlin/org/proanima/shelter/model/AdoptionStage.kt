package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
enum class AdoptionStage {
    NONE,
    FOR_ADOPTION,
    RESERVED,
    ADOPTED
}
