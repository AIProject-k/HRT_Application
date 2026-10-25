package com.hormonelog.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** A bare array of dose objects is the batch the protocol already defines; anything else in an array stays rejected. */
class LlmResponseArrayTest {
    private val twoDoses = """[{"action":"dose","drug":"프로기노바","amount":"2mg","date":"오늘","time":"10:00"},{"action":"dose","drug":"안쿨","amount":"25mg","date":"오늘","time":"20:00"}]"""

    @Test fun arrayOfDosesParsesLikeTheBatchObject() {
        val fromArray = LlmResponseParser.parse(twoDoses)
        val fromObject = LlmResponseParser.parse("""{"action":"batch","doses":${twoDoses}}""")
        assertEquals(fromObject, fromArray)
        assertEquals(2, fromArray.doses.size)
    }

    @Test fun arrayWithANonDoseItemIsRejected() {
        val lab = """[{"action":"dose","drug":"프로기노바","amount":"2mg","time":"10:00"},{"action":"lab","analyte":"E2","value":"100","unit":"pg/mL"}]"""
        assertThrows(IllegalArgumentException::class.java) { LlmResponseParser.parse(lab) }
    }

    @Test fun scalarAnswerIsStillRejected() {
        assertThrows(IllegalArgumentException::class.java) { LlmResponseParser.parse("\"dose\"") }
    }
}
