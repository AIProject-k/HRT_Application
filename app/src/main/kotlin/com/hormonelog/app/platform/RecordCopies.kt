package com.hormonelog.app.platform

import java.io.File

/**
 * The copies of the records that live outside records.json: the reports made to be shared, the safety
 * copies kept before a restore, and the files set aside because they could not be read. Deleting the
 * records has to take these along; plain file work, so it is tested without a phone.
 */
object RecordCopies {
    const val REPORTS_DIR = "reports"
    const val SAFETY_DIR = "backups"

    /** What the stores put in a file's name when they move an unreadable file aside (records.corrupt-…, settings.corrupt-…). */
    private const val SET_ASIDE_MARK = ".corrupt-"

    /** Deletes the reports older than [olderThanMillis] (0 = all of them) from the cache folder. */
    fun purgeReports(cacheDir: File, olderThanMillis: Long, nowMillis: Long) {
        File(cacheDir, REPORTS_DIR).listFiles()?.forEach { if (olderThanMillis <= 0L || nowMillis - it.lastModified() > olderThanMillis) it.delete() }
    }

    /** Every report, every safety copy, and the files set aside as unreadable. */
    fun purgeAll(filesDir: File, cacheDir: File) {
        purgeReports(cacheDir, olderThanMillis = 0L, nowMillis = 0L)
        File(filesDir, SAFETY_DIR).deleteRecursively()
        filesDir.listFiles { f -> f.isFile && f.name.contains(SET_ASIDE_MARK) }?.forEach { it.delete() }
    }
}
