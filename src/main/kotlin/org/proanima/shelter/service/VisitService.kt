package org.proanima.shelter.service

import org.proanima.shelter.model.VisitStatus
import org.proanima.shelter.model.VolunteerDirection
import org.proanima.shelter.model.VolunteerVisit

fun getCatVisits(visits: List<VolunteerVisit>): List<VolunteerVisit> {
    return visits.filter { it.direction == VolunteerDirection.CATS }
}

fun getArchivedVisits(visits: List<VolunteerVisit>): List<VolunteerVisit> {
    return visits.filter { it.status == VisitStatus.COMPLETED }
}

fun getUpcomingVisits(visits: List<VolunteerVisit>): List<VolunteerVisit> {
    return visits.filter { it.status != VisitStatus.COMPLETED }
}