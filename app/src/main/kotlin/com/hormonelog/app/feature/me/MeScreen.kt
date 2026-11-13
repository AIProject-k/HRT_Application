package com.hormonelog.app.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.HomeLogic
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlMenuItem
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.HlSettingRow
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSheetTitle
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.kit.ScreenTitle
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.FontScale
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.ThemeMode
import com.hormonelog.core.evidence.EvidenceBundleV1

class MeActions(
    val theme: (ThemeMode) -> Unit,
    val fontScale: (FontScale) -> Unit,
    val englishNotReady: () -> Unit,
    val e2Unit: (E2Unit) -> Unit,
    val tUnit: (TUnit) -> Unit,
    val clock24: (Boolean) -> Unit,
    val gonadal: () -> Unit,
    val baseline: () -> Unit,
    val schedules: () -> Unit,
    val notifications: () -> Unit,
    val memos: () -> Unit,
    val records: () -> Unit,
    val report: () -> Unit,
    val security: () -> Unit,
    val evidence: () -> Unit,
    val licenses: () -> Unit,
    val deleteAll: () -> Unit,
)

@Composable
fun MeScreen(s: AppState, fmt: Fmt, appVersion: String, actions: MeActions) {
    val c = Hl.colors
    val settings = s.settings
    val baselineLab = s.labs.filter { it.isBaseline }.maxByOrNull { it.collectedAt ?: java.time.Instant.MIN }
    val running = s.regimens.count { it.isRunningAt(fmt.now) }
    val nudge = HomeLogic.backupNudgeDays(settings.copy(backupBannerHiddenUntilMillis = null), true, fmt.now)

    ScreenScroll(gap = 10.dp) {
        ScreenTitle("내 정보")

        Section("화면") {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Setting("테마") {
                    HlSegmented(
                        listOf(SegItem(ThemeMode.DARK, "다크"), SegItem(ThemeMode.LIGHT, "라이트"), SegItem(ThemeMode.SYSTEM, "시스템")),
                        settings.themeMode, actions.theme,
                    )
                }
                Setting("글자 크기", hint = "시스템 글꼴 크기와 함께 적용돼요") {
                    HlSegmented(
                        listOf(SegItem(FontScale.DEFAULT, "기본"), SegItem(FontScale.LARGE, "크게"), SegItem(FontScale.LARGEST, "아주 크게")),
                        settings.fontScale, actions.fontScale,
                    )
                }
                Setting("언어", hint = "English는 준비 중이에요") {
                    HlSegmented(listOf(SegItem("ko", "한국어"), SegItem("en", "English")), "ko", { if (it == "en") actions.englishNotReady() })
                }
            }
        }

        Section("단위 · 시간") {
            Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Setting("E2") { HlSegmented(E2Unit.entries.map { SegItem(it, it.label) }, settings.e2Unit, actions.e2Unit) }
                Setting("Total T", hint = "검사값을 입력할 때의 기본 단위예요. 그래프와 예상값은 pg/mL · ng/dL로 보여요.") { HlSegmented(TUnit.entries.map { SegItem(it, it.label) }, settings.tUnit, actions.tUnit) }
                Setting("시간 형식") {
                    HlSegmented(listOf(SegItem(false, "12시간"), SegItem(true, "24시간")), settings.clock24, actions.clock24)
                }
            }
        }

        Section("신체 정보") {
            // When the user would rather not say, the Total T curve is not drawn at all, so this line must not promise a prediction.
            val testesHint = if (settings.gonadalStatus == GonadalStatus.DECLINED) "Total T 곡선을 그리지 않아요" else "Total T 예측에 사용"
            HlSettingRow("고환 유무", subtitle = "${settings.gonadalStatus.label} · $testesHint", onClick = actions.gonadal, minHeight = 60.dp)
            HlDivider()
            HlSettingRow(
                "HRT 시작 전 검사값",
                subtitle = baselineLab?.let { lab ->
                    val parts = lab.analytes.map { a ->
                        val name = if (a.analyte == Analyte.ESTRADIOL) "E2" else "T"
                        "$name ${plainNumber(a.reportedValue)} ${a.reportedUnit}"
                    } + listOfNotNull(lab.collectedAt?.let { fmt.date(it) }, "실측")
                    parts.joinToString(" · ")
                } ?: "없음 · 있으면 Total T 예측의 출발점으로 써요",
                onClick = actions.baseline, minHeight = 60.dp,
            )
        }

        Section("기록 관리") {
            HlSettingRow("반복 일정", value = if (running > 0) "진행 중 ${running}개" else "없음", onClick = actions.schedules)
            HlDivider()
            HlSettingRow("알림", value = if (settings.notificationsOn > 0) "${settings.notificationsOn}개 켜짐" else "꺼짐", onClick = actions.notifications)
            HlDivider()
            HlSettingRow(
                "병원 메모",
                value = s.nextVisitMillis?.let { "다음 진료 ${fmt.date(java.time.Instant.ofEpochMilli(it))}" } ?: "${s.memos.size + s.clinics.size}건",
                onClick = actions.memos,
            )
            HlDivider()
            HlSettingRow("다른 기록 (컨디션·체중·재고)", onClick = actions.records)
            HlDivider()
            HlSettingRow("병원 방문용 리포트", onClick = actions.report)
            HlDivider()
            HlSettingRow(
                "백업·복원·보안",
                value = when {
                    settings.lastBackupAtMillis == null -> if (s.hasRecords) "백업 안 함" else null
                    nudge != null && nudge >= 0 -> "백업 ${nudge}일 전"
                    else -> null
                },
                valueColor = c.orange, onClick = actions.security,
            )
        }

        Section("정보") {
            HlSettingRow("모델·근거 버전", value = "근거 ${EvidenceBundleV1.bundle.version}", onClick = actions.evidence)
            HlDivider()
            HlSettingRow("오픈소스 라이선스", onClick = actions.licenses)
            HlDivider()
            HlSettingRow("앱 버전", value = appVersion)
        }

        HlButton("모든 기록 삭제", actions.deleteAll, Modifier.fillMaxWidth().padding(top = 6.dp), kind = HlButtonKind.Danger, minHeight = 56.dp, size = HlSize.t14, enabled = s.recordCount > 0)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        HlSectionLabel(title)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(Hl.colors.card)) { content() }
    }
}

