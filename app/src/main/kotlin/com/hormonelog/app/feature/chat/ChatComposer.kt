package com.hormonelog.app.feature.chat

import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlSize

@Composable
internal fun ChatComposer(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Hl.colors
    val density = LocalDensity.current
    val textSizePx = with(density) { HlSize.t14.toPx() }
    val paddingPx = with(density) { 12.dp.roundToPx() }
    val maxHeightPx = with(density) { 144.dp.roundToPx() }
    val onChange by rememberUpdatedState(onValueChange)
    val shape = RoundedCornerShape(12.dp)
    AndroidView(
        modifier = modifier.heightIn(min = 52.dp, max = 144.dp)
            .background(colors.input, shape).border(1.dp, colors.line, shape),
        factory = { context ->
            EditText(context).apply {
                contentDescription = "채팅 입력"
                hint = "무엇을 기록할까요?"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                gravity = Gravity.TOP or Gravity.START
                background = null
                minLines = 1
                isVerticalScrollBarEnabled = true
                setHorizontallyScrolling(false)
                doAfterTextChanged { onChange(it?.toString().orEmpty()) }
            }
        },
        update = { editor ->
            editor.setTextColor(colors.text.toArgb())
            editor.setHintTextColor(colors.dim.toArgb())
            editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            editor.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            editor.maxHeight = maxHeightPx
            // Equal text must retain the native Editable, selection and IME composing spans.
            // Only a restored draft, explicit send or reset replaces its contents.
            if (editor.text.toString() != value) {
                val start = editor.selectionStart.coerceAtLeast(0)
                val end = editor.selectionEnd.coerceAtLeast(0)
                editor.setText(value)
                editor.setSelection(start.coerceAtMost(editor.length()), end.coerceAtMost(editor.length()))
            }
        },
    )
}
