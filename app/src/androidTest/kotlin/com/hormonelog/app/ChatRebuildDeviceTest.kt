package com.hormonelog.app

import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.hormonelog.app.chat.*
import com.hormonelog.app.feature.chat.ChatScreen
import com.hormonelog.app.platform.TermuxChat
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.theme.HormoneLogTheme
import com.hormonelog.core.domain.ChatModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Actual provider, isolated in-memory medical state; no test writes to records.json. */
@RunWith(AndroidJUnit4::class)
class ChatRebuildDeviceTest {
    @Test fun liveConnectionRefreshPreservesPersonalRecordsAndConversation() {
        check(InstrumentationRegistry.getArguments().getString("liveClaude") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "records.json")
        val before = file.takeIf { it.exists() }?.readBytes()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            var originalInput = ""
            scenario.onActivity {
                vm = ViewModelProvider(it)[AppViewModel::class.java]
                originalInput = vm.chat.ui.input
                vm.chatSetup.open(); vm.chatSetup.start()
            }
            val deadline = SystemClock.uptimeMillis() + 180_000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync {
                    check(vm.chatSetup.ui.stage != ChatSetupStage.ERROR) { vm.chatSetup.ui.message }
                    ready = vm.chatSetup.ui.stage == ChatSetupStage.READY
                }
                if (!ready) SystemClock.sleep(200)
            }
            assertTrue("Connection did not reach READY", ready)
            scenario.onActivity {
                vm.chatSetup.close()
                assertTrue(vm.chat.ui.connected)
                assertEquals(originalInput, vm.chat.ui.input)
                vm.chat.flush()
            }
        }
        if (before == null) assertFalse(file.exists()) else assertArrayEquals(before, file.readBytes())
    }

    @Test fun liveSplitRequestResumeAndExplicitPreviousDoseWorkWithoutPersonalWrites() {
        check(InstrumentationRegistry.getArguments().getString("liveClaude") == "yes")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val personal = File(app.filesDir, "records.json")
        val before = personal.takeIf { it.exists() }?.readBytes()
        val conversation = File(app.cacheDir, "chat-rebuild-${System.nanoTime()}.json")
        var state = AppState()
        lateinit var chat: ChatSession
        fun create() = ChatSession({ state }, { next, done -> state = next; done(true) },
            { id, data, done -> TermuxChat.request(app, id, data, ChatModel.HAIKU, done) },
            TermuxChat::cancel, conversationStore = ChatConversationStore(conversation))
        fun settle() {
            val deadline = SystemClock.uptimeMillis() + 100_000
            var busy = true
            while (busy && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { busy = chat.ui.busy }
                if (busy) SystemClock.sleep(100)
            }
            assertFalse("Provider request timed out", busy)
        }
        fun send(text: String) { instrumentation.runOnMainSync { chat.send(text) }; settle() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    chat = create(); chat.connect()
                    activity.setContent { HormoneLogTheme { ChatScreen(chat, {}) } }
                }
                send("유트로게스탄 먹었어")
                assertTrue(state.doses.isEmpty())
                instrumentation.runOnMainSync { chat.showStatus(); chat.editInput("100mg"); chat.flush() }
                scenario.onActivity { activity ->
                    chat = create(); chat.connect()
                    assertEquals("100mg", chat.ui.input)
                    activity.setContent { HormoneLogTheme { ChatScreen(chat, {}) } }
                    chat.submit()
                }
                settle()
                assertTrue(state.doses.isEmpty())
                send("어제 밤 9시")
                assertEquals(chat.ui.lines.last().text, 1, state.doses.size)
                send("같은 약 그제도 먹었어")
                assertEquals(1, state.doses.size)
                send("밤 10시")
                assertEquals(chat.ui.lines.last().text, 2, state.doses.size)
                assertTrue(state.doses.all { it.amountEntered == 100.0 })
                val zone = ZoneId.systemDefault()
                assertEquals(Instant.now().atZone(zone).toLocalDate().minusDays(2), state.doses.minBy { it.occurredAt }.occurredAt.atZone(zone).toLocalDate())
                send("고마워")
                assertEquals(2, state.doses.size)
                assertTrue(chat.canSend("이어서"))
                instrumentation.runOnMainSync { chat.flush() }
                assertTrue(ChatConversationStore(conversation).load().lines.size >= 10)
            }
        } finally {
            conversation.delete()
            if (before == null) assertFalse(personal.exists()) else assertArrayEquals(before, personal.readBytes())
        }
    }
}
