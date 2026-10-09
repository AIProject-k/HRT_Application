package com.hormonelog.core.data

import java.time.Instant
import java.time.LocalDate

/**
 * What a record may hold. A backup or a CSV can come from anywhere and can say anything, and a value no real
 * log could hold — a dose in the year 292278994, an amount of "NaN" — breaks the calculations every screen
 * runs, again and again, for as long as the record is kept. Outside these limits a record is not loaded: from
 * a backup it is set aside whole (kept in the file and counted in the storage warning), from a CSV the row is
 * skipped and reported.
 */
internal object RecordLimits {
    private val EARLIEST: Instant = Instant.parse("1900-01-01T00:00:00Z")
    private val LATEST: Instant = Instant.parse("2200-01-01T00:00:00Z")

    fun isPlausible(instant: Instant): Boolean = instant >= EARLIEST && instant < LATEST

    fun isPlausible(date: LocalDate): Boolean = date >= LocalDate.of(1900, 1, 1) && date < LocalDate.of(2200, 1, 1)

    fun instant(value: Instant): Instant {
        require(isPlausible(value)) { "날짜가 범위를 벗어났어요" }
        return value
    }

    fun date(value: LocalDate): LocalDate {
        require(isPlausible(value)) { "날짜가 범위를 벗어났어요" }
        return value
    }

    /** A measured or entered quantity: a real, finite number that is not negative. */
    fun amount(value: Double): Double {
        require(value.isFinite() && value >= 0.0) { "수치가 올바르지 않아요" }
        return value
    }
}
