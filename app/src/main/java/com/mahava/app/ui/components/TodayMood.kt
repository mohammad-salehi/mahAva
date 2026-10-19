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
import androidx.compose.ui.unit.sp
import com.mahava.app.R
import com.mahava.app.content.BodyNowContent
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.theme.*
import com.mahava.app.util.JalaliDate
import kotlinx.coroutines.launch
import java.time.LocalDate

/** «الان توی بدنت چه خبره؟» in the approved-home body card: text at the start, the kit illustration at the end. */
@Composable
fun BodyNowCard(data: BodyNowContent.BodyNow, pregnant: Boolean, onMore: () -> Unit) {
    val k = com.mahava.app.ui.kit.Kit
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(k.cardRadius))
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFF3F0FE), Color(0xFFF6F3FD))))
            .clickable(onClickLabel = "بیشتر بدون", onClick = onMore).padding(start = 10.dp, end = 18.dp, top = 12.dp, bottom = 12.dp).testTag("today_body_now")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(if (pregnant) R.drawable.redesign_pregnancy else R.drawable.redesign_woman), null,
                Modifier.size(width = 96.dp, height = 96.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("الان توی بدنت چه خبره؟", style = k.cardTitle)
                Spacer(Modifier.height(2.dp))
                val first = data.rows.firstOrNull()
                if (first == null) data.points.forEach { line -> Text(line, style = k.body.copy(color = MahavaTextSecondary)) }
                else BodyNowRowItem(first)
            }
        }
        // The rest of the rows run full width under the picture: short label, one sentence each.
        if (data.rows.size > 1) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.rows.drop(1).forEach { BodyNowRowItem(it) }
            }
        }
        Spacer(Modifier.height(6.dp))
        // The whole card opens «بیشتر بدون» (the science page); a small chevron hints at it.
        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MahavaIcon(R.drawable.kit_info, MahavaTextSecondary, 16.dp)
            Spacer(Modifier.width(6.dp))
            Text(data.basisFa, style = k.small, modifier = Modifier.weight(1f).testTag("today_body_basis"))
            MahavaIcon(R.drawable.kit_chevron_left, MahavaTextSecondary, 16.dp)
        }
    }
}

@Composable
private fun BodyNowRowItem(row: BodyNowContent.Row) {
    val k = com.mahava.app.ui.kit.Kit
    val accent = when (row.key) { "warn" -> MahavaDanger; "limit" -> MahavaLavender; else -> MahavaPrimary }
    Column(Modifier.fillMaxWidth().testTag("today_body_row_${row.key}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(accent))
            Spacer(Modifier.width(6.dp))
            Text(row.label, style = k.small.copy(color = accent, fontWeight = FontWeight.Bold))
        }
        Text(row.text, style = k.body.copy(color = MahavaTextSecondary), modifier = Modifier.padding(start = 12.dp))
    }
}

private val moodIcons = mapOf("good" to R.drawable.redesign_mood_good, "ordinary" to R.drawable.redesign_mood_ordinary, "bad" to R.drawable.redesign_mood_bad)
private val moodTint = mapOf("good" to Color(0xFFFCEEF4), "ordinary" to Color(0xFFEDF1FC), "bad" to Color(0xFFF1F0FA))
private val moodAccent = mapOf("good" to MahavaPrimary, "ordinary" to Color(0xFF6F8FE8), "bad" to MahavaLavender)

/** «حال امروزت چطوره؟» + three options + the mood chart, with nothing in between. */
@Composable
fun TodayMoodSection(vm: AppViewModel, logs: List<DailyLogEntity>, today: LocalDate, showIntimacy: Boolean = false) {
    val scope = rememberCoroutineScope()
    val todayLog = logs.find { it.epochDay == today.toEpochDay() }
    val saved = BodyNowContent.moodOf(todayLog)
    var pending by remember(today) { mutableStateOf<String?>(null) }
    var result by remember(today) { mutableStateOf<AppViewModel.MoodSave?>(null) }
    val shown = pending ?: saved
    Column(Modifier.fillMaxWidth().testTag("today_mood_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("حال امروزت چطوره؟", style = com.mahava.app.ui.kit.Kit.cardTitle)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyNowContent.MOODS.forEach { (key, label) ->
                val on = shown == key
                val accent = moodAccent.getValue(key)
                Column(
                    Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp)).background(moodTint.getValue(key))
                        .border(if (on) 1.5.dp else 0.dp, if (on) accent else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button) {
                            if (pending != null) return@clickable
                            pending = key; result = null
                            scope.launch { result = vm.saveTodayMood(key); pending = null }
                        }
                        .semantics { selected = on }
                        .testTag("today_mood_$key"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Image(painterResource(moodIcons.getValue(key)), null, Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = com.mahava.app.ui.kit.Kit.label, color = if (on) MahavaPrimaryDeep else MahavaTextPrimary)
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
        if (showIntimacy) TodayIntimacyRow(vm, todayLog, today)
        MoodTrendChart(BodyNowContent.moodSeries(logs, today.toEpochDay()), today.toEpochDay())
    }
}

/** «رابطه»: one compact row under the mood options, same look; saved into today's log. */
@Composable
private fun TodayIntimacyRow(vm: AppViewModel, todayLog: DailyLogEntity?, today: LocalDate) {
    val scope = rememberCoroutineScope()
    val saved = com.mahava.app.content.IntimacyLog.of(todayLog)
    var busy by remember(today) { mutableStateOf(false) }
    var pending by remember(today) { mutableStateOf<String?>(null) }
    val shown = if (busy) pending else saved
    Row(Modifier.fillMaxWidth().testTag("today_intimacy"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("رابطه", style = com.mahava.app.ui.kit.Kit.label, color = MahavaTextPrimary)
        com.mahava.app.content.IntimacyLog.OPTIONS.forEach { (key, label) ->
            val on = shown == key
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(14.dp)).background(if (on) MahavaPrimarySoft else Color(0xFFF4F3F8))
                    .border(if (on) 1.5.dp else 0.dp, if (on) MahavaPrimary else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) {
                        if (busy) return@clickable
                        val next = if (saved == key) null else key
                        busy = true; pending = next
                        scope.launch { try { vm.saveTodayIntimacy(next) } finally { busy = false } }
                    }
                    .semantics { selected = on }
                    .testTag("today_intim_$key"),
                contentAlignment = Alignment.Center
            ) {
                Text(label, style = com.mahava.app.ui.kit.Kit.label, color = if (on) MahavaPrimaryDeep else MahavaTextPrimary, maxLines = 1)
            }
        }
    }
}

private val levels = mapOf("good" to 2, "ordinary" to 1, "bad" to 0)

/** «حال این روزهات»: last 14 days, oldest left; unlogged days are gaps (the line breaks). */
@Composable
fun MoodTrendChart(series: List<Pair<Long, String?>>, todayEpoch: Long) {
    var picked by remember(series) { mutableStateOf<Int?>(null) }
    val lastIdx = series.indexOfLast { it.second != null }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(com.mahava.app.ui.kit.Kit.cardRadius)).background(Color.White)
            .border(1.dp, MahavaBorder, RoundedCornerShape(com.mahava.app.ui.kit.Kit.cardRadius)).padding(14.dp).testTag("today_mood_chart")
    ) {
        Text("حال این روزهات", style = com.mahava.app.ui.kit.Kit.cardTitle.copy(fontSize = 15.sp))
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