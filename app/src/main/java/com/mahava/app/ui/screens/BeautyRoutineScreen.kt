package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.content.*
import com.mahava.app.health.models.AppHealthMode
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.kit.Kit
import com.mahava.app.ui.theme.*
import com.mahava.app.util.PersianDigits

@Composable
fun BeautyRoutineScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { BeautyProfileStore.load(context) }
    val profile by BeautyProfileStore.flow.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    val p = profile ?: return
    if (!p.complete || editing) {
        BeautyIntake(p, onBack = { if (editing && p.complete) editing = false else onBack() }) {
            BeautyProfileStore.save(context, it); editing = false
        }
    } else BeautyAdviceView(vm, p, onBack) { editing = true }
}

// ---------------- intake ----------------

@Composable
private fun BeautyIntake(initial: BeautyProfile, onBack: () -> Unit, onDone: (BeautyProfile) -> Unit) {
    var d by remember { mutableStateOf(initial) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().background(MahavaBackground).testTag("beauty_intake")) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp)) {
            ScreenHeader("وضعیت پوست و مو", onBack = { if (step > 0) step-- else onBack() })
            Text("مرحلهٔ ${PersianDigits.toPersian(step + 1)} از ۳ · ${listOf("پوست", "مو", "سابقه و سبک زندگی")[step]}", style = Kit.small.copy(color = MahavaTextSecondary))
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                color = MahavaPrimary, trackColor = MahavaPrimarySoft)
            Spacer(Modifier.height(12.dp))
            Text("وضعیت فعلی خودتان را بگویید تا توصیه‌ها مخصوص شما باشد، نه کلی. فقط روی همین گوشی ذخیره می‌شود و بعداً قابل ویرایش است.",
                style = Kit.body.copy(color = MahavaTextSecondary))
            Spacer(Modifier.height(16.dp))
            if (step == 0) {
                Q("نوع پوستتان") { Single(BeautyLabels.skinType, d.skinType) { d = d.copy(skinType = it) } }
                Q("این روزها پوستتان چطور است؟", "چند گزینه") {
                    Multi(BeautyLabels.skinIssues, d.skinIssues) { d = d.copy(skinIssues = toggle(d.skinIssues, it),
                        acneZones = if (it == "acne" && "acne" in d.skinIssues) emptySet() else d.acneZones) }
                }
                if (d.hasAcne) {
                    Q("جوش بیشتر کجاست؟", "چند گزینه") { Multi(BeautyLabels.acneZones, d.acneZones, exclusive = null) { d = d.copy(acneZones = toggle(d.acneZones, it, null)) } }
                    Q("شدت جوش") { Single(BeautyLabels.acneSeverity, d.acneSeverity) { d = d.copy(acneSeverity = it) } }
                }
                Q("روتین فعلی شما (تقریبی)") { Single(BeautyLabels.routine, d.routine) { d = d.copy(routine = it) } }
                Q("ضدآفتاب روزانه می‌زنید؟") { Single(BeautyLabels.spf, d.spf) { d = d.copy(spf = it) } }
                Q("الان از کدام اکتیوها استفاده می‌کنید؟", "چند گزینه") { Multi(BeautyLabels.actives, d.actives) { d = d.copy(actives = toggle(d.actives, it)) } }
                Q("با محصولات سوزش یا حساسیت پیدا می‌کنید؟") { Single(BeautyLabels.stinging, d.stinging) { d = d.copy(stinging = it) } }
                Q("این مشکل پوستی از کی شروع شده؟") { Single(BeautyLabels.issueDuration, d.issueDuration) { d = d.copy(issueDuration = it) } }
                Q("جوش یا مشکل پوستت قبل از پریود بدتر می‌شود؟") { Single(BeautyLabels.cyclical, d.cyclical) { d = d.copy(cyclical = it) } }
            } else if (step == 1) {
                Q("کف سرتان") { Single(BeautyLabels.scalp, d.scalp) { d = d.copy(scalp = it) } }
                Q("ساقهٔ مو") { Single(BeautyLabels.lengths, d.lengths) { d = d.copy(lengths = it) } }
                Q("نگرانی‌های فعلی", "چند گزینه") { Multi(BeautyLabels.hairConcerns, d.hairConcerns) { d = d.copy(hairConcerns = toggle(d.hairConcerns, it)) } }
                Q("حدوداً چند بار در هفته مو را می‌شویید؟") {
                    Single((1..7).map { it.toString() to PersianDigits.toPersian(it) }, if (d.washesPerWeek > 0) d.washesPerWeek.toString() else "") {
                        d = d.copy(washesPerWeek = it.toInt()) }
                }
                Q("رنگ، دکلره یا کراتین اخیر؟") { Single(BeautyLabels.chemical, d.chemical) { d = d.copy(chemical = it) } }
                Q("ریزش مو از کی شروع شده؟") { Single(BeautyLabels.sheddingTiming, d.sheddingTiming) { d = d.copy(sheddingTiming = it) } }
            } else {
                Text("این بخش فقط برای دقیق‌تر شدن توصیه‌هاست و تشخیص نیست. فقط چیزی را بزنید که پزشک قبلاً به شما گفته.",
                    style = Kit.small.copy(color = MahavaTextSecondary), modifier = Modifier.padding(bottom = 10.dp))
                Q("پزشک قبلاً به شما گفته که…", "چند گزینه") { Multi(BeautyLabels.conditions, d.conditions) { d = d.copy(conditions = toggle(d.conditions, it)) } }
                Q("داروی مؤثر بر پوست مصرف می‌کنید؟ (اختیاری)", "چند گزینه") {
                    Multi(BeautyLabels.meds, d.meds) { k -> d = d.copy(meds = when (k) {
                        "skip", "none" -> if (k in d.meds) emptySet() else setOf(k)
                        else -> toggle(d.meds - "skip" - "none", k, null) }) }
                }
                Q("این روزها خواب و استرس") { Single(BeautyLabels.sleepStress, d.sleepStress) { d = d.copy(sleepStress = it) } }
            }
        }
        val ok = when (step) { 0 -> d.skinComplete && d.issueDuration.isNotEmpty() && d.cyclical.isNotEmpty()
            1 -> d.hairComplete && d.sheddingTiming.isNotEmpty(); else -> d.historyComplete }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (!ok) Text("همهٔ سؤال‌های این مرحله را جواب دهید.", style = Kit.small.copy(color = MahavaTextSecondary),
                modifier = Modifier.padding(bottom = 8.dp))
            Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50))
                .background(if (ok) MahavaPrimary else MahavaPrimary.copy(alpha = .35f))
                .clickable(enabled = ok) { if (step < 2) step++ else onDone(d) }
                .testTag("beauty_intake_next"), contentAlignment = Alignment.Center) {
                Text(listOf("ادامه: مو", "ادامه: سابقه", "ذخیره و دیدن توصیه‌ها")[step], color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

private fun toggle(s: Set<String>, k: String, exclusive: String? = "none"): Set<String> = when {
    k in s -> s - k
    exclusive != null && k == exclusive -> setOf(k)
    exclusive != null -> s - exclusive + k
    else -> s + k
}

@Composable
private fun Q(title: String, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(Kit.cardRadius)).background(Color.White).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Kit.cardTitle.copy(fontSize = 15.sp), modifier = Modifier.weight(1f))
            if (hint != null) Text(hint, style = Kit.small.copy(color = MahavaTextSecondary))
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Single(opts: List<Pair<String, String>>, sel: String, onPick: (String) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (k, l) -> Chip(l, k == sel) { onPick(k) } }
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Multi(opts: List<Pair<String, String>>, sel: Set<String>, exclusive: String? = "none", onToggle: (String) -> Unit) =
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        opts.forEach { (k, l) -> Chip(l, k in sel, check = true) { onToggle(k) } }
    }

