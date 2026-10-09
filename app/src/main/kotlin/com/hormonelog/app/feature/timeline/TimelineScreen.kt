package com.hormonelog.app.feature.timeline

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.analysis.DayGroup
import com.hormonelog.app.analysis.FoldDay
import com.hormonelog.app.analysis.FoldGroup
import com.hormonelog.app.analysis.FoldState
import com.hormonelog.app.analysis.RowKind
import com.hormonelog.app.analysis.TimelineLogic
import com.hormonelog.app.analysis.TimelineRow
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.RowTarget
import com.hormonelog.app.state.TimelinePeriod
import com.hormonelog.app.state.TimelineType
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlCard
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.ScreenTitle
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlColors
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Drug

class TimelineActions(
    val onQuery: (String) -> Unit,
    val onType: (TimelineType) -> Unit,
    val onDrug: (Drug?) -> Unit,
    val onPeriod: (TimelinePeriod) -> Unit,
    val onReset: () -> Unit,
    val onToggleFold: (String) -> Unit,
    val onMenu: (RowTarget) -> Unit,
    val onAdd: () -> Unit,
    val onFirstRecord: () -> Unit,
)

private val TYPE_CHIPS = listOf(
    TimelineType.ALL to "전체", TimelineType.DOSE to "투약", TimelineType.LAB to "검사",
    TimelineType.MISSED to "놓침", TimelineType.CONDITION to "컨디션", TimelineType.MEMO to "메모",
)

private val PERIOD_CHIPS = listOf(TimelinePeriod.MONTH to "1개월", TimelinePeriod.QUARTER to "3개월", TimelinePeriod.ALL to "전체 기간")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(s: AppState, fmt: Fmt, actions: TimelineActions) {
    val c = Hl.colors
    val today = fmt.now.atZone(fmt.zone).toLocalDate()
    val calibration = remember(s.doses, s.labs, s.settings.gonadalStatus) { Analysis.calibrate(s.doses, s.labs, s.settings.gonadalStatus) }
    val model = remember(s.doses, s.labs, s.journal, s.memos, s.timeline, calibration, today, fmt.clock24) { TimelineLogic.build(s, fmt, calibration) }
    val ui = s.timeline

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") { ScreenTitle("타임라인") }

            if (model.adherence.isNotEmpty()) {
                item(key = "adherence") {
                    HlCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            model.adherence.forEach { m ->
                                Column(Modifier.widthIn(min = 130.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    HlText(m.label, size = HlSize.t12, color = c.muted)
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        HlText("${m.taken} / ${m.total}회", size = HlSize.t18, weight = FontWeight.Bold, tabular = true)
                                        HlText("${m.percent}%", size = HlSize.t13, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
                                    }
                                    HlText(m.note, size = HlSize.t12, color = c.muted)
                                }
                            }
                        }
                    }
                }
            }

            if (model.hasAnyRecord) {
                item(key = "search") { SearchField(ui.query, actions.onQuery) }
                item(key = "types") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
                        items(TYPE_CHIPS, key = { it.first }) { (type, label) ->
                            HlChip(label, ui.type == type, { actions.onType(type) }, minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 14.dp))
                        }
                    }
                }
                item(key = "filters") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { HlChip("모든 약물", ui.drug == null, { actions.onDrug(null) }, minHeight = 44.dp, size = HlSize.t12, shape = RoundedCornerShape(HlRadius.chipSmall), padding = PaddingValues(horizontal = 12.dp)) }
                        items(model.drugs, key = { it.name }) { d ->
                            HlChip(d.shortLabel, ui.drug == d, { actions.onDrug(if (ui.drug == d) null else d) }, minHeight = 44.dp, size = HlSize.t12, shape = RoundedCornerShape(HlRadius.chipSmall), padding = PaddingValues(horizontal = 12.dp))
                        }
                        item { Box(Modifier.padding(horizontal = 2.dp, vertical = 8.dp).size(width = 1.dp, height = 28.dp).background(c.line)) }
                        items(PERIOD_CHIPS, key = { it.first }) { (p, label) ->
                            HlChip(label, ui.period == p, { actions.onPeriod(p) }, minHeight = 44.dp, size = HlSize.t12, shape = RoundedCornerShape(HlRadius.chipSmall), padding = PaddingValues(horizontal = 12.dp))
                        }
                    }
                }
            }

            if (!model.hasAnyRecord) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HlText("아직 기록이 없어요", size = HlSize.t14, weight = FontWeight.SemiBold)
                        HlText("투약이나 검사 결과를 기록하면 여기에 날짜순으로 쌓여요.", size = HlSize.t13, color = c.muted, align = androidx.compose.ui.text.style.TextAlign.Center)
                        HlButton("기록하기", actions.onFirstRecord, kind = HlButtonKind.Secondary, minHeight = 48.dp)
                    }
                }
            } else if (model.months.isEmpty()) {
                item(key = "none") {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HlText("조건에 맞는 기록이 없어요", size = HlSize.t14, weight = FontWeight.SemiBold)
                        HlButton("필터 초기화", actions.onReset, kind = HlButtonKind.Secondary, minHeight = 44.dp, size = HlSize.t13, content = c.teal)
                    }
                }
            }

            model.months.forEach { month ->
                item(key = "m-${month.key}") {
                    HlText(month.label, size = HlSize.t13, weight = FontWeight.Bold, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
                items(month.folds, key = { "f-${it.key}" }) { fold ->
                    FoldCard(fold, open = fold.key in ui.openMonths, onToggle = { actions.onToggleFold(fold.key) }, onDay = { it.target?.let(actions.onMenu) })
                }
                items(month.days, key = { "d-${it.key}" }) { day -> DayCard(day, actions.onMenu) }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(c.teal)
                .clickable(role = Role.Button, onClick = actions.onAdd)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HlIcon(HlIcon.Plus, size = 18.dp, tint = c.onTeal, strokeWidth = 2.4f)
            HlText("기록", size = HlSize.t14, weight = FontWeight.Bold, color = c.onTeal)
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Hl.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(HlRadius.chip)).background(c.input).border(1.dp, c.line, RoundedCornerShape(HlRadius.chip)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HlIcon(HlIcon.Search, size = 18.dp, tint = c.muted, strokeWidth = 2f)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) HlText("메모·약물 검색", size = HlSize.t14, color = c.dim)
            BasicTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = HlSize.t14), cursorBrush = SolidColor(c.teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "기록 검색" },
            )
        }
        if (query.isNotEmpty()) HlIconButton(HlIcon.Close, "검색어 지우기", { onQuery("") }, tint = c.muted, size = 36.dp, iconSize = 16.dp)
    }
}

