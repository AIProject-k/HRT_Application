package com.hormonelog.app.chat

import com.hormonelog.app.ai.ModelManager
import com.hormonelog.app.ai.ModelSpec
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class LocalModelTest {
    @get:Rule val folder=TemporaryFolder()
    private val bytes="small model fixture".toByteArray()
    private fun manager() = ModelManager(folder.root, ModelSpec("model.litertlm", "https://example.invalid/model",
        bytes.size.toLong(), MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
    @Test fun onlyVerifiedCompleteFilesAreInstalledAndDeletionKeepsUnrelatedFiles() {
        val manager=manager()
        manager.partial.writeBytes(bytes.dropLast(1).toByteArray())
        assertFalse(manager.isInstalled())
        assertThrows(IllegalArgumentException::class.java) { manager.finishInstall() }
        assertFalse(manager.model.exists())
        manager.partial.writeBytes(bytes)
        assertThrows(java.util.concurrent.CancellationException::class.java) { manager.finishInstall { true } }
        assertFalse(manager.model.exists())
        manager.finishInstall()
        assertTrue(manager.isInstalled())
        manager.model.writeBytes(ByteArray(bytes.size))
        assertFalse(manager.isInstalled())
        val unrelated=File(folder.root,"records.json").apply { writeText("keep") }
        manager.delete()
        assertEquals("keep",unrelated.readText())
    }
}
