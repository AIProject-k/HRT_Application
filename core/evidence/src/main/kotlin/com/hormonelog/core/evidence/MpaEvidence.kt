package com.hormonelog.core.evidence

/** Fed state the source reports for its subjects. */
enum class FedState { FASTED, FED, NOT_STATED }

/** Single dose, repeated dosing, or not stated. */
enum class DoseDesign { SINGLE, REPEATED, NOT_STATED }

/** Analytical method the source names for serum MPA. */
enum class MpaAssay { GC_MS, LC_MS_MS, RIA, NOT_STATED }

/** A mean and its spread exactly as printed. [sd] is null when the source prints no spread. */
data class MeanSd(val mean: Double, val sd: Double?)

/** The summary values one source prints for one arm. A null field means the source does not print it. */
data class PkSummary(
    /** ng/mL of MPA. */
    val cmax: MeanSd? = null,
    /** Hours after the dose. */
    val tmaxHours: MeanSd? = null,
    /** ng·h/mL, AUC from zero to infinity. */
    val aucNgHPerMl: MeanSd? = null,
    /** Hours. */
    val tHalfHours: MeanSd? = null,
)

/**
 * One source's MPA data, with the fields an evidence review needs. Values are copied from the source and nothing here is
 * derived. Units: [doseMg] is milligrams of drug given; concentrations are ng/mL of MPA. MPA is a separate analyte from
 * progesterone, so no value here is interchangeable with a P4 ng/mL value.
 */
data class MpaSource(
    val id: String,
    val citation: String,
    val pmid: String?,
    /** The table, figure, or section where the values appear. */
    val locator: String,
    val population: String,
    /** Subjects as the source states them; null when not stated. */
    val subjects: Int?,
    /** Total milligrams the arm received; null when the source gives several doses or none. */
    val doseMg: Double?,
    val formulation: String,
    val fedState: FedState,
    val design: DoseDesign,
    /** The day of repeated dosing on which the sample was drawn; null otherwise. */
    val dayNumber: Int?,
    val assay: MpaAssay,
    val summary: PkSummary?,
    /** The most this source supports. The bundle decides what a stage allows. */
    val stage: EvidenceStage,
    /** ISO date of the check against the source. */
    val reviewedOn: String,
    val note: String,
)

/** Versioned MPA evidence. The bundle stage is the strongest stage any source reaches; numbers need MODEL_VALIDATED. */
data class MpaEvidenceBundle(val version: String, val sources: List<MpaSource>) {
    val stage: EvidenceStage get() = sources.maxOfOrNull { it.stage } ?: EvidenceStage.METADATA_ONLY
}

/**
 * The MPA evidence this build carries. Printed values were read from the Pfizer prescribing information (Table 1 on the
 * clinical pharmacology page) on 2026-10-28, and agree with the 2026-10-27 read. Literature entries are metadata only:
 * the abstracts were read, but the full text was not reached, so no number from them is stored.
 */
