package com.mahava.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.content.ContextCompat
import com.mahava.app.cycle.CyclePhase
import com.mahava.app.widget.CycleRingBitmapRenderer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Home-screen widget background: translucent pink glass, rounded, text stays readable. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetGlassTest {
    private val ctx = RuntimeEnvironment.getApplication()
    private val textPrimary = 0xFF23243E.toInt()

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }
    private fun contrast(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }

    private fun bgOver(wallpaper: Int, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(wallpaper)
        ContextCompat.getDrawable(ctx, R.drawable.widget_cycle_bg)!!.apply { setBounds(0, 0, w, h); draw(c) }
        return bmp
    }

    @Test fun backgroundIsTranslucentPink_roundedAndReadable() {
        val alone = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        ContextCompat.getDrawable(ctx, R.drawable.widget_cycle_bg)!!.apply { setBounds(0, 0, 300, 300); draw(Canvas(alone)) }
        val mid = alone.getPixel(150, 200)
        assertTrue("glass, not opaque: alpha=${Color.alpha(mid)}", Color.alpha(mid) in 0x99..0xC8)
        assertTrue("pink: ${Integer.toHexString(mid)}", Color.red(mid) > Color.green(mid) + 30 && Color.blue(mid) > Color.green(mid))
        assertTrue("rounded corner stays clear", Color.alpha(alone.getPixel(1, 1)) < 0x20)
        for (wall in intArrayOf(Color.WHITE, 0xFF7A8FA6.toInt(), 0xFF101418.toInt())) {
            val px = bgOver(wall, 300, 300).getPixel(150, 150)
            val k = contrast(textPrimary, px)
            assertTrue("text contrast on ${Integer.toHexString(wall)} = $k", k >= 4.5)
        }
    }

    @Test fun renderPreview() {
        val walls = listOf(
            intArrayOf(0xFFB9D7EA.toInt(), 0xFF6C8FB8.toInt()),
            intArrayOf(0xFFF3E7D3.toInt(), 0xFFC9A27E.toInt()),
            intArrayOf(0xFF2B3A55.toInt(), 0xFF0E1320.toInt())
        )
        val cell = 520; val gap = 40
        val out = Bitmap.createBitmap(walls.size * (cell + gap) + gap, cell + 2 * gap, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val ring = CycleRingBitmapRenderer.render(
            ctx,
            CycleRingBitmapRenderer.RingModel(14, 28, CyclePhase.OVULATION_WINDOW, "پنجرهٔ باروری", 14, isLate = false, periodOngoing = false),
            cell - 48
        )
        walls.forEachIndexed { i, g ->
            val x0 = (gap + i * (cell + gap)).toFloat()
            c.drawRect(x0 - gap / 2f, 0f, x0 + cell + gap / 2f, out.height.toFloat(), Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, out.height.toFloat(), g[0], g[1], Shader.TileMode.CLAMP)
            })
            val bg = ContextCompat.getDrawable(ctx, R.drawable.widget_cycle_bg)!!
            bg.setBounds(x0.toInt(), gap, x0.toInt() + cell, gap + cell); bg.draw(c)
            c.drawBitmap(ring, x0 + 24, gap + 24f, null)
        }
        val f = File("build/screenshots/widget-glass.png").apply { parentFile!!.mkdirs() }
        f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 1000)
    }
}