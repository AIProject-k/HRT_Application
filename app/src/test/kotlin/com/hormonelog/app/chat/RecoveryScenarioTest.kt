package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S14 and S15 on the session. Persistence is judged on [ChatHarness.disk], the state a successful save reported,
 * because the in-memory state moves before the disk write, as in the app. Callbacks are driven by the test, never by sleeps.
 */
class RecoveryScenarioTest {
    @Test fun s14_doseSaveFailsTwiceThenSucceedsOnceAndUndoFailsOnceThenRemovesOnlyTheTarget() {
        val h = ChatHarness()
        h.failNextSaves = 2
        h.send("방금 프로기노바 2mg 복용")
        assertTrue(h.chat.ui.saveFailed)
        h.chat.retrySave()
        assertTrue(h.chat.ui.saveFailed)
        h.chat.retrySave()
        assertFalse(h.chat.ui.saveFailed)
        assertEquals(1, h.disk.doses.size)
        h.failNextSaves = 1
        h.chat.undo()
        assertTrue(h.chat.ui.saveFailed)
        assertEquals(1, h.disk.doses.size)
        h.chat.retrySave()
        assertTrue(h.disk.doses.isEmpty())
    }

    @Test fun s14_memoSaveFailsTwiceThenSucceedsOnceAndUndoRemovesOnlyTheMemo() {
        val h = ChatHarness()
        h.send("방금 프로기노바 2mg 복용")
        h.chat.editInput("합성 메모")
        h.failNextSaves = 2
        h.chat.saveMemo()
        h.chat.saveMemo()
        h.chat.saveMemo()
        assertEquals(1, h.disk.journal.size)
        assertEquals(1, h.disk.doses.size)
        assertTrue(h.chat.ui.canUndo)
        h.chat.undo()
        assertTrue(h.disk.journal.isEmpty())
        assertEquals(1, h.disk.doses.size)
    }

    @Test fun s15_lateChatCallbackDoesNotClearANewerDraftOrDuplicateTheMemo() {
        val h = ChatHarness()
        h.deferSaves = true
        h.send("방금 프로기노바 2mg 복용")
        assertTrue(h.chat.ui.busy)
        h.chat.editInput("합성 메모")
        h.chat.saveMemo()
        assertTrue(h.disk.journal.isEmpty())
        assertEquals("합성 메모", h.chat.ui.input)
        h.deferred.removeAt(0)()
        assertFalse(h.chat.ui.busy)
        assertEquals(1, h.disk.doses.size)
        h.chat.saveMemo()
        h.deferred.removeAt(0)()
        assertEquals(1, h.disk.journal.size)
        assertEquals(1, h.disk.doses.size)
        assertEquals("", h.chat.ui.input)
    }
}
