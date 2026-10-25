package com.hormonelog.app

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hormonelog.app.ai.ModelSpec
import com.hormonelog.app.chat.ChatSession
import com.hormonelog.app.chat.LocalChatController
import com.hormonelog.app.feature.chat.ChatSetupScreen
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.theme.HormoneLogTheme
import java.io.File
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Model choice on the dedicated emulator. Nothing is downloaded or loaded here: the model files are not present,
 * so a tap only changes which model the setup screen and the next chat would use.
 */
class ChatSetupModelDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val choice = File(app.noBackupFilesDir, "offline-ai/selected-model.txt")
    private var store = AppState()

    @Before fun resetChoice() { choice.delete() }

    private fun controller(): LocalChatController {
        val setup = LocalChatController(app, MainScope()) { store }
        setup.chat = ChatSession(
            state = { store },
            save = { next, done -> store = next; done(true) },
            request = { _, _, done -> done(Result.failure(IllegalStateException("MODEL_NOT_RUN"))) },
            cancelRequest = {},
            allowOfflineInput = true,
        )
        return setup
    }

    @Test fun builtInModelIsSelectedByDefaultAndGemmaOnlyWhenTapped() {
        val setup = controller()
        compose.setContent { HormoneLogTheme { ChatSetupScreen(setup) } }
        compose.onNodeWithText("✓ Qwen3-0.6B · 기본 내장").assertIsDisplayed()
        assertEquals(ModelSpec.QWEN, setup.spec)

        compose.onNodeWithText("Gemma 4 E2B · 선택 설치 (약 2.6 GB)").performClick()
        compose.waitForIdle()
        assertEquals(ModelSpec.GEMMA, setup.spec)
        compose.onNodeWithText("✓ Gemma 4 E2B · 선택 설치 (약 2.6 GB)").assertIsDisplayed()
        // The install button sits below the fold on large fonts or narrow widths; the screen scrolls to it.
        compose.onNodeWithText("오프라인 AI 다운로드").performScrollTo().assertIsDisplayed()
    }

    @Test fun pickedModelIsRememberedAcrossAStart() {
        val setup = controller()
        compose.setContent { HormoneLogTheme { ChatSetupScreen(setup) } }
        compose.onNodeWithText("Gemma 4 E2B · 선택 설치 (약 2.6 GB)").performClick()
        compose.waitForIdle()
        assertEquals(ModelSpec.GEMMA.fileName, choice.readText())
        assertEquals(ModelSpec.GEMMA, controller().spec)
    }

    @Test fun unknownChoiceFallsBackToTheBuiltInModel() {
        choice.parentFile?.mkdirs()
        choice.writeText("removed-model.litertlm")
        assertEquals(ModelSpec.QWEN, controller().spec)
    }
}
