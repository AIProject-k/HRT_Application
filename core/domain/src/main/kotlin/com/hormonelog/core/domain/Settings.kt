package com.hormonelog.core.domain

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Extra scale applied on top of the system font size. */
enum class FontScale(val multiplier: Float) {
    DEFAULT(1f),
    LARGE(1.3f),
    LARGEST(2f),
}

enum class E2Unit(val label: String) {
    PG_ML("pg/mL"),
    PMOL_L("pmol/L"),
}

enum class TUnit(val label: String) {
    NG_DL("ng/dL"),
    NG_ML("ng/mL"),
    NMOL_L("nmol/L"),
}

/**
 * Everything the user chooses about the app itself, kept apart from the records so a
 * records-file problem never takes the settings with it (and the other way round).
 */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.DEFAULT,
    /** Default unit offered when entering a lab value; each lab may still use another. */
    val e2Unit: E2Unit = E2Unit.PG_ML,
    val tUnit: TUnit = TUnit.NG_DL,
    val clock24: Boolean = false,
    val gonadalStatus: GonadalStatus = GonadalStatus.UNKNOWN,
    /** Hide the app's content in the recent-apps switcher (and block screenshots). */
    val hideInRecents: Boolean = true,
    /** Show the launcher under a neutral name and icon. */
    val disguiseLauncher: Boolean = false,
    /** Notifications never name a drug, a value, or the app. */
    val neutralNotifications: Boolean = false,
    /** Reminders; each is off until the user turns it on (which is also when the system asks for permission). */
    val notifyInjection: Boolean = false,
    val notifyDaily: Boolean = false,
    val notifyLab: Boolean = false,
    val notifyAppointment: Boolean = false,
    /** On the lock screen a notification shows only a neutral line, whatever its full text is. */
    val hideOnLockScreen: Boolean = true,
    val lastBackupAtMillis: Long? = null,
    /** The backup nudge stays hidden until then. */
    val backupBannerHiddenUntilMillis: Long? = null,
) {
    val notificationsOn: Int get() = listOf(notifyInjection, notifyDaily, notifyLab, notifyAppointment).count { it }
}
