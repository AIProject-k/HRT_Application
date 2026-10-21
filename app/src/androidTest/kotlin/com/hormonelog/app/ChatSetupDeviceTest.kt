package com.hormonelog.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.chat.ChatSetupStage
import com.hormonelog.app.chat.ChatCommands
import com.hormonelog.app.chat.ChatCommand
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ChatSetupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun firstGuideCanBeSkippedAndDeniedPermissionNeverEnablesClaude() {
        check(InstrumentationRegistry.getArguments().getString("dedicatedEmulator") == "yes")
        val app = ApplicationProvider.getApplicationContext<Context>()
        app.getSharedPreferences("chat_setup", Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
            val before = vm.state.snapshot
            compose.onNodeWithText("채팅 설치 가이드").assertExists()
            compose.onNodeWithText("Termux 설치·초기화·권한 설정 방법").performScrollTo().performClick()
            compose.onNodeWithText("4. 최초 준비 명령 붙여넣기").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Termux 설정 방법 접기").performScrollTo().performClick()
            compose.onNodeWithText("4. 최초 준비 명령 붙여넣기").assertDoesNotExist()
            compose.onNodeWithText("건너뛰고 기록 사용").performScrollTo().performClick()
            compose.runOnIdle {
                assertFalse(vm.chatSetup.ui.visible)
                assertFalse(vm.chat.ui.connected)
                vm.chatSetup.open()
                vm.chat.connect()
                vm.chatSetup.permissionResult(false)
                assertEquals(ChatSetupStage.ERROR, vm.chatSetup.ui.stage)
                assertFalse(vm.chat.ui.connected)
            }
            compose.onNodeWithText("설치·연결 다시 시도").assertExists()
            compose.onNodeWithText("건너뛰고 기록 사용").performClick()
            scenario.recreate()
            scenario.onActivity { vm = ViewModelProvider(it)[AppViewModel::class.java] }
            assertFalse(vm.chatSetup.ui.visible)
            assertFalse(vm.chat.ui.connected)
            assertEquals(before, vm.state.snapshot)
        }
    }

    @Test fun adoptsExistingEnvironmentAndGatesSubscriptionWithoutChangingRecords() {
        check(InstrumentationRegistry.getArguments().getString("liveClaude") == "yes")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            lateinit var file: File
            scenario.onActivity {
                vm = ViewModelProvider(it)[AppViewModel::class.java]
                file = File(it.filesDir, "records.json")
                vm.chatSetup.open()
            }
            val before = vm.state.snapshot
            val bytes = file.readBytes()
            scenario.onActivity { vm.chatSetup.start() }
            compose.waitUntil(180_000) { vm.chatSetup.ui.stage in setOf(ChatSetupStage.READY, ChatSetupStage.ERROR, ChatSetupStage.BOOTSTRAP) }
            compose.runOnIdle {
                assertEquals(before, vm.state.snapshot)
                assertArrayEquals(bytes, file.readBytes())
            }
            if (vm.chatSetup.ui.stage == ChatSetupStage.ERROR) {
                assertTrue(vm.chatSetup.ui.message, vm.chatSetup.ui.message.contains("사용량 한도"))
                assertFalse(vm.chat.ui.connected)
                println("HRT_SETUP_RESULT=USAGE_LIMIT_CHAT_DISABLED")
                return@use
            }
            assertEquals(vm.chatSetup.ui.message, ChatSetupStage.READY, vm.chatSetup.ui.stage)
            assertTrue(vm.chat.ui.connected)
            val expected = ChatCommands.apply(vm.state, ChatCommand("recent"), emptyList(), UUID.randomUUID(), Instant.now(), ZoneId.systemDefault()).message
            // Open chat in the same activity without waiting for the live home chart to settle.
            scenario.onActivity {
                vm.chatSetup.done()
                vm.act { state -> com.hormonelog.app.state.Nav.push(state, com.hormonelog.app.state.Screen.CHAT) }
            }
            compose.onNode(hasSetTextAction()).performTextInput("최근 기록 보여줘")
            compose.onNodeWithText("보내기").performClick()
            compose.waitUntil(95_000) { !vm.chat.ui.busy && vm.chat.ui.lines.any { !it.user } }
            compose.runOnIdle {
                assertEquals(expected, vm.chat.ui.lines.last().text)
                assertEquals(before, vm.state.snapshot)
                assertArrayEquals(bytes, file.readBytes())
            }
            println("HRT_SETUP_RESULT=READY_LIVE_QUERY_PASSED")
        }
    }
}
