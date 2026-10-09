package com.hormonelog.core.data

import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Local persistence for records as one plain JSON file. No encryption, no SQL.
 *
 * Reading never guesses: a file that cannot be read is never treated as "no records".
 * It is moved aside first, so the next save writes a fresh file instead of overwriting
 * the damaged one, and the bytes stay on disk to be recovered from. A single record
 * this build does not understand is set aside on its own and written back on the next
 * save — it does not take the rest down with it.
 */
class RecordStore(private val file: File) {

    /** Outcome of a [load]; an unreadable file is distinct from having no records. */
    sealed interface Load {
        data class Ok(val snapshot: RecordSnapshot) : Load

        /** No file yet — a genuine first run. */
        data object Empty : Load

        /**
         * The file existed but could not be read or parsed. It has been renamed to
         * [quarantined] so nothing overwrites it; null if even the rename failed, or if the
         * caller asked for the file to be left where it is (see [load]).
         */
        data class Unreadable(val quarantined: File?) : Load
    }

    /**
     * Reads the file. With [quarantineOnFailure] (the app's own start-up) an unreadable file is moved aside, and
     * the screen says so. A reminder or the widget, which have no screen to say anything on, pass false: it must
     * not rename the user's records away from where the app looks for them.
     */
    fun load(quarantineOnFailure: Boolean = true): Load {
        if (!file.exists()) return Load.Empty
        return try {
            Load.Ok(RecordJson.fromJson(JSONObject(String(file.readBytes(), Charsets.UTF_8))))
        } catch (_: Exception) {
            Load.Unreadable(if (quarantineOnFailure) quarantine() else null)
        }
    }

    /**
     * Writes the file, then reads it back before it replaces the old one: a write that
     * does not round-trip never destroys a good file. Throws [IOException] on failure.
     */
    fun save(snapshot: RecordSnapshot) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            // Everything that can fail is in here, so a failure is an IOException the caller reports.
            val bytes = RecordJson.toJson(snapshot).toString().toByteArray(Charsets.UTF_8)
            file.parentFile?.mkdirs()
            tmp.writeBytes(bytes)
            if (!tmp.readBytes().contentEquals(bytes)) throw IOException("저장한 파일을 다시 읽어 확인하지 못했어요")
            if (!tmp.renameTo(file)) {
                file.writeBytes(bytes)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    /** Move the unreadable file out of the way, keeping every byte of it. */
    private fun quarantine(): File? = try {
        val dest = File(file.parentFile, "${file.nameWithoutExtension}.corrupt-${System.currentTimeMillis()}.json")
        if (file.renameTo(dest)) dest else null
    } catch (_: Exception) {
        null
    }
}
