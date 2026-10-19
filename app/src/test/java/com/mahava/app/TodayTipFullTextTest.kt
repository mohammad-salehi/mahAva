package com.mahava.app

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mahava.app.ui.kit.KitRowCard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** «پیشنهاد امروز» and the other Today cards show their whole text; the card grows instead of "...". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayTipFullTextTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val longTip = "امروز یه کم بیشتر به خودت برس: چند لیوان آب بیشتر بخور، یه پیادهٔ آروم ده‌دقیقه‌ای برو، " +
        "شام رو سبک‌تر و زودتر بخور و قبل از خواب گوشی رو کنار بذار تا خوابت عمیق‌تر بشه. " +
        "اگه دلت درد می‌کنه، کیسهٔ آب گرم و کش‌وقوس ملایم کمرت رو بیشتر از همیشه کمک می‌کنه، " +
        "و یادت باشه حالت امروزت رو هم ثبت کنی تا بعداً بهتر بفهمی بدنت چی می‌خواد."

    @Test fun recommendationShowsAllTheText() {
        compose.setContent {
            Box(Modifier.background(Color(0xFFFFF7FA)).padding(16.dp).width(379.dp).testTag("shot")) {
                KitRowCard("پیشنهاد امروز", longTip, Color(0xFFFFE4EE), tag = "today_recommendation", onClick = {})
            }
        }
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(longTip, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        val r = results.first(); assertFalse("tip is cut: lines=${r.lineCount} w=${r.didOverflowWidth} h=${r.didOverflowHeight} size=${r.size} lastEnd=${r.getLineEnd(r.lineCount - 1, true)} len=${longTip.length}", r.didOverflowHeight || r.getLineEnd(r.lineCount - 1, true) < longTip.length)
        assertTrue("wraps over more than 3 lines", results.first().lineCount > 3)
        compose.waitForIdle()
        val b = compose.onNodeWithTag("shot").fetchSemanticsNode().boundsInRoot
        lateinit var bmp: Bitmap
        compose.runOnUiThread {
            val v = compose.activity.window.decorView
            val full = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(android.graphics.Canvas(full))
            bmp = Bitmap.createBitmap(full, b.left.toInt(), b.top.toInt(), b.width.toInt().coerceAtMost(full.width - b.left.toInt()), b.height.toInt().coerceAtMost(full.height - b.top.toInt()))
        }
        File("build/screenshots/today-tip-full.png").apply { parentFile!!.mkdirs() }.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}