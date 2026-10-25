package com.hormonelog.app

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hormonelog.app.feature.chat.ChatComposer
import com.hormonelog.app.ui.theme.HormoneLogTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Korean composition through the same InputConnection calls an IME makes, on the real composer EditText.
 * This does not drive a Korean keyboard app; it checks what the app receives from one.
 */
class ChatImeDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun editor(view: View): EditText? = when (view) {
        is EditText -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { editor(view.getChildAt(it)) }
        else -> null
    }

    @Test fun koreanCompositionCommitsOnceAndLaterTextAppends() {
        val draft = mutableStateOf("")
        compose.setContent { HormoneLogTheme { ChatComposer(draft.value, { draft.value = it }, Modifier) } }
        compose.waitForIdle()
        compose.runOnUiThread {
            val input = editor(compose.activity.window.decorView)!!
            input.requestFocus()
            val ic = requireNotNull(input.onCreateInputConnection(EditorInfo()))
            // A Korean IME rewrites the composing syllable at each keystroke, then finishes it.
            ic.setComposingText("ㅍ", 1)
            ic.setComposingText("프", 1)
            ic.setComposingText("프ㄹ", 1)
            ic.setComposingText("프로", 1)
            ic.finishComposingText()
            assertEquals("프로", input.text.toString())
            assertEquals(-1, BaseInputConnection.getComposingSpanStart(input.text))
        }
        compose.waitForIdle()
        assertEquals("프로", draft.value)

        compose.runOnUiThread {
            val input = editor(compose.activity.window.decorView)!!
            requireNotNull(input.onCreateInputConnection(EditorInfo())).commitText(" 기노바 2mg", 1)
        }
        compose.waitForIdle()
        assertEquals("프로 기노바 2mg", draft.value)
    }
}
