package org.proanima.shelter.repository

import org.h2.jdbcx.JdbcDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

private val counter = AtomicInteger()

// A fresh in-memory H2 that speaks the PostgreSQL dialect, with the real schema on it. Every call gives
// an empty database of its own, so tests do not see each other's rows.
fun newTestDataSource(): DataSource {
    val dataSource = JdbcDataSource()
    dataSource.setURL("jdbc:h2:mem:shelter-test-${counter.incrementAndGet()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    createSchema(dataSource)
    return dataSource
}

// A clock the test moves by hand, to check the cache lifetime without waiting.
class TestClock(var now: Instant = Instant.parse("2026-11-08T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
