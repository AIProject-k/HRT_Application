package com.hormonelog.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import com.hormonelog.app.feature.dashboard.DashboardActions
import com.hormonelog.app.feature.dashboard.DashboardReducer
import com.hormonelog.app.feature.dashboard.DashboardScreen
import com.hormonelog.app.feature.dashboard.DashboardSheet
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.dashboard.DashboardTab
import com.hormonelog.app.feature.dashboard.LabDraft
import com.hormonelog.app.feature.dashboard.LabTimeChoice
import com.hormonelog.app.ui.theme.HormoneLogTheme
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(initial: DashboardState = DashboardState()) {
        composeRule.setContent {
            HormoneLogTheme {
                val now = remember { Instant.parse("2026-08-27T12:10:00Z") }
                val zone = remember { ZoneId.of("Asia/Seoul") }
                var state by remember { mutableStateOf(initial) }
                DashboardScreen(
                    state = state,
                    now = now,
                    zone = zone,
                    actions = DashboardActions(
                        onTab = { state = DashboardReducer.switchTab(state, it) },
                        onOpenDose = { state = DashboardReducer.openSheet(state, DashboardSheet.DOSE) },
                        onOpenLab = { state = DashboardReducer.openSheet(state, DashboardSheet.LAB) },
                        onCloseSheet = { state = DashboardReducer.closeSheet(state) },
                        onEditDose = { block -> state = DashboardReducer.editDose(state, block) },
                        onSetDoseDrug = { state = DashboardReducer.setDoseDrug(state, it) },
                        onSetDoseRoute = { state = DashboardReducer.setDoseRoute(state, it) },
                        onSetDoseStatus = { state = DashboardReducer.setDoseStatus(state, it) },
                        onStepDose = { up -> state = DashboardReducer.stepDose(state, up) },
                        onSetDoseAmount = { text -> state = DashboardReducer.setDoseAmount(state, text) },
                        onSaveDose = { state = DashboardReducer.saveDose(state, now) },
                        onSaveRegimen = { state = DashboardReducer.saveRegimen(state, now) },
                        onLoadSample = { state = DashboardReducer.loadSampleRegimen(state, now) },
                        onImportCsv = {},
                        exportCsvText = { "" },
                        onOpenClinics = { state = DashboardReducer.openClinics(state) },
                        onCloseClinics = { state = DashboardReducer.closeClinics(state) },
                        onNewClinic = { state = DashboardReducer.newClinic(state) },
                        onEditClinic = { id -> state = DashboardReducer.editClinic(state, id) },
                        onEditClinicDraft = { block -> state = DashboardReducer.editClinicDraft(state, block) },
                        onCancelClinicDraft = { state = DashboardReducer.cancelClinicDraft(state) },
                        onSaveClinic = { state = DashboardReducer.saveClinic(state) },
                        onDeleteClinic = { id -> state = DashboardReducer.deleteClinic(state, id) },
                        onEditLab = { block -> state = DashboardReducer.editLab(state, block) },
                        onFocusLab = { field -> state = DashboardReducer.focusLabField(state, field) },
                        onKeyLab = { key -> state = DashboardReducer.pressKey(state, key) },
                        onSaveLab = { state = DashboardReducer.saveLab(state, now) },
                        onBeginEditDose = { state = DashboardReducer.beginEditDose(state, it) },
                        onBeginEditLab = { state = DashboardReducer.beginEditLab(state, it) },
                        onDeleteDose = { id -> state = DashboardReducer.deleteDose(state, id) },
                        onDeleteLab = { id -> state = DashboardReducer.deleteLab(state, id) },
                        onDeleteRegimen = { id -> state = DashboardReducer.deleteRegimen(state, id) },
                        onClearDoses = { state = DashboardReducer.clearDoses(state) },
                        onClearLabs = { state = DashboardReducer.clearLabs(state) },
                        onClearAll = { state = DashboardReducer.clearAllRecords(state) },
                        onSeries = { state = DashboardReducer.setSeries(state, it) },
                        onRange = { state = DashboardReducer.setRange(state, it) },
                        onScrub = { state = DashboardReducer.setScrub(state, it) },
                        onFilter = { state = DashboardReducer.setTimelineFilter(state, it) },
                        onDismissToast = { state = DashboardReducer.dismissToast(state) },
                        onUndo = { state = DashboardReducer.undoLast(state) },
                        onConfirmDuplicate = { state = DashboardReducer.saveDose(state, now, force = true) },
                        onCancelDuplicate = { state = DashboardReducer.cancelDuplicate(state) },
                        onDismissStorageWarning = { state = DashboardReducer.dismissStorageWarning(state) },
                        onConfirmBackfill = { state = DashboardReducer.confirmBackfill(state) },
                        onCancelBackfill = { state = DashboardReducer.cancelBackfill(state) },
                    ),
                )
            }
        }
    }

    @Test
    fun dashboard_labels_estimate_and_measured_values_separately() {
        setContent()
        composeRule.onNodeWithText("예상 수치 · 실제 검사값 아님").assertExists()
        composeRule.onNodeWithText("마지막 검사").assertExists()
    }

    @Test
    fun recording_a_dose_shows_toast_and_lands_in_the_timeline() {
        setContent()
        composeRule.onNodeWithText("투약 기록").performClick() // home action button opens the sheet
        composeRule.onNodeWithText("기록하기", substring = true).performClick() // sheet CTA
        composeRule.onNodeWithText("투약 기록됨", substring = true).assertExists()

        composeRule.onNodeWithText("타임라인").performClick() // bottom tab
        composeRule.onNodeWithText("에스트라디올 발레레이트").assertExists()
    }

    @Test
    fun invalidLabInputShowsAnErrorAndDisablesSaving() {
        setContent(DashboardState(sheet = DashboardSheet.LAB, labDraft = LabDraft(e2 = "1..2", tt = "20")))
        composeRule.onNodeWithText("올바른 숫자를 입력해 주세요").assertExists()
        composeRule.onNodeWithText("입력값을 확인해 주세요").assertIsNotEnabled()
    }

    @Test
    fun pastLabUsesTheLastActualDoseBeforeCollection() {
        val collection = Instant.parse("2026-08-10T12:10:00Z")
        var state = DashboardReducer.saveDose(DashboardState(), collection.minusSeconds(2 * 86400L))
        state = DashboardReducer.saveDose(state, collection.plusSeconds(5 * 86400L))
        setContent(state.copy(sheet = DashboardSheet.LAB, labDraft = LabDraft(
            e2 = "120", time = LabTimeChoice.CUSTOM, customEpochMillis = collection.toEpochMilli(),
        )))
        composeRule.onNodeWithText("마지막 투약(8/8)으로부터 약 2일 뒤 채혈로 기록돼요.")
            .performScrollTo().assertExists()
    }

    @Test
    fun labWithoutAPrecedingDoseHasNoIntervalMessage() {
        val collection = Instant.parse("2026-08-10T12:10:00Z")
        val state = DashboardReducer.saveDose(DashboardState(), collection.plusSeconds(86400L))
        setContent(state.copy(sheet = DashboardSheet.LAB, labDraft = LabDraft(
            e2 = "120", time = LabTimeChoice.CUSTOM, customEpochMillis = collection.toEpochMilli(),
        )))
        composeRule.onNodeWithText("뒤 채혈로 기록돼요", substring = true).assertDoesNotExist()
    }

    @Test
    fun meScreenKeepsOnlyTheWorkingExportButton() {
        val state = DashboardReducer.saveDose(DashboardState(), Instant.parse("2026-08-27T12:10:00Z"))
        setContent(state.copy(tab = DashboardTab.ME))
        composeRule.onNodeWithText("데이터 내보내기").assertDoesNotExist()
        composeRule.onNodeWithText("CSV 내보내기").performScrollTo().assertIsEnabled()
    }
}
