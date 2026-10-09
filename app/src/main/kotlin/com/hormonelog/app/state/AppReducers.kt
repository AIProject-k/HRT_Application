package com.hormonelog.app.state

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.FontScale
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.ThemeMode
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Where the user is and how they get back. */
object Nav {
    fun tab(s: AppState, screen: Screen): AppState =
        s.copy(screen = screen, stack = emptyList(), sheet = Sheet.None, flow = s.flow.copy(scrub = null))

    fun push(s: AppState, screen: Screen): AppState =
        s.copy(stack = s.stack + s.screen, screen = screen, sheet = Sheet.None, confirm = null)

    /** One step back: a sheet or question first, then the screen below on the stack. */
    fun back(s: AppState): AppState = when {
        s.confirm != null -> s.copy(confirm = null)
        s.sheet != Sheet.None -> RecordsReducer.closeSheet(s)
        s.stack.isNotEmpty() -> s.copy(screen = s.stack.last(), stack = s.stack.dropLast(1), restore = null, restoreIssue = null)
        s.screen != Screen.HOME && s.screen != Screen.ONBOARDING -> s.copy(screen = Screen.HOME)
        else -> s
    }

    /** Whether back would do anything inside the app (otherwise it leaves the app). */
    fun canGoBack(s: AppState): Boolean =
        s.confirm != null || s.sheet != Sheet.None || s.stack.isNotEmpty() ||
            (s.screen != Screen.HOME && s.screen != Screen.ONBOARDING)

    fun sheet(s: AppState, sheet: Sheet): AppState = s.copy(sheet = sheet)

    fun ask(s: AppState, confirm: Confirm): AppState = s.copy(confirm = confirm)

    fun dismissConfirm(s: AppState): AppState = s.copy(confirm = null)
}

/** The app's own preferences. */
object SettingsOps {
    fun update(s: AppState, block: (AppSettings) -> AppSettings): AppState = s.copy(settings = block(s.settings))

    fun theme(s: AppState, mode: ThemeMode) = update(s) { it.copy(themeMode = mode) }
    fun fontScale(s: AppState, scale: FontScale) = update(s) { it.copy(fontScale = scale) }
    fun clock24(s: AppState, on: Boolean) = update(s) { it.copy(clock24 = on) }

    /** The units chosen here are what the lab sheet offers first; an open draft follows them. */
    fun e2Unit(s: AppState, unit: E2Unit) = update(s) { it.copy(e2Unit = unit) }.let { it.copy(labDraft = it.labDraft.copy(e2Unit = unit)) }
    fun tUnit(s: AppState, unit: TUnit) = update(s) { it.copy(tUnit = unit) }.let { it.copy(labDraft = it.labDraft.copy(ttUnit = unit)) }

    fun gonadal(s: AppState, status: GonadalStatus): AppState =
        update(s) { it.copy(gonadalStatus = status) }.copy(sheet = Sheet.None, toast = "바꿨어요 · 곡선을 다시 계산했어요")

    /**
     * The settings after a backup is restored. Preferences come from the file; the backup nudge stays as it
     * is here. A privacy protection that is on now stays on whatever the file says, so restoring an older
     * (or someone else's) backup cannot quietly show the app again.
     */
    fun afterRestore(current: AppSettings, restored: AppSettings): AppSettings = current.copy(
        onboardingDone = true,
        themeMode = restored.themeMode, fontScale = restored.fontScale, e2Unit = restored.e2Unit, tUnit = restored.tUnit,
        clock24 = restored.clock24, gonadalStatus = restored.gonadalStatus,
        hideInRecents = current.hideInRecents || restored.hideInRecents,
        disguiseLauncher = current.disguiseLauncher || restored.disguiseLauncher,
        neutralNotifications = current.neutralNotifications || restored.neutralNotifications,
        hideOnLockScreen = current.hideOnLockScreen || restored.hideOnLockScreen,
        notifyInjection = restored.notifyInjection, notifyDaily = restored.notifyDaily, notifyLab = restored.notifyLab,
        notifyAppointment = restored.notifyAppointment,
    )
}

/** First-run setup. */
object OnboardingOps {
    fun start(s: AppState): AppState = s.copy(
        screen = Screen.ONBOARDING,
        stack = emptyList(),
        sheet = Sheet.None,
        onboarding = OnboardingState(e2Unit = s.settings.e2Unit, tUnit = s.settings.tUnit),
    )

    fun edit(s: AppState, block: (OnboardingState) -> OnboardingState): AppState = s.copy(onboarding = block(s.onboarding))

    fun setRoute(s: AppState, route: Route): AppState {
        val o = s.onboarding
        val drug = if (o.drug in drugsFor(route)) o.drug else drugsFor(route).first()
        return s.copy(onboarding = o.copy(route = route, drug = drug))
    }

    fun back(s: AppState): AppState {
        val o = s.onboarding
        val step = if (o.step == 3 && o.noSchedule) 1 else o.step - 1
        return if (o.step == 0) s else s.copy(onboarding = o.copy(step = step.coerceAtLeast(0)))
    }

