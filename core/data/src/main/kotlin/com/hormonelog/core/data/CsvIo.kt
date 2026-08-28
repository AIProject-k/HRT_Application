package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Route
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * CSV import/export for records. One flat table, header required:
 *
 * `type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note`
 *
 * `type` is `dose` or `lab`. `datetime` is `yyyy-MM-dd` or `yyyy-MM-dd'T'HH:mm`
 * (local). Unknown rows are skipped rather than failing the whole import.
 */
object CsvIo {

    private const val HEADER = "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note"

    data class Imported(
        val doses: List<DoseEvent>,
        val labs: List<LabResult>,
        val skipped: Int,
    )

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Imported {
        val doses = ArrayList<DoseEvent>()
        val labs = ArrayList<LabResult>()
        var skipped = 0
        val lines = text.split(Regex("\r?\n")).filter { it.isNotBlank() }
        for ((i, line) in lines.withIndex()) {
            if (i == 0 && line.trimStart().startsWith("type", ignoreCase = true)) continue
            val f = splitCsv(line)
            if (f.size < 2) { skipped++; continue }
            val at = parseInstant(f.getOrEmpty(1), zone)
            try {
                when (f.getOrEmpty(0).lowercase()) {
                    "dose" -> {
                        val drug = parseDrug(f.getOrEmpty(2))
                        val route = parseRoute(f.getOrEmpty(3))
                        val amount = f.getOrEmpty(4).toDoubleOrNull()
                        if (drug == null || route == null || amount == null || at == null) { skipped++; continue }
                        val unit = parseUnit(f.getOrEmpty(5))
                        doses += DoseEvent(
                            id = UUID.randomUUID(),
                            occurredAt = at,
                            sourceZoneId = zone.id,
                            drug = drug,
                            route = route,
                            amountEntered = amount,
                            enteredUnit = unit,
                            normalizedMilligrams = DoseEvent.normalizeMilligrams(amount, unit),
                            status = DoseStatus.ADMINISTERED,
                            note = f.getOrEmpty(10).ifBlank { null },
                        )
                    }
                    "lab" -> {
                        val e2 = f.getOrEmpty(6).toDoubleOrNull()
                        val tt = f.getOrEmpty(7).toDoubleOrNull()
                        if ((e2 == null && tt == null) || at == null) { skipped++; continue }
                        val e2Unit = f.getOrEmpty(8).ifBlank { LabAnalyteValue.E2_CANONICAL_UNIT }
                        val analytes = buildList {
                            if (e2 != null) add(LabAnalyteValue(Analyte.ESTRADIOL, e2, e2Unit, LabAnalyteValue.canonical(Analyte.ESTRADIOL, e2, e2Unit)))
                            if (tt != null) add(
                                LabAnalyteValue(
                                    Analyte.TOTAL_TESTOSTERONE, tt, LabAnalyteValue.TT_CANONICAL_UNIT,
                                    LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, tt, LabAnalyteValue.TT_CANONICAL_UNIT),
                                ),
                            )
                        }
                        labs += LabResult(
                            id = UUID.randomUUID(),
                            collectedAt = at,
                            sourceZoneId = zone.id,
                            assay = parseAssay(f.getOrEmpty(9)),
                            analytes = analytes,
                            note = f.getOrEmpty(10).ifBlank { null },
                        )
                    }
                    else -> skipped++
                }
            } catch (_: Exception) {
                skipped++
            }
        }
        return Imported(doses, labs, skipped)
    }

    fun export(doses: List<DoseEvent>, labs: List<LabResult>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder(HEADER).append('\n')
        for (d in doses.sortedBy { it.occurredAt }) {
            sb.append("dose,")
                .append(d.occurredAt.atZone(zone).toLocalDateTime()).append(',')
                .append(drugKey(d.drug)).append(',')
                .append(routeKey(d.route)).append(',')
                .append(trimNum(d.amountEntered)).append(',')
                .append(unitKey(d.enteredUnit)).append(",,,,,")
                .append(csvField(d.note ?: "")).append('\n')
        }
        for (l in labs.sortedBy { it.collectedAt ?: Instant.MIN }) {
            val e2 = l.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
            val tt = l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }
            sb.append("lab,")
                .append(l.collectedAt?.atZone(zone)?.toLocalDateTime() ?: "").append(",,,,,")
                .append(e2?.reportedValue?.let(::trimNum) ?: "").append(',')
                .append(tt?.reportedValue?.let(::trimNum) ?: "").append(',')
                .append(e2?.reportedUnit ?: "").append(',')
                .append(assayKey(l.assay)).append(',')
                .append(csvField(l.note ?: "")).append('\n')
        }
        return sb.toString()
    }

    // ── helpers ──────────────────────────────────────────────
    private fun List<String>.getOrEmpty(i: Int) = getOrNull(i)?.trim() ?: ""

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { out.add(cur.toString()); cur.setLength(0) }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun csvField(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun trimNum(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    private fun parseInstant(s: String, zone: ZoneId): Instant? {
        val t = s.trim()
        return try {
            when {
                t.isEmpty() -> null
                t.contains('T') -> LocalDateTime.parse(t).atZone(zone).toInstant()
                t.length == 10 -> LocalDate.parse(t).atTime(9, 0).atZone(zone).toInstant()
                else -> null
            }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseDrug(s: String): Drug? = when (s.trim().lowercase().replace(" ", "_")) {
        "estradiol_valerate", "ev", "발레레이트", "에스트라디올_발레레이트" -> Drug.ESTRADIOL_VALERATE
        "estradiol_tablet", "tablet", "정제" -> Drug.ESTRADIOL_TABLET
        "estradiol_patch", "e2_patch" -> Drug.ESTRADIOL_PATCH
        "spironolactone", "spiro", "스피로노락톤" -> Drug.SPIRONOLACTONE
        "cyproterone", "cpa", "androcur", "안드로쿨", "사이프로테론" -> Drug.CYPROTERONE
        else -> null
    }

    private fun parseRoute(s: String): Route? = when (s.trim().lowercase()) {
        "oral", "po", "경구" -> Route.ORAL
        "sublingual", "sl", "설하" -> Route.SUBLINGUAL
        "im", "im_injection", "근육주사" -> Route.IM_INJECTION
        "sc", "subq", "sc_injection", "피하주사" -> Route.SC_INJECTION
        "patch", "패치" -> Route.PATCH
        "gel", "젤" -> Route.GEL
        else -> null
    }

    private fun parseUnit(s: String): DoseUnit = when (s.trim().lowercase()) {
        "mg_per_day", "mg/day", "mg/일" -> DoseUnit.MG_PER_DAY
        "patch", "매" -> DoseUnit.PATCH
        else -> DoseUnit.MG
    }

    private fun parseAssay(s: String): Assay = when (s.trim().lowercase().replace("-", "_").replace("/", "_")) {
        "lc_ms_ms", "lcmsms", "lc_ms", "질량분석" -> Assay.LC_MS_MS
        "immunoassay", "eclia", "clia", "면역측정" -> Assay.IMMUNOASSAY
        else -> Assay.UNKNOWN
    }

    private fun drugKey(d: Drug) = when (d) {
        Drug.ESTRADIOL_VALERATE -> "estradiol_valerate"
        Drug.ESTRADIOL_TABLET -> "estradiol_tablet"
        Drug.ESTRADIOL_PATCH -> "estradiol_patch"
        Drug.SPIRONOLACTONE -> "spironolactone"
        Drug.CYPROTERONE -> "cyproterone"
    }

    private fun routeKey(r: Route) = when (r) {
        Route.ORAL -> "oral"; Route.SUBLINGUAL -> "sublingual"; Route.IM_INJECTION -> "im"
        Route.SC_INJECTION -> "sc"; Route.PATCH -> "patch"; Route.GEL -> "gel"
    }

    private fun unitKey(u: DoseUnit) = when (u) {
        DoseUnit.MG -> "mg"; DoseUnit.MG_PER_DAY -> "mg_per_day"; DoseUnit.PATCH -> "patch"
    }

    private fun assayKey(a: Assay) = when (a) {
        Assay.LC_MS_MS -> "lc_ms_ms"; Assay.IMMUNOASSAY -> "immunoassay"; Assay.UNKNOWN -> "unknown"
    }
}
