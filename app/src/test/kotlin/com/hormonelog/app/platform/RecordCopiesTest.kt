package com.hormonelog.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecordCopiesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun file(dir: File, name: String, ageMillis: Long = 0L): File =
        File(dir, name).apply { parentFile!!.mkdirs(); writeText("records in the clear"); setLastModified(now - ageMillis) }

    private class Layout(val files: File, val cache: File)

    private fun layout(): Layout {
        val files = tmp.newFolder("files")
        val cache = tmp.newFolder("cache")
        file(files, "records.json")
        file(files, "settings.json")
        file(files, "records.corrupt-1.json")
        file(files, "settings.corrupt-2.json")
        file(File(files, "backups"), "before-restore-1.hlb")
        file(File(cache, "reports"), "report-old.pdf", ageMillis = 5 * hour)
        file(File(cache, "reports"), "report-new.png", ageMillis = 60_000L)
        return Layout(files, cache)
    }

    private fun names(dir: File): Set<String> = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path.replace('\\', '/') }.toSet()

    @Test
    fun anOldReportGoesAndANewOneStaysForTheShareSheetToReadIt() {
        val l = layout()
        RecordCopies.purgeReports(l.cache, olderThanMillis = hour, nowMillis = now)
        assertEquals(setOf("reports/report-new.png"), names(l.cache))
    }

    @Test
    fun zeroAgeTakesEveryReport() {
        val l = layout()
        RecordCopies.purgeReports(l.cache, olderThanMillis = 0L, nowMillis = now)
        assertTrue(names(l.cache).isEmpty())
    }

    @Test
    fun deletingTheRecordsTakesEveryCopyButLeavesTheTwoFilesTheAppOwns() {
        val l = layout()
        RecordCopies.purgeAll(l.files, l.cache)
        assertEquals(setOf("records.json", "settings.json"), names(l.files))
        assertFalse(File(l.files, "backups").exists())
        assertTrue(names(l.cache).isEmpty())
    }

    @Test
    fun missingFoldersAreNotAnError() {
        RecordCopies.purgeAll(File(tmp.root, "nothing-here"), File(tmp.root, "nor-here"))
        RecordCopies.purgeReports(File(tmp.root, "nor-here"), 0L, now)
    }
}
