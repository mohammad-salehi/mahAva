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
        rule.onNodeWithTag("quick_fatigue").performClick()
        rule.waitUntil(15000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); vm.logFor(vm.today())?.physicalSymptoms?.contains("fatigue")==true }
        Assert.assertEquals(1,vm.state.value.dailyLogs.count { it.epochDay==vm.today().toEpochDay() })
    }
    @Test fun calendarSelection() {
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
}

class DesignTestApplication : MahavaApplication()
