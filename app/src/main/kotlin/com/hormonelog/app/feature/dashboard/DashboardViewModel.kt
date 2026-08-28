package com.hormonelog.app.feature.dashboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.hormonelog.core.data.CsvIo
import com.hormonelog.core.data.RecordStore
import java.io.File
import java.time.Instant

/**
 * Holds the whole [DashboardState] and persists the record-bearing parts
 * (doses, labs, regimens) to a plain-JSON file after any change that touches
 * them. Transient UI transitions (tab, sheet, scrub, drafts, toast) are not
 * persisted.
 */
class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val store = RecordStore(File(app.filesDir, "records.json"))

    var state by mutableStateOf(DashboardState())
        private set

    init {
        val snap = store.load()
        state = state.copy(doses = snap.doses, labs = snap.labs, regimens = snap.regimens, clinics = snap.clinics)
    }

    private fun set(next: DashboardState) { state = next }

    private fun setAndPersist(next: DashboardState) {
        state = next
        store.save(RecordStore.Snapshot(state.doses, state.labs, state.regimens, state.clinics))
    }

    // ── transient ──
    fun switchTab(tab: DashboardTab) = set(DashboardReducer.switchTab(state, tab))
    fun openSheet(sheet: DashboardSheet) = set(DashboardReducer.openSheet(state, sheet))
    fun closeSheet() = set(DashboardReducer.closeSheet(state))
    fun editDose(block: (DoseDraft) -> DoseDraft) = set(DashboardReducer.editDose(state, block))
    fun stepDose(up: Boolean) = set(DashboardReducer.stepDose(state, up))
    fun setDoseAmount(text: String) = set(DashboardReducer.setDoseAmount(state, text))
    fun editLab(block: (LabDraft) -> LabDraft) = set(DashboardReducer.editLab(state, block))
    fun focusLabField(field: LabField) = set(DashboardReducer.focusLabField(state, field))
    fun pressKey(key: String) = set(DashboardReducer.pressKey(state, key))
    fun setSeries(series: HormoneSeries) = set(DashboardReducer.setSeries(state, series))
    fun setRange(range: ChartRange) = set(DashboardReducer.setRange(state, range))
    fun setScrub(fraction: Float?) = set(DashboardReducer.setScrub(state, fraction))
    fun setTimelineFilter(filter: TimelineFilter) = set(DashboardReducer.setTimelineFilter(state, filter))
    fun dismissToast() = set(DashboardReducer.dismissToast(state))

    fun openClinics() = set(DashboardReducer.openClinics(state))
    fun closeClinics() = set(DashboardReducer.closeClinics(state))
    fun newClinic() = set(DashboardReducer.newClinic(state))
    fun editClinic(id: java.util.UUID) = set(DashboardReducer.editClinic(state, id))
    fun editClinicDraft(block: (ClinicDraft) -> ClinicDraft) = set(DashboardReducer.editClinicDraft(state, block))
    fun cancelClinicDraft() = set(DashboardReducer.cancelClinicDraft(state))

    // ── persisted ──
    fun saveDose(now: Instant) = setAndPersist(DashboardReducer.saveDose(state, now))
    fun saveLab(now: Instant) = setAndPersist(DashboardReducer.saveLab(state, now))
    fun saveRegimen(now: Instant) = setAndPersist(DashboardReducer.saveRegimen(state, now))
    fun loadSampleRegimen(now: Instant) = setAndPersist(DashboardReducer.loadSampleRegimen(state, now))
    fun saveClinic() = setAndPersist(DashboardReducer.saveClinic(state))
    fun deleteClinic(id: java.util.UUID) = setAndPersist(DashboardReducer.deleteClinic(state, id))

    fun deleteDose(id: java.util.UUID) = setAndPersist(DashboardReducer.deleteDose(state, id))
    fun deleteLab(id: java.util.UUID) = setAndPersist(DashboardReducer.deleteLab(state, id))
    fun deleteRegimen(id: java.util.UUID) = setAndPersist(DashboardReducer.deleteRegimen(state, id))
    fun clearDoses() = setAndPersist(DashboardReducer.clearDoses(state))
    fun clearLabs() = setAndPersist(DashboardReducer.clearLabs(state))
    fun clearAllRecords() = setAndPersist(DashboardReducer.clearAllRecords(state))

    fun importCsv(text: String) {
        val imp = CsvIo.parse(text)
        setAndPersist(DashboardReducer.mergeImported(state, imp.doses, imp.labs, imp.skipped))
    }

    fun exportCsv(): String = CsvIo.export(state.doses, state.labs)
}
