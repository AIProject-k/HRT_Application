package com.hormonelog.app.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val signer = "ff4876b1492f2eebba6f0ef460e6f065fc33c328fb38f5d2c1e8b4fab17f02cf"
    private val apkUrl = "https://github.com/AIProject-k/HRT_Application/releases/download/v0.3.4/hormonelog-v0.3.4-debug.apk"
    private val release = """{"tag_name":"v0.3.4","assets":[
        {"name":"hormonelog-v0.3.4-debug.apk","browser_download_url":"$apkUrl"},
        {"name":"store-manifest.json","browser_download_url":"https://github.com/AIProject-k/HRT_Application/releases/download/v0.3.4/store-manifest.json"}]}"""
    private fun manifest(code: Long = 8, pkg: String = "com.hormonelog.app", key: String = signer, asset: String = "hormonelog-v0.3.4-debug.apk") =
        """{"packageName":"$pkg","versionCode":$code,"versionName":"0.3.4","signerSha256":"$key","sha256":"e32e","size":86008783,"asset":"$asset"}"""
    private val assets = releaseAssets(release)
    private fun read(json: String, installed: Long = 7) = readUpdate(json, assets, "com.hormonelog.app", installed, signer)

    @Test fun aNewerReleaseOffersItsApk() {
        assertEquals(UpdateUi.Available("0.3.4", "hormonelog-v0.3.4-debug.apk", apkUrl, 86008783, "e32e"), read(manifest()))
    }

    @Test fun theSameOrAnOlderReleaseIsLatest() {
        assertEquals(UpdateUi.Latest, read(manifest(), installed = 8))
        assertEquals(UpdateUi.Latest, read(manifest(), installed = 9))
    }

    @Test fun aReleaseThatCannotReplaceThisAppIsNotOffered() {
        assertTrue(read(manifest(key = "00".repeat(32))) is UpdateUi.Failed)
        assertTrue(read(manifest(pkg = "com.other.app")) is UpdateUi.Failed)
        assertTrue(read(manifest(asset = "missing.apk")) is UpdateUi.Failed)
    }
}
