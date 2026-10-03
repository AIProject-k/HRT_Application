package com.hormonelog.app.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.clinics.ClinicsScreen
import com.hormonelog.app.feature.common.BottomTabs
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.ToastOverlay
import com.hormonelog.app.feature.dose.DoseSheet
import com.hormonelog.app.feature.flow.FlowScreen
import com.hormonelog.app.feature.home.HomeScreen
import com.hormonelog.app.feature.lab.LabSheet
import com.hormonelog.app.feature.me.MeScreen
import com.hormonelog.app.feature.timeline.TimelineScreen
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import com.hormonelog.app.ui.components.ConfirmDialog
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import java.time.Instant
import java.time.ZoneId

/** Callback surface for the whole dashboard — one lambda per reducer transition. */
class DashboardActions(
    val onTab: (DashboardTab) -> Unit,
    val onOpenDose: () -> Unit,
    val onOpenLab: () -> Unit,
    val onCloseSheet: () -> Unit,
    val onEditDose: ((DoseDraft) -> DoseDraft) -> Unit,
    val onSetDoseDrug: (Drug) -> Unit,
    val onSetDoseRoute: (Route) -> Unit,
    val onSetDoseStatus: (DoseStatus) -> Unit,
    val onStepDose: (Boolean) -> Unit,
    val onSetDoseAmount: (String) -> Unit,
    val onSaveDose: () -> Unit,
    val onSaveRegimen: () -> Unit,
    val onLoadSample: () -> Unit,
    val onImportCsv: (String) -> Unit,
    val exportCsvText: () -> String,
    val onOpenClinics: () -> Unit,
    val onCloseClinics: () -> Unit,
    val onNewClinic: () -> Unit,
    val onEditClinic: (java.util.UUID) -> Unit,
    val onEditClinicDraft: ((ClinicDraft) -> ClinicDraft) -> Unit,
    val onCancelClinicDraft: () -> Unit,
    val onSaveClinic: () -> Unit,
    val onDeleteClinic: (java.util.UUID) -> Unit,
    val onEditLab: ((LabDraft) -> LabDraft) -> Unit,
    val onFocusLab: (LabField) -> Unit,
    val onKeyLab: (String) -> Unit,
    val onSaveLab: () -> Unit,
    val onBeginEditDose: (java.util.UUID) -> Unit,
    val onBeginEditLab: (java.util.UUID) -> Unit,
    val onDeleteDose: (java.util.UUID) -> Unit,
    val onDeleteLab: (java.util.UUID) -> Unit,
    val onDeleteRegimen: (java.util.UUID) -> Unit,
    val onClearDoses: () -> Unit,
    val onClearLabs: () -> Unit,
    val onClearAll: () -> Unit,
    val onSeries: (HormoneSeries) -> Unit,
    val onRange: (ChartRange) -> Unit,
    val onScrub: (Float?) -> Unit,
    val onFilter: (TimelineFilter) -> Unit,
    val onDismissToast: () -> Unit,
    val onUndo: () -> Unit,
    val onConfirmDuplicate: () -> Unit,
    val onCancelDuplicate: () -> Unit,
    val onDismissStorageWarning: () -> Unit,
    val onConfirmBackfill: () -> Unit,
    val onCancelBackfill: () -> Unit,
)

