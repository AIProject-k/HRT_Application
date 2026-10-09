package com.hormonelog.core.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Things a person may tick under "증상"; the stored value is the enum name, never the label. */
enum class Symptom {
    FATIGUE,
    HEADACHE,
    CHEST_PAIN,
    DIZZINESS,
    LOW_MOOD,
    ANXIETY,
    SLEEP,
    SWELLING,
    HOT_FLASH,
    SKIN,
}

/** Lab values worth keeping next to the curve that the model itself does not use. */
enum class ExtraLab(val unit: String) {
    LH("IU/L"),
    FSH("IU/L"),
    PROLACTIN("ng/mL"),
    SHBG("nmol/L"),
    POTASSIUM("mmol/L"),
}

/**
 * One saved note about how the body is doing: a condition score with symptoms, a weight and
 * blood pressure, or extra lab values. Each save holds exactly one of those groups, so the
 * timeline can show it as one row. None of it feeds the curves.
 */
data class JournalEntry(
    val id: UUID,
    val at: Instant,
    val sourceZoneId: String,
    /** 1 (나쁨) .. 5 (좋음). */
    val condition: Int? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String? = null,
    val weightKg: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val extraLabs: Map<ExtraLab, Double> = emptyMap(),
) {
    enum class Kind { CONDITION, BODY, EXTRA_LABS }

    val kind: Kind
        get() = when {
            condition != null || symptoms.isNotEmpty() || note != null && weightKg == null && systolic == null && extraLabs.isEmpty() -> Kind.CONDITION
            extraLabs.isNotEmpty() -> Kind.EXTRA_LABS
            else -> Kind.BODY
        }
}

/** A note about one doctor's visit: what was said, what was prescribed, where to read more. */
data class VisitMemo(
    val id: UUID,
    val date: LocalDate,
    val title: String,
    val body: String = "",
    /** Free text such as "EV 5 mg · 7일". */
    val prescription: String? = null,
    val link: String? = null,
)

/**
 * How much of one product the person has left. A dose of the same drug, route and amount
 * takes one off [count]; the schedule for it tells how long the rest will last.
 */
data class StockItem(
    val id: UUID,
    val drug: Drug,
    val route: Route,
    val amountEntered: Double,
    val enteredUnit: DoseUnit,
    val count: Int,
) {
    fun matches(dose: DoseEvent): Boolean =
        dose.drug == drug && dose.route == route && dose.amountEntered == amountEntered && dose.enteredUnit == enteredUnit
}
