package com.hormonelog.app

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.summary
import com.hormonelog.app.feature.dose.DoseSheet
import com.hormonelog.app.feature.dose.DoseSheetActions
import com.hormonelog.app.feature.flow.EvidenceScreen
import com.hormonelog.app.feature.flow.FlowActions
import com.hormonelog.app.feature.flow.FlowScreen
import com.hormonelog.app.feature.flow.ModelActions
import com.hormonelog.app.feature.flow.ModelScreen
import com.hormonelog.app.feature.home.HomeActions
import com.hormonelog.app.feature.home.HomeScreen
import com.hormonelog.app.feature.lab.LabResultScreen
import com.hormonelog.app.feature.lab.LabSheet
import com.hormonelog.app.feature.lab.LabSheetActions
import com.hormonelog.app.feature.me.LicensesScreen
import com.hormonelog.app.feature.me.MeActions
import com.hormonelog.app.feature.me.MeScreen
import com.hormonelog.app.feature.me.TestesSheet
import com.hormonelog.app.feature.memos.AppointmentSheet
import com.hormonelog.app.feature.memos.ClinicEditorSheet
import com.hormonelog.app.feature.memos.MemoActions
import com.hormonelog.app.feature.memos.MemoEditorSheet
import com.hormonelog.app.feature.memos.MemosScreen
import com.hormonelog.app.feature.notifications.NotificationActions
import com.hormonelog.app.feature.notifications.NotificationsScreen
import com.hormonelog.app.feature.onboarding.OnboardingActions
import com.hormonelog.app.feature.onboarding.OnboardingScreen
import com.hormonelog.app.feature.records.RecordsActions
import com.hormonelog.app.feature.records.RecordsScreen
import com.hormonelog.app.feature.records.StockAddSheet
import com.hormonelog.app.feature.report.ReportActions
import com.hormonelog.app.feature.report.ReportScreen
import com.hormonelog.app.feature.schedules.ScheduleActions
import com.hormonelog.app.feature.schedules.ScheduleEditScreen
import com.hormonelog.app.feature.schedules.SchedulesScreen
import com.hormonelog.app.feature.security.RestoreActions
import com.hormonelog.app.feature.security.RestoreScreen
import com.hormonelog.app.feature.security.SecurityActions
import com.hormonelog.app.feature.security.SecurityScreen
import com.hormonelog.app.feature.timeline.AddRecordSheet
import com.hormonelog.app.feature.timeline.RowMenuSheet
import com.hormonelog.app.feature.timeline.TimelineActions
import com.hormonelog.app.feature.timeline.TimelineScreen
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.Confirm
import com.hormonelog.app.state.FlowOps
import com.hormonelog.app.state.JournalOps
import com.hormonelog.app.state.MemoOps
import com.hormonelog.app.state.Nav
import com.hormonelog.app.state.OnboardingOps
import com.hormonelog.app.state.RecordTab
import com.hormonelog.app.state.RecordsReducer
import com.hormonelog.app.state.ReportOps
import com.hormonelog.app.state.ScheduleOps
import com.hormonelog.app.state.Screen
import com.hormonelog.app.state.SettingsOps
import com.hormonelog.app.state.Sheet
import com.hormonelog.app.state.TimelineOps
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBottomNav
import com.hormonelog.app.ui.kit.HlDialog
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlToastHost
import com.hormonelog.app.ui.kit.NavTab
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.app.ui.theme.HormoneLogTheme
import com.hormonelog.app.ui.theme.isDarkFor
import com.hormonelog.core.domain.AppSettings
import kotlinx.coroutines.delay
import java.time.ZoneId

/** The scrims the activity library uses for a three-button bar; written out so a dark app can ask for the dark one. */
private val LIGHT_NAV_SCRIM = 0xE6FFFFFF.toInt()
private val DARK_NAV_SCRIM = 0x801B1B1B.toInt()

private val TABS = listOf(
    NavTab(Screen.HOME, "홈", HlIcon.Home),
    NavTab(Screen.TIMELINE, "타임라인", HlIcon.Timeline),
    NavTab(Screen.FLOW, "예상 흐름", HlIcon.Flow),
    NavTab(Screen.ME, "내 정보", HlIcon.Me),
)

