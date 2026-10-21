package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.*
import com.hormonelog.core.evidence.EvidenceBundleV1
import java.io.File
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in desktop JVM measurement, never a clinical accuracy or Android performance test. */
class TtResolutionBenchmarkTest {
    private val bundle = EvidenceBundleV1.bundle
    private val start = Instant.parse("2026-07-01T09:07:00Z")
    private data class Scenario(val name: String, val doses: List<DoseEvent>, val from: Instant, val to: Instant, val referenceMinutes: Int)
    private fun dose(route: Route, drug: Drug, mg: Double, seconds: Long) = DoseEvent(
        UUID.randomUUID(), start.plusSeconds(seconds), "UTC", drug, route,
        mg, DoseUnit.MG, mg, DoseStatus.ADMINISTERED,
    )
    private fun tablets(route: Route, days: Int, perDay: Int = 1) = (0 until days * perDay).map {
        dose(route, Drug.ESTRADIOL_TABLET, 2.0, it * 86400L / perDay)
    }
    private fun points(doses: List<DoseEvent>, from: Instant, to: Instant, step: Int) =
        (TtSuppressionEngine(bundle, step).curve(doses, from, to, samples = 96) as EstimateResult.Available).series.points
    private fun exposure(s: Scenario, step: Int): List<EstimatePoint> {
        val samples = ((s.to.epochSecond - s.from.epochSecond) / (step * 60)).toInt()
        return (E2CurveEngine(bundle).curve(s.doses, s.from, s.to, samples = samples) as EstimateResult.Available).series.points
    }
    private fun auc(points: List<EstimatePoint>) = points.zipWithNext().sumOf { (a, b) ->
        (a.median + b.median) / 2 * (b.at.toEpochMilli() - a.at.toEpochMilli()) / 3_600_000.0
    }

    @Test fun writeResolutionAndSharedExposureReports() {
        assumeTrue(System.getenv("HORMONELOG_MODEL_BENCHMARK") == "1")
        val out = File(System.getenv("HORMONELOG_MODEL_REPORT_DIR") ?: "build/reports/model-validation").apply { mkdirs() }
        val from = start.plusSeconds(19 * 86400L - 60)
        val to = from.plusSeconds(86400)
        val sl = tablets(Route.SUBLINGUAL, 20, 2)
        val cpa = (0..19).map { dose(Route.ORAL, Drug.CYPROTERONE, 10.0, it * 86400L) }
        val cases = listOf(
            Scenario("oral_daily_off_grid", tablets(Route.ORAL, 20), from, to, 1),
            Scenario("sublingual_daily_off_grid", tablets(Route.SUBLINGUAL, 20), from, to, 1),
            Scenario("sublingual_twice_daily_off_grid", sl, from, to, 1),
            Scenario("sublingual_with_cpa", sl + cpa, from, to, 1),
            Scenario("weekly_injection", (0..4).map { dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 4.0, it * 7 * 86400L) }, from, to, 1),
            Scenario("one_year_sublingual_history", tablets(Route.SUBLINGUAL, 365, 2), start, start.plusSeconds(365 * 86400L), 5),
        )
        val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        fun allocated() = if (bean?.isThreadAllocatedMemoryEnabled == true) bean.getThreadAllocatedBytes(Thread.currentThread().id) else -1L
        File(out, "resolution-comparison.csv").printWriter().use { csv ->
            csv.println("scenario,step_minutes,reference_minutes,max_abs_t_ng_dl,mae_t_ng_dl,rmse_t_ng_dl,max_relative_t_percent,e2_auc_pg_h_ml,e2_auc_error_percent,t_compute_ms,thread_allocated_bytes,dose_count,output_points")
            for (s in cases) {
                val reference = points(s.doses, s.from, s.to, s.referenceMinutes)
                val referenceAuc = auc(exposure(s, s.referenceMinutes))
                for (step in listOf(60, 15, 5)) {
                    val before = allocated()
                    val t0 = System.nanoTime()
                    val actual = points(s.doses, s.from, s.to, step)
                    val millis = (System.nanoTime() - t0) / 1e6
                    val bytes = allocated().let { if (before >= 0 && it >= 0) it - before else -1 }
                    val errors = actual.zip(reference).map { (a, b) -> a.median - b.median }
                    val relative = actual.zip(reference).maxOf { (a, b) -> abs(a.median-b.median) / b.median * 100 }
                    val area = auc(exposure(s, step))
                    csv.println(listOf(s.name, step, s.referenceMinutes, errors.maxOf { abs(it) }, errors.map { abs(it) }.average(), sqrt(errors.map { it*it }.average()), relative, area, (area/referenceAuc-1)*100, millis, bytes, s.doses.size, actual.size).joinToString(","))
                    csv.flush()
                }
            }
        }
        // Use actual Kotlin E2/CPA exposure and current runtime T, not a Python reimplementation.
        val exportFrom = start.minusSeconds(86400)
        val exportTo = start.plusSeconds(20 * 86400L)
        val samples = ((exportTo.epochSecond-exportFrom.epochSecond) / 300).toInt()
        val history = sl + cpa
        val e2 = (E2CurveEngine(bundle).curve(history, exportFrom, exportTo, samples) as EstimateResult.Available).series.points
        val tt = (TtSuppressionEngine(bundle).curve(history, exportFrom, exportTo, samples = samples) as EstimateResult.Available).series.points
        val pd = bundle.pdParameters()
        val cpaModel = CpaModel(pd.getValue("cpaF"), pd.getValue("cpaKaPerDay"), pd.getValue("cpaKePerDay"))
        File(out, "shared-exposure.csv").printWriter().use { csv ->
            csv.println("day,e2_pg_ml,cpa_level,effect_compartment_tt_ng_dl,measured_tt_ng_dl,split,condition")
            e2.zip(tt).forEach { (e, t) ->
                val day = (e.at.toEpochMilli()-exportFrom.toEpochMilli())/86_400_000.0
                csv.println("$day,${e.median},${cpaModel.levelAt(history, e.at.toEpochMilli())},${t.median},,,sublingual_plus_cpa_synthetic")
            }
        }
        File(out, "measurement-notes.txt").writeText(
            "Synthetic numerical comparison only. Short scenarios reference 1 minute; one-year scenario references 5 minutes. " +
            "Single-run timings include JVM/JIT/GC effects. Thread allocated bytes are cumulative allocations, not peak or retained memory. " +
            "Desktop JVM performance is not Android performance. E2 AUC uses trapezoids; T errors compare identical output times. " +
            "No measured T values, fitted turnover rate, biological interval or clinical validation.\n"
        )
        println("Model validation reports: ${out.absolutePath}")
    }
}
