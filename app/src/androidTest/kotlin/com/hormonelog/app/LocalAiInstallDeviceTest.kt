package com.hormonelog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.ai.ModelDownloader
import com.hormonelog.app.ai.ModelManager
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Installs the pinned model with the app's own downloader. Dedicated emulator only: gated by -e localAiInstall yes. */
class LocalAiInstallDeviceTest {
    @Test fun installsThePinnedModelWhenAsked() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localAiInstall") == "yes")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = ModelManager(File(context.noBackupFilesDir, "offline-ai"))
        if (!manager.isInstalled()) ModelDownloader(manager).download {}
        assertTrue("Pinned model must be installed and verified", manager.isInstalled())
    }
}
