package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A save callback reports the write that actually finished. A late failure for one request must not mark
 * a later request as saved, and what is on disk must match only the writes that reported success.
 */
class SaveOrderGateTest {
    @Test fun lateFailureForOneRequestDoesNotMarkTheNextAsSaved() {
        val h = ChatHarness()
        h.failNextSaves = 1
        h.deferSaves = true
        h.send("방금 프로기노바 2mg 복용")
        assertTrue("disk must not change before the callback", h.disk.doses.isEmpty())
        h.deferSaves = false
        h.deferred.forEach { it() }
        h.deferred.clear()
        assertFalse("failed save reported as saved", h.lastReply.contains("저장했어요"))
        assertTrue(h.disk.doses.isEmpty())
    }

    /** After a failed save, new input waits; one retry writes the pending record once, then the next input saves. */
    @Test fun inputWaitsForRetryAndTheRetryWritesOnce() {
        val h = ChatHarness()
        h.failNextSaves = 1
        h.send("방금 프로기노바 2mg 복용")
        assertTrue(h.chat.ui.saveFailed)
        h.send("오늘 10:00 프로기노바 4mg 복용")
        assertEquals(0, h.disk.doses.size)
        h.chat.retrySave()
        assertEquals(1, h.disk.doses.size)
        h.send("오늘 10:00 프로기노바 4mg 복용")
        assertEquals(2, h.disk.doses.size)
    }
}
