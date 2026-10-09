package com.hormonelog.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hormonelog.app.analysis.ReportLogic
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.platform.LauncherAlias
import com.hormonelog.app.platform.RecordCopies
import com.hormonelog.app.platform.Reminders
import com.hormonelog.app.platform.ReportFiles
import com.hormonelog.app.platform.Storage
import com.hormonelog.app.platform.ViewModelHolder
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.BackupUi
import com.hormonelog.app.state.Confirm
import com.hormonelog.app.state.MemoOps
import com.hormonelog.app.state.Nav
import com.hormonelog.app.state.OnboardingOps
import com.hormonelog.app.state.RecordsReducer
import com.hormonelog.app.state.ReportKind
import com.hormonelog.app.state.ReportOps
import com.hormonelog.app.state.SettingsOps
import com.hormonelog.app.state.RestoreIssue
import com.hormonelog.app.state.RestoreKind
import com.hormonelog.app.state.RestorePreview
import com.hormonelog.app.state.Screen
import com.hormonelog.app.state.ScheduleOps
import com.hormonelog.core.data.BackupCodec
import com.hormonelog.core.data.CsvIo
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.data.RecordStore
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Holds the whole [AppState]. Every change goes through a pure reducer; the ViewModel only
 * adds what is not pure: the clock, the files, the launcher.
 *
 * Records and settings are written off the main thread, one write at a time and in order,
 * and a write that fails is reported on screen instead of crashing or being lost silently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val recordStore = Storage.records(app)
    private val settingsStore = Storage.settings(app)
    private val files: File = app.filesDir
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** A built backup waiting for the file picker. */
    private var pendingBackup: ByteArray? = null

    /** The time every screen shows; it moves on, so nothing is frozen at the moment the app opened. */
    var now: Instant by mutableStateOf(Instant.now())
        private set

    var state by mutableStateOf(AppState())
        private set

    init {
        val storedSettings = settingsStore.load()
        var s = AppState(settings = storedSettings ?: AppSettings())
        when (val loaded = recordStore.load()) {
            is RecordStore.Load.Ok -> loaded.snapshot.let { snap ->
                s = s.withRecords(snap)
                if (snap.carriedCount > 0) {
                    s = s.copy(storageWarning = "이 버전이 읽지 못하는 기록 ${snap.carriedCount}건이 있어요. 새 버전에서 만든 기록이거나 값이 올바르지 않은 기록이에요. 지우지 않고 보관하고, ‘모든 기록 삭제’를 하면 함께 지워져요.")
                }
            }
            RecordStore.Load.Empty -> Unit
            // Start empty so the app is usable, but say so — silently showing an empty app
            // would read as "my records are gone" with no hint that they are not.
            is RecordStore.Load.Unreadable -> {
                val kept = loaded.quarantined
                s = s.copy(
                    storageWarning = if (kept != null) {
                        "기록 파일을 읽지 못했어요. 원본은 ${kept.name} 으로 따로 보관했고, 덮어쓰지 않아요. 백업 파일이 있으면 불러오기로 복구할 수 있어요."
                    } else {
                        "기록 파일을 읽지 못했어요. 원본을 옮기지도 못해, 새로 기록하면 덮어쓰일 수 있어요. 백업 파일을 먼저 만들어 두세요."
                    },
                )
            }
        }
        // Someone who was already using the app has no use for first-run setup.
        if (!s.settings.onboardingDone && s.hasRecords) s = s.copy(settings = s.settings.copy(onboardingDone = true))
        if (storedSettings == null || storedSettings != s.settings) persistSettings(s.settings)
        s = s.copy(screen = if (!s.settings.onboardingDone) Screen.ONBOARDING else Screen.HOME)
        state = s
        LauncherAlias.apply(app, s.settings.disguiseLauncher)
        ViewModelHolder.current = this
        Reminders.update(app, s)
    }

    override fun onCleared() {
        if (ViewModelHolder.current === this) ViewModelHolder.current = null
        super.onCleared()
    }

    private fun AppState.withRecords(snap: RecordSnapshot) = copy(
        doses = snap.doses, labs = snap.labs, regimens = snap.regimens, clinics = snap.clinics, memos = snap.memos,
        journal = snap.journal, stock = snap.stock, nextVisitMillis = snap.nextVisitMillis, carried = snap.carried,
    )

    // ── plumbing ──────────────────────────────────────────────
    private fun commit(next: AppState) {
        val prev = state
        state = next
        val recordsChanged = prev.doses !== next.doses || prev.labs !== next.labs || prev.regimens !== next.regimens ||
            prev.clinics !== next.clinics || prev.memos !== next.memos || prev.journal !== next.journal ||
            prev.stock !== next.stock || prev.nextVisitMillis != next.nextVisitMillis || prev.carried != next.carried
        if (recordsChanged) persistRecords(next)
        if (prev.settings != next.settings) {
            persistSettings(next.settings)
            if (prev.settings.disguiseLauncher != next.settings.disguiseLauncher) LauncherAlias.apply(getApplication(), next.settings.disguiseLauncher)
        }
        if (recordsChanged || prev.settings != next.settings) Reminders.update(getApplication(), next)
    }

    /** Apply a pure transition. */
    fun act(block: (AppState) -> AppState) = commit(block(state))

    private fun persistRecords(s: AppState) {
        val snapshot = s.snapshot
        viewModelScope.launch(io) {
            try {
                Storage.locked { recordStore.save(snapshot) }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    state = state.copy(
                        storageWarning = "마지막 기록을 저장하지 못했어요 (${e.message ?: "저장 공간 부족일 수 있어요"}). 공간을 비운 뒤 다시 시도해 주세요. 입력한 내용은 앱을 닫기 전까지 남아 있어요.",
                    )
                }
            }
        }
    }

    /** Off the main thread and in order with the writes of the records. */
    private fun purgeCopies() {
        val app = getApplication<Application>()
        viewModelScope.launch(io) { runCatching { Storage.purgeCopies(app) } }
    }

    private fun persistSettings(settings: AppSettings) {
        viewModelScope.launch(io) {
            try {
                Storage.locked { settingsStore.save(settings) }
            } catch (_: IOException) {
                // Settings are cheap to redo; the records warning is the one worth interrupting for.
            }
        }
    }

    // ── time ──────────────────────────────────────────────────
    fun tick() { now = Instant.now() }

    fun onResume() {
        now = Instant.now()
        val app = getApplication<Application>()
        viewModelScope.launch(io) { runCatching { Storage.purgeOldReports(app) } }
    }

    // ── records ───────────────────────────────────────────────
    fun undo() = act { RecordsReducer.undo(it) }
    fun saveDose() = act { RecordsReducer.saveDose(it, Instant.now(), zone) }
    fun saveRegimen() = act { RecordsReducer.saveRegimen(it, Instant.now(), zone) }
    fun quickLog() = act { RecordsReducer.quickLog(it, Instant.now(), zone) }
    fun saveLab() = act { RecordsReducer.saveLab(it, Instant.now(), zone) }

    /** A dose confirmed from a reminder or the widget while the app is open. */
    fun recordExternalDose(dose: DoseEvent) = act {
        it.copy(
            doses = (it.doses + dose).sortedBy { d -> d.occurredAt },
            stock = RecordsReducer.useStock(it.stock, dose),
            toast = null,
        )
    }

    /** Answer yes to whatever question is open. */
    fun confirm() {
        val c = state.confirm ?: return
        when (c) {
            is Confirm.DuplicateDose -> act { RecordsReducer.confirmDuplicate(it, Instant.now(), zone) }
            is Confirm.Backfill -> act { RecordsReducer.confirmBackfill(it) }
            is Confirm.ReplaceRegimen -> act { RecordsReducer.confirmReplace(it) }
            Confirm.ClearAll -> {
                act { RecordsReducer.clearAll(it) }
                // "All records" means the copies too: reports, restore safety copies, files set aside as unreadable.
                purgeCopies()
            }
            is Confirm.DeleteRegimen -> act { ScheduleOps.afterDelete(RecordsReducer.deleteRegimen(it, c.id)) }
            is Confirm.DeleteMemo -> act { MemoOps.deleteMemo(it, c.id) }
            is Confirm.DeleteClinic -> act { MemoOps.deleteClinic(it, c.id) }
        }
    }

    /** Answer no; a declined back-fill still says so. */
    fun decline() {
        val c = state.confirm
        if (c is Confirm.Backfill) act { RecordsReducer.declineBackfill(it) } else act { Nav.dismissConfirm(it) }
    }

    // ── first run ─────────────────────────────────────────────
    fun onboardingNext() = act { OnboardingOps.next(it, Instant.now(), zone) }

    // ── backup ────────────────────────────────────────────────
    fun backupFileName(): String = "hlbackup-${LocalDate.now(zone)}.hlb"

    /** Build the backup in memory; the screen then asks where to put it. */
    fun createBackup() {
        act { it.copy(backup = BackupUi.Running) }
        val s = state
        viewModelScope.launch(io) {
            try {
                val bytes = BackupCodec.create(s.snapshot, s.settings, System.currentTimeMillis())
                pendingBackup = bytes
                withContext(Dispatchers.Main) { act { it.copy(backup = BackupUi.ReadyToSave(backupFileName())) } }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { act { it.copy(backup = BackupUi.Failed(e.message ?: "백업 파일을 만들지 못했어요")) } }
            }
        }
    }

    fun cancelBackupSave() {
        pendingBackup = null
        act { it.copy(backup = BackupUi.Idle) }
    }

    /** The picker returned a place; write the built file there. */
    fun writeBackupTo(uri: Uri) {
        val bytes = pendingBackup ?: return
        val s = state
        viewModelScope.launch(io) {
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("파일을 열 수 없어요")
            }
            withContext(Dispatchers.Main) {
                pendingBackup = null
                result.onSuccess {
                    act {
                        it.copy(
                            backup = BackupUi.Done(backupFileName(), (bytes.size / 1024).coerceAtLeast(1), s.doses.size, s.labs.size, s.regimens.size, s.memos.size + s.clinics.size),
                            settings = it.settings.copy(lastBackupAtMillis = System.currentTimeMillis(), backupBannerHiddenUntilMillis = null),
                        )
                    }
                }.onFailure { e ->
                    act { it.copy(backup = BackupUi.Failed(e.message ?: "저장 공간이 부족할 수 있어요")) }
                }
            }
        }
    }

    fun resetBackup() = act { it.copy(backup = BackupUi.Idle) }

    fun hideBackupBanner(untilMillis: Long) = act { it.copy(settings = it.settings.copy(backupBannerHiddenUntilMillis = untilMillis)) }

    fun writeCsvTo(uri: Uri, onResult: (Boolean) -> Unit) {
        val text = CsvIo.export(state.doses, state.labs, zone)
        viewModelScope.launch(io) {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IOException()
            }.isSuccess
            withContext(Dispatchers.Main) { onResult(ok) }
        }
    }

    // ── restore ───────────────────────────────────────────────
    /** Read a file the user picked and show what it holds; nothing changes until they confirm. */
    fun openRestoreFile(uri: Uri, displayName: String) {
        viewModelScope.launch(io) {
            val bytes = runCatching { getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            withContext(Dispatchers.Main) {
                if (bytes == null || bytes.isEmpty()) showRestoreIssue(RestoreIssue.Unreadable) else previewRestore(bytes, displayName)
            }
        }
    }

    private fun restoreStack(s: AppState): List<Screen> = if (s.screen == Screen.RESTORE) s.stack else s.stack + s.screen

    private fun showRestoreIssue(issue: RestoreIssue) =
        act { it.copy(restore = null, restoreIssue = issue, screen = Screen.RESTORE, stack = restoreStack(it)) }

    private fun previewRestore(bytes: ByteArray, name: String) {
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar()
        if (first == '{') {
            when (val opened = BackupCodec.open(bytes)) {
                is BackupCodec.Opened.Ok -> act {
                    it.copy(
                        restore = RestorePreview(name, RestoreKind.BACKUP, opened.preview.createdAtMillis, opened.snapshot, opened.settings, emptyList()),
                        restoreIssue = null, screen = Screen.RESTORE, stack = restoreStack(it),
                    )
                }
                BackupCodec.Opened.Unreadable -> showRestoreIssue(RestoreIssue.Unreadable)
            }
            return
        }
        val parsed = CsvIo.parse(String(bytes, Charsets.UTF_8), zone)
        if (parsed.doses.isEmpty() && parsed.labs.isEmpty() && parsed.skipped == 0) {
            showRestoreIssue(RestoreIssue.Unreadable)
            return
        }
        val fresh = RecordsReducer.countNew(state, parsed.doses, parsed.labs)
        act {
            it.copy(
                restore = RestorePreview(
                    name, RestoreKind.CSV, null, RecordSnapshot(doses = parsed.doses, labs = parsed.labs), null, parsed.skippedRows,
                    duplicates = parsed.doses.size + parsed.labs.size - fresh,
                ),
                restoreIssue = null, screen = Screen.RESTORE, stack = restoreStack(it),
            )
        }
    }

    /** Apply the previewed file. A backup replaces the records (after saving the current ones aside); a CSV adds to them. */
    fun restoreNow() {
        val p = state.restore ?: return
        when (p.kind) {
            RestoreKind.CSV -> {
                var duplicates = 0
                act { s ->
                    val (merged, dup) = RecordsReducer.mergeImported(s.copy(confirm = null), p.snapshot.doses, p.snapshot.labs)
                    duplicates = dup
                    merged.copy(restore = null, screen = Screen.ME, stack = emptyList())
                }
                val skipped = p.skipped.size
                act {
                    RecordsReducer.toast(
                        it,
                        buildString {
                            append(it.toast.orEmpty())
                            if (duplicates > 0) append(" · 이미 있는 기록 ${duplicates}건은 건너뛰었어요")
                            if (skipped > 0) append(" · 읽지 못한 행 ${skipped}건")
                        },
                        it.undo,
                    )
                }
            }
            RestoreKind.BACKUP -> {
                keepCurrentAside(state)
                act { s ->
                    val restored = p.settings
                    s.withRecords(p.snapshot).copy(
                        settings = if (restored == null) s.settings else SettingsOps.afterRestore(s.settings, restored),
                        confirm = null, restore = null, screen = Screen.HOME, stack = emptyList(),
                        undo = RecordsReducer.undoPoint(s), toast = "백업에서 복원했어요",
                    )
                }
            }
        }
    }

    /** Before a restore replaces everything, the present records are saved aside (the last three are kept). */
    private fun keepCurrentAside(s: AppState) {
        val bytes = BackupCodec.create(s.snapshot, s.settings, System.currentTimeMillis())
        val dir = File(files, RecordCopies.SAFETY_DIR).apply { mkdirs() }
        viewModelScope.launch(io) {
            runCatching {
                File(dir, "before-restore-${System.currentTimeMillis()}.hlb").writeBytes(bytes)
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.delete() }
            }
        }
    }

    fun clearRestore() = act { it.copy(restore = null, restoreIssue = null) }

    // ── report ────────────────────────────────────────────────
    /** Builds the report off the main thread; when it is done the screen offers it to the share sheet. */
    fun createReport(kind: ReportKind) {
        if (state.report.busy) return
        val s = state
        act { ReportOps.busy(it, true) }
        viewModelScope.launch(Dispatchers.Default) {
            val file = runCatching {
                val fmt = Fmt(zone, s.settings.clock24, Instant.now())
                ReportFiles.create(getApplication(), kind, ReportLogic.build(s, fmt), fmt)
            }.getOrNull()
            withContext(Dispatchers.Main) { act { ReportOps.done(it, file) } }
        }
    }

    fun reportShared() = act { ReportOps.shared(it) }

}
