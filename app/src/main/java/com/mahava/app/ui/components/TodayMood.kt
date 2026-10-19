package com.mahava.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.content.BodyNowContent
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/** «الان توی بدنت چه خبره؟»: 2–3 short lines, the woman illustration, a link to the science page. */
@Composable
fun BodyNowCard(data: BodyNowContent.BodyNow, pregnant: Boolean, onMore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MahavaLavenderSoft)
            .clickable(onClick = onMore).padding(16.dp).testTag("today_body_now")
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("الان توی بدنت چه خبره؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                data.points.forEach { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 9.dp).size(5.dp).clip(RoundedCornerShape(50)).background(MahavaPrimary))
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MahavaTextPrimary)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Image(painterResource(if (pregnant) R.drawable.redesign_pregnancy else R.drawable.redesign_woman), null,
                Modifier.size(width = 84.dp, height = 112.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MahavaIcon(R.drawable.design_info, MahavaTextSecondary, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(data.basisFa, style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary, modifier = Modifier.weight(1f).testTag("today_body_basis"))
            Text("بیشتر بدون", style = MaterialTheme.typography.bodySmall, color = MahavaPrimaryDeep, fontWeight = FontWeight.SemiBold)
        }
    }
}

private val moodIcons = mapOf("good" to R.drawable.redesign_mood_good, "ordinary" to R.drawable.redesign_mood_ordinary, "bad" to R.drawable.redesign_mood_bad)
private val moodTint = mapOf("good" to Color(0xFFFFF0F6), "ordinary" to Color(0xFFEFF3FF), "bad" to Color(0xFFF0EDFF))
private val moodAccent = mapOf("good" to MahavaPrimary, "ordinary" to Color(0xFF6F8FE8), "bad" to MahavaLavender)

/** «حال امروزت چطوره؟» + three options + the mood chart, with nothing in between. */
@Composable
fun TodayMoodSection(vm: AppViewModel, logs: List<DailyLogEntity>, today: LocalDate) {
    val scope = rememberCoroutineScope()
    val todayLog = logs.find { it.epochDay == today.toEpochDay() }
    val saved = BodyNowContent.moodOf(todayLog)
    var pending by remember(today) { mutableStateOf<String?>(null) }
    var result by remember(today) { mutableStateOf<AppViewModel.MoodSave?>(null) }
    val shown = pending ?: saved
    Column(Modifier.fillMaxWidth().testTag("today_mood_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("حال امروزت چطوره؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyNowContent.MOODS.forEach { (key, label) ->
                val on = shown == key
                val accent = moodAccent.getValue(key)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(moodTint.getValue(key))
                        .border(if (on) 2.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) {
                            if (pending != null) return@clickable
                            pending = key; result = null
                            scope.launch { result = vm.saveTodayMood(key); pending = null }
                        }
                        .semantics { selected = on }
                        .padding(vertical = 12.dp).testTag("today_mood_$key"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(moodIcons.getValue(key)), null, Modifier.size(if (on) 40.dp else 34.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) accent else MahavaTextPrimary)
                }
            }
        }
        val status = when {
            pending != null -> "در حال ذخیره…"
            result == AppViewModel.MoodSave.SYNCED -> "ذخیره شد ✓"
            result == AppViewModel.MoodSave.LOCAL_ONLY -> "روی گوشیت ذخیره شد ✓"
            result == AppViewModel.MoodSave.OFFLINE_PENDING -> "روی گوشیت ذخیره شد؛ هنوز به سرور نرسیده. وقتی اینترنت وصل شه، خودمون می‌فرستیمش."
            result == AppViewModel.MoodSave.FAILED -> "ذخیره نشد؛ یه بار دیگه امتحان کن."
            else -> null
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (result == AppViewModel.MoodSave.FAILED) MahavaDanger else MahavaTextSecondary, modifier = Modifier.testTag("today_mood_status")) }
        MoodTrendChart(BodyNowContent.moodSeries(logs, today.toEpochDay()), today.toEpochDay())
    }
}

private val levels = mapOf("good" to 2, "ordinary" to 1, "bad" to 0)

