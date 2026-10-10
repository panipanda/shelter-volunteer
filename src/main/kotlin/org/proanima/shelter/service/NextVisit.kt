package org.proanima.shelter.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// Regular schedule of trips from Vracar. Until there is a chat sync, the "next visit"
// is computed from it rather than from data about specific visits.
private val DEPARTURES = listOf(
    DayOfWeek.WEDNESDAY to LocalTime.of(9, 0),
    DayOfWeek.SATURDAY to LocalTime.of(10, 0)
)

data class NextVisit(val date: LocalDate, val departure: LocalTime)

// The next trip is not earlier than now: if today's trip time has already passed, we take the next one.
fun nextScheduledVisit(now: LocalDateTime): NextVisit {
    return DEPARTURES
        .map { (day, time) ->
            val date = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
            NextVisit(date, time)
        }
        .map { visit -> if (visit.date.atTime(visit.departure) < now) visit.next() else visit }
        .minBy { it.date.atTime(it.departure) }
}

private fun NextVisit.next() = NextVisit(date.plusWeeks(1), departure)
