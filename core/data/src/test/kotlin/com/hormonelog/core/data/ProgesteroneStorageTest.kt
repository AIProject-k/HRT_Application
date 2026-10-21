package com.hormonelog.core.data

import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ProgesteroneStorageTest {
    private val at = Instant.parse("2026-10-17T00:00:00Z")
    private val dose = DoseEvent(UUID.randomUUID(), at, "UTC", Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0, DoseUnit.MG, 200.0, DoseStatus.ADMINISTERED)
    private val lab = LabResult(UUID.randomUUID(), at, "UTC", Assay.UNKNOWN, listOf(LabAnalyteValue(Analyte.PROGESTERONE, 31.8, "nmol/L", 10.0)))

    @Test fun csvRoundTripsOralP4AndReportedP4ValueWithItsUnit() {
        val text = CsvIo.export(listOf(dose), listOf(lab), ZoneId.of("UTC"))
        val restored = CsvIo.parse(text, ZoneId.of("UTC"))
        assertEquals(0, restored.skipped)
        assertEquals(dose.drug, restored.doses.single().drug)
        assertEquals(lab.analytes, restored.labs.single().analytes)
    }

    @Test fun oldCsvWithoutP4ColumnsStillLoadsAndMalformedP4CannotBePartiallyImported() {
        val old = "type,datetime,e2,tt\nlab,2026-10-17T00:00:00,100,20"
        assertEquals(2, CsvIo.parse(old).labs.single().analytes.size)
        val invalid = "type,datetime,e2,p4\nlab,2026-10-17T00:00:00,100,NaN"
        assertEquals(1, CsvIo.parse(invalid).skipped)
        assertTrue(CsvIo.parse(invalid).labs.isEmpty())
    }

    @Test fun backupAndJsonRoundTripNewEnumsWithoutLosingOlderRecords() {
        val snapshot = RecordSnapshot(doses = listOf(dose), labs = listOf(lab))
        val json = RecordJson.fromJson(RecordJson.toJson(snapshot))
        assertEquals(snapshot.doses, json.doses)
        assertEquals(snapshot.labs, json.labs)
        val backup = BackupCodec.open(BackupCodec.create(snapshot, AppSettings(), at.toEpochMilli())) as BackupCodec.Opened.Ok
        assertEquals(snapshot.labs, backup.snapshot.labs)
        assertEquals(snapshot.doses, backup.snapshot.doses)
    }
}
