package com.hormonelog.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hormonelog.app.feature.dashboard.DashboardReducer
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.dashboard.LabDraft
import com.hormonelog.app.feature.dashboard.LabTimeChoice
import com.hormonelog.app.feature.lab.LabSheet
import com.hormonelog.app.ui.theme.HormoneLogTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LabTimeSelectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-02T15:15:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private var draft by mutableStateOf(LabDraft(e2 = "120"))

    private fun setContent() {
        composeRule.setContent {
            HormoneLogTheme {
                LabSheet(
                    draft = draft, now = now, zone = zone, lastDose = null,
                    onEdit = { draft = it(draft) },
                    onFocus = { draft = draft.copy(focus = it) },
                    onKey = {}, onClose = {}, onSave = {},
                )
            }
        }
    }

    @Test
    fun directSelectionOpensDateThenTimeAndSavesTheLocalInstant() {
        setContent()
        composeRule.onNodeWithText("날짜·시간 직접 선택").performScrollTo().performClick()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.onNodeWithText("확인").performClick()
        composeRule.runOnIdle {
            assertEquals(LabTimeChoice.CUSTOM, draft.time)
            assertEquals(now.toEpochMilli(), draft.customEpochMillis)
            val saved = DashboardReducer.saveLab(DashboardState(labDraft = draft), now)
            assertEquals(now, saved.labs.single().collectedAt)
        }
        composeRule.onNodeWithText("2026년 10월 3일 오전 12:15").assertExists()
    }

    @Test
    fun cancellingTimeSelectionKeepsTheOriginalDraft() {
        setContent()
        composeRule.onNodeWithText("날짜·시간 직접 선택").performScrollTo().performClick()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.onNodeWithText("취소").performClick()
        composeRule.runOnIdle {
            assertEquals(LabTimeChoice.NOW, draft.time)
            assertNull(draft.customEpochMillis)
        }
    }

    @Test
    fun editingAnOlderCollectionTimeKeepsItsDateAndTime() {
        val original = Instant.parse("2025-08-01T14:45:00Z")
        draft = draft.copy(time = LabTimeChoice.CUSTOM, customEpochMillis = original.toEpochMilli())
        setContent()
        composeRule.onNodeWithText("날짜·시간 변경").performScrollTo().performClick()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.onNodeWithText("확인").performClick()
        composeRule.runOnIdle { assertEquals(original.toEpochMilli(), draft.customEpochMillis) }
        composeRule.onNodeWithText("2025년 8월 1일 오후 11:45").assertExists()
    }
}
