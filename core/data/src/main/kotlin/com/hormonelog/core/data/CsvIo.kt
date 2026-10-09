package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.allowedRoutes
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

/** Why a CSV row was left out; the app turns these into plain sentences. */
enum class SkipReason {
    TOO_FEW_COLUMNS,
    UNKNOWN_TYPE,
    BAD_DATE,
    UNKNOWN_DRUG,
    UNKNOWN_ROUTE,
    BAD_AMOUNT,
    BAD_COMBINATION,
    NO_VALUE,
}

/** [line] is 1-based and counts the header, so it matches what a spreadsheet shows. */
data class SkippedRow(val line: Int, val reason: SkipReason)

/**
 * CSV import/export for records. One flat table, header required:
 *
 * `type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note,status,tt_unit,site,baseline`
 *
 * `type` is `dose` or `lab`. `datetime` is `yyyy-MM-dd` or `yyyy-MM-dd'T'HH:mm`
 * (local). The last four columns are optional, so files from older builds still load.
 * Rows that cannot be read are skipped and reported — never half-imported.
 */
object CsvIo {

    private const val HEADER = "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note,status,tt_unit,site,baseline"
    private val DEFAULT_COLUMNS = HEADER.split(',')

    /** An amount outside this cannot be a real dose in any unit the app records. */
    private const val AMOUNT_MAX = 1000.0

