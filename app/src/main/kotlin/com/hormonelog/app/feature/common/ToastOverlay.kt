package com.hormonelog.app.feature.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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

@Composable
fun ToastOverlay(text: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(text) {
        if (text != null) {
            delay(2600)
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
                )
            }
        }
    }
}