/** The whole app: the current screen, the sheets and questions over it, and the toast. */
@Composable
fun AppRoot(vm: AppViewModel, onSettingsChanged: (AppSettings) -> Unit) {
    val s = vm.state
    val settings = s.settings
    val now = vm.now

    // The clock moves on while the app stays open; nothing is frozen at the moment it started.
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            vm.tick()
        }
    }
    LaunchedEffect(settings.hideInRecents, settings.disguiseLauncher) { onSettingsChanged(settings) }

    HormoneLogTheme(mode = settings.themeMode, fontScale = settings.fontScale) {
        val c = Hl.colors
        val dark = isDarkFor(settings.themeMode)
        // The system bars follow the app's own theme, not the phone's: a dark app on a light phone must not get a light bar.
        val host = LocalActivity.current as? ComponentActivity
        LaunchedEffect(dark) {
            host?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { dark },
            )
        }
        val fmt = remember(settings.clock24, now, ZoneId.systemDefault()) { Fmt(ZoneId.systemDefault(), settings.clock24, now) }

        BackHandler(enabled = Nav.canGoBack(s)) { vm.act { Nav.back(it) } }

        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                s.storageWarning?.let { StorageWarningBanner(it) { vm.act { st -> st.copy(storageWarning = null) } } }
                Box(Modifier.weight(1f).fillMaxWidth().then(if (s.screen.isTab) Modifier else Modifier.navigationBarsPadding().imePadding())) {
                    ScreenHost(vm, s, fmt)
                }
                if (s.screen.isTab) {
                    HlBottomNav(TABS, s.screen, onSelect = { tab -> vm.act { Nav.tab(it, tab) } })
                }
            }

            HlToastHost(
                message = s.toast,
                onUndo = s.undo?.let { { vm.undo() } },
                onDismiss = { vm.act { RecordsReducer.dismissToast(it) } },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (s.screen.isTab) 76.dp else 24.dp),
            )

            Sheets(vm, s, fmt)
            Confirms(vm, s, fmt)
        }
    }
}

// ── screens ───────────────────────────────────────────────────

