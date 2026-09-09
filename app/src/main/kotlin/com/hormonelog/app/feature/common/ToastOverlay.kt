package com.hormonelog.app.feature.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import kotlinx.coroutines.delay

/**
 * [onUndo] non-null puts a 실행 취소 action on the toast; the message then lingers
 * longer, because a toast the user has to read *and* decide about needs more than the
 * glance an acknowledgement needs.
 */
@Composable
fun ToastOverlay(
    text: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: (() -> Unit)? = null,
) {
    LaunchedEffect(text, onUndo != null) {
        if (text != null) {
            delay(if (onUndo != null) 5000 else 2600)
            onDismiss()
        }
    }
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AnimatedVisibility(
            visible = text != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HlColor.ToastBackground)
                    .padding(horizontal = 15.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("✓", fontSize = 14.sp, color = HlColor.ToastForeground)
                Text(
                    text ?: "",
                    style = HlType.CardTitle,
                    color = HlColor.ToastForeground,
                    modifier = Modifier.weight(1f),
                )
                if (onUndo != null) {
                    Text(
                        "실행 취소",
                        style = HlType.LabelStrong,
                        color = HlColor.Teal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onUndo)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
