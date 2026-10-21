package com.hormonelog.app.chat

import com.hormonelog.app.ai.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelDownloaderTest {
    @get:Rule val folder=TemporaryFolder()
    private val bytes="model download fixture".toByteArray()
    private fun manager()=ModelManager(folder.root,ModelSpec("model.litertlm","https://example.invalid/model",bytes.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }))
    private class Response(private val code: Int, private val body: ByteArray, private val range: String?=null): HttpURLConnection(URL("https://example.invalid")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy()=false
        override fun getResponseCode()=code
        override fun getInputStream()=ByteArrayInputStream(body)
        override fun getHeaderField(name: String)=if(name=="Content-Range") range else null
    }
    @Test fun partialDownloadResumesOnlyFromValidatedRange() {
        val manager=manager()
        manager.partial.writeBytes(bytes.take(4).toByteArray())
        val response=Response(206,bytes.drop(4).toByteArray(),"bytes 4-${bytes.lastIndex}/${bytes.size}")
        ModelDownloader(manager) { response }.download()
        assertEquals("bytes=4-",response.getRequestProperty("Range"))
        assertTrue(manager.isInstalled())
    }
    @Test fun ignoredRangeRestartsInsteadOfAppending() {
        val manager=manager(); manager.partial.writeText("bad partial")
        ModelDownloader(manager) { Response(200,bytes) }.download()
        assertArrayEquals(bytes,manager.model.readBytes())
    }
    @Test fun invalidRangesOversizeAndHashMismatchNeverInstall() {
        val manager=manager(); manager.partial.writeBytes(bytes.take(4).toByteArray())
        assertThrows(IllegalStateException::class.java) {
            ModelDownloader(manager) { Response(206,bytes,"bytes 0-${bytes.lastIndex}/${bytes.size}") }.download()
        }
        assertFalse(manager.model.exists())
        assertThrows(IllegalStateException::class.java) {
            ModelDownloader(manager) { Response(200,bytes+byteArrayOf(0)) }.download()
        }
        assertFalse(manager.model.exists())
        assertThrows(IllegalArgumentException::class.java) {
            ModelDownloader(manager) { Response(200,ByteArray(bytes.size)) }.download()
        }
        assertFalse(manager.model.exists()); assertFalse(manager.partial.exists())
    }
    @Test fun cancellationKeepsPartialForVerifiedRetry() {
        val manager=manager()
        val downloader=ModelDownloader(manager) { Response(200,bytes) }
        assertThrows(java.util.concurrent.CancellationException::class.java) { downloader.download { downloader.cancel() } }
        assertFalse(manager.model.exists())
        assertArrayEquals(bytes,manager.partial.readBytes())
        downloader.reset(); downloader.download()
        assertTrue(manager.isInstalled())
    }
    @Test fun prematureEndOfResponseKeepsReceivedBytesForResume() {
        val manager=manager()
        assertThrows(IllegalStateException::class.java) {
            ModelDownloader(manager) { Response(200,bytes.take(4).toByteArray()) }.download()
        }
        assertArrayEquals(bytes.take(4).toByteArray(),manager.partial.readBytes())
        assertFalse(manager.model.exists())
    }
}