@Composable
private fun ScreenHost(vm: AppViewModel, s: AppState, fmt: Fmt) {
    val context = LocalContext.current
    val zone = fmt.zone
    fun now() = java.time.Instant.now()

    when (s.screen) {
        Screen.HOME -> HomeScreen(
            s, fmt,
            HomeActions(
                onStorage = { vm.act { Nav.push(it, Screen.SECURITY) } },
                onFirstDose = { vm.act { RecordsReducer.openDose(it) } },
                onNewPlan = { vm.act { RecordsReducer.openDose(it, repeat = true) } },
                onQuickLog = vm::quickLog,
                onOtherCombo = { vm.act { RecordsReducer.openDose(it) } },
                onLab = { vm.act { RecordsReducer.openLab(it) } },
                onBackup = { vm.act { Nav.push(it, Screen.SECURITY) } },
                onHideBanner = { vm.hideBackupBanner(fmt.now.toEpochMilli() + 7L * 86_400_000L) },
            ),
        )

        Screen.TIMELINE -> TimelineScreen(
            s, fmt,
            TimelineActions(
                onQuery = { q -> vm.act { TimelineOps.query(it, q) } },
                onType = { t -> vm.act { TimelineOps.type(it, t) } },
                onDrug = { d -> vm.act { TimelineOps.drug(it, d) } },
                onPeriod = { p -> vm.act { TimelineOps.period(it, p) } },
                onReset = { vm.act { TimelineOps.reset(it) } },
                onToggleFold = { k -> vm.act { TimelineOps.toggleMonth(it, k) } },
                onMenu = { t -> vm.act { TimelineOps.menu(it, t) } },
                onAdd = { vm.act { Nav.sheet(it, Sheet.AddRecord) } },
                onFirstRecord = { vm.act { RecordsReducer.openDose(it) } },
            ),
        )

        Screen.FLOW -> FlowScreen(
            s, fmt,
            FlowActions(
                series = { x -> vm.act { FlowOps.series(it, x) } },
                range = { r -> vm.act { FlowOps.range(it, r) } },
                customFrom = { m -> vm.act { FlowOps.customFrom(it, m) } },
                scrub = { f -> vm.act { FlowOps.scrub(it, f) } },
                guide = { on -> vm.act { FlowOps.guide(it, on) } },
                model = { vm.act { Nav.push(it, Screen.MODEL) } },
                gonadal = { vm.act { Nav.sheet(it, Sheet.Testes) } },
                record = { vm.act { RecordsReducer.openDose(it) } },
            ),
        )

        Screen.ME -> {
            val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
            MeScreen(
                s, fmt, version,
                MeActions(
                    theme = { m -> vm.act { SettingsOps.theme(it, m) } },
                    fontScale = { f -> vm.act { SettingsOps.fontScale(it, f) } },
                    englishNotReady = { vm.act { RecordsReducer.toast(it, "English는 아직 준비 중이에요") } },
                    e2Unit = { u -> vm.act { SettingsOps.e2Unit(it, u) } },
                    tUnit = { u -> vm.act { SettingsOps.tUnit(it, u) } },
                    clock24 = { on -> vm.act { SettingsOps.clock24(it, on) } },
                    gonadal = { vm.act { Nav.sheet(it, Sheet.Testes) } },
                    baseline = { vm.act { RecordsReducer.openBaselineLab(it) } },
                    schedules = { vm.act { Nav.push(it, Screen.SCHEDULES) } },
                    notifications = { vm.act { Nav.push(it, Screen.NOTIFICATIONS) } },
                    memos = { vm.act { Nav.push(it, Screen.MEMOS) } },
                    records = { vm.act { JournalOps.open(it, RecordTab.CONDITION) } },
                    report = { vm.act { Nav.push(it, Screen.REPORT) } },
                    security = { vm.act { Nav.push(it, Screen.SECURITY) } },
                    evidence = { vm.act { Nav.push(it, Screen.EVIDENCE) } },
                    licenses = { vm.act { Nav.push(it, Screen.LICENSES) } },
                    deleteAll = { vm.act { Nav.ask(it, Confirm.ClearAll) } },
                ),
            )
        }

        Screen.MODEL -> ModelScreen(
            s, fmt,
            ModelActions(
                back = { vm.act { Nav.back(it) } },
                evidence = { vm.act { Nav.push(it, Screen.EVIDENCE) } },
                editLab = { id -> vm.act { RecordsReducer.editLab(it, id) } },
                gonadal = { vm.act { Nav.sheet(it, Sheet.Testes) } },
            ),
        )

        Screen.EVIDENCE -> EvidenceScreen(s, fmt, onBack = { vm.act { Nav.back(it) } })

        Screen.SECURITY -> SecurityScreen(
            s, fmt,
            SecurityActions(
                back = { vm.act { Nav.back(it) } },
                createBackup = vm::createBackup,
                writeBackup = vm::writeBackupTo,
                cancelBackupSave = vm::cancelBackupSave,
                resetBackup = vm::resetBackup,
                openRestore = vm::openRestoreFile,
                writeCsv = vm::writeCsvTo,
                disguiseLauncher = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(disguiseLauncher = on) } } },
                hideInRecents = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(hideInRecents = on) } } },
                neutralNotifications = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(neutralNotifications = on) } } },
            ),
        )

        Screen.RESTORE -> RestoreScreen(
            s, fmt,
            RestoreActions(
                back = { vm.act { Nav.back(it) } },
                restoreNow = vm::restoreNow,
            ),
        )

        Screen.SCHEDULES -> SchedulesScreen(s, fmt, scheduleActions(vm, fmt))
        Screen.SCHEDULE_EDIT -> ScheduleEditScreen(s, fmt, scheduleActions(vm, fmt))

        Screen.NOTIFICATIONS -> NotificationsScreen(
            s, fmt,
            NotificationActions(
                back = { vm.act { Nav.back(it) } },
                injection ={ on -> vm.act { SettingsOps.update(it) { st -> st.copy(notifyInjection = on) } } },
                daily = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(notifyDaily = on) } } },
                lab = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(notifyLab = on) } } },
                appointment = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(notifyAppointment = on) } } },
                hideOnLockScreen = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(hideOnLockScreen = on) } } },
                neutral = { on -> vm.act { SettingsOps.update(it) { st -> st.copy(neutralNotifications = on) } } },
            ),
        )

        Screen.MEMOS -> MemosScreen(
            s, fmt,
            MemoActions(
                back = { vm.act { Nav.back(it) } },
                report = { vm.act { Nav.push(it, Screen.REPORT) } },
                editVisit = { vm.act { Nav.sheet(it, Sheet.Appointment) } },
                newMemo = { vm.act { MemoOps.newMemo(it, fmt.now.atZone(zone).toLocalDate()) } },
                editMemo = { id -> vm.act { MemoOps.editMemo(it, id) } },
                askDeleteMemo = { id -> vm.act { MemoOps.askDeleteMemo(it, id) } },
                newClinic = { vm.act { MemoOps.newClinic(it) } },
                editClinic = { id -> vm.act { MemoOps.editClinic(it, id) } },
                askDeleteClinic = { id -> vm.act { MemoOps.askDeleteClinic(it, id) } },
            ),
        )

        Screen.RECORDS -> RecordsScreen(
            s, fmt,
            RecordsActions(
                back = { vm.act { Nav.back(it) } },
                tab = { t -> vm.act { JournalOps.setTab(it, t) } },
                draft = { block -> vm.act { JournalOps.draft(it, block) } },
                toggleSymptom = { sym -> vm.act { JournalOps.toggleSymptom(it, sym) } },
                save = { vm.act { JournalOps.save(it, now(), zone) } },
                stockChange = { id, d -> vm.act { JournalOps.changeStock(it, id, d) } },
                stockRemove = { id -> vm.act { JournalOps.removeStock(it, id) } },
                stockAdd = { vm.act { Nav.sheet(it, Sheet.StockAdd) } },
            ),
        )

        Screen.REPORT -> ReportScreen(
            s,
            ReportActions(
                back = { vm.act { Nav.back(it) } },
                period ={ p -> vm.act { ReportOps.period(it, p) } },
                toggle = { part -> vm.act { ReportOps.toggle(it, part) } },
                create = vm::createReport,
                shared = vm::reportShared,
            ),
        )

        Screen.LICENSES -> LicensesScreen(onBack = { vm.act { Nav.back(it) } })

        Screen.LAB_RESULT -> s.labResult?.let { summary ->
            LabResultScreen(
                summary, fmt,
                onDone = { vm.act { Nav.back(it).copy(labResult = null) } },
                onEdit = { vm.act { RecordsReducer.editLab(Nav.back(it), summary.labId) } },
            )
        }

        Screen.ONBOARDING -> {
            BackHandler(enabled = s.onboarding.step > 0) { vm.act { OnboardingOps.back(it) } }
            OnboardingScreen(
                s, fmt,
                OnboardingActions(
                    back = { vm.act { OnboardingOps.back(it) } },
                    skip = { vm.act { OnboardingOps.skip(it) } },
                    next = vm::onboardingNext,
                    edit = { block -> vm.act { OnboardingOps.edit(it, block) } },
                    route = { r -> vm.act { OnboardingOps.setRoute(it, r) } },
                ),
            )
        }
    }
}

