package com.hormonelog.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.chat.ChatSession
import com.hormonelog.app.chat.RuleBasedParser
import com.hormonelog.app.feature.chat.ChatScreen
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.theme.HormoneLogTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Undo right after a save, from the chat screen, on a dedicated emulator. The model is never called: the rules decide. */
class ChatUndoDeviceTest {
    @get:Rule val compose = createComposeRule()
    private var store = AppState()

    private fun session() = ChatSession(
        state = { store },
        save = { next, done -> store = next; done(true) },
        request = { _, payload, done ->
            val rule = RuleBasedParser.parse(payload, store)
            if (rule != null) done(Result.success(JSONObject().put("structured_output", ChatProtocol.commandJson(rule)).toString()))
            else done(Result.failure(IllegalStateException("MODEL_NOT_RUN")))
        },
        cancelRequest = {},
        allowOfflineInput = true,
    )

    @Test fun doseSaveIsUndoneFromTheChatButton() {
        val chat = session()
        compose.setContent { HormoneLogTheme { ChatScreen(chat, back = {}) } }
        chat.editInput("방금 프로기노바 2mg 복용")
        compose.waitForIdle()
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()
        assertEquals(1, store.doses.size)

        compose.onNodeWithText("직전 채팅 등록 되돌리기").performClick()
        compose.waitForIdle()
        assertTrue("undo must remove the saved dose", store.doses.isEmpty())
        compose.onAllNodesWithText("직전 채팅 등록 되돌리기").assertCountEquals(0)
    }

    @Test fun memoSaveIsUndoneFromTheChatButton() {
        val chat = session()
        compose.setContent { HormoneLogTheme { ChatScreen(chat, back = {}) } }
        chat.editInput("머리가 아파요. 다음 진료 때 얘기할 것")
        compose.waitForIdle()
        compose.onNodeWithText("메모로 저장").performClick()
        compose.waitForIdle()
        assertEquals(1, store.journal.size)

        compose.onNodeWithText("직전 채팅 등록 되돌리기").performClick()
        compose.waitForIdle()
        assertTrue("undo must remove the saved memo", store.journal.isEmpty())
        assertEquals(0, store.doses.size)
    }
}
