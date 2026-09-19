package org.proanima.shelter.service

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// Регулярное расписание выездов из Врачара. Пока нет синхронизации с чатом, «ближайший визит»
// считаем по нему, а не по данным о конкретных визитах.
private val DEPARTURES = listOf(
    DayOfWeek.WEDNESDAY to LocalTime.of(9, 0),
    DayOfWeek.SATURDAY to LocalTime.of(10, 0)
)

data class NextVisit(val date: LocalDate, val departure: LocalTime)

// Ближайший выезд — не раньше now: если время сегодняшнего выезда уже прошло, берём следующий.
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
