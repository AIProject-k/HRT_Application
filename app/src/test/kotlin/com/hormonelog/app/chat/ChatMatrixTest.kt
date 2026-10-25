package com.hormonelog.app.chat

import com.hormonelog.app.eval.Dose
import com.hormonelog.app.eval.EvalScorer
import com.hormonelog.app.eval.LabRec
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Drug
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * The 62 single inputs and 10 follow-up sequences from docs/chat-review-2026-10-24/Input-Matrix.md.
 * Expectations are the correct product behavior with the fixed clock 2026-10-24 23:00 Asia/Seoul,
 * not the output the current code gives. Model-only rows are reported, not counted as failures.
 * Writes build/reports/chat-matrix.tsv for before/after comparison.
 */
class ChatMatrixTest {
    /**
     * Expected end state. `refuse` = no record and a reply that is not a save confirmation.
     * `modelAllowed` = the model may be called; every other row must be decided by the rules without any model call.
     */
    private class Want(
        val doses: List<Dose> = emptyList(), val labs: List<LabRec> = emptyList(),
        val refuse: Boolean = false, val modelAllowed: Boolean = false,
    )

    private class Row(val id: String, val inputs: List<String>, val expect: Want)

    private fun one(id: String, text: String, expect: Want) = Row(id, listOf(text), expect)
    private fun seq(id: String, vararg texts: String, expect: Want) = Row(id, texts.toList(), expect)
    private fun save(at: String, mg: Double, drug: Drug = Drug.ESTRADIOL_TABLET) = Want(doses = listOf(Dose(drug, mg, at)))
    private val none = Want(refuse = true)
    private val noneModelAllowed = Want(refuse = true, modelAllowed = true)

