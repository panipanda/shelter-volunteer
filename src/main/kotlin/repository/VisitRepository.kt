package org.proanima.shelter.repository

import org.proanima.shelter.model.VolunteerVisit

interface VisitRepository {
    fun findAll(): List<VolunteerVisit>

    fun findById(id: Int): VolunteerVisit?
}