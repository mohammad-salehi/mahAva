package com.mahava.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Capture the real launch activity; isolate WorkManager before its ViewModel starts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSmokeTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val work = object : ExternalResource() {
        override fun before() {
            WorkManagerTestInitHelper.initializeTestWorkManager(RuntimeEnvironment.getApplication())
        }
        override fun after() {
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(work).around(compose)

    @Test fun captureLaunch() {
        compose.waitForIdle()
        val file = File("build/screenshots/launch.png").apply { parentFile!!.mkdirs() }
        lateinit var bitmap: Bitmap
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(file.length() > 1000)
    }
}
