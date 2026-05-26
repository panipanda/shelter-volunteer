package org.proanima.shelter.service

import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import org.proanima.shelter.model.VolunteerVisit
import org.proanima.shelter.repository.VisitRepository

class VisitService(private val repository: VisitRepository) {

    fun getAllVisits(): List<VolunteerVisit> {
        return repository.findAll()
    }

    fun getArchivedVisits(): List<VolunteerVisit> {
        return repository.findAll().filter { it.status == VisitStatus.COMPLETED }
    }

    fun getCatVisits(): List<VolunteerVisit> {
        return repository.findAll().filter { it.direction == VolunteerDirection.CATS }
    }

    fun getUpcomingVisits(): List<VolunteerVisit> {
        return repository.findAll().filter { it.status != VisitStatus.COMPLETED }
    }

    fun getVisitById(id: Int): VolunteerVisit? {
        return repository.findById(id)
    }   
}