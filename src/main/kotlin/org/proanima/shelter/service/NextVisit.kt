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

// Changes to the regular schedule that the coordinators announce in the chat: a cancelled trip is
// skipped, a moved or extra one is added (a move is a cancellation plus an extra trip).
data class VisitOverrides(
    val cancelled: Set<LocalDate> = emptySet(),
    val extra: List<NextVisit> = emptyList()
)

// The next trip is not earlier than now: if today's trip time has already passed, we take the next one.
fun nextScheduledVisit(now: LocalDateTime, overrides: VisitOverrides = VisitOverrides()): NextVisit {
    val regular = DEPARTURES.map { (day, time) ->
        generateSequence(NextVisit(now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)), time)) { it.next() }
            .first { it.date !in overrides.cancelled && !it.startsBefore(now) }
    }
    val extra = overrides.extra.filter { !it.startsBefore(now) }

    return (regular + extra).minBy { it.date.atTime(it.departure) }
}

private fun NextVisit.startsBefore(now: LocalDateTime) = date.atTime(departure) < now

private fun NextVisit.next() = NextVisit(date.plusWeeks(1), departure)
