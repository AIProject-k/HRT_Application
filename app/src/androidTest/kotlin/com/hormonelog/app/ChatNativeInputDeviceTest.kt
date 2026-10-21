package com.hormonelog.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.text.Editable
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.chat.ChatSession
import com.hormonelog.app.feature.chat.ChatComposer
import com.hormonelog.app.feature.chat.ChatScreen
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.theme.HormoneLogTheme
import com.hormonelog.core.domain.ThemeMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native input checks with synthetic text only; no Compose test rule or Espresso injection. */
@RunWith(AndroidJUnit4::class)
class ChatNativeInputDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    @Test fun selectedMessageTextPastesIntoChatDraft() {
        requireDedicatedEmulator()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val answer = "선택해서 복사할 문장입니다"
            lateinit var session: ChatSession
            scenario.onActivity { activity ->
                session = ChatSession({ AppState() }, { _, _ -> error("must not save records") },
                    { _, _, done -> done(Result.success("""{"structured_output":{"action":"ask","reply":"$answer"}}""")) }, {})
                session.connect()
                session.send("입력창 확인용 가상 질문")
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            val info = automation.serviceInfo
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            val message = waitForNode { it.text?.toString() == answer }
            val bounds = Rect().also(message::getBoundsInScreen)
            longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            click(waitForNode { it.text?.toString() in setOf("복사", "Copy") })

            var copied = ""
            scenario.onActivity { activity ->
                copied = (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                assertTrue(copied.isNotBlank())
                assertTrue(answer.contains(copied))
                assertTrue("A text block, not the whole message, must be selected", copied.length < answer.length)
                editor(activity.window.decorView)!!.apply { requestFocus(); setSelection(0) }
            }
            val input = waitForNode { it.className?.toString() == EditText::class.java.name }
            assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_PASTE))
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(copied, editor(activity.window.decorView)!!.text.toString())
                assertEquals(copied, session.ui.input)
                assertEquals(2, session.ui.lines.size)
            }
        }
    }

    @Test fun recompositionPreservesNativeSelectionAndComposingTextAndBoundsLength() {
        requireDedicatedEmulator()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val draft = mutableStateOf("가상 입력 선택 검사")
            val light = mutableStateOf(false)
            lateinit var original: Editable
            scenario.onActivity { activity ->
                activity.setContent {
                    HormoneLogTheme(mode = if (light.value) ThemeMode.LIGHT else ThemeMode.DARK) {
                        ChatComposer(draft.value, { draft.value = it }, Modifier.fillMaxWidth())
                    }
                }
            }
            waitForNode { it.className?.toString() == EditText::class.java.name }
            scenario.onActivity { activity ->
                val input = editor(activity.window.decorView)!!
                requireNotNull(input.onCreateInputConnection(android.view.inputmethod.EditorInfo())).setComposingRegion(3, 5)
                input.setSelection(3, 5)
                original = input.text
                assertEquals(3, BaseInputConnection.getComposingSpanStart(input.text))
                light.value = true
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val input = editor(activity.window.decorView)!!
                assertSame(original, input.text)
                assertEquals(3, input.selectionStart)
                assertEquals(5, input.selectionEnd)
                assertEquals(3, BaseInputConnection.getComposingSpanStart(input.text))
                assertEquals(5, BaseInputConnection.getComposingSpanEnd(input.text))
                input.text.replace(0, input.length(), "첫째 줄\n둘째 줄")
                assertEquals("첫째 줄\n둘째 줄", draft.value)
                input.text.replace(0, input.length(), "가".repeat(8100))
                assertEquals(8000, input.length())
                assertEquals(8000, draft.value.length)
            }
        }
    }

    private fun requireDedicatedEmulator() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("dedicatedEmulator") == "yes" || args.getString("liveClipboard") == "yes")
    }

    private fun editor(view: View): EditText? = when (view) {
        is EditText -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { editor(view.getChildAt(it)) }
        else -> null
    }

    private fun waitForNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
            roots.firstNotNullOfOrNull { findNode(it, predicate) }?.let { return it }
            SystemClock.sleep(50)
        }
        error("Expected native accessibility node did not appear")
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        return (0 until node.childCount).firstNotNullOfOrNull { node.getChild(it)?.let { child -> findNode(child, predicate) } }
    }

    private fun click(node: AccessibilityNodeInfo) {
        var target = node
        while (!target.isClickable && target.parent != null) target = target.parent
        if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            val bounds = Rect().also(node::getBoundsInScreen)
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            }
        }
    }

    private fun longPress(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        event(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(800)
        event(MotionEvent.ACTION_UP)
    }
}
