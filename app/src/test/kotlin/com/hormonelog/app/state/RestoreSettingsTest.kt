package com.hormonelog.app.state

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSettingsTest {
    private val everythingOff = AppSettings(hideInRecents = false, disguiseLauncher = false, neutralNotifications = false, hideOnLockScreen = false)
    private val everythingOn = AppSettings(hideInRecents = true, disguiseLauncher = true, neutralNotifications = true, hideOnLockScreen = true)

    @Test
    fun aBackupThatSaysPrivacyIsOffCannotSwitchOffWhatIsOnNow() {
        val after = SettingsOps.afterRestore(current = everythingOn, restored = everythingOff)
        assertTrue(after.hideInRecents)
        assertTrue(after.disguiseLauncher)
        assertTrue(after.neutralNotifications)
        assertTrue(after.hideOnLockScreen)
    }

    @Test
    fun aBackupThatSaysPrivacyIsOnSwitchesItOnNeverOff() {
        val after = SettingsOps.afterRestore(current = everythingOff, restored = everythingOn)
        assertTrue(after.hideInRecents && after.disguiseLauncher && after.neutralNotifications && after.hideOnLockScreen)
        assertFalse(SettingsOps.afterRestore(everythingOff, everythingOff).disguiseLauncher)
    }

    @Test
    fun preferencesComeFromTheFile() {
        val restored = AppSettings(themeMode = ThemeMode.LIGHT, e2Unit = E2Unit.PMOL_L, clock24 = true, notifyDaily = true)
        val after = SettingsOps.afterRestore(AppSettings(), restored)
        assertEquals(ThemeMode.LIGHT, after.themeMode)
        assertEquals(E2Unit.PMOL_L, after.e2Unit)
        assertTrue(after.clock24)
        assertTrue(after.notifyDaily)
        assertTrue(after.onboardingDone)
    }

    @Test
    fun theBackupNudgeStaysAsItIsOnThisPhone() {
        val current = AppSettings(lastBackupAtMillis = 99L, backupBannerHiddenUntilMillis = 123L)
        val restored = AppSettings(lastBackupAtMillis = null, backupBannerHiddenUntilMillis = null)
        val after = SettingsOps.afterRestore(current, restored)
        assertEquals(99L, after.lastBackupAtMillis)
        assertEquals(123L, after.backupBannerHiddenUntilMillis)
    }
}
