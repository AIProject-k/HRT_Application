package com.hormonelog.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.records.RecordsActions
import com.hormonelog.app.feature.records.RecordsScreen
import com.hormonelog.app.feature.timeline.RowMenuSheet
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.JournalOps
import com.hormonelog.app.state.RowTarget
import com.hormonelog.app.ui.theme.HormoneLogTheme
import com.hormonelog.core.domain.JournalEntry
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A memo written on the phone, edited and deleted through the real screens, on a dedicated emulator.
 * The state is a plain AppState held by the test; every action calls the same JournalOps the app uses.
 */
class MemoEditDeviceTest {
    @get:Rule val compose = createComposeRule()

    private val now = Instant.parse("2026-10-24T14:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val fmt = Fmt(zone, clock24 = true, now = now)
    private val id = UUID.fromString("11111111-1111-4111-8111-111111111111")
    private fun entry(note: String) = JournalEntry(id = id, at = now, sourceZoneId = "Asia/Seoul", note = note)

    private fun recordsActions(store: androidx.compose.runtime.MutableState<AppState>) = RecordsActions(
        back = {},
        tab = { store.value = store.value.copy(journalDraft = store.value.journalDraft.copy(tab = it)) },
        draft = { block -> store.value = JournalOps.draft(store.value, block) },
        toggleSymptom = {},
        save = { store.value = JournalOps.save(store.value, now, zone) },
        stockChange = { _, _ -> },
        stockRemove = {},
        stockAdd = {},
    )

    @Test fun longMemoEditKeepsEveryCharacterAndSavesWhole() {
        val original = "진료 메모 ".repeat(300).trim()
        assertTrue(original.length in 1500..8000)
        val store = mutableStateOf(AppState(journal = listOf(entry(original))))
        store.value = JournalOps.edit(store.value, id)
        val actions = recordsActions(store)
        compose.setContent { HormoneLogTheme { RecordsScreen(store.value, fmt, actions) } }
        // The counter sits under a long multi-line field; the screen scrolls to it.
        compose.onNodeWithText("${original.length} / 8000자").performScrollTo().assertIsDisplayed()

        val edited = original + " 수정 끝"
        compose.onNode(hasSetTextAction()).performTextReplacement(edited)
        compose.waitForIdle()
        compose.onNodeWithText("${edited.length} / 8000자").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("저장").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(edited, store.value.journal.single().note)
    }

    @Test fun overLimitMemoIsKeptInTheDraftAndCannotBeSaved() {
        val original = "짧은 메모"
        val store = mutableStateOf(AppState(journal = listOf(entry(original))))
        store.value = JournalOps.edit(store.value, id)
        val actions = recordsActions(store)
        compose.setContent { HormoneLogTheme { RecordsScreen(store.value, fmt, actions) } }
        val tooLong = "가".repeat(8001)
        compose.onNode(hasSetTextAction()).performTextReplacement(tooLong)
        compose.waitForIdle()
        compose.onNodeWithText("저장").assertIsNotEnabled()
        assertEquals(8001, store.value.journalDraft.note.length)
        assertEquals(original, store.value.journal.single().note)
    }

    @Test fun deletingAMemoFromItsRowMenuRemovesItFromTheRecords() {
        val store = mutableStateOf(AppState(journal = listOf(entry("머리가 아파요"))))
        compose.setContent {
            HormoneLogTheme {
                RowMenuSheet(store.value, RowTarget.Journal(id), fmt,
                    onEdit = {}, onDuplicate = {},
                    onDelete = { store.value = JournalOps.delete(store.value, id) }, onDismiss = {})
            }
        }
        compose.onNodeWithText("삭제").performClick()
        compose.waitForIdle()
        assertTrue(store.value.journal.isEmpty())
    }
}
