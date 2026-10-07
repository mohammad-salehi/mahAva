package com.mahava.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.theme.MahavaFertility
import com.mahava.app.ui.theme.MahavaFertilitySoft
import com.mahava.app.ui.theme.MahavaMenstruation
import com.mahava.app.ui.theme.MahavaMenstruationSoft
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaPrimarySoft
import com.mahava.app.ui.theme.MahavaSurface
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary

/**
 * Home check-in: three clearly separate sections (food, body, mood). Each pick shows one
 * short line; tapping it opens the full, sourced explanation on its own screen.
 */
@Composable
fun TodaySignalsCard(vm: AppViewModel, onOpenSignal: (kind: String, key: String) -> Unit = { _, _ -> }) {
    val state by vm.state.collectAsState()
    val day = vm.today()
    val log = state.dailyLogs.find { it.epochDay == day.toEpochDay() }
    val context = vm.dayContext()
    val profile = state.profile
    val phaseGroup = TodaySignalContent.effectivePhaseGroup(
        context.subWindow,
        state.cycle?.phase,
        hormonalContraception = profile?.hormonalContraception == true,
        regularCycles = profile?.regularCycles
    )
    var food by remember(day) { mutableStateOf(emptySet<String>()) }
    var body by remember(day) { mutableStateOf(emptySet<String>()) }
    var mood by remember(day) { mutableStateOf<String?>(null) }
    var touched by remember(day) { mutableStateOf(false) }

    // Load saved choices once the database is ready; taps stay responsive while saving.
    LaunchedEffect(day, log) {
        if (!touched) {
            food = log?.foodCravings?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            body = log?.physicalSymptoms?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
            mood = log?.moods?.split(',')?.firstOrNull { it.isNotBlank() }
        }
    }

    // "Based on your own pattern" lines (null until there are 2+ complete cycles with the item).
    val personal: (String, String) -> String? = remember(state.dailyLogs, state.periods) {
        val cache = mutableMapOf<String, String?>()
        val fn: (String, String) -> String? = { kind, key ->
            cache.getOrPut("$kind/$key") { vm.personalSignal(kind, key)?.textFa }
        }
        fn
    }

    fun save() {
        touched = true
        vm.saveTodaySignals(food, body, mood)
    }

    MahavaCard(Modifier.testTag("today_signals_card")) {
        Text("حال امروزت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        QuietInfo("هر چیزی که حس می‌کنی را بزن. روی هر مورد بزنی، دلیلش را می‌بینی.")
        Spacer(Modifier.height(8.dp))
        val stageLabel = when {
            profile?.hormonalContraception == true -> "روش هورمونی ثبت شده؛ توضیح‌ها کلی‌اند"
            profile?.regularCycles == false -> "چرخه نامنظم ثبت شده؛ توضیح‌ها کلی‌اند"
            phaseGroup == "general" -> "مرحلهٔ امروز معلوم نیست؛ توضیح‌ها کلی‌اند"
            else -> "امروز: ${context.subWindow.titleFa}"
        }
        Surface(shape = RoundedCornerShape(50), color = MahavaPrimarySoft) {
            Text(
                stageLabel,
                color = MahavaPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        SignalSection(
            icon = "🍫", title = "چی هوس کردی؟", hint = "می‌تونی چند تا انتخاب کنی",
            accent = MahavaMenstruation, soft = MahavaMenstruationSoft, tag = "signal_section_food",
            options = FoodCravingKeys.ALL, selected = food, chipTag = "today_food",
            onToggle = { key -> food = if (key in food) food - key else food + key; save() },
            kind = "food", phaseGroup = phaseGroup, onOpen = onOpenSignal, personal = personal
        )
        Spacer(Modifier.height(12.dp))
        SignalSection(
            icon = "🌿", title = "بدنت چه حسی دارد؟", hint = "می‌تونی چند تا انتخاب کنی",
            accent = MahavaPrimary, soft = MahavaPrimarySoft, tag = "signal_section_body",
            options = TodaySignalContent.physical, selected = body, chipTag = "today_body",
            onToggle = { key -> body = if (key in body) body - key else body + key; save() },
            kind = "body", phaseGroup = phaseGroup, onOpen = onOpenSignal, personal = personal
        )
        Spacer(Modifier.height(12.dp))
        SignalSection(
            icon = "💜", title = "حالت چطوره؟", hint = "یکی را انتخاب کن",
            accent = MahavaFertility, soft = MahavaFertilitySoft, tag = "signal_section_mood",
            options = TodaySignalContent.moods, selected = mood?.let { setOf(it) }.orEmpty(), chipTag = "today_mood",
            onToggle = { key -> mood = if (mood == key) null else key; save() },
            kind = "mood", phaseGroup = phaseGroup, onOpen = onOpenSignal, personal = personal
        )
        Spacer(Modifier.height(10.dp))
        QuietInfo("انتخاب‌ها در ثبت امروزت ذخیره می‌شوند. برای پاک کردن، دوباره بزن.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignalSection(
    icon: String,
    title: String,
    hint: String,
    accent: Color,
    soft: Color,
    tag: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    chipTag: String,
    onToggle: (String) -> Unit,
    kind: String,
    phaseGroup: String,
    onOpen: (String, String) -> Unit,
    personal: (String, String) -> String? = { _, _ -> null }
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = RoundedCornerShape(18.dp),
        color = soft
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MahavaSurface),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 18.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaTextPrimary)
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (key, label) ->
                    SectionChip(label, key in selected, accent, "${chipTag}_$key") { onToggle(key) }
                }
            }
            val picked = options.filter { it.first in selected }
            if (picked.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picked.forEach { (key, label) ->
                        val ex = TodaySignalContent.explainFull(kind, key, phaseGroup)
                        TeaserRow(label, ex.teaserFa, accent, "signal_explanation_${kind}_$key", personal(kind, key)) { onOpen(kind, key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, selected: Boolean, accent: Color, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else MahavaSurface)
            .border(BorderStroke(1.dp, accent.copy(alpha = if (selected) 1f else 0.35f)), RoundedCornerShape(14.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else MahavaTextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TeaserRow(label: String, teaser: String, accent: Color, tag: String, personalFa: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).testTag(tag),
        shape = RoundedCornerShape(14.dp),
        color = MahavaSurface
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(teaser, style = MaterialTheme.typography.bodyMedium, color = MahavaTextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (personalFa != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("الگوی خودت: $personalFa", style = MaterialTheme.typography.bodySmall, color = accent,
                        fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("${tag}_personal"))
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("چرا؟ ‹", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
