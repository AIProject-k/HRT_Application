package com.hormonelog.app.analysis

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.settings
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.core.data.CsvIo
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.modelengine.CalibrationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A backup or a CSV can come from anywhere, and every screen calculates with whatever was loaded. Dates and
 * non-numbers are stopped at the file boundary (core:data, RecordLimits); this checks the two things that are
 * not: the calculations stay finite and quick on absurd numbers and absurd plans, and a CSV with absurd dates
 * never gets a record as far as the calculations.
 */
class HostileRecordsTest {
    private val now = at(2026, 10, 7, 12)
    private val fmt = Fmt(SEOUL, false, now)

    private val ordinary: List<DoseEvent> = (0 until 10).map { week -> dose(now.minus(Duration.ofDays(7L * week + 1))) }

    private fun dose(at: Instant, amount: Double = 5.0, normalized: Double? = amount) = DoseEvent(
        id = UUID.randomUUID(), occurredAt = at, sourceZoneId = SEOUL.id, drug = Drug.ESTRADIOL_VALERATE, route = Route.IM_INJECTION,
        amountEntered = amount, enteredUnit = DoseUnit.MG, normalizedMilligrams = normalized, status = DoseStatus.ADMINISTERED, source = RecordSource.MANUAL,
    )

    private fun lab(at: Instant?, reported: Double = 150.0, canonical: Double? = reported) = LabResult(
        id = UUID.randomUUID(), collectedAt = at, sourceZoneId = SEOUL.id, assay = Assay.UNKNOWN,
        analytes = listOf(LabAnalyteValue(Analyte.ESTRADIOL, reported, "pg/mL", canonical)), source = RecordSource.MANUAL,
    )

    private fun plan(everyDays: Int = 7, weekdays: Int = 0, amount: Double = 5.0) = Regimen(
        id = UUID.randomUUID(), drug = Drug.ESTRADIOL_VALERATE, route = Route.IM_INJECTION, amountEntered = amount, enteredUnit = DoseUnit.MG,
        everyDays = everyDays, startAt = now.minus(Duration.ofDays(70)), endAt = null, weekdays = weekdays,
    )

    /** Every calculation the screens make from the records; returns what went wrong, if anything. */
    private fun problemsWith(snapshot: RecordSnapshot): List<String> {
        val problems = ArrayList<String>()
        val state = AppState(settings = settings, doses = snapshot.doses, labs = snapshot.labs, regimens = snapshot.regimens)
        fun step(name: String, block: () -> Unit) {
            val started = System.nanoTime()
            try {
                block()
            } catch (t: Throwable) {
                problems += "$name → ${t::class.simpleName}: ${t.message}"
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            if (ms > 3_000) problems += "$name took $ms ms"
        }
        var calibration: CalibrationResult? = null
        step("calibrate") { calibration = Analysis.calibrate(state.doses, state.labs, GonadalStatus.INTACT) }
        val cal = calibration ?: return problems
        val from = now.minus(Duration.ofDays(90))
        val to = now.plus(Duration.ofDays(30))
        step("curves") {
            val curves = Analysis.curves(state.doses, state.regimens, state.labs, GonadalStatus.INTACT, now, from, to, SEOUL, cal)
            chartModelFor(HormoneSeries.E2, curves, state.doses, state.labs, from, to, now, true)
        }
        step("e2 now") { Analysis.e2At(state.doses, state.regimens, cal, now, now, SEOUL) }
        step("cycle") { Analysis.cycle(state.doses, state.regimens, cal, now, SEOUL) }
        step("next dose") { HomeLogic.nextDose(state.regimens, state.doses, now, SEOUL) }
        step("timeline") { TimelineLogic.build(state, fmt, cal) }
        step("report") { ReportLogic.build(state, fmt) }
        return problems
    }

    @Test(timeout = 300_000)
    fun absurdNumbersAndAbsurdPlansDoNotBreakTheCalculations() {
        val cases = linkedMapOf(
            "dose NaN mg" to RecordSnapshot(doses = ordinary + dose(now, Double.NaN, Double.NaN)),
            "dose Infinity mg" to RecordSnapshot(doses = ordinary + dose(now, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)),
            "dose 1e308 mg" to RecordSnapshot(doses = ordinary + dose(now, 1e308, 1e308)),
            "dose -5 mg" to RecordSnapshot(doses = ordinary + dose(now, -5.0, -5.0)),
            "lab NaN" to RecordSnapshot(doses = ordinary, labs = listOf(lab(now, Double.NaN, Double.NaN))),
            "lab Infinity" to RecordSnapshot(doses = ordinary, labs = listOf(lab(now, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))),
            "lab 1e308" to RecordSnapshot(doses = ordinary, labs = listOf(lab(now, 1e308, 1e308))),
            "lab negative" to RecordSnapshot(doses = ordinary, labs = listOf(lab(now, -40.0, -40.0))),
            "plan every 0 days" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(everyDays = 0))),
            "plan every -3 days" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(everyDays = -3))),
            "plan every Int.MAX days" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(everyDays = Int.MAX_VALUE))),
            "plan weekdays -1" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(weekdays = -1))),
            "plan amount NaN" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(amount = Double.NaN))),
            "plan amount 1e308" to RecordSnapshot(doses = ordinary, regimens = listOf(plan(amount = 1e308))),
        )
        val broke = cases.mapNotNull { (name, snapshot) -> problemsWith(snapshot).takeIf { it.isNotEmpty() }?.let { "$name: ${it.joinToString("; ")}" } }
        assertTrue("these broke:\n" + broke.joinToString("\n"), broke.isEmpty())
    }

    @Test(timeout = 300_000)
    fun aCsvRowWithAnAbsurdDateIsSkippedSoNoCalculationEverSeesIt() {
        val header = "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note,status,tt_unit,site,baseline"
        val absurd = listOf(
            "dose,0001-01-01,ev,im,5,mg",
            "dose,+999999999-12-31T00:00,ev,im,5,mg",
            "dose,9999-12-31,ev,im,5,mg",
            "lab,0001-01-01,,,,,150,,pg/mL",
            "lab,+999999999-12-31,,,,,150,,pg/mL",
        )
        for (row in absurd) {
            val parsed = CsvIo.parse("$header\n$row\n", SEOUL)
            assertEquals("row was loaded: $row", 0, parsed.doses.size + parsed.labs.size)
            assertEquals("row was not reported: $row", 1, parsed.skipped)
        }
    }

    @Test(timeout = 300_000)
    fun aCsvWithLargeButPossibleValuesIsSafe() {
        val header = "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note,status,tt_unit,site,baseline"
        val rows = listOf(
            "dose,2026-10-01,ev,im,1000,mg",
            "lab,2026-10-02,,,,,1e308,,pg/mL",
            "lab,2026-10-03,,,,,150,1e308,pg/mL,,,,ng/dL",
        )
        for (row in rows) {
            val parsed = CsvIo.parse("$header\n$row\n", SEOUL)
            val problems = problemsWith(RecordSnapshot(doses = ordinary + parsed.doses, labs = parsed.labs))
            assertTrue("$row broke: $problems", problems.isEmpty())
        }
    }
}
