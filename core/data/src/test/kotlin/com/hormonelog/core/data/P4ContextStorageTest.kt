package com.hormonelog.core.data

import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class P4ContextStorageTest {
    private val at = Instant.parse("2026-10-19T03:00:00Z")
    private val context = P4Context(P4Product.UTROGESTAN_SOFT_CAPSULE, P4Meal.FASTING)
    private val dose = DoseEvent(UUID.randomUUID(), at, "UTC", Drug.PROGESTERONE_MICRONIZED,
        Route.ORAL, 200.0, DoseUnit.MG, 200.0, DoseStatus.ADMINISTERED, p4Context = context)
    private val plan = Regimen(UUID.randomUUID(), dose.drug, dose.route, 200.0, DoseUnit.MG, 1, at, null, p4Context = context)

    @Test fun recordsAndFullBackupKeepTheProductAndMeal() {
        val original = RecordSnapshot(doses = listOf(dose), regimens = listOf(plan))
        val saved = RecordJson.fromJson(RecordJson.toJson(original))
        assertEquals(context, saved.doses.single().p4Context)
        assertEquals(context, saved.regimens.single().p4Context)
        val restored = BackupCodec.open(BackupCodec.create(original, AppSettings(), at.toEpochMilli())) as BackupCodec.Opened.Ok
        assertEquals(context, restored.snapshot.doses.single().p4Context)
    }

    @Test fun oldRecordsDoNotGainAnAssumedProductOrMeal() {
        val original = RecordSnapshot(doses = listOf(dose.copy(p4Context = null)))
        val saved = RecordJson.fromJson(RecordJson.toJson(original))
        assertNull(saved.doses.single().p4Context)
    }

    @Test fun csvRetainsContextAndAnOlderCsvHasUnknownContext() {
        val text = CsvIo.export(listOf(dose), emptyList(), ZoneId.of("UTC"))
        assertEquals(context, CsvIo.parse(text, ZoneId.of("UTC")).doses.single().p4Context)
        val old = "type,datetime,drug,route,amount,unit\ndose,2026-10-19T03:00,p4,oral,200,mg"
        assertNull(CsvIo.parse(old).doses.single().p4Context)
    }

    @Test fun backfilledDosesDoNotClaimAPlannedMealActuallyHappened() {
        val actual = Regimen.expand(plan, at.plusSeconds(86400), ZoneId.of("UTC")).first()
        assertEquals(P4Product.UTROGESTAN_SOFT_CAPSULE, actual.p4Context!!.product)
        assertEquals(P4Meal.UNKNOWN, actual.p4Context!!.meal)
    }
}