    private val rows = listOf(
        one("M01", "방금 프로기노바 2mg 복용", save("2026-10-24 23:00", 2.0)),
        one("M02", "프로기노바 2mg 방금 복용", save("2026-10-24 23:00", 2.0)),
        one("M03", "오늘 방금 프로기노바 2mg 복용", save("2026-10-24 23:00", 2.0)),
        one("M04", "10월 24일 10시 프로기노바 2mg 복용", none),
        one("M05", "10월 24일 오전 10시 프로기노바 2mg 복용", none),
        one("M06", "2026-10-24 10:00 프로기노바 2mg 복용", save("2026-10-24 10:00", 2.0)),
        one("M07", "2026년 10월 24일 오전 10시 프로기노바 2mg 복용", save("2026-10-24 10:00", 2.0)),
        one("M08", "10/24 10:00 프로기노바 2mg 복용", none),
        one("M09", "2026.10.24 10:00 프로기노바 2mg 복용", save("2026-10-24 10:00", 2.0)),
        one("M10", "오늘 10시 프로기노바 2mg 복용", none),
        one("M11", "오늘 22시 프로기노바 2mg 복용", save("2026-10-24 22:00", 2.0)),
        one("M12", "오늘 22:00 프로기노바 2mg 복용", save("2026-10-24 22:00", 2.0)),
        one("M13", "오늘 오전 10시 프로기노바 2mg 복용", save("2026-10-24 10:00", 2.0)),
        one("M14", "오늘 오후 10시 반 프로기노바 2mg 복용", save("2026-10-24 22:30", 2.0)),
        one("M15", "오늘 오후 10시 15분 프로기노바 2mg 복용", save("2026-10-24 22:15", 2.0)),
        one("M16", "오늘 저녁 10시 프로기노바 2mg 복용", save("2026-10-24 22:00", 2.0)),
        one("M17", "오늘 아침 10시 프로기노바 2mg 복용", save("2026-10-24 10:00", 2.0)),
        one("M18", "오늘 밤 10시 프로기노바 2mg 복용", save("2026-10-24 22:00", 2.0)),
        one("M19", "오늘 자정 프로기노바 2mg 복용", none),
        one("M20", "어제 10:00 프로기노바 2mg 복용", save("2026-10-23 10:00", 2.0)),
        one("M21", "그제 10:00 프로기노바 2mg 복용", save("2026-10-22 10:00", 2.0)),
        one("M22", "엊그제 10:00 프로기노바 2mg 복용", save("2026-10-22 10:00", 2.0)),
        one("M23", "3일 전 10:00 프로기노바 2mg 복용", save("2026-10-21 10:00", 2.0)),
        one("M24", "30분 전 프로기노바 2mg 복용", save("2026-10-24 22:30", 2.0)),
        one("M25", "2시간 전 프로기노바 2mg 복용", save("2026-10-24 21:00", 2.0)),
        one("M26", "아까 프로기노바 2mg 복용", none),
        one("M27", "내일 10:00 프로기노바 2mg 복용", none),
        one("M28", "2026-10-25 10:00 프로기노바 2mg 복용", none),
        one("M29", "2026-02-30 10:00 프로기노바 2mg 복용", none),
        one("M30", "오늘 25:00 프로기노바 2mg 복용", none),
        one("M31", "오늘 10:60 프로기노바 2mg 복용", none),
        one("M32", "10월 23일 방금 프로기노바 2mg 복용", none),
        one("M33", "내일 방금 프로기노바 2mg 복용", none),
        one("M34", "오늘 10시에서 11시 사이 프로기노바 2mg 복용", none),
        one("M35", "방금 프로기노바 2 mg 복용", save("2026-10-24 23:00", 2.0)),
        one("M36", "방금 프로기노바 2MG 복용", save("2026-10-24 23:00", 2.0)),
        one("M37", "방금 프로기노바 2밀리그램 복용", save("2026-10-24 23:00", 2.0)),
        one("M38", "방금 프로기노바 이 밀리그램 복용", none),
        one("M39", "방금 프로기노바 1정 복용", none),
        one("M40", "방금 프로기노바 0.5mg 복용", save("2026-10-24 23:00", 0.5)),
        one("M41", "방금 프로기노바 .5mg 복용", none),
        one("M42", "방금 프로기노바 -2mg 복용", none),
        one("M43", "방금 프로기노바 0mg 복용", none),
        one("M44", "방금 프로기노바 2 복용", none),
        one("M45", "방금 프로기노바 2mg 먹었어", save("2026-10-24 23:00", 2.0)),
        one("M46", "방금 프로기노바 2mg 먹었음", save("2026-10-24 23:00", 2.0)),
        one("M47", "방금 프로기노바 2mg 복용했어요", save("2026-10-24 23:00", 2.0)),
        one("M48", "방금 프로기노바 2mg 먹지 않았어", none),
        one("M49", "방금 프로기노바 2mg 복용 못했어", none),
        one("M50", "방금 프로기노바 2mg 먹었어?", none),
        one("M51", "방금 프로기노바 2mg 복용 취소", none),
        one("M52", "방금 프로기노바 2mg 먹었고 어지러웠어", none),
        one("M53", "프로기노바 2mg 아침약으로 기억해줘", noneModelAllowed),
        one("M54", "오늘 09:00 프로기노바 2mg 복용\n오늘 09:00 프로기노바 2mg 복용", none),
        one("M55", "오늘 09:00 프로기노바 2mg 복용\n오늘 21:00 프로기노바 2mg 복용", Want(doses = listOf(
            Dose(Drug.ESTRADIOL_TABLET, 2.0, "2026-10-24 09:00"), Dose(Drug.ESTRADIOL_TABLET, 2.0, "2026-10-24 21:00")))),
        one("M56", "방금 프로기노바 2mg과 안쿨 25mg 복용", noneModelAllowed),
        one("M57", "오늘 10:00 E2 100pg/mL 검사 기록해줘", Want(labs = listOf(LabRec(Analyte.ESTRADIOL, 100.0, "pg/mL")))),
        one("M58", "오늘 머리가 아프고 피곤했어 메모해줘", none),
        one("M59", "몸무게 60kg 기록해줘", none),
        one("M60", "혈압 120/80 기록해줘", none),
        one("M61", "다음 진료 때 어지럼 얘기할 것 메모해줘", none),
        one("M62", "최근 기록 보여줘", none),
    )

