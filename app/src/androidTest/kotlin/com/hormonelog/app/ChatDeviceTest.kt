package com.hormonelog.app

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.chat.*
import com.hormonelog.app.feature.chat.ChatScreen
import com.hormonelog.app.platform.TermuxChat
import com.hormonelog.app.state.*
import com.hormonelog.app.ui.theme.HormoneLogTheme
import com.hormonelog.core.data.RecordStore
import com.hormonelog.core.data.SettingsStore
import com.hormonelog.core.domain.ChatModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChatDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun messageBodiesSupportSelectingAndCopyingPartOfTheirText() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val userText = "텍스트 선택 복사를 확인하는 사용자 문장입니다"
            val answer = "텍스트 선택 복사를 확인하는 도우미 문장입니다"
            lateinit var clipboard: android.content.ClipboardManager
            scenario.onActivity { activity ->
                clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val session = ChatSession({ AppState() }, { _, _ -> error("must not save") }, { _, _, done ->
                    done(Result.success("""{"structured_output":{"action":"ask","reply":"$answer"}}"""))
                }, {})
                session.connect(); session.send(userText)
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val serviceInfo = automation.serviceInfo
            serviceInfo.flags = serviceInfo.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = serviceInfo
            for (text in listOf(userText, answer)) {
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                val message = compose.onNodeWithText(text).performScrollTo()
                message.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val word = layouts.single().getBoundingBox(text.indexOf("선택")).center
                message.performTouchInput { longClick(word) }
                var copy: android.view.accessibility.AccessibilityNodeInfo? = null
                compose.waitUntil(10_000) {
                    val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
                    copy = roots.firstNotNullOfOrNull { root -> listOf("Copy", "복사").firstNotNullOfOrNull { label ->
                        root.findAccessibilityNodeInfosByText(label).firstOrNull { it.text?.toString() == label }
                    } }
                    copy != null
                }
                while (copy?.isClickable == false && copy?.parent != null) copy = copy?.parent
                assertTrue(copy!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
                compose.runOnIdle {
                    val selected = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    assertTrue(selected.isNotBlank())
                    assertTrue(text.contains(selected))
                    assertTrue(selected.length < text.length)
                }
            }
        }
    }

    @Test fun preloadedReadShortcutsWorkWithoutClaudeOnDevice() {
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("dedicatedEmulator") == "yes" || args.getString("liveClaude") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            lateinit var recordsFile: File
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                recordsFile = File(activity.filesDir, "records.json")
                activity.setContent { HormoneLogTheme { ChatScreen(vm.chat, {}) } }
            }
            val before = vm.state.snapshot
            val bytes = recordsFile.takeIf { it.exists() }?.readBytes()
            compose.onNode(hasText("단축어 목록", substring = true)).performClick()
            compose.waitUntil(10_000) { vm.chat.ui.lines.lastOrNull()?.text?.startsWith("기본 단축어") == true }
            for ((label, typed, prefix) in listOf(Triple("현재 상태", "현재상태", "현재 상태"),
                Triple("최근 검사", "최근 검사", "실측 검사"), Triple("다음 주사", "다음주사", "다음 주사"))) {
                compose.onNodeWithText(label, useUnmergedTree = true).performClick()
                compose.runOnIdle { assertTrue(vm.chat.ui.lines.last().text.startsWith(prefix)) }
                val count = vm.chat.ui.lines.size
                compose.onNode(hasSetTextAction()).performTextInput(typed)
                compose.onNodeWithText("보내기").assertIsEnabled().performClick()
                compose.waitUntil(10_000) { vm.chat.ui.lines.size == count + 2 }
                compose.runOnIdle {
                    assertTrue(vm.chat.ui.lines.last().text.startsWith(prefix))
                    assertFalse(vm.chat.ui.connected)
                }
            }
            assertEquals(before, vm.state.snapshot)
            if (bytes == null) assertFalse(recordsFile.exists()) else assertArrayEquals(bytes, recordsFile.readBytes())
        }
    }

    @Test fun readsCurrentStatusAndLabsOfflineWithoutChangingStoredRecords() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: ChatSession
            lateinit var file: File
            var calls = 0
            scenario.onActivity { activity ->
                val lab = com.hormonelog.core.domain.LabResult(UUID.randomUUID(), null, null,
                    com.hormonelog.core.domain.Assay.UNKNOWN, listOf(com.hormonelog.core.domain.LabAnalyteValue(
                        com.hormonelog.core.domain.Analyte.ESTRADIOL, 123.45, "pg/mL", 123.45)))
                val state = AppState(labs = listOf(lab))
                file = File(activity.cacheDir, "chat-status-test-${UUID.randomUUID()}.json")
                RecordStore(file).save(state.snapshot)
                session = ChatSession({ state }, { _, _ -> error("read-only query must not save") },
                    { _, _, done -> calls++; done(Result.success("""{"structured_output":{"action":"status"}}""")) }, {})
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            try {
                val before = file.readBytes()
                compose.onNodeWithText("현재 상태", useUnmergedTree = true).performClick()
                compose.waitUntil(10_000) { session.ui.lines.any { it.text.startsWith("현재 상태") } }
                compose.onNodeWithText("최근 검사", useUnmergedTree = true).performClick()
                compose.waitUntil(10_000) { session.ui.lines.last().text.contains("E2 123.45 pg/mL") }
                assertFalse(session.ui.connected)
                assertEquals(0, calls)
                compose.runOnIdle { session.connect() }
                compose.onNode(hasSetTextAction()).performTextInput("현재 상태 알려줘")
                compose.onNodeWithText("보내기").performClick()
                compose.waitUntil(10_000) { session.ui.lines.last().text.startsWith("현재 상태") }
                assertEquals(1, calls)
                assertArrayEquals(before, file.readBytes())
            } finally { file.delete() }
        }
    }

    @Test fun liveCurrentStatusAndLabQueriesKeepPersonalRecordsUnchanged() {
        check(InstrumentationRegistry.getArguments().getString("liveClaude") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            lateinit var recordsFile: File
            scenario.onActivity { activity ->
                check(TermuxChat.permitted(activity))
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                recordsFile = File(activity.filesDir, "records.json")
            }
            val before = vm.state.snapshot
            val beforeBytes = recordsFile.readBytes()
            compose.runOnIdle { vm.chatSetup.done() }
            compose.onNodeWithText("채팅으로 기록").performClick()
            compose.onNodeWithText("현재 상태", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { vm.chat.ui.lines.lastOrNull()?.text?.startsWith("현재 상태") == true }
            compose.onNodeWithText("최근 검사", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { vm.chat.ui.lines.last().text.startsWith("실측 검사") }
            ensureLiveConnection(vm)
            for ((text, prefix) in listOf("현재 상태 알려줘" to "현재 상태", "최근 검사 결과 보여줘" to "실측 검사")) {
                val count = vm.chat.ui.lines.size
                compose.onNode(hasSetTextAction()).performTextInput(text)
                compose.onNodeWithText("보내기").performClick()
                compose.waitUntil(95_000) { !vm.chat.ui.busy && vm.chat.ui.lines.size >= count + 2 }
                compose.runOnIdle { assertTrue(vm.chat.ui.lines.last().text, vm.chat.ui.lines.last().text.startsWith(prefix)) }
            }
            assertEquals(before, vm.state.snapshot)
            assertArrayEquals(beforeBytes, recordsFile.readBytes())
        }
    }

    @Test fun selectsAllModelsAndKeepsSelectionAfterActivityRestart() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            lateinit var settingsFile: File
            var records = AppState().snapshot
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                records = vm.state.snapshot
                settingsFile = File(activity.filesDir, "settings.json")
                activity.setContent { HormoneLogTheme { ChatScreen(vm.chat, {}) } }
            }
            for (model in listOf(ChatModel.OPUS, ChatModel.HAIKU, ChatModel.SONNET)) {
                compose.onNodeWithText(model.label, useUnmergedTree = true).performClick()
                compose.waitUntil(10_000) { vm.chat.model == model && SettingsStore(settingsFile).load()?.chatModel == model }
                assertEquals(records, vm.state.snapshot)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
                activity.setContent { HormoneLogTheme { ChatScreen(vm.chat, {}) } }
            }
            compose.onNodeWithText("Sonnet 5.5 · 다음 요청부터 사용해요").assertExists()
            assertEquals(ChatModel.SONNET, vm.chat.model)
            assertEquals(records, vm.state.snapshot)
            compose.onNodeWithText("Haiku", useUnmergedTree = true).performClick()
            compose.waitUntil(10_000) { SettingsStore(settingsFile).load()?.chatModel == ChatModel.HAIKU }
        }
    }

    @Test fun writesAndUndoesInDedicatedTemporaryStore() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: ChatSession
            lateinit var store: RecordStore
            lateinit var file: File
            scenario.onActivity { activity ->
                file = File(activity.cacheDir, "chat-test-${UUID.randomUUID()}.json")
                store = RecordStore(file)
                var state = AppState()
                session = ChatSession({ state }, { next, done -> state = next; store.save(next.snapshot); done(true) },
                    { _, _, done -> done(Result.success("""{"structured_output":{"action":"dose","drug":"EV","route":"근육주사","amount":"5 mg","date":"2020-01-01","time":"15:30"}}""")) }, {})
                session.connect()
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            try {
                compose.onNode(hasSetTextAction()).performTextInput("2020-01-01 15:30 EV 5 mg 근육주사 기록해줘")
                compose.onNodeWithText("보내기").performClick()
                compose.waitUntil(10_000) { session.ui.lines.any { it.text.startsWith("저장 완료") } }
                assertEquals(1, (store.load() as RecordStore.Load.Ok).snapshot.doses.size)
                compose.onNodeWithText("직전 채팅 등록 되돌리기").performClick()
                compose.waitUntil(10_000) { (store.load() as RecordStore.Load.Ok).snapshot.doses.isEmpty() }
            } finally { file.delete() }
        }
    }

    /** Simulated model responses; all writes use this test's temporary file on an emulator. */
    @Test fun friendlyMultiDoseClarificationAndJustTookUtrogestan() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: ChatSession
            lateinit var store: RecordStore
            lateinit var file: File
            var state = AppState()
            var calls = 0
            scenario.onActivity { activity ->
                file = File(activity.cacheDir, "chat-ux-test-${UUID.randomUUID()}.json")
                store = RecordStore(file)
                session = ChatSession({ state }, { next, done -> state = next; store.save(next.snapshot); done(true) },
                    { _, _, done ->
                        calls++
                        val result = when (calls) {
                            1 -> """{"structured_output":{"action":"batch","time":"방금","doses":[{"drug":"EV","route":"근육주사","amount":"2mg"},{"drug":"안쿨","amount":"12.5"}]}}"""
                            2 -> """{"structured_output":{"action":"batch","time":"방금","doses":[{"drug":"EV","route":"근육주사","amount":"2mg"},{"drug":"안쿨","amount":"12.5","amountUnit":"mg"}]}}"""
                            else -> """{"structured_output":{"action":"dose","drug":"유트로게스탄","amount":"100mg","time":"방금"}}"""
                        }
                        done(Result.success(result))
                    }, {})
                session.connect()
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            fun send(text: String) {
                compose.onNode(hasSetTextAction()).performTextInput(text)
                compose.onNodeWithText("보내기").performClick()
            }
            try {
                send("방금 EV 2mg 근육주사맞고 안쿨 12.5 먹엇어")
                compose.waitUntil(10_000) { session.ui.lines.last().text.contains("단위") }
                assertTrue(state.doses.isEmpty())
                assertFalse(file.exists())
                send("안쿨은 mg 단위야")
                compose.waitUntil(10_000) { session.ui.lines.last().text.startsWith("저장 완료") }
                assertEquals(2, (store.load() as RecordStore.Load.Ok).snapshot.doses.size)
                send("유트로게스탄 100mg 기록해줘 방금복용")
                compose.waitUntil(10_000) { state.doses.size == 3 }
                assertEquals(3, (store.load() as RecordStore.Load.Ok).snapshot.doses.size)
                compose.onNodeWithText("직전 채팅 등록 되돌리기").performClick()
                compose.waitUntil(10_000) { state.doses.size == 2 }
                assertEquals(2, (store.load() as RecordStore.Load.Ok).snapshot.doses.size)
            } finally { file.delete() }
        }
    }

    /** Simulated model responses and a dedicated temporary store on the emulator. */
    @Test fun teachesMemoryReloadsInvokesEditsAndForgetsWithoutDeletingDoses() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: ChatSession
            lateinit var store: RecordStore
            lateinit var file: File
            var state = AppState()
            var calls = 0
            fun newSession() = ChatSession({ state }, { next, done -> state = next; store.save(next.snapshot); done(true) },
                { _, _, done ->
                    calls++
                    val result = when (calls) {
                        1 -> """{"structured_output":{"action":"remember_doses","memoryName":"밤약","doses":[{"drug":"유트로게스탄","route":"경구","amount":"100mg"}]}}"""
                        2 -> """{"structured_output":{"action":"shortcut","memoryName":"밤약"}}"""
                        3 -> """{"structured_output":{"action":"update_doses","memoryName":"밤약","doses":[{"amount":"200mg"}]}}"""
                        else -> """{"structured_output":{"action":"memory_forget","memoryName":"밤약"}}"""
                    }
                    done(Result.success(result))
                }, {})
            scenario.onActivity { activity ->
                file = File(activity.cacheDir, "chat-memory-test-${UUID.randomUUID()}.json")
                store = RecordStore(file)
                session = newSession(); session.connect()
                activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
            }
            fun send(text: String) {
                compose.onNode(hasSetTextAction()).performTextInput(text)
                compose.onNodeWithText("보내기").performClick()
            }
            try {
                send("밤약은 유트로게스탄 100mg 경구로 기억해줘")
                compose.waitUntil(10_000) { session.memoryCount == 1 }
                val snapshot = (store.load() as RecordStore.Load.Ok).snapshot
                assertTrue(snapshot.doses.isEmpty())
                scenario.onActivity { activity ->
                    state = AppState(chatMemories = snapshot.chatMemories)
                    session = newSession(); session.connect()
                    activity.setContent { HormoneLogTheme { ChatScreen(session, {}) } }
                }
                send("밤약")
                compose.waitUntil(10_000) { state.doses.size == 1 }
                assertEquals(1, (store.load() as RecordStore.Load.Ok).snapshot.doses.size)
                send("밤약 용량을 200mg로 바꿔줘")
                compose.waitUntil(10_000) { (state.chatMemories.single() as com.hormonelog.core.domain.ChatMemory.Shortcut).doses.single().amountMg == 200.0 }
                assertEquals(1, state.doses.size)
                send("밤약 기억 지워줘")
                compose.waitUntil(10_000) { state.chatMemories.isEmpty() }
                assertEquals(1, state.doses.size)
                compose.onNodeWithText("직전 채팅 등록 되돌리기").performClick()
                compose.waitUntil(10_000) { state.chatMemories.size == 1 }
                assertEquals(1, state.doses.size)
            } finally { file.delete() }
        }
    }

    /** Opt-in live provider check. Never deletes or registers records on the personal phone. */
    @Test fun liveSubscriptionRoundTripReadsRecordsWithoutChangingThem() {
        check(InstrumentationRegistry.getArguments().getString("liveClaude") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            scenario.onActivity { activity ->
                check(TermuxChat.permitted(activity))
                vm = ViewModelProvider(activity)[AppViewModel::class.java]
            }
            val before = vm.state.snapshot
            val expected = ChatCommands.apply(vm.state, ChatCommand("recent"), emptyList(), UUID.randomUUID(), Instant.now(), ZoneId.systemDefault()).message
            compose.runOnIdle { vm.chatSetup.done() }
            compose.onNodeWithText("채팅으로 기록").performClick()
            ensureLiveConnection(vm)
            val originalModel = vm.chat.model
            try {
                for (model in ChatModel.entries) {
                    compose.onNodeWithText(model.label, useUnmergedTree = true).performClick()
                    compose.runOnIdle { assertEquals(model, vm.chat.model) }
                    val count = vm.chat.ui.lines.size
                    compose.onNode(hasSetTextAction()).performTextInput("최근 기록 보여줘")
                    compose.onNodeWithText("보내기").performClick()
                    compose.waitUntil(95_000) { !vm.chat.ui.busy && vm.chat.ui.lines.size >= count + 2 }
                    compose.runOnIdle {
                        assertEquals(expected, vm.chat.ui.lines.last().text)
                        assertEquals(before, vm.state.snapshot)
                    }
                }
            } finally { compose.runOnIdle { vm.chat.selectModel(originalModel) } }
        }
    }

    private fun ensureLiveConnection(vm: AppViewModel) {
        if (vm.chat.ui.connected) return
        compose.runOnIdle { vm.chatSetup.open(); vm.chatSetup.start() }
        compose.waitUntil(180_000) { vm.chatSetup.ui.stage in setOf(ChatSetupStage.READY, ChatSetupStage.ERROR) }
        assertEquals(vm.chatSetup.ui.message, ChatSetupStage.READY, vm.chatSetup.ui.stage)
        compose.onNodeWithText("채팅 사용하기").performClick()
    }
}
