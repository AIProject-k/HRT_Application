package com.hormonelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hormonelog.app.feature.dashboard.DashboardActions
import com.hormonelog.app.feature.dashboard.DashboardScreen
import com.hormonelog.app.feature.dashboard.DashboardSheet
import com.hormonelog.app.feature.dashboard.DashboardViewModel
import com.hormonelog.app.ui.theme.HormoneLogTheme
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HormoneLogTheme {
                val now = remember { Instant.now() }
                val zone = remember { ZoneId.systemDefault() }
                val vm: DashboardViewModel = viewModel()

                DashboardScreen(
                    state = vm.state,
                    now = now,
                    zone = zone,
                    actions = DashboardActions(
                        onTab = vm::switchTab,
                        onOpenDose = { vm.openSheet(DashboardSheet.DOSE) },
                        onOpenLab = { vm.openSheet(DashboardSheet.LAB) },
                        onCloseSheet = vm::closeSheet,
                        onEditDose = vm::editDose,
                        onSetDoseDrug = vm::setDoseDrug,
                        onSetDoseRoute = vm::setDoseRoute,
                        onSetDoseStatus = vm::setDoseStatus,
                        onStepDose = vm::stepDose,
                        onSetDoseAmount = vm::setDoseAmount,
                        onSaveDose = { vm.saveDose(now) },
                        onSaveRegimen = { vm.saveRegimen(now) },
                        onLoadSample = { vm.loadSampleRegimen(now) },
                        onImportCsv = vm::importCsv,
                        exportCsvText = vm::exportCsv,
                        onOpenClinics = vm::openClinics,
                        onCloseClinics = vm::closeClinics,
                        onNewClinic = vm::newClinic,
                        onEditClinic = vm::editClinic,
                        onEditClinicDraft = vm::editClinicDraft,
                        onCancelClinicDraft = vm::cancelClinicDraft,
                        onSaveClinic = vm::saveClinic,
                        onDeleteClinic = vm::deleteClinic,
                        onEditLab = vm::editLab,
                        onFocusLab = vm::focusLabField,
                        onKeyLab = vm::pressKey,
                        onSaveLab = { vm.saveLab(now) },
                        onBeginEditDose = vm::beginEditDose,
                        onBeginEditLab = vm::beginEditLab,
                        onDeleteDose = vm::deleteDose,
                        onDeleteLab = vm::deleteLab,
                        onDeleteRegimen = vm::deleteRegimen,
                        onClearDoses = vm::clearDoses,
                        onClearLabs = vm::clearLabs,
                        onClearAll = vm::clearAllRecords,
                        onSeries = vm::setSeries,
                        onRange = vm::setRange,
                        onScrub = vm::setScrub,
                        onFilter = vm::setTimelineFilter,
                        onDismissToast = vm::dismissToast,
                        onUndo = vm::undoLast,
                        onConfirmDuplicate = { vm.saveDose(now, force = true) },
                        onCancelDuplicate = vm::cancelDuplicate,
                    ),
                )
            }
        }
    }
}