    private val sequences = listOf(
        seq("F01", "프로기노바 2mg 복용", "방금", expect = save("2026-10-24 23:00", 2.0)),
        seq("F02", "10월 24일 10시 프로기노바 2mg 복용", "오전 10시", "네", expect = save("2026-10-24 10:00", 2.0)),
        seq("F03", "10월 24일 10시 프로기노바 2mg 복용", "네", "오전", expect = save("2026-10-24 10:00", 2.0)),
        seq("F04", "방금 프로기노바 2 복용", "mg", expect = save("2026-10-24 23:00", 2.0)),
        seq("F05", "방금 프로기노바 2 복용", "2mg", expect = save("2026-10-24 23:00", 2.0)),
        seq("F06", "오늘 프로기노바 2mg 복용", "10시", "오전", expect = save("2026-10-24 10:00", 2.0)),
        seq("F07", "오늘 프로기노바 2mg 복용", "10시", "오전 10시", expect = save("2026-10-24 10:00", 2.0)),
        seq("F08", "방금 프로기노바 2mg 복용 못했어", expect = none),
        seq("F09", "방금 프로기노바 2mg 복용하지 않았어", "방금 프로기노바 2mg 복용", expect = save("2026-10-24 23:00", 2.0)),
        seq("F10", "프로기노바 2mg 복용", "방금 안쿨 25mg 복용", expect = save("2026-10-24 23:00", 25.0, Drug.CYPROTERONE)),
    )

    @Test fun matrixMatchesStrictExpectations() {
        val report = StringBuilder("id\tinputs\texpected\tactual\tmodelCalls\tresult\n")
        val failures = mutableListOf<String>()
        for (row in rows + sequences) {
            val h = ChatHarness()
            row.inputs.forEach { h.send(it) }
            val savedDoses = h.doses.map { Dose(it.drug, it.amountEntered, ChatHarness.kst(it.occurredAt)) }
            val savedLabs = h.state.labs.flatMap { lab -> lab.analytes.map { LabRec(it.analyte, it.reportedValue, it.reportedUnit) } }
            val want = row.expect
            val wrong = EvalScorer.surplus(savedDoses, want.doses) + EvalScorer.surplus(savedLabs, want.labs)
            val missed = EvalScorer.surplus(want.doses, savedDoses) + EvalScorer.surplus(want.labs, savedLabs)
            val reply = h.lastReplyOrEmpty()
            val refusalOk = !want.refuse || (reply.isNotEmpty() && !reply.startsWith("저장 완료"))
            val modelOk = want.modelAllowed || h.modelCalls == 0
            val result = if (wrong == 0 && missed == 0 && refusalOk && modelOk) "PASS" else "FAIL"
            val expected = (want.doses.map { "${it.at} ${it.mg}mg ${it.drug}" } + want.labs.map { "lab ${it.analyte} ${it.value} ${it.unit}" })
                .ifEmpty { listOf("none") }.joinToString(";")
            val actual = (savedDoses.map { "${it.at} ${it.mg}mg ${it.drug}" } + savedLabs.map { "lab ${it.analyte} ${it.value} ${it.unit}" })
                .ifEmpty { listOf("none") }.joinToString(";")
            report.append("${row.id}\t${row.inputs.joinToString(" → ").replace("\n", " / ")}\t$expected\t$actual\t${h.modelCalls}\t$result\n")
            if (result == "FAIL") failures += "${row.id} expected=$expected actual=$actual wrong=$wrong missed=$missed refusalOk=$refusalOk modelCalls=${h.modelCalls} reply=$reply"
        }
        File("build/reports").mkdirs()
        File("build/reports/chat-matrix.tsv").writeText(report.toString())
        if (failures.isNotEmpty()) fail("${failures.size} strict matrix rows failed:\n" + failures.joinToString("\n"))
    }

    private fun ChatHarness.lastReplyOrEmpty(): String = runCatching { lastReply }.getOrDefault("")
}
