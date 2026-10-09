package com.hormonelog.app.platform

import android.content.Context
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.data.RecordStore
import com.hormonelog.core.data.SettingsStore
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import java.io.File

/**
 * The one place that knows where the records and the settings live. The app's ViewModel, a
 * reminder's "투약 완료" button and the home-screen widget all reach the same two files through
 * here, and every read and write goes through [lock] so two of them never interleave.
 */
object Storage {
    private val lock = Any()

    fun records(context: Context): RecordStore =
        RecordStore(File(context.applicationContext.filesDir, "records.json"))

    fun settings(context: Context): SettingsStore =
        SettingsStore(File(context.applicationContext.filesDir, "settings.json"))

    fun <T> locked(block: () -> T): T = synchronized(lock, block)

    /** Takes down every copy of the records outside records.json (see [RecordCopies]), the unreadable originals included. */
    fun purgeCopies(context: Context) = locked {
        val app = context.applicationContext
        RecordCopies.purgeAll(app.filesDir, app.cacheDir)
    }

    /** Reports older than [ReportFiles.KEEP_MILLIS]: they hold the records and are not needed once shared. */
    fun purgeOldReports(context: Context) {
        val app = context.applicationContext
        RecordCopies.purgeReports(app.cacheDir, ReportFiles.KEEP_MILLIS, System.currentTimeMillis())
    }

    /** Records and settings as they are on disk now, or null when nothing readable is there. */
    fun read(context: Context): Pair<RecordSnapshot, AppSettings>? = locked {
        // No screen to warn on here, and a failure may be passing: leave an unreadable file exactly where it is.
        val loaded = records(context).load(quarantineOnFailure = false) as? RecordStore.Load.Ok ?: return@locked null
        loaded.snapshot to (settings(context).load() ?: AppSettings())
    }

    /**
     * Adds one real dose to the records on disk, for callers that run while the app's screen is
     * not open (a notification button, the widget). Returns the new snapshot, or null when the
     * records could not be read — in which case nothing is written, so nothing is overwritten.
     */
    fun appendDose(context: Context, dose: DoseEvent): RecordSnapshot? = locked {
        val store = records(context)
        val loaded = store.load(quarantineOnFailure = false) as? RecordStore.Load.Ok ?: return@locked null
        val snapshot = loaded.snapshot
        val next = snapshot.copy(
            doses = (snapshot.doses + dose).sortedBy { it.occurredAt },
            stock = snapshot.stock.map { if (it.matches(dose) && it.count > 0) it.copy(count = it.count - 1) else it },
        )
        runCatching { store.save(next) }.fold({ next }, { null })
    }
}

/** The ViewModel that is alive right now, if any, so a reminder button can hand its work to it. */
object ViewModelHolder {
    @Volatile
    var current: com.hormonelog.app.AppViewModel? = null
}
