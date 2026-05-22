package org.proanima.shelter.model

import kotlinx.serialization.Serializable

@Serializable
enum class VisitStatus {
    OPEN,
    FULL,
    TENTATIVE,
    CANCELLED,
    COMPLETED
}