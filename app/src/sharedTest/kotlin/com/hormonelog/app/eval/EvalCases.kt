package com.hormonelog.app.eval

import com.hormonelog.app.chat.ChatCommands
import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Drug
import java.time.Instant
import java.time.ZoneId

/** Fixed clock for every synthetic case: 2026-10-24 23:00 Asia/Seoul. */
val EVAL_NOW: Instant = Instant.parse("2026-10-24T14:00:00Z")
val EVAL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/** One record the product must save (or none). `at` is "yyyy-MM-dd HH:mm" in Asia/Seoul. */
data class Dose(val drug: Drug, val mg: Double, val at: String)
data class LabRec(val analyte: Analyte, val value: Double, val unit: String)

/** Expectation, fixed before any model run. An empty list with save=false means nothing may be saved. */
data class EvalCase(
    val id: String,
    val input: String,
    val save: Boolean,
    val doses: List<Dose> = emptyList(),
    val labs: List<LabRec> = emptyList(),
)

private fun drugOf(name: String): Drug = ChatCommands.knownDrug(AppState(), name)!!.drug
private fun dose(name: String, mg: Double, at: String) = Dose(drugOf(name), mg, at)
private fun saved(id: String, input: String, vararg doses: Dose) = EvalCase(id, input, save = true, doses = doses.toList())
private fun refused(id: String, input: String) = EvalCase(id, input, save = false)

/** Synthetic cases only. Ids match the earlier device run where the input is unchanged. */
val EVAL_CASES: List<EvalCase> = listOf(
    saved("batch-two-correct", "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 10:00"), dose("안쿨", 25.0, "2026-10-24 20:00")),
    refused("negation", "방금 프로기노바 2mg 먹지 않았어"),
    refused("question", "방금 안쿨 25mg 먹었어?"),
    refused("plan", "내일 안쿨 25mg 먹을 거야"),
    refused("symptom-mixed", "방금 프로기노바 2mg 먹었고 어지러웠어"),
    EvalCase("lab-e2", "오늘 10:00 E2 100pg/mL 검사 기록해줘", save = true,
        labs = listOf(LabRec(Analyte.ESTRADIOL, 100.0, "pg/mL"))),
    refused("memo", "오늘 머리가 아프고 피곤했어 메모해줘"),
    refused("body-weight", "몸무게 60kg 기록해줘"),
    saved("batch-five", "오늘 09:00 프로기노바 2mg 복용\n오늘 12:00 프로기노바 2mg 복용\n오늘 15:00 프로기노바 2mg 복용\n오늘 18:00 프로기노바 2mg 복용\n오늘 21:00 프로기노바 2mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 09:00"), dose("프로기노바", 2.0, "2026-10-24 12:00"),
        dose("프로기노바", 2.0, "2026-10-24 15:00"), dose("프로기노바", 2.0, "2026-10-24 18:00"),
        dose("프로기노바", 2.0, "2026-10-24 21:00")),
    saved("batch-ten", (8..17).joinToString("\n") { "오늘 %02d:00 프로기노바 2mg 복용".format(it) },
        *(8..17).map { dose("프로기노바", 2.0, "2026-10-24 %02d:00".format(it)) }.toTypedArray()),
    refused("crossed-values", "오늘 10:00 프로기노바 2mg 안쿨 25mg 오늘 20:00 복용"),
    saved("same-drug-two", "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 프로기노바 4mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 10:00"), dose("프로기노바", 4.0, "2026-10-24 20:00")),
    saved("shared-time-two", "오늘 10:00 프로기노바 2mg 복용, 오늘 10:00 안쿨 25mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 10:00"), dose("안쿨", 25.0, "2026-10-24 10:00")),
    saved("explicit-date", "2026-10-24 10:00 프로기노바 2mg 복용", dose("프로기노바", 2.0, "2026-10-24 10:00")),
    refused("year-unconfirmed", "10월 24일 오전 10시 프로기노바 2mg 복용"),
)

/**
 * The 15 cases above plus 43 adversarial and positive cases, written before any model run on them.
 * Ambiguity is refused: a case that cannot be read with certainty expects no record.
 */
