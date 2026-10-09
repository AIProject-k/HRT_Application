package com.hormonelog.app

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.platform.Storage
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.data.RecordStore
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The app on a real (virtual) device, from a clean start: first-run setup, the home screen and a
 * quick log with its undo. Each test writes the files the
 * app would have written, so no screen has to be clicked through just to set the scene.
 *
 * These tests delete the app's records and settings before and after each run, so they refuse to start
 * unless told, on the command line, that this device is meant for it:
 * `adb -s <the dedicated emulator> shell am instrument -w -e wipeAppData yes com.hormonelog.app.test/androidx.test.runner.AndroidJUnitRunner`.
 * Never run them on a phone, nor on an emulator that holds anything you want to keep.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Only a run that was told it may wipe the app's data is allowed to do it, afterwards as well. */
    private var allowedToWipe = false

    @Before
    fun clean() {
        check(InstrumentationRegistry.getArguments().getString("wipeAppData") == "yes") {
            "AppFlowTest deletes this app's records and settings on the device it runs on. " +
                "Run it only on a dedicated emulator, and say so with: am instrument -e wipeAppData yes ..."
        }
        allowedToWipe = true
        wipe()
    }

    @After
    fun finish() {
        scenario?.close()
        if (allowedToWipe) wipe()
    }

    private fun wipe() {
        listOf("records.json", "settings.json").forEach { File(context.filesDir, it).delete() }
        File(context.filesDir, "backups").deleteRecursively()
    }

    private fun seed(settings: AppSettings, snapshot: RecordSnapshot = RecordSnapshot()) {
        Storage.settings(context).save(settings)
        Storage.records(context).save(snapshot)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(text: String, timeoutMillis: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis)

    /** The app writes off the main thread, so a file is read back only once it says what it should. */
    private fun <T> eventually(what: String, timeoutMillis: Long = 5_000, read: () -> T, ok: (T) -> Boolean): T {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var value = read()
        while (!ok(value) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            value = read()
        }
        assertTrue("$what (last seen: $value)", ok(value))
        return value
    }

    private fun savedDoses(): Int = (Storage.records(context).load() as? RecordStore.Load.Ok)?.snapshot?.doses?.size ?: -1

    private fun dose(daysAgo: Long): DoseEvent = DoseEvent(
        id = UUID.randomUUID(), occurredAt = Instant.now().minusSeconds(daysAgo * 86_400L), sourceZoneId = ZoneId.systemDefault().id,
        drug = Drug.ESTRADIOL_VALERATE, route = Route.IM_INJECTION, amountEntered = 5.0, enteredUnit = DoseUnit.MG,
        normalizedMilligrams = 5.0, status = DoseStatus.ADMINISTERED, source = RecordSource.MANUAL,
    )

    private val usable = AppSettings(onboardingDone = true, gonadalStatus = GonadalStatus.INTACT, hideInRecents = false)

    // ── first run ────────────────────────────────────────────

    @Test
    fun aFirstRunWalksThroughSixStepsAndEndsOnAnEmptyHome() {
        launch()
        waitFor("예상 곡선을 그려 드려요")
        compose.onNodeWithText("1 / 6").assertIsDisplayed()
        compose.onNodeWithText("시작하기").performClick()

        waitFor("지금 쓰는 약을 알려 주세요")
        compose.onNodeWithText("2 / 6").assertIsDisplayed()
        // The schedule step cannot be finished without a dose, but it can be skipped, and skipping leaves nothing to back-fill.
        compose.onNodeWithText("건너뛰기").performClick()

        waitFor("고환이 있나요?")
        compose.onNodeWithText("4 / 6").assertIsDisplayed()
        compose.onNodeWithText("건너뛰기").performClick()
        waitFor("검사지에 적힌 단위를 골라 주세요")
        compose.onNodeWithText("건너뛰기").performClick()
        waitFor("HRT 시작 전 검사값이 있나요?")
        compose.onNodeWithText("6 / 6").assertIsDisplayed()
        // The last step has nothing after it to skip to; its button finishes the setup.
        compose.onNodeWithText("건너뛰기").assertDoesNotExist()
        compose.onNodeWithText("완료").performClick()

        waitFor("아직 기록이 없어요")
        compose.onNodeWithText("첫 투약 기록하기").assertIsDisplayed()
        eventually("first-run setup was never saved as done", read = { Storage.settings(context).load() }, ok = { it?.onboardingDone == true })
    }

    @Test
    fun theWelcomeStepHasNoBackButAnyLaterStepDoes() {
        launch()
        waitFor("예상 곡선을 그려 드려요")
        compose.onNodeWithContentDescription("뒤로").assertDoesNotExist()
        compose.onNodeWithText("시작하기").performClick()
        waitFor("지금 쓰는 약을 알려 주세요")
        compose.onNodeWithContentDescription("뒤로").performClick()
        waitFor("예상 곡선을 그려 드려요")
    }

    // ── home ─────────────────────────────────────────────────

    @Test
    fun homeShowsTheEstimateAndAQuickLogCanBeUndone() {
        seed(usable, RecordSnapshot(doses = listOf(dose(daysAgo = 3), dose(daysAgo = 10))))
        launch()
        waitFor("지금 예상 E2")
        compose.onNodeWithText("예상 · 실측 아님").assertIsDisplayed()
        // The disclaimer sits at the foot of the home screen; on a shorter display it is below the fold until scrolled to.
        compose.onNodeWithText("참고용 추정", substring = true).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("바로 기록 · 1탭").performClick()
        waitFor("를 기록했어요")
        compose.onNodeWithText("실행 취소").assertIsDisplayed()
        eventually("the quick log never reached the file", read = ::savedDoses, ok = { it == 3 })

        compose.onNodeWithText("실행 취소").performClick()
        waitFor("되돌렸어요")
        // The undo is written too: the two original doses are all that is left on disk.
        eventually("the undo never reached the file", read = ::savedDoses, ok = { it == 2 })
    }

    @Test
    fun aQuickLogSurvivesARestart() {
        seed(usable, RecordSnapshot(doses = listOf(dose(daysAgo = 3))))
        launch()
        waitFor("바로 기록 · 1탭")
        compose.onNodeWithText("바로 기록 · 1탭").performClick()
        waitFor("를 기록했어요")
        eventually("the new dose never reached the file", read = ::savedDoses, ok = { it == 2 })
        scenario?.close()
        launch()
        waitFor("지금 예상 E2")
        assertEquals(2, savedDoses())
    }
}
