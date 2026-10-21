package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class P4ContextIntegrationTest {
    private val now = at(2026, 10, 19, 12)
    private val recorded = P4Context(P4Product.UTROGESTAN_SOFT_CAPSULE, P4Meal.FASTING)
    private val previous = dose(now.minusSeconds(86400), Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0).copy(p4Context = recorded)

    @Test fun aSavedDoseKeepsExplicitConditionsAndAnEditRestoresThem() {
        val s = AppState(doseDraft = DoseDraft(drug = Drug.PROGESTERONE_MICRONIZED, route = Route.ORAL,
            amountText = "200", p4Context = recorded))
        val saved = RecordsReducer.saveDose(s, now, SEOUL)
        assertEquals(recorded, saved.doses.single().p4Context)
        assertEquals(recorded, RecordsReducer.editDose(saved, saved.doses.single().id).doseDraft.p4Context)
    }

    @Test fun aNewDoseAndAFrequentComboDoNotCopyAnOldMeal() {
        val s = AppState(doses = listOf(previous))
        val draft = RecordsReducer.openDose(s).doseDraft
        assertEquals(recorded.product, draft.p4Context.product)
        assertEquals(P4Meal.UNKNOWN, draft.p4Context.meal)
        val chosen = RecordsReducer.pickCombo(s, frequentCombos(s.doses, now).single()).doseDraft
        assertEquals(recorded.product, chosen.p4Context.product)
        assertEquals(P4Meal.UNKNOWN, chosen.p4Context.meal)
    }

    @Test fun duplicatingAnOldDoseDoesNotAssertTheOldMealForTheNewDose() {
        val duplicated = TimelineOps.duplicate(AppState(doses = listOf(previous)), RowTarget.Dose(previous.id), now, SEOUL)
        val current = duplicated.doses.first { it.id != previous.id }
        assertEquals(recorded.product, current.p4Context!!.product)
        assertEquals(P4Meal.UNKNOWN, current.p4Context!!.meal)
    }

    @Test fun aRepeatingPlanKeepsItsExpectationButBackfillDoesNotAssertMeals() {
        val s = AppState(doseDraft = DoseDraft(drug = Drug.PROGESTERONE_MICRONIZED, route = Route.ORAL,
            amountText = "200", repeat = true, interval = IntervalChoice.DAILY, p4Context = recorded))
        val saved = RecordsReducer.saveRegimen(s, now, SEOUL)
        val plan = saved.regimens.single()
        assertEquals(recorded, plan.p4Context)
        val predicted = Analysis.planned(listOf(plan), emptyList(), now, now.plusSeconds(2 * 86400), SEOUL)
        assertTrue(predicted.isNotEmpty())
        assertTrue(predicted.all { it.p4Context == recorded })
        val actual = Regimen.expand(plan, now.plusSeconds(86400), SEOUL)
        assertTrue(actual.all { it.p4Context?.meal == P4Meal.UNKNOWN })
    }

    @Test fun estrogenRecordsDoNotGainP4Conditions() {
        val saved = RecordsReducer.saveDose(AppState(doseDraft = DoseDraft(amountText = "5", p4Context = recorded)), now, SEOUL)
        assertNull(saved.doses.single().p4Context)
    }

    @Test fun theCurrentReferenceDoesNotPretendToApplyAnUnvalidatedFoodMultiplier() {
        val first = Analysis.p4At(listOf(previous), now)
        val fed = Analysis.p4At(listOf(previous.copy(p4Context = recorded.copy(meal = P4Meal.HIGH_FAT_MEAL))), now)
        assertEquals(first, fed)
        assertTrue(com.hormonelog.core.evidence.ProgesteroneEvidence.LIMITS.contains("조건별 새 모델은 검증 중"))
    }
}