/** «حال این روزهات»: last 14 days, oldest left; unlogged days are gaps (the line breaks). */
@Composable
fun MoodTrendChart(series: List<Pair<Long, String?>>, todayEpoch: Long) {
    var picked by remember(series) { mutableStateOf<Int?>(null) }
    val lastIdx = series.indexOfLast { it.second != null }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFFFF8FB))
            .border(1.dp, MahavaBorder, RoundedCornerShape(24.dp)).padding(14.dp).testTag("today_mood_chart")
    ) {
        Text("حال این روزهات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (lastIdx < 0) {
            Text("حالت رو ثبت کن تا کم‌کم روندش رو اینجا ببینی.", style = MaterialTheme.typography.bodyMedium,
                color = MahavaTextSecondary, modifier = Modifier.testTag("today_mood_chart_empty"))
            return@Column
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth().height(118.dp)) {
                Column(Modifier.fillMaxHeight().padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf("good", "ordinary", "bad").forEach { k ->
                        Text(BodyNowContent.moodLabel(k)!!, style = MaterialTheme.typography.labelSmall, color = moodAccent.getValue(k))
                    }
                }
                val line = MahavaPrimary
                val grid = MahavaBorder
                val todayFill = MahavaLavenderSoft
                Canvas(Modifier.weight(1f).fillMaxHeight().pointerInput(series) {
                    detectTapGestures { pos ->
                        val n = series.size
                        val step = if (n > 1) (size.width - 24f) / (n - 1) else 0f
                        val i = if (n > 1) Math.round((pos.x - 12f) / step).coerceIn(0, n - 1) else 0
                        picked = if (series[i].second != null) i else null
                    }
                }) {
                    val n = series.size
                    val inset = 12f
                    val step = if (n > 1) (size.width - 2 * inset) / (n - 1) else 0f
                    val top = 10f; val bottom = size.height - 10f
                    fun x(i: Int) = if (n > 1) inset + i * step else size.width / 2
                    fun y(level: Int) = bottom - level / 2f * (bottom - top)
                    // Today column: a very light band.
                    val ti = series.indexOfFirst { it.first == todayEpoch }
                    if (ti >= 0) drawRoundRect(todayFill, Offset(x(ti) - 10f, 0f), androidx.compose.ui.geometry.Size(20f, size.height),
                        androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                    (0..2).forEach { l -> drawLine(grid, Offset(0f, y(l)), Offset(size.width, y(l)), 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))) }
                    // Smooth line only between consecutive logged days; a missing day breaks it.
                    var path: Path? = null
                    var prev: Offset? = null
                    series.forEachIndexed { i, (_, m) ->
                        if (m == null) { path?.let { drawPath(it, line, style = Stroke(5f, cap = StrokeCap.Round)) }; path = null; prev = null; return@forEachIndexed }
                        val p = Offset(x(i), y(levels.getValue(m)))
                        if (path == null) path = Path().apply { moveTo(p.x, p.y) }
                        else { val c = prev!!; val mx = (c.x + p.x) / 2; path!!.cubicTo(mx, c.y, mx, p.y, p.x, p.y) }
                        prev = p
                    }
                    path?.let { drawPath(it, line, style = Stroke(5f, cap = StrokeCap.Round)) }
                    series.forEachIndexed { i, (_, m) ->
                        if (m == null) return@forEachIndexed
                        val p = Offset(x(i), y(levels.getValue(m)))
                        val big = i == lastIdx || i == picked
                        drawCircle(Color.White, if (big) 13f else 9f, p)
                        drawCircle(moodAccent.getValue(m), if (big) 9f else 6f, p)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                val first = LocalDate.ofEpochDay(series.first().first)
                Text(JalaliDate.from(first).formatShortFa(), style = MaterialTheme.typography.labelSmall, color = MahavaTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("امروز", style = MaterialTheme.typography.labelSmall, color = MahavaPrimaryDeep, fontWeight = FontWeight.Bold)
            }
        }
        val info = picked?.let { series[it] } ?: series[lastIdx]
        val dayFa = if (info.first == todayEpoch) "امروز" else JalaliDate.from(LocalDate.ofEpochDay(info.first)).formatShortFa()
        Text((if (picked == null) "آخرین ثبت: " else "") + "$dayFa — ${BodyNowContent.moodLabel(info.second)}",
            style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary, modifier = Modifier.padding(top = 6.dp).testTag("today_mood_chart_info"))
    }
}