private fun scheduleActions(vm: AppViewModel, fmt: Fmt) = ScheduleActions(
    back = { vm.act { if (it.screen == Screen.SCHEDULE_EDIT) ScheduleOps.leave(it) else Nav.back(it) } },
    create = { vm.act { ScheduleOps.create(it, java.time.Instant.now(), fmt.zone) } },
    edit = { id -> vm.act { ScheduleOps.edit(it, id, fmt.zone) } },
    endToday = { id -> vm.act { ScheduleOps.endToday(it, id, java.time.Instant.now()) } },
    draft = { block -> vm.act { ScheduleOps.draft(it, block) } },
    route = { r -> vm.act { ScheduleOps.setRoute(it, r) } },
    drug = { d -> vm.act { ScheduleOps.setDrug(it, d) } },
    amount = { t -> vm.act { ScheduleOps.setAmount(it, t) } },
    interval = { i -> vm.act { ScheduleOps.setInterval(it, i, java.time.Instant.now(), fmt.zone) } },
    patchCycle = { p -> vm.act { ScheduleOps.setPatchCycle(it, p, java.time.Instant.now(), fmt.zone) } },
    toggleDay = { d -> vm.act { ScheduleOps.toggleDay(it, d) } },
    save = { vm.act { ScheduleOps.save(it, java.time.Instant.now(), fmt.zone) } },
    askDelete = { id -> vm.act { ScheduleOps.askDelete(it, id) } },
)

