package com.hormonelog.core.evidence

import com.hormonelog.core.domain.Route

/** Kept separate so oral P4 parameters can never overwrite oral E2 parameters. */
object ProgesteroneEvidence {
    const val URL = "https://base-donnees-publique.medicaments.gouv.fr/medicament/61673424/extrait"
    const val LIMITS = "경구 미분화 연질캡슐 100–300 mg의 복용분만 근사해요. 체내 총농도·개인별 예측이 아니에요. " +
        "식사·제품·검사법에 따라 크게 달라질 수 있어요. 8시간 이후와 반복 복용은 외삽·합산 가정이며, " +
        "개인별 범위나 검사값 자동 보정은 제공하지 않아요. E2·Total T 계산에는 반영하지 않아요. " +
        "제품·식사 조건은 기록하지만 현재 곡선은 일반 허가자료 참고값이며 조건별 새 모델은 검증 중이에요."
    private val source = EvidenceSource(
        "ansm_utrogestan_oral", "UTROGESTAN 100 mg · RCP §5.2", "ANSM / Besins Healthcare", 2026,
        URL, "Volunteers, 200 mg oral; assay/meal conditions not specified in this table",
        "regulatory product information; population means, not individual prediction",
    )
    private val doseSource = EvidenceSource(
        "dailymed_progesterone_oral", "Progesterone capsules · Clinical pharmacology", "DailyMed / NLM", 2025,
        "https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=0aeb0f0f-2749-416b-942f-83950200fd75",
        "Postmenopausal women; 100–300 mg/day, five daily doses. Food increases bioavailability.",
        "regulatory label; dose scaling here is an approximation, not formulation equivalence",
    )
    private val assaySource = EvidenceSource(
        "nahoul1987_p4_assay", "Radioimmunoassay of plasma progesterone after oral administration of micronized progesterone",
        "Nahoul, Dehennin, Scholler", 1987, "https://pubmed.ncbi.nlm.nih.gov/3560939/",
        "Six healthy volunteers, oral 200 mg; metabolite interference without adequate chromatography",
        "primary comparative assay study; explains why P4 labs are not auto-calibrated",
    )
    val bundle = EvidenceBundle("p4-v1", listOf(source, doseSource, assaySource).associateBy { it.id }, buildList {
        fun p(name: String, value: Double, unit: String) {
            val evidence = if (name == "minMg" || name == "maxMg") doseSource.id else source.id
            add(ModelParameter("p4.$name", Route.ORAL, "P4", name, value, unit, setOf(evidence)))
        }
        p("referenceMg", 200.0, "mg")
        p("studyBaseline", 0.13, "ng/mL")
        listOf(1 to 4.25, 2 to 11.75, 4 to 8.37, 6 to 2.0, 8 to 1.64).forEach { (h, value) ->
            p("hour$h", value, "ng/mL")
        }
        p("minMg", 100.0, "mg")
        p("maxMg", 300.0, "mg")
    })
}
