package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.ExtraLab
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import com.hormonelog.core.domain.Symptom
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.domain.VisitMemo
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Everything the app keeps about the user's treatment, as one value. */
data class RecordSnapshot(
    val doses: List<DoseEvent> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val regimens: List<Regimen> = emptyList(),
    val clinics: List<Clinic> = emptyList(),
    val memos: List<VisitMemo> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val stock: List<StockItem> = emptyList(),
    /** The next doctor's visit, as epoch millis; null = none planned. */
    val nextVisitMillis: Long? = null,
    /**
     * Records this build could not read (a newer build wrote them). They are written back
     * untouched on every save, so downgrading and upgrading again never loses them.
     */
    val carried: Map<String, List<String>> = emptyMap(),
) {
    val carriedCount: Int get() = carried.values.sumOf { it.size }
}

/**
 * The JSON shape of [RecordSnapshot], shared by the records file and backups.
 *
 * Reading is per record: one value this build does not understand (an enum added by a
 * newer build, say) sets only that record aside instead of discarding the whole file.
 */
internal object RecordJson {
    const val VERSION = 3

    fun toJson(s: RecordSnapshot): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("doses", jsonArray(s.doses.map(::doseTo), s.carried["doses"]))
        put("labs", jsonArray(s.labs.map(::labTo), s.carried["labs"]))
        put("regimens", jsonArray(s.regimens.map(::regimenTo), s.carried["regimens"]))
        put("clinics", jsonArray(s.clinics.map(::clinicTo), s.carried["clinics"]))
        put("memos", jsonArray(s.memos.map(::memoTo), s.carried["memos"]))
        put("journal", jsonArray(s.journal.map(::journalTo), s.carried["journal"]))
        put("stock", jsonArray(s.stock.map(::stockTo), s.carried["stock"]))
        putOpt("nextVisitMillis", s.nextVisitMillis)
    }

    private fun jsonArray(known: List<JSONObject>, carried: List<String>?): JSONArray = JSONArray().also { a ->
        known.forEach { a.put(it) }
        carried?.forEach { raw -> runCatching { JSONObject(raw) }.getOrNull()?.let(a::put) }
    }

    /** Throws only when [root] is not a records object at all. */
    fun fromJson(root: JSONObject): RecordSnapshot {
        val carried = LinkedHashMap<String, List<String>>()
        fun <T> read(name: String, map: (JSONObject) -> T): List<T> {
            val array = root.optJSONArray(name) ?: return emptyList()
            val out = ArrayList<T>()
            val skipped = ArrayList<String>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                try {
                    out += map(o)
                } catch (_: Exception) {
                    skipped += o.toString()
                }
            }
            if (skipped.isNotEmpty()) carried[name] = skipped
            return out
        }
        return RecordSnapshot(
            doses = read("doses", ::doseFrom),
            labs = read("labs", ::labFrom),
            regimens = read("regimens", ::regimenFrom),
            clinics = read("clinics", ::clinicFrom),
            memos = read("memos", ::memoFrom),
            journal = read("journal", ::journalFrom),
            stock = read("stock", ::stockFrom),
            // A visit date no calendar could show is dropped, not allowed to break the home screen.
            nextVisitMillis = if (root.isNull("nextVisitMillis")) null else root.optLong("nextVisitMillis").takeIf { RecordLimits.isPlausible(Instant.ofEpochMilli(it)) },
            carried = carried,
        )
    }

    // ── mapping ──────────────────────────────────────────────
    private fun doseTo(d: DoseEvent) = JSONObject().apply {
        put("id", d.id.toString())
        put("occurredAt", d.occurredAt.toEpochMilli())
        put("sourceZoneId", d.sourceZoneId)
        put("drug", d.drug.name)
        put("route", d.route.name)
        put("amountEntered", d.amountEntered)
        put("enteredUnit", d.enteredUnit.name)
        putOpt("normalizedMilligrams", d.normalizedMilligrams)
        put("status", d.status.name)
        putOpt("note", d.note)
        put("revision", d.revision)
        put("source", d.source.name)
        putOpt("site", d.site?.name)
        putOpt("patchCycle", d.patchCycle?.name)
    }

    private fun doseFrom(o: JSONObject) = DoseEvent(
        id = UUID.fromString(o.getString("id")),
        occurredAt = RecordLimits.instant(Instant.ofEpochMilli(o.getLong("occurredAt"))),
        sourceZoneId = o.getString("sourceZoneId"),
        drug = Drug.valueOf(o.getString("drug")),
        route = Route.valueOf(o.getString("route")),
        amountEntered = RecordLimits.amount(o.getDouble("amountEntered")),
        enteredUnit = DoseUnit.valueOf(o.getString("enteredUnit")),
        normalizedMilligrams = if (o.isNull("normalizedMilligrams")) null else RecordLimits.amount(o.getDouble("normalizedMilligrams")),
        status = DoseStatus.valueOf(o.getString("status")),
        note = o.optStringOrNull("note"),
        revision = o.optInt("revision", 1),
        source = o.optEnum("source", RecordSource.MANUAL),
        site = o.optEnumOrNull<InjectionSite>("site"),
        patchCycle = o.optEnumOrNull<PatchCycle>("patchCycle"),
    )

    private fun labTo(l: LabResult) = JSONObject().apply {
        put("id", l.id.toString())
        putOpt("collectedAt", l.collectedAt?.toEpochMilli())
        putOpt("sourceZoneId", l.sourceZoneId)
        put("assay", l.assay.name)
        putOpt("note", l.note)
        if (l.isBaseline) put("isBaseline", true)
        put("source", l.source.name)
        put("analytes", JSONArray().also { arr ->
            l.analytes.forEach { v ->
                arr.put(JSONObject().apply {
                    put("analyte", v.analyte.name)
                    put("reportedValue", v.reportedValue)
                    put("reportedUnit", v.reportedUnit)
                    putOpt("canonicalValue", v.canonicalValue)
                })
            }
        })
    }

    private fun labFrom(o: JSONObject): LabResult {
        val array = o.optJSONArray("analytes")
        val analytes = ArrayList<LabAnalyteValue>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val a = array.getJSONObject(i)
                analytes += LabAnalyteValue(
                    analyte = Analyte.valueOf(a.getString("analyte")),
                    reportedValue = RecordLimits.amount(a.getDouble("reportedValue")),
                    reportedUnit = a.getString("reportedUnit"),
                    canonicalValue = if (a.isNull("canonicalValue")) null else RecordLimits.amount(a.getDouble("canonicalValue")),
                )
            }
        }
        return LabResult(
            id = UUID.fromString(o.getString("id")),
            collectedAt = if (o.isNull("collectedAt")) null else RecordLimits.instant(Instant.ofEpochMilli(o.getLong("collectedAt"))),
            sourceZoneId = o.optStringOrNull("sourceZoneId"),
            assay = Assay.valueOf(o.getString("assay")),
            note = o.optStringOrNull("note"),
            analytes = analytes,
            isBaseline = o.optBoolean("isBaseline", false),
            source = o.optEnum("source", RecordSource.MANUAL),
        )
    }

    private fun regimenTo(r: Regimen) = JSONObject().apply {
        put("id", r.id.toString())
        put("drug", r.drug.name)
        put("route", r.route.name)
        put("amountEntered", r.amountEntered)
        put("enteredUnit", r.enteredUnit.name)
        put("everyDays", r.everyDays)
        put("startAt", r.startAt.toEpochMilli())
        putOpt("endAt", r.endAt?.toEpochMilli())
        put("active", r.active)
        if (r.weekdays != 0) put("weekdays", r.weekdays)
        putOpt("timeMinutes", r.timeMinutes)
        putOpt("patchCycle", r.patchCycle?.name)
    }

    private fun regimenFrom(o: JSONObject) = Regimen(
        id = UUID.fromString(o.getString("id")),
        drug = Drug.valueOf(o.getString("drug")),
        route = Route.valueOf(o.getString("route")),
        amountEntered = RecordLimits.amount(o.getDouble("amountEntered")),
        enteredUnit = DoseUnit.valueOf(o.getString("enteredUnit")),
        everyDays = o.getInt("everyDays"),
        startAt = RecordLimits.instant(Instant.ofEpochMilli(o.getLong("startAt"))),
        endAt = if (o.isNull("endAt")) null else RecordLimits.instant(Instant.ofEpochMilli(o.getLong("endAt"))),
        active = o.optBoolean("active", true),
        weekdays = o.optInt("weekdays", 0),
        timeMinutes = if (o.isNull("timeMinutes")) null else o.getInt("timeMinutes"),
        patchCycle = o.optEnumOrNull<PatchCycle>("patchCycle"),
    )

    private fun clinicTo(c: Clinic) = JSONObject().apply {
        put("id", c.id.toString())
        put("name", c.name)
        put("region", c.region)
        put("prescriptionBasis", c.prescriptionBasis.name)
        put("telehealth", c.telehealth.name)
        put("priceNote", c.priceNote)
        put("memo", c.memo)
        put("sourceUrl", c.sourceUrl)
    }

    private fun clinicFrom(o: JSONObject) = Clinic(
        id = UUID.fromString(o.getString("id")),
        name = o.optString("name"),
        region = o.optString("region"),
        prescriptionBasis = o.optEnum("prescriptionBasis", PrescriptionBasis.UNKNOWN),
        telehealth = o.optEnum("telehealth", Telehealth.UNKNOWN),
        priceNote = o.optString("priceNote"),
        memo = o.optString("memo"),
        sourceUrl = o.optString("sourceUrl"),
    )

    private fun memoTo(m: VisitMemo) = JSONObject().apply {
        put("id", m.id.toString())
        put("date", m.date.toString())
        put("title", m.title)
        put("body", m.body)
        putOpt("prescription", m.prescription)
        putOpt("link", m.link)
    }

    private fun memoFrom(o: JSONObject) = VisitMemo(
        id = UUID.fromString(o.getString("id")),
        date = RecordLimits.date(LocalDate.parse(o.getString("date"))),
        title = o.optString("title"),
        body = o.optString("body"),
        prescription = o.optStringOrNull("prescription"),
        link = o.optStringOrNull("link"),
    )

    private fun journalTo(j: JournalEntry) = JSONObject().apply {
        put("id", j.id.toString())
        put("at", j.at.toEpochMilli())
        put("sourceZoneId", j.sourceZoneId)
        putOpt("condition", j.condition)
        if (j.symptoms.isNotEmpty()) put("symptoms", JSONArray().also { a -> j.symptoms.sortedBy { it.ordinal }.forEach { a.put(it.name) } })
        putOpt("note", j.note)
        putOpt("weightKg", j.weightKg)
        putOpt("systolic", j.systolic)
        putOpt("diastolic", j.diastolic)
        if (j.extraLabs.isNotEmpty()) put("extraLabs", JSONObject().also { m -> j.extraLabs.forEach { (k, v) -> m.put(k.name, v) } })
    }

    private fun journalFrom(o: JSONObject): JournalEntry {
        // A symptom or lab kind this build does not know is dropped on its own, not the entry.
        val symptoms = o.optJSONArray("symptoms")?.let { a ->
            (0 until a.length()).mapNotNull { i -> Symptom.entries.firstOrNull { it.name == a.optString(i) } }.toSet()
        }.orEmpty()
        val labs = o.optJSONObject("extraLabs")?.let { m ->
            m.keys().asSequence().mapNotNull { k -> ExtraLab.entries.firstOrNull { it.name == k }?.let { it to RecordLimits.amount(m.getDouble(k)) } }.toMap()
        }.orEmpty()
        return JournalEntry(
            id = UUID.fromString(o.getString("id")),
            at = RecordLimits.instant(Instant.ofEpochMilli(o.getLong("at"))),
            sourceZoneId = o.getString("sourceZoneId"),
            condition = if (o.isNull("condition")) null else o.getInt("condition"),
            symptoms = symptoms,
            note = o.optStringOrNull("note"),
            weightKg = if (o.isNull("weightKg")) null else RecordLimits.amount(o.getDouble("weightKg")),
            systolic = if (o.isNull("systolic")) null else o.getInt("systolic"),
            diastolic = if (o.isNull("diastolic")) null else o.getInt("diastolic"),
            extraLabs = labs,
        )
    }

    private fun stockTo(s: StockItem) = JSONObject().apply {
        put("id", s.id.toString())
        put("drug", s.drug.name)
        put("route", s.route.name)
        put("amountEntered", s.amountEntered)
        put("enteredUnit", s.enteredUnit.name)
        put("count", s.count)
    }

    private fun stockFrom(o: JSONObject) = StockItem(
        id = UUID.fromString(o.getString("id")),
        drug = Drug.valueOf(o.getString("drug")),
        route = Route.valueOf(o.getString("route")),
        amountEntered = RecordLimits.amount(o.getDouble("amountEntered")),
        enteredUnit = DoseUnit.valueOf(o.getString("enteredUnit")),
        count = o.getInt("count"),
    )
    // Nulls are simply omitted; JSONObject.isNull(key) is true for absent keys too.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    private inline fun <reified E : Enum<E>> JSONObject.optEnumOrNull(key: String): E? =
        optStringOrNull(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

    private inline fun <reified E : Enum<E>> JSONObject.optEnum(key: String, default: E): E =
        optEnumOrNull<E>(key) ?: default
}