// ── sheets ────────────────────────────────────────────────────

@Composable
private fun Sheets(vm: AppViewModel, s: AppState, fmt: Fmt) {
    val zone = fmt.zone
    val close = { vm.act { RecordsReducer.closeSheet(it) } }
    when (val sheet = s.sheet) {
        Sheet.None -> Unit

        Sheet.Dose -> DoseSheet(
            doses = s.doses, draft = s.doseDraft, fmt = fmt,
            actions = DoseSheetActions(
                close = { close() },
                edit = { block -> vm.act { RecordsReducer.editDraft(it, block) } },
                route = { r -> vm.act { RecordsReducer.setRoute(it, r) } },
                drug = { d -> vm.act { RecordsReducer.setDrug(it, d) } },
                amount = { t -> vm.act { RecordsReducer.setAmountText(it, t) } },
                combo = { c -> vm.act { RecordsReducer.pickCombo(it, c) } },
                save = vm::saveDose,
                savePlan = vm::saveRegimen,
            ),
        )

        Sheet.Lab -> LabSheet(
            s, fmt,
            LabSheetActions(
                close = { close() },
                edit = { block -> vm.act { RecordsReducer.editLabDraft(it, block) } },
                save = vm::saveLab,
            ),
        )

        Sheet.AddRecord -> AddRecordSheet(
            onDose = { vm.act { RecordsReducer.openDose(it) } },
            onLab = { vm.act { RecordsReducer.openLab(it) } },
            onCondition = { vm.act { JournalOps.open(it, RecordTab.CONDITION) } },
            onBody = { vm.act { JournalOps.open(it, RecordTab.BODY) } },
            onStock = { vm.act { JournalOps.open(it, RecordTab.STOCK) } },
            onExtraLabs = { vm.act { JournalOps.open(it, RecordTab.LABS) } },
            onMemo = { vm.act { Nav.push(it, Screen.MEMOS) } },
            onDismiss = { close() },
        )

        is Sheet.RowMenu -> RowMenuSheet(
            s, sheet.target, fmt,
            onEdit = { vm.act { TimelineOps.edit(it, sheet.target) } },
            onDuplicate = { vm.act { TimelineOps.duplicate(it, sheet.target, java.time.Instant.now(), zone) } },
            onDelete = { vm.act { TimelineOps.delete(it, sheet.target) } },
            onDismiss = { close() },
        )

        Sheet.Testes -> TestesSheet(
            current = s.settings.gonadalStatus,
            onPick = { g -> vm.act { SettingsOps.gonadal(it, g) } },
            onDismiss = { close() },
        )

        Sheet.Appointment -> AppointmentSheet(
            currentMillis = s.nextVisitMillis, fmt = fmt,
            onSave = { millis -> vm.act { MemoOps.setVisit(it, millis) } },
            onClear = if (s.nextVisitMillis != null) ({ vm.act { MemoOps.setVisit(it, null) } }) else null,
            onDismiss = { close() },
        )

        Sheet.MemoEditor -> s.memoDraft?.let { draft ->
            MemoEditorSheet(
                draft, fmt,
                onEdit = { block -> vm.act { MemoOps.memoDraft(it, block) } },
                onSave = { vm.act { MemoOps.saveMemo(it) } },
                onDelete = draft.editingId?.let { id -> { vm.act { MemoOps.askDeleteMemo(it, id) } } },
                onDismiss = { vm.act { it.copy(sheet = Sheet.None, memoDraft = null) } },
            )
        }

        Sheet.ClinicEditor -> s.clinicDraft?.let { draft ->
            ClinicEditorSheet(
                draft,
                onEdit = { block -> vm.act { MemoOps.clinicDraft(it, block) } },
                onSave = { vm.act { MemoOps.saveClinic(it) } },
                onDelete = draft.editingId?.let { id -> { vm.act { MemoOps.askDeleteClinic(it, id) } } },
                onDismiss = { vm.act { it.copy(sheet = Sheet.None, clinicDraft = null) } },
            )
        }

        Sheet.StockAdd -> StockAddSheet(
            s, fmt,
            onPick = { combo -> vm.act { JournalOps.addStock(it, combo) } },
            onDismiss = { close() },
        )
    }
}