    /** Whether step 1 has what it needs to make a schedule. */
    fun scheduleReady(o: OnboardingState): Boolean =
        o.route == Route.PATCH || (o.amountText.trim().toDoubleOrNull()?.let { it > 0 && it <= DOSE_MAX } == true)

    fun draftOf(o: OnboardingState): DoseDraft = DoseDraft(
        repeat = true,
        drug = o.drug,
        route = o.route,
        amountText = o.amountText,
        patchStrength = o.patchStrength,
        patchCycle = o.patchCycle,
        interval = o.interval,
        startMillis = o.startMillis,
    )

    /** The schedule the first steps describe, or null when skipped or incomplete. */
    fun regimenOf(o: OnboardingState, now: Instant, zone: ZoneId): Regimen? =
        if (o.noSchedule || !scheduleReady(o)) null else RecordsReducer.regimenFromDraft(draftOf(o), now, zone)

    fun skip(s: AppState): AppState {
        val o = s.onboarding
        return when {
            o.step == 1 -> s.copy(onboarding = o.copy(noSchedule = true, step = 3))
            o.step < OnboardingState.STEPS - 1 -> s.copy(onboarding = o.copy(step = o.step + 1))
            else -> s
        }
    }

    /** The CTA: the next step, or — on the last — set everything up and leave. */
    fun next(s: AppState, now: Instant, zone: ZoneId): AppState {
        val o = s.onboarding
        if (o.step == 1 && !scheduleReady(o)) return s
        if (o.step < OnboardingState.STEPS - 1) return s.copy(onboarding = o.copy(step = o.step + 1))
        return finish(s, now, zone)
    }

    private fun finish(s: AppState, now: Instant, zone: ZoneId): AppState {
        val o = s.onboarding
        val regimen = regimenOf(o, now, zone)
        var next = s.copy(
            settings = s.settings.copy(
                onboardingDone = true,
                gonadalStatus = o.gonadal ?: s.settings.gonadalStatus,
                e2Unit = o.e2Unit,
                tUnit = o.tUnit,
            ),
            labDraft = s.labDraft.copy(e2Unit = o.e2Unit, ttUnit = o.tUnit),
        )
        if (regimen != null) {
            next = next.copy(regimens = next.regimens + regimen)
            if (o.backfill) {
                val doses = Regimen.expand(regimen, now, zone, RecordSource.SCHEDULE)
                    .filterNot { g -> next.doses.any { it.drug == g.drug && it.route == g.route && it.occurredAt.atZone(zone).toLocalDate() == g.occurredAt.atZone(zone).toLocalDate() } }
                next = next.copy(doses = (next.doses + doses).sortedBy { it.occurredAt })
            }
        }
        baselineLab(o, zone)?.let { lab -> next = next.copy(labs = (next.labs + lab).sortedBy { it.collectedAt ?: Instant.MIN }) }
        return next.copy(onboarding = OnboardingState(), screen = Screen.HOME, stack = emptyList())
    }

    /** The pre-HRT labs the user typed in, kept as a lab that never calibrates anything. */
    fun baselineLab(o: OnboardingState, zone: ZoneId): LabResult? {
        val e2 = o.baselineE2.trim().toDoubleOrNull()?.takeIf { it > 0 }
        val t = o.baselineT.trim().toDoubleOrNull()?.takeIf { it > 0 }
        if (e2 == null && t == null) return null
        val drawn = o.baselineMillis?.let(Instant::ofEpochMilli)
        return LabResult(
            id = UUID.randomUUID(),
            collectedAt = drawn,
            sourceZoneId = zone.id,
            assay = Assay.UNKNOWN,
            analytes = buildList {
                if (e2 != null) add(LabAnalyteValue(Analyte.ESTRADIOL, e2, o.e2Unit.label, LabAnalyteValue.canonical(Analyte.ESTRADIOL, e2, o.e2Unit.label)))
                if (t != null) add(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, t, o.tUnit.label, LabAnalyteValue.canonical(Analyte.TOTAL_TESTOSTERONE, t, o.tUnit.label)))
            },
            isBaseline = true,
            note = "HRT 시작 전",
        )
    }

    /** First day the plan can start: whatever the user picked, else today. */
    fun startDate(o: OnboardingState, now: Instant, zone: ZoneId): LocalDate =
        o.startMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: now.atZone(zone).toLocalDate()

    /** How many past doses the plan would have produced, for the question on step 2. */
    fun backfillCount(s: AppState, now: Instant, zone: ZoneId): Int {
        val r = regimenOf(s.onboarding, now, zone) ?: return 0
        return Regimen.expand(r, now, zone).count { g ->
            s.doses.none { it.drug == g.drug && it.route == g.route && it.occurredAt.atZone(zone).toLocalDate() == g.occurredAt.atZone(zone).toLocalDate() }
        }
    }
}
