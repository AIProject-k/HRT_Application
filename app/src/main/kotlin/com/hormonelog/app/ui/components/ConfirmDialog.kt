package com.hormonelog.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType

/**
 * Destructive-action confirmation. [onConfirm] fires then the dialog dismisses;
 * the caller only has to clear its own "pending" state in [onDismiss].
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String = "삭제",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, style = HlType.LabelStrong, color = HlColor.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", style = HlType.LabelStrong, color = HlColor.TextMuted)
            }
        },
        title = { Text(title, style = HlType.CardTitleLg, color = HlColor.TextPrimary) },
        text = { Text(body, style = HlType.Body, color = HlColor.TextSecondary) },
        containerColor = HlColor.Card,
    )
}