@Composable
fun DashboardScreen(
    state: DashboardState,
    now: Instant,
    zone: ZoneId,
    actions: DashboardActions,
    modifier: Modifier = Modifier,
) {
    val lastEstrogenDose: DoseEvent? = state.doses
        .filter { it.drug != Drug.SPIRONOLACTONE && it.drug != Drug.CYPROTERONE && it.status.wasTaken }
        .maxByOrNull { it.occurredAt }
    val collectedAt = resolveLabTime(state.labDraft.time, now, zone, state.labDraft.customEpochMillis)
    val lastDoseBeforeLab = collectedAt?.let { time ->
        state.doses.filter {
            it.drug != Drug.SPIRONOLACTONE && it.drug != Drug.CYPROTERONE && it.status.wasTaken &&
                !it.occurredAt.isAfter(time)
        }.maxByOrNull { it.occurredAt }
    }

    // System back closes the topmost overlay before leaving the app.
    BackHandler(enabled = state.clinicDraft != null || state.clinicsOpen || state.sheet != DashboardSheet.NONE) {
        when {
            state.clinicDraft != null -> actions.onCancelClinicDraft()
            state.clinicsOpen -> actions.onCloseClinics()
            state.sheet != DashboardSheet.NONE -> actions.onCloseSheet()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(HlColor.Background)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            state.storageWarning?.let { StorageWarningBanner(it, actions.onDismissStorageWarning) }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (state.tab) {
                    DashboardTab.HOME -> HomeScreen(
                        state = state, now = now, zone = zone,
                        onOpenDose = actions.onOpenDose,
                        onOpenLab = actions.onOpenLab,
                        onGoFlow = { actions.onTab(DashboardTab.FLOW) },
                        onGoTimeline = { actions.onTab(DashboardTab.TIMELINE) },
                        onGoModel = { actions.onTab(DashboardTab.ME) },
                    )
                    DashboardTab.TIMELINE -> TimelineScreen(
                        state = state, now = now, zone = zone, onFilter = actions.onFilter,
                        onDeleteDose = actions.onDeleteDose,
                        onDeleteLab = actions.onDeleteLab,
                        onEditDose = actions.onBeginEditDose,
                        onEditLab = actions.onBeginEditLab,
                    )
                    DashboardTab.FLOW -> FlowScreen(
                        state = state, now = now, zone = zone,
                        onSeries = actions.onSeries,
                        onRange = actions.onRange,
                        onScrub = actions.onScrub,
                        onGoModel = { actions.onTab(DashboardTab.ME) },
                    )
                    DashboardTab.ME -> MeScreen(
                        state = state, now = now,
                        onLoadSample = actions.onLoadSample,
                        onOpenClinics = actions.onOpenClinics,
                        onImportCsv = actions.onImportCsv,
                        exportCsvText = actions.exportCsvText,
                        onDeleteRegimen = actions.onDeleteRegimen,
                        onClearDoses = actions.onClearDoses,
                        onClearLabs = actions.onClearLabs,
                        onClearAll = actions.onClearAll,
                    )
                }
            }
            BottomTabs(current = state.tab, onSelect = actions.onTab)
        }

        ToastOverlay(
            text = state.toast,
            onUndo = state.undo?.let { actions.onUndo },
            onDismiss = actions.onDismissToast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )

        when (state.sheet) {
            DashboardSheet.DOSE -> DoseSheet(
                draft = state.doseDraft, now = now, zone = zone, lastDose = lastEstrogenDose,
                onEdit = actions.onEditDose,
                onSetDrug = actions.onSetDoseDrug,
                onSetRoute = actions.onSetDoseRoute,
                onSetStatus = actions.onSetDoseStatus,
                onStep = actions.onStepDose,
                onSetAmount = actions.onSetDoseAmount,
                onClose = actions.onCloseSheet,
                onSave = actions.onSaveDose,
                onSaveRegimen = actions.onSaveRegimen,
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
            DashboardSheet.LAB -> LabSheet(
                draft = state.labDraft, now = now, zone = zone, lastDose = lastDoseBeforeLab,
                onEdit = actions.onEditLab,
                onFocus = actions.onFocusLab,
                onKey = actions.onKeyLab,
                onClose = actions.onCloseSheet,
                onSave = actions.onSaveLab,
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
            DashboardSheet.NONE -> Unit
        }

        state.pendingBackfill?.let { p ->
            ConfirmDialog(
                title = p.title,
                body = p.body,
                confirmLabel = "${p.doses.size}건 기록",
                onConfirm = actions.onConfirmBackfill,
                onDismiss = actions.onCancelBackfill,
            )
        }

        // A same-drug record within a few hours is nearly always a re-tap, so the save
        // waits here rather than quietly doubling the dose the curve engine sees.
        state.duplicateDose?.let { dup ->
            ConfirmDialog(
                title = "이미 비슷한 시각에 기록이 있어요",
                body = "${fmtDate(dup.occurredAt, zone)} ${fmtTime(dup.occurredAt, zone)} 에 ${dup.drug.label} " +
                    "${trimDose(dup.amountEntered)}${dup.enteredUnit.label} 기록이 있어요. 그래도 새로 기록할까요?",
                confirmLabel = "그래도 기록",
                onConfirm = actions.onConfirmDuplicate,
                onDismiss = actions.onCancelDuplicate,
            )
        }

        if (state.clinicsOpen) {
            ClinicsScreen(
                clinics = state.clinics,
                draft = state.clinicDraft,
                onClose = actions.onCloseClinics,
                onNew = actions.onNewClinic,
                onEdit = actions.onEditClinic,
                onEditDraft = actions.onEditClinicDraft,
                onCancelDraft = actions.onCancelClinicDraft,
                onSave = actions.onSaveClinic,
                onDelete = actions.onDeleteClinic,
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
        }
    }
}

private fun trimDose(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/**
 * Sits above everything and stays until dismissed. A storage failure is the one
 * message where a timeout could cost the user their records.
 */
@Composable
private fun StorageWarningBanner(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HlColor.YellowTintSoft)
            .border(1.dp, HlColor.YellowTint, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("⚠️", style = HlType.Caption)
        Text(text, style = HlType.BodySm, color = HlColor.TextPrimary, modifier = Modifier.weight(1f))
        Text(
            "닫기",
            style = HlType.LabelStrong,
            color = HlColor.Yellow,
            modifier = Modifier.clickable(onClick = onDismiss),
        )
    }
}
