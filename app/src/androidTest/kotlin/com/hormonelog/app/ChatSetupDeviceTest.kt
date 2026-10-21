package com.hormonelog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatSetupDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun optionalOfflineModelSetupCanBeClosedWithoutDownloadingOrChangingRecords() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: AppViewModel
            scenario.onActivity { vm=ViewModelProvider(it)[AppViewModel::class.java]; vm.chatSetup.open() }
            val before=vm.state.snapshot
            compose.onNodeWithText("오프라인 AI 모델").assertExists()
            compose.onNodeWithText("나중에 · 기본 기록 사용").performScrollTo().performClick()
            compose.runOnIdle { assertFalse(vm.chatSetup.ui.visible); assertEquals(before,vm.state.snapshot) }
            scenario.recreate()
            scenario.onActivity { vm=ViewModelProvider(it)[AppViewModel::class.java] }
            compose.runOnIdle { assertFalse(vm.chatSetup.ui.visible); assertEquals(before,vm.state.snapshot) }
        }
    }
}