@Composable
private fun Confirms(vm: AppViewModel, s: AppState, fmt: Fmt) {
    when (val c = s.confirm ?: return) {
        is Confirm.DuplicateDose -> HlDialog(
            title = "이미 비슷한 시각에 기록이 있어요",
            body = "${fmt.dateDay(c.existing.occurredAt)} ${fmt.time(c.existing.occurredAt)}에 ${doseSummary(c.existing.drug, c.existing.route, c.existing.amountEntered, c.existing.enteredUnit)} 기록이 있어요. 그래도 새로 기록할까요?",
            confirmLabel = "그래도 기록", onConfirm = vm::confirm, onDismiss = vm::decline,
        )
        is Confirm.Backfill -> HlDialog(
            title = c.pending.title, body = c.pending.body,
            confirmLabel = "${c.pending.doses.size}건 기록", cancelLabel = "기록 안 함", onConfirm = vm::confirm, onDismiss = vm::decline,
        )
        is Confirm.ReplaceRegimen -> HlDialog(
            title = "같은 약물 일정이 이미 있어요",
            body = "${c.existing.summary()} 일정이 진행 중이에요. 기존 일정을 ${fmt.date(c.replacement.startAt.minusSeconds(86_400))}로 종료하고 새 일정을 ${fmt.date(c.replacement.startAt)}부터 시작할까요?",
            confirmLabel = "기존 일정 종료하고 저장", cancelLabel = "돌아가기", onConfirm = vm::confirm, onDismiss = vm::decline,
        )
        Confirm.ClearAll -> HlDialog(
            title = "모든 기록을 삭제할까요?",
            body = "투약·검사 기록, 일정, 병원 메모가 모두 지워져요. 5초 안에는 실행 취소할 수 있고, 그 뒤에는 백업 파일로만 되살릴 수 있어요.",
            confirmLabel = "모두 삭제", onConfirm = vm::confirm, onDismiss = vm::decline, danger = true,
        )
        is Confirm.DeleteRegimen -> HlDialog(
            title = "이 일정을 지울까요?",
            body = "앞으로의 예정만 사라지고 이미 기록된 투약은 남아요. 5초 안에 실행 취소할 수 있어요.",
            confirmLabel = "일정 지우기", onConfirm = vm::confirm, onDismiss = vm::decline, danger = true,
        )
        is Confirm.DeleteMemo -> HlDialog(
            title = "이 메모를 삭제할까요?",
            body = "“${s.memos.firstOrNull { it.id == c.id }?.title.orEmpty()}” · 삭제 후 5초 안에 실행 취소할 수 있어요.",
            confirmLabel = "삭제", onConfirm = vm::confirm, onDismiss = vm::decline, danger = true,
        )
        is Confirm.DeleteClinic -> HlDialog(
            title = "이 병원 정보를 삭제할까요?",
            body = "“${s.clinics.firstOrNull { it.id == c.id }?.name.orEmpty()}” · 삭제 후 5초 안에 실행 취소할 수 있어요.",
            confirmLabel = "삭제", onConfirm = vm::confirm, onDismiss = vm::decline, danger = true,
        )
    }
}

/**
 * Sits above everything and stays until dismissed. A storage failure is the one message where a
 * timeout could cost the user their records.
 */
@Composable
private fun StorageWarningBanner(text: String, onDismiss: () -> Unit) {
    val c = Hl.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.dangerSoft)
            .padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HlIcon(HlIcon.Alert, size = 18.dp, tint = c.danger, strokeWidth = 2f)
        HlText(text, modifier = Modifier.weight(1f), size = HlSize.t13, color = c.text, lineHeight = 1.5f)
        HlIconButton(HlIcon.Close, "경고 닫기", onDismiss, tint = c.danger, size = 44.dp, iconSize = 16.dp)
    }
}