/** Icon, background tint and foreground for a row kind. */
private data class Look(val icon: HlIcon, val bg: Color, val fg: Color)

private fun look(c: HlColors, row: TimelineRow): Look = when (row.kind) {
    RowKind.DOSE -> if (row.antiandrogen) Look(HlIcon.Pill, c.violetSoft, c.violet) else Look(HlIcon.Injection, c.blueSoft, c.blue)
    RowKind.GEL -> Look(HlIcon.Gel, c.orangeSoft, c.orange)
    RowKind.MISS -> Look(HlIcon.Missed, c.dangerSoft, c.danger)
    RowKind.LAB -> Look(HlIcon.Lab, c.yellowSoft, c.yellow)
    RowKind.CONDITION -> Look(HlIcon.Condition, c.tealSoft, c.teal)
    RowKind.BODY -> Look(HlIcon.Body, c.tealSoft, c.teal)
    RowKind.EXTRA_LAB -> Look(HlIcon.Lab, c.tealSoft, c.teal)
    RowKind.MEMO -> Look(HlIcon.Memo, c.input, c.muted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(day: DayGroup, onMenu: (RowTarget) -> Unit) {
    val c = Hl.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HlText(day.label, size = HlSize.t12, color = c.dim, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
            day.rows.forEachIndexed { i, r ->
                if (i > 0) HlDivider()
                val l = look(c, r)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(l.bg), contentAlignment = Alignment.Center) { HlIcon(l.icon, size = 20.dp, tint = l.fg) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        HlText(r.title, size = HlSize.t14, weight = FontWeight.SemiBold, lineHeight = 1.35f)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            if (r.timeText.isNotEmpty()) HlText(r.timeText, size = HlSize.t12, color = c.muted)
                            if (r.sub.isNotEmpty()) HlText("· ${r.sub}", size = HlSize.t12, color = c.muted)
                            HlText(
                                r.source,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).border(1.dp, c.line, RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 1.dp),
                                size = HlSize.t12, weight = FontWeight.SemiBold, color = if (r.isExample) c.orange else c.muted,
                            )
                        }
                    }
                    HlIconButton(HlIcon.Dots, "${r.title} 메뉴", { onMenu(r.target) }, tint = c.muted, iconSize = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun FoldCard(fold: FoldGroup, open: Boolean, onToggle: () -> Unit, onDay: (FoldDay) -> Unit) {
    val c = Hl.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics { contentDescription = "${fold.title}, ${fold.sub}, ${if (open) "접기" else "펼치기"}" },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (fold.antiandrogen) c.violetSoft else c.blueSoft), contentAlignment = Alignment.Center) {
                HlIcon(HlIcon.Pill, size = 20.dp, tint = if (fold.antiandrogen) c.violet else c.blue)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HlText(fold.title, size = HlSize.t14, weight = FontWeight.SemiBold)
                HlText(fold.sub, size = HlSize.t12, color = c.muted)
            }
            HlText(if (open) "접기" else "펼치기", size = HlSize.t12, color = c.muted)
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fold.days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        week.forEach { d ->
                            val (bg, border, fg) = when (d.state) {
                                FoldState.TAKEN -> Triple(c.violetSoft, Color.Transparent, c.violet)
                                FoldState.MISSED -> Triple(Color.Transparent, c.danger, c.danger)
                                FoldState.NONE -> Triple(Color.Transparent, c.line, c.dim)
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(bg).border(BorderStroke(1.5.dp, border), RoundedCornerShape(8.dp))
                                    .then(if (d.target != null) Modifier.clickable(role = Role.Button) { onDay(d) } else Modifier)
                                    .semantics { contentDescription = d.description },
                                contentAlignment = Alignment.Center,
                            ) { HlText(d.day.toString(), size = HlSize.t11, weight = FontWeight.SemiBold, color = fg) }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
