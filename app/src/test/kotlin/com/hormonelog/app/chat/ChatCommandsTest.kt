package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ChatCommandsTest {
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val id = UUID.randomUUID()
    private val text = "오늘 오후 3시에 EV 5 mg 근육주사 맞았어 기록해줘"
    private val dose = ChatCommand("dose", drug = "EV", route = "근육주사", amount = "5 mg", date = "오늘", time = "오후 3시")
    private fun apply(c: ChatCommand = dose, input: String = text, s: AppState = AppState()) =
        ChatCommands.apply(s, c, listOf(input), id, now, zone)

    @Test fun completeDoseUsesExactTimeAndRequestIdentity() {
        val result = apply()
        val event = result.next!!.doses.single()
        assertEquals(id, event.id)
        assertEquals(Instant.parse("2026-10-19T06:00:00Z"), event.occurredAt)
        assertEquals(5.0, event.amountEntered, 0.0)
        assertNull(result.next.toast)
    }
    @Test fun unsupportedDepotDoesNotGuessEster() {
        assertNull(apply(dose.copy(drug = "데포"), text.replace("EV", "데포")).next)
    }
    @Test fun fabricatedAmountIsRefusedEvenWhenModelSaysDose() {
        assertNull(apply(dose.copy(amount = "10 mg")).next)
    }
    @Test fun smallerNumberCannotMatchInsideAnotherAmount() {
        assertNull(apply(input = text.replace("5 mg", "15 mg")).next)
    }
    @Test fun omittedMinutesCannotBeTruncatedToWholeHour() {
        assertNull(apply(input = text.replace("오후 3시", "오후 3시 30분")).next)
    }
    @Test fun unrelatedMessageCannotReplayEarlierRegistration() {
        assertNull(apply(input = "고마워 EV 5 mg 근육주사 오늘 오후 3시").next)
    }
    @Test fun negatedRequestCannotWrite() {
        assertNull(apply(input = "$text 하지만 기록하지 마").next)
    }
    @Test fun ambiguousThreeOClockDoesNotChooseAmOrPm() {
        assertNull(apply(dose.copy(time = "3시"), text.replace("오후 3시", "3시")).next)
    }
    @Test fun futureDoseCannotBeRecordedAsTaken() {
        assertNull(apply(dose.copy(date = "내일"), text.replace("오늘", "내일")).next)
    }
    @Test fun sameRequestOrNearbyDoseNeverWritesTwice() {
        val s = apply().next!!
        assertNull(apply(s = s).next)
        val duplicate = ChatCommands.apply(s, dose, listOf(text), UUID.randomUUID(), now, zone)
        assertNull(duplicate.next)
    }
    @Test fun MillilitersAreNotConvertedWithoutConcentration() {
        assertNull(apply(dose.copy(amount = "1 mL"), text.replace("5 mg", "1 mL")).next)
    }
    @Test fun usualDoseRequiresOneExistingMatchingPlan() {
        val input = "오늘 오후 3시 평소대로 데포 맞았어 기록해줘"
        val c = dose.copy(drug = "데포", route = "", amount = "", usual = true)
        assertNull(apply(c, input).next)
        val plan = Regimen(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, 7, now.minusSeconds(86400), null)
        assertNotNull(apply(c, input, AppState(regimens = listOf(plan))).next)
        assertNull(apply(c, input, AppState(regimens = listOf(plan, plan.copy(id = UUID.randomUUID())))).next)
    }
    @Test fun usualMustNotReplaceAnUnrecognizedExplicitProductOrRoute() {
        val plan = Regimen(UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, 7, now.minusSeconds(86400), null)
        val s = AppState(regimens = listOf(plan))
        val c = dose.copy(drug = "모르는제품", amount = "", usual = true)
        assertNull(apply(c, "오늘 오후 3시 평소대로 모르는제품 근육주사 기록해줘", s).next)
        assertNull(apply(c.copy(drug = "EV", route = "모르는경로"), "오늘 오후 3시 평소대로 EV 모르는경로 기록해줘", s).next)
    }
    @Test fun oralProgesteroneKeepsExplicitProductAndMeal() {
        val c = dose.copy(drug = "유트로게스탄", route = "경구", amount = "100 mg")
        val event = apply(c, "오늘 오후 3시 유트로게스탄 100 mg 경구 공복 복용했어 기록해줘").next!!.doses.single()
        assertEquals(P4Context(P4Product.UTROGESTAN_SOFT_CAPSULE, P4Meal.FASTING), event.p4Context)
    }
    @Test fun labKeepsReportedUnitsAndExplicitCollectionTime() {
        val c = ChatCommand("lab", date = "어제", time = "오전 9시", labs = listOf(ChatLabInput("P4", "2.5", "nmol/L")))
        val lab = apply(c, "어제 오전 9시 P4 2.5 nmol/L 검사 등록해줘").next!!.labs.single()
        assertEquals(id, lab.id)
        assertEquals("nmol/L", lab.analytes.single().reportedUnit)
        assertEquals(2.5 / 3.18, lab.analytes.single().canonicalValue!!, 0.00001)
        assertEquals(Instant.parse("2026-10-18T00:00:00Z"), lab.collectedAt)
    }
    @Test fun missingLabUnitCannotBorrowPreviousUnit() {
        assertNull(apply(ChatCommand("lab", date = "오늘", time = "오후 3시", labs = listOf(ChatLabInput("E2", "100", ""))), "오늘 오후 3시 E2 100 검사 기록해줘").next)
    }
    @Test fun duplicateLabDoesNotAppend() {
        val c = ChatCommand("lab", date = "어제", time = "오전 9시", labs = listOf(ChatLabInput("E2", "100", "pg/mL")))
        val input = "어제 오전 9시 E2 100 pg/mL 검사 등록해줘"
        val s = apply(c, input).next!!
        assertNull(ChatCommands.apply(s, c, listOf(input), UUID.randomUUID(), now, zone).next)
    }
    @Test fun malformedOrFailedCliOutputDoesNotBecomeAnAction() {
        assertThrows(Exception::class.java) { ChatProtocol.decode("{\"is_error\":true,\"result\":\"failed\"}") }
        assertThrows(Exception::class.java) { ChatProtocol.decode("{\"structured_output\":{\"action\":\"delete_all\"}}") }
    }
    @Test fun justTookUtrogestanNeedsNeitherTodayNorOralRepeated() {
        val c = ChatCommand("dose", drug = "유트로게스탄", amount = "100mg", time = "방금")
        val r = apply(c, "유트로게스탄 100mg 기록해줘 방금복용")
        assertNotNull(r.message, r.next)
        assertEquals(now, r.next!!.doses.single().occurredAt)
        assertEquals(Route.ORAL, r.next.doses.single().route)
    }
    @Test fun androcurUserAliasIsUnderstoodWithoutRequestingIngredientName() {
        for (name in listOf("안쿨", "안드로쿨", "안드로쿨정", "시프로테론", "시프로테론아세테이트", "Androcur")) {
            val c = ChatCommand("dose", drug = name, amount = "12.5mg", time = "방금")
            val r = apply(c, "방금먹은 $name 12.5mg 기록해줘")
            assertNotNull("$name: ${r.message}", r.next)
            assertEquals(Drug.CYPROTERONE, r.next!!.doses.single().drug)
            assertEquals(12.5, r.next.doses.single().amountEntered, 0.0)
        }
    }
    @Test fun reportsOfTakingDoNotRequireAnExtraRegistrationImperative() {
        val c = ChatCommand("dose", drug = "안쿨", amount = "12.5mg", time = "방금")
        assertNotNull(apply(c, "방금 안쿨 12.5mg 먹엇어").next)
        assertNotNull(apply(c, "방금 안쿨 12.5mg 복용").next)
        val injection = dose.copy(date = "", time = "방금")
        assertNotNull(apply(injection, "방금 EV 5 mg 근육주사 맞고 왔어").next)
    }
    @Test fun missingOnlyAmountAsksOnlyAmount() {
        val r = apply(ChatCommand("dose", drug = "유트로게스탄", time = "방금"), "유트로게스탄 방금복용 기록해줘")
        assertNull(r.next)
        assertTrue(r.message, r.message.contains("용량"))
        assertFalse(r.message.contains("날짜"))
        assertFalse(r.message.contains("제품명"))
        assertFalse(r.message.contains("500"))
    }
    @Test fun aliasDoesNotAssumeProductStrengthAsDose() {
        val r = apply(ChatCommand("dose", drug = "안쿨", time = "방금"), "안쿨 방금 먹었어 기록해줘")
        assertNull(r.next)
        assertTrue(r.message.contains("용량"))
    }
    @Test fun bareClockStillRequiresDateButNowDoesNot() {
        val r = apply(dose.copy(date = ""), text.replace("오늘 ", ""))
        assertNull(r.next)
        assertTrue(r.message, r.message.contains("날짜"))
    }
    @Test fun numericAmountAndItsUnitCanComeFromSeparateClarification() {
        val c = ChatCommand("dose", drug = "안쿨", amount = "12.5", amountUnit = "mg", time = "방금")
        val r = ChatCommands.apply(AppState(), c, listOf("방금 안쿨 12.5 먹었어", "안쿨은 mg 단위야"), id, now, zone)
        assertNotNull(r.message, r.next)
        assertEquals(12.5, r.next!!.doses.single().amountEntered, 0.0)
        assertNull(apply(c.copy(amountUnit = ""), "방금 안쿨 12.5 먹었어").next)
    }
    @Test fun multipleDrugsAreValidatedAndRecordedTogether() {
        val c = ChatCommand("batch", time = "방금", doses = listOf(
            dose.copy(date = "", time = "", amount = "5mg"),
            ChatCommand("dose", drug = "안쿨", amount = "12.5mg"),
        ))
        val input = "방금 EV 5mg 근육주사맞고 안쿨 12.5mg 먹엇어"
        val r = apply(c, input)
        assertNotNull(r.message, r.next)
        assertEquals(2, r.next!!.doses.size)
        assertEquals(2, r.next.doses.map { it.id }.distinct().size)
        assertTrue(r.next.doses.all { it.occurredAt == now })
        assertNull(apply(c, input, r.next).next)
    }
    @Test fun incompleteSecondDrugDoesNotPartiallyRecordFirstDrug() {
        val c = ChatCommand("batch", time = "방금", doses = listOf(
            dose.copy(date = "", time = "", amount = "5mg"), ChatCommand("dose", drug = "안쿨"),
        ))
        val r = apply(c, "방금 EV 5mg 근육주사맞고 안쿨 먹었어")
        assertNull(r.next)
        assertTrue(r.message, r.message.contains("안쿨") && r.message.contains("용량"))
    }
    @Test fun alsoUsesExplicitlySuppliedLocalPreviousTime() {
        val c = ChatCommand("dose", drug = "안쿨", amount = "12.5mg")
        val at = now.minusSeconds(120)
        val r = ChatCommands.apply(AppState(), c, listOf("안쿨 12.5mg도"), id, now, zone, previousDoseAt = at)
        assertNotNull(r.message, r.next)
        assertEquals(at, r.next!!.doses.single().occurredAt)
        assertNull(apply(c, "안쿨 12.5mg도").next)
    }
    @Test fun implicitTodayFromJustNowDoesNotRequireTheWordToday() {
        val c = ChatCommand("dose", drug = "유트로게스탄", amount = "100mg", date = "오늘", time = "방금")
        assertNotNull(apply(c, "유트로게스탄 100mg 방금 복용 기록해줘").next)
        assertNull(apply(c, "유트로게스탄 100mg 어제 방금 복용 기록해줘").next)
    }
    @Test fun oneDrugsUnitCannotBeBorrowedForAnotherDrug() {
        val c = ChatCommand("batch", time = "방금", doses = listOf(
            dose.copy(date = "", time = "", amount = "5mg"),
            ChatCommand("dose", drug = "안쿨", amount = "12.5", amountUnit = "mg"),
        ))
        val r = apply(c, "방금 EV 5mg 근육주사맞고 안쿨 12.5 먹엇어")
        assertNull(r.next)
        assertTrue(r.message, r.message.contains("단위"))
    }
    @Test fun askingBeforeTakingNeverRegistersEvenWithNowAndAlias() {
        val c = ChatCommand("dose", drug = "안쿨", amount = "12.5mg", time = "방금")
        assertNull(apply(c, "방금 안쿨 12.5mg 먹어도돼?").next)
        assertNull(apply(c, "방금 안쿨 12.5mg 복용전 확인해줘").next)
    }
}