@Composable
private fun Setting(label: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText(label, size = HlSize.t14, weight = FontWeight.SemiBold)
        content()
        if (hint != null) HlText(hint, size = HlSize.t12, color = Hl.colors.muted, lineHeight = 1.5f)
    }
}

/** 고환 유무 — used only for the Total T estimate, and never leaves the device. */
@Composable
fun TestesSheet(current: GonadalStatus, onPick: (GonadalStatus) -> Unit, onDismiss: () -> Unit) {
    HlSheet(onDismiss = onDismiss) {
        HlSheetTitle("고환 유무", "Total T 예측에만 써요")
        Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            listOf(GonadalStatus.INTACT, GonadalStatus.POST_ORCHIECTOMY, GonadalStatus.DECLINED).forEach { g ->
                HlMenuItem(g.label, { onPick(g) }, selected = current == g)
            }
        }
    }
}

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val c = Hl.colors
    Column(Modifier.fillMaxWidth()) {
        HlTopBar("오픈소스 라이선스", onBack)
        ScreenScroll(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 8.dp, bottomInset = true) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                LICENSES.forEachIndexed { i, (name, license) ->
                    if (i > 0) HlDivider()
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        HlText(name, size = HlSize.t14, weight = FontWeight.SemiBold)
                        HlText(license, size = HlSize.t12, color = c.muted)
                    }
                }
            }
            HlText(
                "예상 곡선의 주사·패치 모델 상수는 estrannaise.js(MIT 라이선스, © 2025 alix)에서 가져왔어요. 문헌 출처는 내 정보 › 모델·근거 버전에서 볼 수 있어요.",
                size = HlSize.t12, color = c.muted, lineHeight = 1.6f,
            )
        }
    }
}

private val LICENSES = listOf(
    "Jetpack Compose · AndroidX" to "Apache License 2.0",
    "Kotlin · kotlinx.coroutines" to "Apache License 2.0",
    "estrannaise.js (모델 상수)" to "MIT License · © 2025 alix",
    "org.json (Android 내장)" to "Apache License 2.0",
)