object MpaEvidence {
    val bundle = MpaEvidenceBundle(
        version = "mpa-evidence-2026-10-28.1",
        sources = listOf(
            MpaSource(
                id = "pfizer-single-2x10mg",
                citation = "PROVERA (medroxyprogesterone acetate) tablets, prescribing information, clinical pharmacology, Table 1",
                pmid = null,
                locator = "Table 1, row '2 × 10 mg' (pfizermedical.com/provera/clinical-pharmacology)",
                population = "postmenopausal women",
                subjects = 20,
                doseMg = 20.0,
                formulation = "oral tablets, 2 × 10 mg",
                fedState = FedState.FASTED,
                design = DoseDesign.SINGLE,
                dayNumber = null,
                assay = MpaAssay.GC_MS,
                summary = PkSummary(
                    cmax = MeanSd(1.01, 0.599),
                    tmaxHours = MeanSd(2.65, 1.41),
                    aucNgHPerMl = MeanSd(6.95, 3.39),
                    tHalfHours = MeanSd(12.1, 3.49),
                ),
                stage = EvidenceStage.PK_SUMMARY,
                reviewedOn = "2026-10-28",
                note = "The 20 mg total is 2 × 10 mg added up; the page prints no total. This is one 20 mg dose, not a 10 mg single dose. The page says the single-dose study had 20 women; it gives no per-arm split.",
            ),
            MpaSource(
                id = "pfizer-single-8x2.5mg",
                citation = "PROVERA (medroxyprogesterone acetate) tablets, prescribing information, clinical pharmacology, Table 1",
                pmid = null,
                locator = "Table 1, row '8 × 2.5 mg' (pfizermedical.com/provera/clinical-pharmacology)",
                population = "postmenopausal women",
                subjects = 20,
                doseMg = 20.0,
                formulation = "oral tablets, 8 × 2.5 mg",
                fedState = FedState.FASTED,
                design = DoseDesign.SINGLE,
                dayNumber = null,
                assay = MpaAssay.GC_MS,
                summary = PkSummary(
                    cmax = MeanSd(0.805, 0.413),
                    tmaxHours = MeanSd(2.22, 1.39),
                    aucNgHPerMl = MeanSd(5.62, 2.79),
                    tHalfHours = MeanSd(11.6, 2.81),
                ),
                stage = EvidenceStage.PK_SUMMARY,
                reviewedOn = "2026-10-28",
                note = "The 20 mg total is 8 × 2.5 mg added up; the page prints no total. This is one 20 mg dose, not a 2.5 mg single dose. The page says the single-dose study had 20 women; it gives no per-arm split.",
            ),
            MpaSource(
                id = "pfizer-repeated-10mg-day7",
                citation = "PROVERA (medroxyprogesterone acetate) tablets, prescribing information, clinical pharmacology, Table 1",
                pmid = null,
                locator = "Table 1, row 'Multiple Dose 10 mg' (pfizermedical.com/provera/clinical-pharmacology)",
                population = "postmenopausal women",
                subjects = 30,
                doseMg = 10.0,
                formulation = "oral tablet, 1 × 10 mg daily for 7 days",
                fedState = FedState.FASTED,
                design = DoseDesign.REPEATED,
                dayNumber = 7,
                assay = MpaAssay.GC_MS,
                summary = PkSummary(
                    cmax = MeanSd(0.71, 0.35),
                    tmaxHours = MeanSd(2.83, 1.83),
                    aucNgHPerMl = MeanSd(6.01, 3.16),
                    tHalfHours = MeanSd(16.6, 15.0),
                ),
                stage = EvidenceStage.PK_SUMMARY,
                reviewedOn = "2026-10-28",
                note = "Sampled after the day-7 dose at steady state (table footnote: 'Following Day 7 dose'). Not a single 10 mg dose. The page calls these estimates highly variable; the half-life SD is 15.0 h.",
            ),
            MpaSource(
                id = "pfizer-food-10mg",
                citation = "PROVERA (medroxyprogesterone acetate) tablets, prescribing information, clinical pharmacology, food effect",
                pmid = null,
                locator = "clinical pharmacology text (pfizermedical.com/provera/clinical-pharmacology)",
                population = "not stated for this comparison",
                subjects = null,
                doseMg = 10.0,
                formulation = "oral tablet, 10 mg",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.NOT_STATED,
                summary = null,
                stage = EvidenceStage.PK_SUMMARY,
                reviewedOn = "2026-10-28",
                note = "A relative change, not a fed-arm mean. Quoted: 'A 10 mg dose of PROVERA, taken immediately before or after a meal, increased MPA Cmax (50 to 70%) and AUC (18 to 33%).' Half-life unchanged. No n, population, design or fed-arm mean is given.",
            ),
            MpaSource(
                id = "pfizer-liver-disease",
                citation = "PROVERA (medroxyprogesterone acetate) tablets, prescribing information, clinical pharmacology, hepatic impairment",
                pmid = null,
                locator = "clinical pharmacology text (pfizermedical.com/provera/clinical-pharmacology)",
                population = "advanced liver disease",
                subjects = 14,
                doseMg = null,
                formulation = "oral",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.NOT_STATED,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-28",
                note = "Quoted: 'In 14 patients with advanced liver disease, MPA disposition was significantly altered (reduced elimination).' No values are given. Carry this into the disclaimers.",
            ),
            MpaSource(
                id = "wikstrom-1984-pmid6233840",
                citation = "Wikström et al., 1984 (oral 5 and 10 mg, repeated)",
                pmid = "6233840",
                locator = "abstract",
                population = "women (abstract)",
                subjects = 5,
                doseMg = null,
                formulation = "oral, 5 mg and 10 mg",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.REPEATED,
                dayNumber = null,
                assay = MpaAssay.RIA,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Abstract only; the full text was not read, so no number is stored. The abstract reports Tmax of 1 to 3 h, and a 12-hour level on 5 mg that stayed constant over 8 weeks.",
            ),
            MpaSource(
                id = "svensson-1994-pmid8015506",
                citation = "Svensson et al., 1994 (randomised crossover with a combined hormone product)",
                pmid = "8015506",
                locator = "abstract",
                population = "postmenopausal women (abstract)",
                subjects = 12,
                doseMg = 5.0,
                formulation = "oral MPA 5 mg, taken together with Trisequence",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.NOT_STATED,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Abstract only; no number is stored. The MPA arm was co-administered with the combined product, so it is not a plain oral MPA curve.",
            ),
            MpaSource(
                id = "johansson-1986-pmid2943134",
                citation = "Johansson et al., 1986 (high-dose oral MPA)",
                pmid = "2943134",
                locator = "abstract",
                population = "healthy women (per the 2026-10-27 check)",
                subjects = null,
                doseMg = null,
                formulation = "oral, 500 mg and 1000 mg",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.RIA,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Abstract only; no number is stored. The abstract describes a two-compartment model with first-order absorption, a biological half-life of 40 to 60 h and an absorption half-life of 15 to 30 min. High dose, RIA and 96-hour sampling: not used as a 10 mg reference.",
            ),
            MpaSource(
                id = "victor-johansson-1976-pmid975821",
                citation = "Victor & Johansson, 1976",
                pmid = "975821",
                locator = "not verified",
                population = "not verified",
                subjects = null,
                doseMg = null,
                formulation = "oral",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.NOT_STATED,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Not verified: PubMed has no abstract and ScienceDirect returned 403. Figures quoted in a summary (Cmax 1.15 to 5.15 ng/mL, half-life about 30 h) are deliberately not stored and must not be used.",
            ),
            MpaSource(
                id = "kim-kim-2001-pmid11675672",
                citation = "Kim & Kim, 2001 (LC-MS/MS method)",
                pmid = "11675672",
                locator = "abstract",
                population = "method paper; no population used",
                subjects = null,
                doseMg = null,
                formulation = "not applicable",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.LC_MS_MS,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Method metadata only: lower limit of quantitation 0.05 ng/mL, calibration range 0.05 to 6.0 ng/mL, as the abstract gives them. No PK values are used.",
            ),
            MpaSource(
                id = "blue-2018-pmid29407362",
                citation = "Blue 2018 (multi-analyte LC-MS/MS)",
                pmid = "29407362",
                locator = "abstract",
                population = "method paper; no population used",
                subjects = null,
                doseMg = null,
                formulation = "not applicable",
                fedState = FedState.NOT_STATED,
                design = DoseDesign.NOT_STATED,
                dayNumber = null,
                assay = MpaAssay.LC_MS_MS,
                summary = null,
                stage = EvidenceStage.METADATA_ONLY,
                reviewedOn = "2026-10-27",
                note = "Method metadata only: MPA is measured as a separate analyte from progesterone, with a lower limit of quantitation of 0.020 ng/mL, as the abstract gives it. No PK values are used.",
            ),
        ),
    )
}
