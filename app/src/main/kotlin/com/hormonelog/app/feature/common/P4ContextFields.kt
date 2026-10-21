package com.hormonelog.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.P4Context
import com.hormonelog.core.domain.P4Meal
import com.hormonelog.core.domain.P4Product

/** Actual conditions are recorded; a schedule's meal is only a declared expectation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun P4ContextFields(context: P4Context, planned: Boolean, onChange: (P4Context) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText("제품·제형", size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            P4Product.entries.forEach { product ->
                HlChip(product.label, context.product == product, { onChange(context.copy(product = product)) })
            }
        }
        HlText(if (planned) "예정 식사 조건" else "이번 복용의 식사 조건", size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            P4Meal.entries.forEach { meal ->
                HlChip(meal.label, context.meal == meal, { onChange(context.copy(meal = meal)) })
            }
        }
        HlText(
            if (planned) "예정 조건으로만 보관해요. 지난 투약·알림 완료 기록의 실제 식사는 모름으로 남겨요."
            else "실제로 복용했을 때의 조건을 기록해요. 복용법이나 식사법을 권하는 항목이 아니에요.",
            size = HlSize.t12, color = Hl.colors.muted,
        )
        HlText("현재 P4 곡선은 일반 허가자료 참고값이에요. 이 제품·식사 조건을 반영한 새 계수는 아직 검증 중이에요.", size = HlSize.t12, color = Hl.colors.orange)
    }
}