    data class Imported(
        val doses: List<DoseEvent>,
        val labs: List<LabResult>,
        val skippedRows: List<SkippedRow>,
    ) {
        val skipped: Int get() = skippedRows.size
    }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): Imported {
        val doses = ArrayList<DoseEvent>()
        val labs = ArrayList<LabResult>()
        val skipped = ArrayList<SkippedRow>()
        var columns = DEFAULT_COLUMNS
        for ((n, record) in splitRecords(text.removePrefix("﻿")).withIndex()) {
            val lineNo = record.first
            val f = record.second
            if (n == 0 && f.firstOrNull()?.trim()?.startsWith("type", ignoreCase = true) == true) {
                columns = f.map { it.trim().lowercase() }
                continue
            }
            if (f.size < 2) { skipped += SkippedRow(lineNo, SkipReason.TOO_FEW_COLUMNS); continue }
            fun col(name: String): String = columns.indexOf(name).let { if (it < 0) "" else f.getOrEmpty(it) }
            val at = parseInstant(col("datetime"), zone)
            try {
                when (col("type").lowercase()) {
                    "dose" -> {
                        val drug = parseDrug(col("drug"))
                        val route = parseRoute(col("route"))
                        val amount = col("amount").toDoubleOrNull()
                        val reason = when {
                            at == null -> SkipReason.BAD_DATE
                            drug == null -> SkipReason.UNKNOWN_DRUG
                            route == null -> SkipReason.UNKNOWN_ROUTE
                            amount == null || !amount.isFinite() || amount <= 0.0 || amount > AMOUNT_MAX -> SkipReason.BAD_AMOUNT
                            route !in drug.allowedRoutes -> SkipReason.BAD_COMBINATION
                            else -> null
                        }
                        if (reason != null) { skipped += SkippedRow(lineNo, reason); continue }
                        // A patch is rated in µg/day and nothing else is; a unit that does not fit the
                        // route is the file's mistake, not a different dose.
                        val unit = parseUnit(col("unit")).let {
                            when {
                                route == Route.PATCH && it == DoseUnit.MG -> DoseUnit.UG_PER_DAY
                                route != Route.PATCH && it != DoseUnit.MG -> DoseUnit.MG
                                else -> it
                            }
                        }
                        doses += DoseEvent(
                            id = UUID.randomUUID(),
                            occurredAt = at!!,
                            sourceZoneId = zone.id,
                            drug = drug!!,
                            route = route!!,
                            amountEntered = amount!!,
                            enteredUnit = unit,
                            normalizedMilligrams = DoseEvent.normalizeMilligrams(amount, unit),
                            status = parseStatus(col("status")),
                            note = restored(col("note")),
                            source = RecordSource.IMPORT,
                            site = parseSite(col("site")),
                        )
                    }
                    "lab" -> {
                        val e2 = col("e2").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                        val tt = col("tt").toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
                        val reason = when {
                            at == null -> SkipReason.BAD_DATE
                            e2 == null && tt == null -> SkipReason.NO_VALUE
                            else -> null
                        }
                        if (reason != null) { skipped += SkippedRow(lineNo, reason); continue }
                        val e2Unit = col("e2_unit").ifBlank { LabAnalyteValue.E2_CANONICAL_UNIT }
                        val ttUnit = col("tt_unit").ifBlank { LabAnalyteValue.TT_CANONICAL_UNIT }
                        val analytes = buildList {
                            if (e2 != null) add(LabAnalyteValue(Analyte.ESTRADIOL, e2, e2Unit, LabAnalyteValue.canonical(Analyte.ESTRADIOL, e2, e2Unit)))
                            if (tt != null) add(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, tt, ttUnit, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, tt, ttUnit)))
                        }
                        labs += LabResult(
                            id = UUID.randomUUID(),
                            collectedAt = at,
                            sourceZoneId = zone.id,
                            assay = parseAssay(col("assay")),
                            analytes = analytes,
                            note = restored(col("note")),
                            isBaseline = col("baseline").lowercase() in setOf("1", "true", "yes", "y"),
                            source = RecordSource.IMPORT,
                        )
                    }
                    else -> skipped += SkippedRow(lineNo, SkipReason.UNKNOWN_TYPE)
                }
            } catch (_: Exception) {
                skipped += SkippedRow(lineNo, SkipReason.TOO_FEW_COLUMNS)
            }
        }
        return Imported(doses, labs, skipped)
    }

    /**
     * Doses and labs only. Schedules, notes about clinics and calibration state are not
     * part of a CSV — a full backup carries those.
     */
    fun export(doses: List<DoseEvent>, labs: List<LabResult>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder("﻿").append(HEADER).append('\n')
        for (d in doses.sortedBy { it.occurredAt }) {
            row(
                sb,
                "dose", d.occurredAt.atZone(zone).toLocalDateTime().toString(), drugKey(d.drug), routeKey(d.route),
                trimNum(d.amountEntered), unitKey(d.enteredUnit), "", "", "", "", defused(d.note.orEmpty()),
                statusKey(d.status), "", d.site?.let(::siteKey).orEmpty(), "",
            )
        }
        for (l in labs.sortedBy { it.collectedAt ?: Instant.MIN }) {
            val e2 = l.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
            val tt = l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }
            row(
                sb,
                "lab", l.collectedAt?.atZone(zone)?.toLocalDateTime()?.toString().orEmpty(), "", "", "", "",
                e2?.reportedValue?.let(::trimNum).orEmpty(), tt?.reportedValue?.let(::trimNum).orEmpty(),
                e2?.reportedUnit.orEmpty(), assayKey(l.assay), defused(l.note.orEmpty()),
                "", tt?.reportedUnit.orEmpty(), "", if (l.isBaseline) "1" else "",
            )
        }
        return sb.toString()
    }

    private fun row(sb: StringBuilder, vararg fields: String) {
        sb.append(fields.joinToString(",") { csvField(it) }).append('\n')
    }

    // ── helpers ──────────────────────────────────────────────
    private fun List<String>.getOrEmpty(i: Int) = getOrNull(i)?.trim() ?: ""

    /**
     * Records with the 1-based line each one starts on. A quoted field may hold a line
     * break (the export writes a note like that), so records are split here, not by line.
     * Completely blank lines are dropped.
     */
    private fun splitRecords(text: String): List<Pair<Int, List<String>>> {
        val out = ArrayList<Pair<Int, List<String>>>()
        var fields = ArrayList<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var line = 1
        var startLine = 1
        var i = 0
        fun endRecord() {
            fields.add(cur.toString())
            cur.setLength(0)
            if (!(fields.size == 1 && fields[0].isBlank())) out += startLine to fields
            fields = ArrayList()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' && inQuotes && i + 1 < text.length && text[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { fields.add(cur.toString()); cur.setLength(0) }
                c == '\r' && !inQuotes -> Unit
                c == '\n' && !inQuotes -> { endRecord(); line++; startLine = line }
                else -> { cur.append(c); if (c == '\n') line++ }
            }
            i++
        }
        if (cur.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return out
    }

    private fun csvField(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun trimNum(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** A date no real log could hold (year 1, year 999999999) is a bad date, not a record that breaks the screens later. */
    private fun parseInstant(s: String, zone: ZoneId): Instant? {
        val t = s.trim().replace(' ', 'T')
        return try {
            when {
                t.isEmpty() -> null
                t.contains('T') -> LocalDateTime.parse(t).atZone(zone).toInstant()
                t.length == 10 -> LocalDate.parse(t).atTime(9, 0).atZone(zone).toInstant()
                else -> null
            }?.takeIf { RecordLimits.isPlausible(it) }
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * A spreadsheet reads a cell that starts with = + - @ (or a tab or a return) as a formula, and a formula
     * can reach out of the file. A note is free text, so one that starts that way gets an apostrophe in front,
     * which the spreadsheet hides and the import takes off again.
     */
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')

    private fun defused(note: String): String = if (note.isNotEmpty() && note[0] in FORMULA_STARTS) "'$note" else note

    private fun restored(note: String): String? =
        (if (note.length > 1 && note[0] == '\'' && note[1] in FORMULA_STARTS) note.substring(1) else note).ifBlank { null }

    private fun parseDrug(s: String): Drug? = when (s.trim().lowercase().replace(" ", "_")) {
        "estradiol_valerate", "ev", "발레레이트", "에스트라디올_발레레이트" -> Drug.ESTRADIOL_VALERATE
        "estradiol_cypionate", "ec", "사이피오네이트", "에스트라디올_사이피오네이트" -> Drug.ESTRADIOL_CYPIONATE
        "estradiol_tablet", "tablet", "정제", "에스트라디올", "에스트라디올_정제" -> Drug.ESTRADIOL_TABLET
        "estradiol_patch", "e2_patch", "에스트라디올_패치" -> Drug.ESTRADIOL_PATCH
        "estradiol_gel", "e2_gel", "에스트라디올_젤" -> Drug.ESTRADIOL_GEL
        "spironolactone", "spiro", "스피로노락톤", "스피로놀락톤" -> Drug.SPIRONOLACTONE
        "cyproterone", "cpa", "androcur", "안드로쿨", "사이프로테론" -> Drug.CYPROTERONE
        else -> null
    }

    private fun parseRoute(s: String): Route? = when (s.trim().lowercase()) {
        "oral", "po", "경구" -> Route.ORAL
        "sublingual", "sl", "설하" -> Route.SUBLINGUAL
        "im", "im_injection", "근육주사", "주사" -> Route.IM_INJECTION
        "sc", "subq", "sc_injection", "피하주사" -> Route.SC_INJECTION
        "patch", "패치" -> Route.PATCH
        "gel", "젤" -> Route.GEL
        else -> null
    }

    private fun parseUnit(s: String): DoseUnit = when (s.trim().lowercase()) {
        "mg_per_day", "mg/day", "mg/일" -> DoseUnit.MG_PER_DAY
        "ug_per_day", "ug/day", "µg/day", "µg/일", "mcg/day" -> DoseUnit.UG_PER_DAY
        "patch", "매" -> DoseUnit.PATCH
        else -> DoseUnit.MG
    }

    private fun parseStatus(s: String): DoseStatus = when (s.trim().lowercase()) {
        "skipped", "miss", "missed", "놓침" -> DoseStatus.SKIPPED
        "delayed", "late", "늦게", "늦게 투약" -> DoseStatus.DELAYED
        "corrected", "정정" -> DoseStatus.CORRECTED
        else -> DoseStatus.ADMINISTERED
    }

    private fun parseSite(s: String): InjectionSite? = InjectionSite.entries.firstOrNull { siteKey(it) == s.trim().lowercase() }

    private fun parseAssay(s: String): Assay = when (s.trim().lowercase().replace("-", "_").replace("/", "_")) {
        "lc_ms_ms", "lcmsms", "lc_ms", "질량분석" -> Assay.LC_MS_MS
        "immunoassay", "eclia", "clia", "면역측정" -> Assay.IMMUNOASSAY
        else -> Assay.UNKNOWN
    }

    private fun drugKey(d: Drug) = when (d) {
        Drug.ESTRADIOL_VALERATE -> "estradiol_valerate"
        Drug.ESTRADIOL_CYPIONATE -> "estradiol_cypionate"
        Drug.ESTRADIOL_TABLET -> "estradiol_tablet"
        Drug.ESTRADIOL_PATCH -> "estradiol_patch"
        Drug.ESTRADIOL_GEL -> "estradiol_gel"
        Drug.SPIRONOLACTONE -> "spironolactone"
        Drug.CYPROTERONE -> "cyproterone"
    }

    private fun routeKey(r: Route) = when (r) {
        Route.ORAL -> "oral"; Route.SUBLINGUAL -> "sublingual"; Route.IM_INJECTION -> "im"
        Route.SC_INJECTION -> "sc"; Route.PATCH -> "patch"; Route.GEL -> "gel"
    }

    private fun unitKey(u: DoseUnit) = when (u) {
        DoseUnit.MG -> "mg"; DoseUnit.MG_PER_DAY -> "mg_per_day"; DoseUnit.PATCH -> "patch"; DoseUnit.UG_PER_DAY -> "ug_per_day"
    }

    private fun statusKey(s: DoseStatus) = when (s) {
        DoseStatus.ADMINISTERED -> "administered"; DoseStatus.SKIPPED -> "skipped"
        DoseStatus.DELAYED -> "delayed"; DoseStatus.CORRECTED -> "corrected"
    }

    private fun siteKey(s: InjectionSite) = s.name.lowercase()

    private fun assayKey(a: Assay) = when (a) {
        Assay.LC_MS_MS -> "lc_ms_ms"; Assay.IMMUNOASSAY -> "immunoassay"; Assay.UNKNOWN -> "unknown"
    }
}