@Composable
private fun Chip(label: String, selected: Boolean, check: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50))
        .background(if (selected) MahavaPrimary else MahavaPrimarySoft.copy(alpha = .5f))
        .border(1.dp, if (selected) MahavaPrimary else Color(0xFFE3DEF2), RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (check && selected) { Text("✓", color = Color.White, fontSize = 13.sp); Spacer(Modifier.width(5.dp)) }
        Text(label, fontSize = 14.sp, color = if (selected) Color.White else MahavaTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ---------------- advice ----------------

@Composable
private fun BeautyAdviceView(vm: AppViewModel, p: BeautyProfile, onBack: () -> Unit, onEdit: () -> Unit) {
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val pregnant = state.health?.appMode == AppHealthMode.PREGNANCY
    val group = TodaySignalContent.effectivePhaseGroup(ctx.subWindow, state.cycle?.phase,
        state.profile?.hormonalContraception == true, state.profile?.regularCycles)
    val phaseTitle = BeautyRoutineContent.forState(group, pregnant).phaseTitleFa
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val a = if (tab == 0) BeautyAdviceEngine.skin(p, group, pregnant) else BeautyAdviceEngine.hair(p, group, pregnant)
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp).testTag("beauty_routine_screen")) {
        ScreenHeader("روتین پوست و موی شما", onBack = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(phaseTitle, MahavaPrimarySoft, MahavaPrimary)
            Spacer(Modifier.weight(1f))
            Text("ویرایش وضعیت", color = MahavaPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onEdit).padding(8.dp).testTag("beauty_edit"))
        }
        Text("آخرین به‌روزرسانی وضعیت: ${relative(p.updatedAt)}", style = Kit.small.copy(color = MahavaTextSecondary))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White).padding(4.dp)) {
            listOf("پوست", "مو").forEachIndexed { i, l ->
                val s = tab == i
                Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (s) MahavaPrimary else Color.Transparent)
                    .clickable { tab = i }.padding(vertical = 10.dp).testTag(if (i == 0) "beauty_tab_skin" else "beauty_tab_hair"),
                    contentAlignment = Alignment.Center) {
                    Text(l, color = if (s) Color.White else MahavaTextSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // summary of THEIR answers
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Kit.cardRadius)).background(Kit.cardLavender).padding(16.dp)) {
            Text("خلاصهٔ وضعیت شما", style = Kit.cardTitle.copy(fontSize = 15.sp))
            Spacer(Modifier.height(6.dp)); Text(a.summaryFa, style = Kit.body)
        }
        Spacer(Modifier.height(12.dp))
        Section("۳ اقدام اولویت‌دار امروز") {
            a.priorities.forEachIndexed { i, t ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(MahavaPrimary), contentAlignment = Alignment.Center) {
                        Text(PersianDigits.toPersian(i + 1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.becauseFa, style = Kit.small.copy(color = MahavaPrimary, fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(2.dp)); Text(t.doFa, style = Kit.body.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(4.dp))
                        Text("چرا: " + t.whyFa, style = Kit.small.copy(color = MahavaTextSecondary),
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MahavaBackground).padding(10.dp))
                    }
                }
            }
        }
        Steps(if (tab == 0) "روتین صبح" else "شستشو", if (tab == 0) "☀" else "💧", a.morning)
        Steps(if (tab == 0) "روتین شب" else "مراقبت", if (tab == 0) "☾" else "✿", a.night)
        Section("پرهیز", bg = Kit.cardPink) { a.avoid.forEach { Text("✕  $it", style = Kit.body, modifier = Modifier.padding(vertical = 3.dp)) } }
        Section("نکتهٔ فاز", bg = Kit.cardBlue) { Text(a.phaseNoteFa, style = Kit.body) }
        if (a.flags.isNotEmpty()) Section("بهتر است با پزشک چک شود", bg = Color(0xFFFFF4E5)) {
            a.flags.forEach { Text("• $it", style = Kit.body, modifier = Modifier.padding(vertical = 3.dp)) } }
        Section("بیشتر بدان") { a.learn.forEach { (t, b) ->
            Text(t, style = Kit.body.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 6.dp))
            Text(b, style = Kit.small.copy(color = MahavaTextSecondary)) } }
        Text(a.disclaimerFa, style = Kit.small.copy(color = MahavaTextSecondary), modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
    }
}

@Composable
private fun Section(title: String, bg: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(Kit.cardRadius)).background(bg).padding(16.dp)) {
        Text(title, style = Kit.cardTitle.copy(fontSize = 15.sp)); Spacer(Modifier.height(6.dp)); content()
    }
}

@Composable
private fun Steps(title: String, icon: String, steps: List<String>) = Section("$icon  $title") {
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("قدم ${PersianDigits.toPersian(i + 1)}", fontSize = 12.sp, color = MahavaPrimary, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MahavaPrimarySoft).padding(horizontal = 8.dp, vertical = 3.dp))
            Spacer(Modifier.width(10.dp)); Text(s, style = Kit.body, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Pill(t: String, bg: Color, fg: Color) =
    Text(t, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp))

private fun relative(ts: Long): String {
    if (ts <= 0) return "—"
    val days = ((System.currentTimeMillis() - ts) / 86_400_000L).toInt()
    return when (days) { 0 -> "امروز"; 1 -> "دیروز"; else -> "${PersianDigits.toPersian(days)} روز پیش" }
}
