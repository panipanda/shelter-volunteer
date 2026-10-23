package org.proanima.shelter.service

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class NextVisitTest {

    // 2026-09-16 is a Wednesday, 2026-09-19 a Saturday.
    private fun at(dateTime: String, overrides: VisitOverrides = VisitOverrides()) =
        nextScheduledVisit(LocalDateTime.parse(dateTime), overrides)

    @Test
    fun `the regular schedule picks the nearest Wednesday or Saturday`() {
        assertEquals(NextVisit(LocalDate.of(2026, 9, 16), LocalTime.of(9, 0)), at("2026-09-14T10:00:00"))
        assertEquals(NextVisit(LocalDate.of(2026, 9, 19), LocalTime.of(10, 0)), at("2026-09-17T12:00:00"))
    }

    @Test
    fun `a trip that has already left is not the next one`() {
        assertEquals(LocalDate.of(2026, 9, 19), at("2026-09-16T09:01:00").date)
        assertEquals(LocalDate.of(2026, 9, 23), at("2026-09-19T10:01:00").date)
    }

    @Test
    fun `a cancelled trip is skipped`() {
        val overrides = VisitOverrides(cancelled = setOf(LocalDate.of(2026, 9, 16)))

        assertEquals(LocalDate.of(2026, 9, 19), at("2026-09-14T10:00:00", overrides).date)
    }

    @Test
    fun `cancelling several trips in a row moves on to the following week`() {
        val overrides = VisitOverrides(
            cancelled = setOf(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 23))
        )

        assertEquals(LocalDate.of(2026, 9, 26), at("2026-09-14T10:00:00", overrides).date)
    }

    @Test
    fun `an extra trip is chosen when it is earlier than the regular ones`() {
        val extra = NextVisit(LocalDate.of(2026, 9, 15), LocalTime.of(11, 0))

        assertEquals(extra, at("2026-09-14T10:00:00", VisitOverrides(extra = listOf(extra))))
    }

    @Test
    fun `an extra trip in the past is ignored`() {
        val extra = NextVisit(LocalDate.of(2026, 9, 13), LocalTime.of(11, 0))

        assertEquals(LocalDate.of(2026, 9, 16), at("2026-09-14T10:00:00", VisitOverrides(extra = listOf(extra))).date)
    }

    @Test
    fun `a moved trip is a cancellation plus an extra trip`() {
        val moved = NextVisit(LocalDate.of(2026, 9, 17), LocalTime.of(9, 0))
        val overrides = VisitOverrides(cancelled = setOf(LocalDate.of(2026, 9, 16)), extra = listOf(moved))

        assertEquals(moved, at("2026-09-14T10:00:00", overrides))
    }
}