val EVAL_CASES_V2: List<EvalCase> = EVAL_CASES + listOf(
    // Not completed: question, plan, hypothetical, quoted, cancelled, skipped.
    refused("a-question-no-mark", "방금 프로기노바 2mg 먹었나"),
    refused("a-question-polite", "방금 프로기노바 2mg 먹었나요"),
    refused("a-planned-future", "오늘 10:00 프로기노바 2mg 복용할 거야"),
    refused("a-planned-expected", "오늘 10:00 프로기노바 2mg 복용할 예정이야"),
    refused("a-next-time", "다음에 프로기노바 2mg 먹을 거야"),
    refused("a-attempt-failed", "방금 프로기노바 2mg 먹으려다 안 먹었어"),
    refused("a-hypothetical", "방금 프로기노바 2mg 먹었다고 가정하면 어떻게 돼"),
    refused("a-example", "예시: 방금 프로기노바 2mg 먹었어"),
    refused("a-quoted-other", "친구가 '프로기노바 2mg 먹었어'라고 했어"),
    refused("a-mixed-yesterday-taken", "어제는 프로기노바 2mg 못 먹었고 오늘은 먹었어"),
    refused("a-cancelled", "방금 프로기노바 2mg 복용 취소"),
    refused("a-skipped", "오늘 프로기노바 2mg 건너뛰었어"),
    // Values that are missing, impossible or unknown.
    refused("a-unit-missing", "방금 프로기노바 2 복용"),
    refused("a-tablet-unit", "방금 프로기노바 2정 복용"),
    refused("a-negative", "방금 프로기노바 -2mg 복용"),
    refused("a-zero", "방금 프로기노바 0mg 복용"),
    refused("a-invalid-date", "2026-02-30 10:00 프로기노바 2mg 복용"),
    refused("a-future-date", "2026-10-25 10:00 프로기노바 2mg 복용"),
    refused("a-time-range", "오늘 10시에서 11시 사이 프로기노바 2mg 복용"),
    refused("a-hour-25", "오늘 25:00 프로기노바 2mg 복용"),
    refused("a-vague-hour", "오늘 10시쯤 프로기노바 2mg 복용"),
    refused("a-evening-only", "어제 저녁 프로기노바 2mg 먹었어"),
    refused("a-unknown-drug", "방금 아무약 2mg 복용"),
    refused("a-two-amounts", "방금 프로기노바 2mg 4mg 복용"),
    refused("a-symptom-only", "오늘 머리가 아파요"),
    refused("a-measure-bp", "혈압 120/80 기록해줘"),
    refused("a-memo-prefixed", "메모해줘: 방금 프로기노바 2mg 먹었어"),
    refused("a-lab-wrong-unit", "오늘 10:00 E2 100mg/mL 검사 기록해줘"),
    refused("a-lab-plus-dose", "오늘 10:00 E2 100pg/mL 검사 기록하고 프로기노바 2mg 복용했어"),
    // Binding and batches that cannot be bound with certainty: nothing may be saved.
    refused("a-crossed-no-comma", "오늘 10:00 프로기노바 2mg 안쿨 25mg 20:00 복용"),
    refused("a-batch-one-bad-line", "오늘 09:00 프로기노바 2mg 복용\n오늘 09:10 프로기노바 2mg 복용 못했어"),
    refused("a-duplicate-line", "오늘 10:00 프로기노바 2mg 복용\n오늘 10:00 프로기노바 2mg 복용"),
    refused("a-same-drug-no-separator", "오늘 10:00 프로기노바 2mg 복용 오늘 20:00 프로기노바 2mg 복용"),
    // Batches and single items that bind cleanly.
    saved("a-three-same-drug", "오늘 09:00 프로기노바 2mg 복용, 오늘 12:00 프로기노바 4mg 복용, 오늘 15:00 프로기노바 2mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 09:00"), dose("프로기노바", 4.0, "2026-10-24 12:00"), dose("프로기노바", 2.0, "2026-10-24 15:00")),
    saved("a-shared-time-reordered", "오늘 10:00 안쿨 25mg 복용, 오늘 10:00 프로기노바 2mg 복용",
        dose("안쿨", 25.0, "2026-10-24 10:00"), dose("프로기노바", 2.0, "2026-10-24 10:00")),
    saved("a-clause-first-drug", "프로기노바 2mg 오늘 10:00, 안쿨 25mg 오늘 20:00 복용",
        dose("프로기노바", 2.0, "2026-10-24 10:00"), dose("안쿨", 25.0, "2026-10-24 20:00")),
    saved("a-lines-self-contained", "오늘 프로기노바 2mg 10:00 복용\n오늘 안쿨 25mg 20:00 복용",
        dose("프로기노바", 2.0, "2026-10-24 10:00"), dose("안쿨", 25.0, "2026-10-24 20:00")),
    saved("a-two-dates", "어제 22:00 프로기노바 2mg 복용\n오늘 09:00 프로기노바 2mg 복용",
        dose("프로기노바", 2.0, "2026-10-23 22:00"), dose("프로기노바", 2.0, "2026-10-24 09:00")),
    saved("a-reverse-order", "오늘 21:00 프로기노바 2mg 복용\n오늘 09:00 프로기노바 2mg 복용",
        dose("프로기노바", 2.0, "2026-10-24 21:00"), dose("프로기노바", 2.0, "2026-10-24 09:00")),
    // Positive controls: the plain forms must still save exactly what was said.
    saved("a-simple-now", "방금 프로기노바 2mg 먹었어", dose("프로기노바", 2.0, "2026-10-24 23:00")),
    saved("a-afternoon", "오늘 오후 3시 프로기노바 2mg 복용", dose("프로기노바", 2.0, "2026-10-24 15:00")),
    saved("a-iso-date", "2026-10-24 15:00 프로기노바 4mg 먹었어", dose("프로기노바", 4.0, "2026-10-24 15:00")),
    EvalCase("a-lab-e2-evening", "오늘 22:00 E2 80pg/mL 검사 기록해줘", save = true,
        labs = listOf(LabRec(Analyte.ESTRADIOL, 80.0, "pg/mL"))),
)
