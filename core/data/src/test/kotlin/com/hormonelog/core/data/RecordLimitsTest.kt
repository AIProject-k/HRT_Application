package com.hormonelog.core.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A backup can come from anywhere. A record it carries that no real log could hold is set aside whole — kept in
 * the file, counted in the storage warning — and never loaded, because every screen calculates with what is loaded.
 */
class RecordLimitsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val ok = 1_788_000_000_000L // 2026-08

    private fun uuid(n: Int) = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

    private fun dose(id: Int, occurredAt: String = "$ok", amount: String = "5.0", normalized: String? = null) =
        """{"id":"${uuid(id)}","occurredAt":$occurredAt,"sourceZoneId":"Asia/Seoul","drug":"ESTRADIOL_VALERATE","route":"IM_INJECTION","amountEntered":$amount,"enteredUnit":"MG",${normalized?.let { "\"normalizedMilligrams\":$it," } ?: ""}"status":"ADMINISTERED"}"""

    private fun lab(id: Int, collectedAt: String = "$ok", reported: String = "150", canonical: String? = null) =
        """{"id":"${uuid(100 + id)}","collectedAt":$collectedAt,"sourceZoneId":"Asia/Seoul","assay":"UNKNOWN","analytes":[{"analyte":"ESTRADIOL","reportedValue":$reported,"reportedUnit":"pg/mL"${canonical?.let { ",\"canonicalValue\":$it" } ?: ""}}]}"""

    private fun regimen(id: Int, startAt: String = "$ok", endAt: String? = null, amount: String = "5.0") =
        """{"id":"${uuid(200 + id)}","drug":"ESTRADIOL_VALERATE","route":"IM_INJECTION","amountEntered":$amount,"enteredUnit":"MG","everyDays":7,"startAt":$startAt${endAt?.let { ",\"endAt\":$it" } ?: ""}}"""

    private fun read(doses: List<String> = emptyList(), labs: List<String> = emptyList(), regimens: List<String> = emptyList(), memos: List<String> = emptyList(), journal: List<String> = emptyList(), extra: String = ""): RecordSnapshot =
        RecordJson.fromJson(JSONObject("""{"version":3,"doses":[${doses.joinToString(",")}],"labs":[${labs.joinToString(",")}],"regimens":[${regimens.joinToString(",")}],"clinics":[],"memos":[${memos.joinToString(",")}],"journal":[${journal.joinToString(",")}],"stock":[]$extra}"""))

    @Test
    fun ordinaryRecordsStillLoad() {
        val s = read(doses = listOf(dose(1), dose(2, occurredAt = "631152000000" /* 1990 */), dose(3, amount = "0")), labs = listOf(lab(1)), regimens = listOf(regimen(1)))
        assertEquals(3, s.doses.size)
        assertEquals(1, s.labs.size)
        assertEquals(1, s.regimens.size)
        assertEquals(0, s.carriedCount)
    }

    @Test
    fun aDoseThatCouldNotHaveHappenedIsSetAsideAndTheRestLoads() {
        val s = read(
            doses = listOf(
                dose(1),
                dose(2, occurredAt = "9223372036854775807"), // the year 292278994
                dose(3, occurredAt = "-62135596800000"), // the year 1
                dose(4, occurredAt = "253402300799000"), // the year 9999
                dose(5, amount = "\"NaN\""),
                dose(6, amount = "\"Infinity\""),
                dose(7, amount = "-5"),
                dose(8, normalized = "\"NaN\""),
            ),
        )
        assertEquals(1, s.doses.size)
        assertEquals(7, s.carried["doses"]!!.size)
    }

    @Test
    fun aLabThatCouldNotHaveBeenDrawnIsSetAside() {
        val s = read(
            labs = listOf(
                lab(1),
                lab(2, collectedAt = "9223372036854775807"),
                lab(3, collectedAt = "-62135596800000"),
                lab(4, reported = "\"NaN\""),
                lab(5, reported = "-40"),
                lab(6, canonical = "\"Infinity\""),
            ),
        )
        assertEquals(1, s.labs.size)
        assertEquals(5, s.carried["labs"]!!.size)
    }

    @Test
    fun aPlanThatStartsInTheYearOneOrHoldsNaNIsSetAside() {
        val s = read(
            regimens = listOf(
                regimen(1),
                regimen(2, startAt = "-62135596800000"),
                regimen(3, endAt = "9223372036854775807"),
                regimen(4, amount = "\"NaN\""),
            ),
        )
        assertEquals(1, s.regimens.size)
        assertEquals(3, s.carried["regimens"]!!.size)
    }

    @Test
    fun aMemoOrAJournalEntryOutsideAnyCalendarIsSetAside() {
        val memo = { id: Int, date: String -> """{"id":"${uuid(300 + id)}","date":"$date","title":"t","body":"b"}""" }
        val s = read(
            memos = listOf(memo(1, "2026-09-18"), memo(2, "+999999999-12-31"), memo(3, "0001-01-01")),
            journal = listOf(
                """{"id":"${uuid(401)}","at":$ok,"sourceZoneId":"Asia/Seoul","weightKg":59.1}""",
                """{"id":"${uuid(402)}","at":9223372036854775807,"sourceZoneId":"Asia/Seoul"}""",
                """{"id":"${uuid(403)}","at":$ok,"sourceZoneId":"Asia/Seoul","weightKg":"NaN"}""",
            ),
        )
        assertEquals(1, s.memos.size)
        assertEquals(2, s.carried["memos"]!!.size)
        assertEquals(1, s.journal.size)
        assertEquals(2, s.carried["journal"]!!.size)
    }

    @Test
    fun aNextVisitNoCalendarCouldShowIsDroppedButARealOneStays() {
        assertNull(read(extra = ""","nextVisitMillis":9223372036854775807""").nextVisitMillis)
        assertNull(read(extra = ""","nextVisitMillis":-62135596800000""").nextVisitMillis)
        assertEquals(ok, read(extra = ""","nextVisitMillis":$ok""").nextVisitMillis)
    }

    @Test
    fun whatWasSetAsideIsWrittenBackUntouchedSoNothingIsLost() {
        val s = read(doses = listOf(dose(1), dose(2, occurredAt = "9223372036854775807")))
        val again = RecordJson.fromJson(RecordJson.toJson(s))
        assertEquals(1, again.doses.size)
        assertEquals(1, again.carriedCount)
    }

    @Test
    fun aBackgroundReadLeavesAnUnreadableFileWhereItIs() {
        val f = tmp.newFile("records.json").apply { writeText("not json at all") }
        val store = RecordStore(f)

        val quiet = store.load(quarantineOnFailure = false) as RecordStore.Load.Unreadable
        assertNull(quiet.quarantined)
        assertTrue("the file was not renamed away", f.exists())

        // The app's own start-up, which has a screen to say so on, does set it aside.
        val loud = store.load() as RecordStore.Load.Unreadable
        assertFalse(f.exists())
        assertTrue(loud.quarantined!!.exists())
    }

}
