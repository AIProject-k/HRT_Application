package com.hormonelog.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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

/**
 * Compose UI on the dedicated emulator. No records are wiped; the in-memory state is a fresh AppState.
 * The composer is a native text view, so input is set through ChatSession.editInput, the call it makes on each keystroke.
 * The model is stubbed: a sentence the rules cannot finish fails the way an unavailable model fails.
 */
class ChatScreenDeviceTest {
    @get:Rule val compose = createComposeRule()

    private var store = AppState()

    private fun session() = ChatSession(
        state = { store },
        save = { next, done -> store = next; done(true) },
        request = { _, payload, done ->
            val rule = RuleBasedParser.parse(payload, store)
            if (rule != null) done(Result.success(JSONObject().put("structured_output", ChatProtocol.commandJson(rule)).toString()))
            else done(Result.failure(IllegalStateException("기기 내 AI 처리에 실패했어요. 기록은 저장하지 않았어요. 모델 상태와 입력 길이를 확인해 주세요.")))
        },
        cancelRequest = {},
        allowOfflineInput = true,
    )

    private fun show(chat: ChatSession) = compose.setContent { HormoneLogTheme { ChatScreen(chat, back = {}) } }

    @Test fun memoButtonSavesTheTypedTextWithoutAnyModel() {
        val chat = session()
        show(chat)
        chat.editInput("머리가 아파요. 다음 진료 때 얘기할 것")
        compose.waitForIdle()
        compose.onNodeWithText("메모로 저장").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("메모로 저장했어요", substring = true).assertIsDisplayed()
        assertEquals("머리가 아파요. 다음 진료 때 얘기할 것", store.journal.single().note)
        assertTrue(store.doses.isEmpty())
    }

    @Test fun failedRequestRestoresTheRawTextForAMemo() {
        val chat = session()
        show(chat)
        val raw = "오늘 머리가 아프고 피곤했어"
        chat.editInput(raw)
        compose.waitForIdle()
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()
        assertEquals(raw, chat.ui.input)
        compose.onNodeWithText("메모로 저장").performClick()
        compose.waitForIdle()
        assertEquals(raw, store.journal.single().note)
    }

    @Test fun refusedSymptomSentenceIsRestoredOnScreenAndSavedAsAMemo() {
        val chat = session()
        show(chat)
        val raw = "방금 프로기노바 2mg 먹었고 어지러웠어"
        chat.editInput(raw)
        compose.waitForIdle()
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()
        assertTrue(store.doses.isEmpty())
        assertEquals(raw, chat.ui.input)
        compose.onNodeWithText("메모로 저장").performClick()
        compose.waitForIdle()
        assertEquals(raw, store.journal.single().note)
    }

    @Test fun overLengthTextIsKeptAndSaysSoOnScreen() {
        val chat = session()
        show(chat)
        val long = "가".repeat(8001)
        chat.editInput(long)
        compose.waitForIdle()
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("이하로 입력해 주세요", substring = true).assertIsDisplayed()
        assertEquals(long, chat.ui.input)
        assertTrue(store.journal.isEmpty())
    }

    @Test fun openRequestShowsChoicesAndCanBeCancelled() {
        val chat = session()
        show(chat)
        chat.editInput("10월 24일 10시 프로기노바 2mg 복용")
        compose.waitForIdle()
        compose.onNodeWithText("보내기").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("이 요청 취소").assertIsDisplayed()
        compose.onNodeWithText("오전").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("로 기록할까요", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("이 요청 취소").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("이 요청 취소").assertCountEquals(0)
        assertTrue(store.doses.isEmpty())
    }
}
