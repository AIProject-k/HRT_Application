package com.hormonelog.app.chat

import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProveraChatTest {
    @Test fun brandOrGenericNameWithOralMgIsRecordedAsProvera() {
        for (text in listOf("방금 프로베라 10mg 먹었어", "방금 메드록시프로게스테론 5mg 먹었어", "방금 provera 2.5mg 복용")) {
            val h = ChatHarness()
            h.send(text)
            assertEquals("from: $text", 1, h.doses.size)
            val d = h.doses.single()
            assertEquals("from: $text", Drug.MEDROXYPROGESTERONE_ACETATE, d.drug)
            assertEquals("from: $text", Route.ORAL, d.route)
            assertEquals("from: $text", DoseUnit.MG, d.enteredUnit)
        }
    }

    @Test fun proveraGivenByInjectionIsRefused() {
        val h = ChatHarness()
        h.send("방금 프로베라 10mg 근육주사")
        assertTrue(h.doses.isEmpty())
    }
}
