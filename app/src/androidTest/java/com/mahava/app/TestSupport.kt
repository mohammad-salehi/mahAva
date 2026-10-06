package com.mahava.app

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.data.db.PeriodEventEntity
import com.mahava.app.data.db.UserProfileEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

val today: LocalDate get() = MahavaTestApplication.TEST_TODAY
fun app(): MahavaTestApplication = ApplicationProvider.getApplicationContext()

object Seed {
    /** Wipes the in-memory DB and test hooks. Called before every test. */
    fun reset() = runBlocking {
        val a = app()
        a.repository.deleteAllData()
        a.documentPickerOverride = null
        a.fakeClock.setToday(today)
        a.getSharedPreferences("mahava_reminders", 0).edit().clear().commit()
        File(a.cacheDir, "saf").deleteRecursively()
    }

    fun profile(p: UserProfileEntity = UserProfileEntity(onboardingDone = true, typicalCycleLength = 28, typicalBleedLength = 5, regularCycles = true)) =
        runBlocking { app().database.profileDao().upsert(p.copy(onboardingDone = true)) }

    fun period(start: LocalDate, end: LocalDate?, ongoing: Boolean = false) = runBlocking {
        app().database.periodDao().upsert(
            PeriodEventEntity(startEpochDay = start.toEpochDay(), endEpochDay = end?.toEpochDay(), stillOngoing = ongoing, createdAt = 1, updatedAt = 1)
        )
    }

    fun log(l: DailyLogEntity) = runBlocking { app().database.dailyLogDao().upsert(l) }

    /** Three completed cycles of 28 days + current cycle, like a real user after 3 months. */
    fun regularHistory(cycleDayToday: Int = 20) {
        profile()
        val curStart = today.minusDays((cycleDayToday - 1).toLong())
        listOf(84L, 56L, 28L, 0L).forEach { back ->
            val s = curStart.minusDays(back)
            if (!s.isAfter(today)) period(s, minOf(s.plusDays(4), today))
        }
    }

    fun periods() = runBlocking { app().database.periodDao().getAll() }
    fun logs() = runBlocking { app().database.dailyLogDao().getAll() }
    fun logFor(d: LocalDate) = runBlocking { app().database.dailyLogDao().getByDay(d.toEpochDay()) }
    fun profileNow() = runBlocking { app().database.profileDao().get() }

    /** File-backed replacement for the system file picker. */
    fun installFilePicker(): File {
        val dir = File(app().cacheDir, "saf").apply { mkdirs() }
        app().documentPickerOverride = { _, name -> Uri.fromFile(File(dir, name)) }
        return dir
    }
}

object Shots {
    private const val TAG = "MahavaShots"
    val dir: File by lazy {
        (app().getExternalFilesDir("screens") ?: File(app().filesDir, "screens")).apply { mkdirs() }
    }

    fun take(rule: ComposeTestRule, name: String) {
        rule.waitForIdle()
        try {
            val roots = rule.onAllNodes(isRoot()).fetchSemanticsNodes()
            // When a dialog is open, the dialog is the last root; otherwise take the activity window.
            val idx = if (roots.size > 1) roots.size - 1 else 0
            val bmp = rule.onAllNodes(isRoot())[idx].captureToImage().asAndroidBitmap()
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "saved ${File(dir, "$name.png").absolutePath}")
        } catch (t: Throwable) {
            Log.w(TAG, "screenshot $name failed: $t")
        }
    }
}

fun launchMain(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java)

fun ComposeTestRule.waitTag(tag: String, timeoutMs: Long = 8000) =
    waitUntil(timeoutMs) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitText(text: String, substring: Boolean = true, timeoutMs: Long = 8000) =
    waitUntil(timeoutMs) { onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

fun ComposeTestRule.waitFor(timeoutMs: Long = 8000, cond: () -> Boolean) = waitUntil(timeoutMs) { cond() }

/** Scrolls to the node (if inside a scrollable column) and clicks it. */
fun ComposeTestRule.tap(tag: String) {
    waitTag(tag)
    val n = onNodeWithTag(tag, useUnmergedTree = true)
    try { n.performScrollTo() } catch (_: Throwable) {}
    n.performClick()
    waitForIdle()
}

fun ComposeTestRule.tapText(text: String, substring: Boolean = false) {
    waitText(text, substring)
    val n = onAllNodesWithText(text, substring = substring)[0]
    try { n.performScrollTo() } catch (_: Throwable) {}
    n.performClick()
    waitForIdle()
}

fun ComposeTestRule.exists(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
fun ComposeTestRule.hasText(text: String) = onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
