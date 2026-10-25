package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.TimelineType
import com.hormonelog.app.state.TimelineUi
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.modelengine.CalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class NoteOnlyTimelineTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-10-24T14:00:00Z")
    private val note = JournalEntry(id = UUID.randomUUID(), at = now, sourceZoneId = "Asia/Seoul", note = "머리가 아파요")

    private fun rowsFor(type: TimelineType): List<TimelineRow> {
        val s = AppState(journal = listOf(note), timeline = TimelineUi(type = type))
        val calibration = CalibrationResult(labs = emptyMap(), groups = emptyMap(), tt = null)
        return TimelineLogic.build(s, Fmt(seoul, clock24 = true, now = now), calibration).months.flatMap { it.days }.flatMap { it.rows }
    }

    @Test fun noteOnlyEntryIsAMemoRowNotAConditionScore() {
        val row = rowsFor(TimelineType.ALL).single()
        assertEquals(RowKind.MEMO, row.kind)
        assertEquals("메모", row.title)
    }

    @Test fun memoFilterFindsIt() {
        assertEquals(1, rowsFor(TimelineType.MEMO).size)
    }

    @Test fun conditionFilterDoesNotShowIt() {
        assertEquals(0, rowsFor(TimelineType.CONDITION).size)
    }
}
