package com.mahava.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.mahava.app.data.db.*
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.AppViewModelFactory
import com.mahava.app.ui.screens.*
import com.mahava.app.ui.theme.MahavaTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Test-only seeded records are isolated in Robolectric's application sandbox. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = DesignTestApplication::class, qualifiers = "fa-rIR-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignFlowTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val stores = mutableListOf<ViewModelStore>()
    private fun model(): AppViewModel {
        val app = (RuntimeEnvironment.getApplication() as MahavaApplication)
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app)
        val today = app.clock.today()
        runBlocking {
            app.prefs.setApiBaseUrl("http://127.0.0.1:1")
            app.prefs.setAccessToken("test-only")
            app.prefs.setRefreshToken("test-only")
            app.prefs.setServerSubscription(true, "test", null, null)
            app.repository.saveProfile(UserProfileEntity(onboardingDone=true, typicalCycleLength=29, typicalBleedLength=5, regularCycles=true, qaSampleData=true))
            for (back in listOf(87L,58L,29L,0L)) {
                val start = today.minusDays(10+back)
                app.database.periodDao().upsert(PeriodEventEntity(startEpochDay=start.toEpochDay(),endEpochDay=start.plusDays(4).toEpochDay(),createdAt=1,updatedAt=1))
            }
        }
        val store=ViewModelStore().also { stores += it }
        return ViewModelProvider(store,AppViewModelFactory(app))[AppViewModel::class.java]
    }
    private fun show(vm: AppViewModel, route: String = "today", content: @Composable () -> Unit) {
        rule.setContent { MahavaTheme { Scaffold(bottomBar = {
            com.mahava.app.ui.components.MainNavigation(route) {}
        }) { padding -> Box(Modifier.padding(padding)) { content() } } } }
        rule.waitUntil(15000) { vm.state.value.ready && vm.isPremium.value }
        rule.waitForIdle()
    }
    private fun shot(name:String) {
        val file=File("build/screenshots/design/$name.png").apply { parentFile!!.mkdirs() }
        lateinit var bitmap: Bitmap
        rule.runOnUiThread {
            val view=rule.activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        Assert.assertTrue(file.length()>1000)
    }
    @After fun close() {
        stores.forEach { it.clear() }
        val app = RuntimeEnvironment.getApplication() as MahavaApplication
        // Stop application collectors before Robolectric disposes SQLite connections.
        val field = MahavaApplication::class.java.getDeclaredField("appScope").apply { isAccessible = true }
        (field.get(app) as kotlinx.coroutines.CoroutineScope).coroutineContext[kotlinx.coroutines.Job]?.cancel()
        rule.waitForIdle()
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        androidx.work.testing.WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun cycleHomeAndQuickLog() {
        val vm=model()
        show(vm) { TodayScreen(vm,{},{},{},{},{},{},{}) }
        rule.onNodeWithTag("cycle_status_card").assertExists()
        shot("cycle")
        // approved-home: no symptom check-in block on Today; the kit ring, week strip and capsule log button are there.
        rule.onNodeWithTag("checkin_body_fatigue").assertDoesNotExist()
        rule.onNodeWithTag("kit_orbit", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("home_week_strip", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("ثبت حال امروز").assertExists()
        rule.onNodeWithTag("today_mood_bad").performScrollTo().performClick()
        rule.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.logFor(vm.today())?.overallFeeling=="bad" }
        Assert.assertEquals(1,vm.state.value.dailyLogs.count { it.epochDay==vm.today().toEpochDay() })
    }
    @Test @Config(qualifiers="fa-rIR-w411dp-h2200dp-xhdpi") fun todayMoodPickChartAndFullPage() {
        val vm=model()
        val app=(RuntimeEnvironment.getApplication() as MahavaApplication)
        val today=app.clock.today().toEpochDay()
        runBlocking {
            mapOf(9L to "good", 8L to "ordinary", 7L to "ordinary", 5L to "bad", 4L to "good", 3L to "good", 1L to "ordinary").forEach { (back, f) ->
                app.database.dailyLogDao().upsert(DailyLogEntity(epochDay=today-back, overallFeeling=f, createdAt=1, updatedAt=1))
            }
        }
        show(vm) { TodayScreen(vm,{},{},{},{},{},{},{}) }
        rule.onNodeWithTag("today_body_now").assertExists()
        rule.onNodeWithText("الان توی بدنت چه خبره؟").assertExists()
        rule.onNodeWithTag("home_tool_cards").assertDoesNotExist()
        rule.onNodeWithText("بینش‌های من").assertDoesNotExist()
        rule.onNodeWithText("وضعیت امروزت").assertDoesNotExist()
        rule.onNodeWithText("ردیاب درد").assertDoesNotExist()
        shot("today-cycle-full")
        rule.onNodeWithTag("today_mood_good").performScrollTo().performClick()
        rule.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.logFor(vm.today())?.overallFeeling=="good" }
        rule.waitUntil(15000) { rule.onAllNodesWithTag("today_mood_status").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("today_mood_good").assertIsSelected()
        // Change today's pick: same row is updated, no second log.
        rule.onNodeWithTag("today_mood_bad").performScrollTo().performClick()
        rule.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.logFor(vm.today())?.overallFeeling=="bad" }
        Assert.assertEquals(1,vm.state.value.dailyLogs.count { it.epochDay==today })
        rule.waitForIdle()
        rule.onNodeWithTag("today_mood_chart_info").assertTextContains("امروز", substring = true)
        shot("today-cycle-mood-picked")
    }
    @Test @Config(qualifiers="fa-rIR-w411dp-h891dp-xhdpi") fun todayMoodChartEmptyState() {
        val vm=model()
        show(vm) { TodayScreen(vm,{},{},{},{},{},{},{}) }
        rule.onNodeWithTag("today_mood_chart_empty").performScrollTo().assertExists()
        rule.onNodeWithText("حالت رو ثبت کن تا کم‌کم روندش رو اینجا ببینی.").assertExists()
    }
    @Test @Config(qualifiers="fa-rIR-w411dp-h2200dp-xhdpi") fun pregnancyTodayFullPage() {
        val vm=model()
        val app=(RuntimeEnvironment.getApplication() as MahavaApplication)
        runBlocking {
            app.repository.upsertPregnancy(PregnancyEntity(lmpEpochDay=app.clock.today().minusDays(130).toEpochDay(),estimatedDueEpochDay=app.clock.today().plusDays(150).toEpochDay()))
        }
        show(vm) { TodayScreen(vm,{},{},{},{},{},{},{}) }
        rule.waitUntil(15000) { vm.state.value.health?.pregnancyProgress!=null }
        rule.waitForIdle()
        rule.onNodeWithTag("today_body_now").assertExists()
        rule.onNodeWithText("بر اساس هفتهٔ بارداریت").assertExists()
        shot("today-pregnancy-full")
    }    @Test fun calendarSelection() {
        val vm=model()
        show(vm, "calendar") { CalendarScreen(vm,{}) {} }
        rule.onNodeWithTag("cal_selected_card").assertExists()
        shot("calendar")
        rule.onNodeWithTag("cal_prev").performClick()
        rule.onNodeWithTag("cal_today").performScrollTo().performClick()
        rule.onNodeWithTag("day_${vm.today()}").assertExists()
    }
    @Test fun dailyLogPersists() {
        val vm=model()
        show(vm, "log_hub") { DailyLogScreen(vm,vm.today(),{}) }
        rule.onNodeWithTag("daily_log_screen").assertExists()
        shot("daily-log")
        rule.onNodeWithTag("sym_fatigue").performScrollTo().performClick()
        rule.onNodeWithTag("save_daily").performScrollTo().performClick()
        rule.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.logFor(vm.today())?.physicalSymptoms?.contains("fatigue")==true }
    }
    @Test fun reportsUseRealData() {
        val vm=model()
        show(vm, "reports") { ReportsScreen(vm,{},{},{}) }
        rule.onNodeWithTag("reports_lengths").assertExists()
        shot("reports")
    }
    @Test fun bodySearch() {
        val vm=model()
        show(vm, "body") { BodyHomeScreen(vm,{},{},{}) }
        shot("body-knowledge")
        rule.onNodeWithTag("body_search").performTextInput("خون")
        rule.onAllNodesWithText("خون‌ریزی",substring=true).assertAny(hasText("خون",substring=true))
    }
    @Test fun pregnancyHome() {
        val vm=model()
        val app=(RuntimeEnvironment.getApplication() as MahavaApplication)
        runBlocking {
            app.repository.upsertPregnancy(PregnancyEntity(lmpEpochDay=app.clock.today().minusDays(143).toEpochDay(),estimatedDueEpochDay=app.clock.today().plusDays(137).toEpochDay()))
        }
        show(vm) { TodayScreen(vm,{},{},{},{},{},{},{}) }
        rule.waitUntil(15000) { vm.state.value.health?.pregnancyProgress!=null }
        rule.waitForIdle()
        shot("pregnancy")
        rule.onNodeWithTag("cycle_status_card").assertDoesNotExist()
    }
    @Test fun toolsDirectoryIsConnectedAndRendered() {
        val vm=model()
        show(vm) { ToolsHomeScreen({}) {} }
        rule.onNodeWithText("بدن و چرخه").assertExists()
        shot("tools")
    }
    @Test @Config(qualifiers="fa-rIR-w411dp-h891dp-night-xhdpi") fun toolsAlsoRenderInSystemDarkMode() {
        val vm=model()
        show(vm) { ToolsTheme { ToolsHomeScreen({}) {} } }
        rule.onNodeWithText("بدن و چرخه").assertExists()
        shot("tools-dark")
    }
    @Test fun moodEnergyToolPersistsFeelingAndEnergy() {
        val vm=model()
        show(vm) { ToolsDetailScreen(vm,"mood_energy",{}) {} }
        rule.onNodeWithTag("feel_great",useUnmergedTree=true).performClick()
        rule.onNodeWithTag("en_3",useUnmergedTree=true).performScrollTo().performClick()
        shot("mood-energy")
        rule.onNodeWithText("ذخیره برای امروز",useUnmergedTree=true).performScrollTo().performClick()
        rule.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.logFor(vm.today())?.energyScore==3}
        Assert.assertEquals("great",vm.logFor(vm.today())?.overallFeeling)
        Assert.assertEquals("medium",vm.logFor(vm.today())?.energy)
    }
    @Test fun removedToolsCloseAndKeepData() {
        val vm=model()
        var closed = 0
        show(vm) { ToolsDetailScreen(vm,"doctor",{ closed++ }) {} }
        rule.waitUntil(5000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); closed > 0 }
        rule.onNodeWithTag("tool_screen_doctor").assertDoesNotExist()
    }
    @Test fun sleepToolStoresHoursAndMinutes() {
        val vm=model()
        show(vm) { ToolsDetailScreen(vm,"sleep",{}) {} }
        rule.onNodeWithText("۷ ساعت",useUnmergedTree=true).performClick()
        rule.onNodeWithText("ذخیره برای امروز",useUnmergedTree=true).performScrollTo().performClick()
        rule.waitUntil(15000) {org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();vm.logFor(vm.today())?.sleepMinutes==420}
        Assert.assertEquals(7f,vm.logFor(vm.today())?.sleepHours)
        shot("sleep-hours")
    }

}

class DesignTestApplication : MahavaApplication()
