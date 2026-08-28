package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.Telehealth
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Plain-JSON local persistence for records — no encryption, no SQL. Enough to
 * survive an app restart; SQLCipher/Room is a later step. Corrupt or missing
 * files load as empty rather than crashing.
 */
class RecordStore(private val file: File) {

    data class Snapshot(
        val doses: List<DoseEvent> = emptyList(),
        val labs: List<LabResult> = emptyList(),
        val regimens: List<Regimen> = emptyList(),
        val clinics: List<Clinic> = emptyList(),
    )

    fun load(): Snapshot {
        if (!file.exists()) return Snapshot()
        return try {
            val root = JSONObject(file.readText())
            Snapshot(
                doses = root.optJSONArray("doses").mapObjects(::doseFrom),
                labs = root.optJSONArray("labs").mapObjects(::labFrom),
                regimens = root.optJSONArray("regimens").mapObjects(::regimenFrom),
                clinics = root.optJSONArray("clinics").mapObjects(::clinicFrom),
            )
        } catch (_: Exception) {
            Snapshot()
        }
    }

    fun save(snapshot: Snapshot) {
        val root = JSONObject().apply {
            put("version", 1)
            put("doses", JSONArray().also { a -> snapshot.doses.forEach { a.put(doseTo(it)) } })
            put("labs", JSONArray().also { a -> snapshot.labs.forEach { a.put(labTo(it)) } })
            put("regimens", JSONArray().also { a -> snapshot.regimens.forEach { a.put(regimenTo(it)) } })
            put("clinics", JSONArray().also { a -> snapshot.clinics.forEach { a.put(clinicTo(it)) } })
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
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
    }

    private fun doseFrom(o: JSONObject) = DoseEvent(
        id = UUID.fromString(o.getString("id")),
        occurredAt = Instant.ofEpochMilli(o.getLong("occurredAt")),
        sourceZoneId = o.getString("sourceZoneId"),
        drug = Drug.valueOf(o.getString("drug")),
        route = Route.valueOf(o.getString("route")),
        amountEntered = o.getDouble("amountEntered"),
        enteredUnit = DoseUnit.valueOf(o.getString("enteredUnit")),
        normalizedMilligrams = if (o.isNull("normalizedMilligrams")) null else o.getDouble("normalizedMilligrams"),
        status = DoseStatus.valueOf(o.getString("status")),
        note = o.optStringOrNull("note"),
        revision = o.optInt("revision", 1),
    )

    private fun labTo(l: LabResult) = JSONObject().apply {
        put("id", l.id.toString())
        putOpt("collectedAt", l.collectedAt?.toEpochMilli())
        putOpt("sourceZoneId", l.sourceZoneId)
        put("assay", l.assay.name)
        putOpt("note", l.note)
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

    private fun labFrom(o: JSONObject) = LabResult(
        id = UUID.fromString(o.getString("id")),
        collectedAt = if (o.isNull("collectedAt")) null else Instant.ofEpochMilli(o.getLong("collectedAt")),
        sourceZoneId = o.optStringOrNull("sourceZoneId"),
        assay = Assay.valueOf(o.getString("assay")),
        note = o.optStringOrNull("note"),
        analytes = o.optJSONArray("analytes").mapObjects { a ->
            LabAnalyteValue(
                analyte = Analyte.valueOf(a.getString("analyte")),
                reportedValue = a.getDouble("reportedValue"),
                reportedUnit = a.getString("reportedUnit"),
                canonicalValue = if (a.isNull("canonicalValue")) null else a.getDouble("canonicalValue"),
            )
        },
    )

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
    }

    private fun regimenFrom(o: JSONObject) = Regimen(
        id = UUID.fromString(o.getString("id")),
        drug = Drug.valueOf(o.getString("drug")),
        route = Route.valueOf(o.getString("route")),
        amountEntered = o.getDouble("amountEntered"),
        enteredUnit = DoseUnit.valueOf(o.getString("enteredUnit")),
        everyDays = o.getInt("everyDays"),
        startAt = Instant.ofEpochMilli(o.getLong("startAt")),
        endAt = if (o.isNull("endAt")) null else Instant.ofEpochMilli(o.getLong("endAt")),
        active = o.optBoolean("active", true),
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
        prescriptionBasis = runCatching { PrescriptionBasis.valueOf(o.getString("prescriptionBasis")) }.getOrDefault(PrescriptionBasis.UNKNOWN),
        telehealth = runCatching { Telehealth.valueOf(o.getString("telehealth")) }.getOrDefault(Telehealth.UNKNOWN),
        priceNote = o.optString("priceNote"),
        memo = o.optString("memo"),
        sourceUrl = o.optString("sourceUrl"),
    )

    // Nulls are simply omitted; JSONObject.isNull(key) is true for absent keys too.

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifEmpty { null }

    private inline fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i ->
            (opt(i) as? JSONObject)?.let(map)
        }
    }
}
