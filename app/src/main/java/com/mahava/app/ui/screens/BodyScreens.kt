package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.content.BodyCategories
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*

@Composable
fun BodyHomeScreen(
    vm: AppViewModel,
    onSettings: () -> Unit,
    onCategory: (String) -> Unit,
    onCare: () -> Unit,
    onPhaseDetail: (String) -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val sel = vm.phaseTodaySelection()
    val ctx = vm.dayContext()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("body_screen")) {
        ScreenHeader("شناخت بدن", onSettings = onSettings)
        MahavaCard(Modifier.padding(16.dp).testTag("body_phase_card").clickable { onPhaseDetail(sel.item.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("امروز در بدنت", color = MahavaTextSecondary)
                    Text(ctx.subWindow.titleFa, style = MaterialTheme.typography.titleLarge)
                    QuietInfo(sel.item.summaryFa)
                    Text("بیشتر بخوان", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
                }
                Illustration(R.drawable.ill_uterus_education, Modifier.width(100.dp).height(80.dp))
            }
        }
        val cats = listOf(
            BodyCategories.PERIOD_PAIN to (R.drawable.ic_droplet to "پریود و درد"),
            BodyCategories.CYCLE_HORMONES to (R.drawable.ic_uterus to "چرخه و هورمون‌ها"),
            BodyCategories.SLEEP_MOOD to (R.drawable.ic_moon to "خواب و حال روحی"),
            BodyCategories.FERTILITY_DISCHARGE to (R.drawable.ic_leaf to "باروری و ترشحات"),
            BodyCategories.DIGESTION_SKIN to (R.drawable.ic_heart to "گوارش و پوست"),
            BodyCategories.PATTERN_CARE to (R.drawable.ic_book to "الگو و مراقبت")
        )
        val hidden = state.profile?.hiddenBodyCategories?.split(',')?.toSet() ?: emptySet()
        val visible = cats.filter { it.first !in hidden }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visible.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (id, pair) ->
                        MahavaCard(Modifier.weight(1f).fillMaxHeight().testTag("cat_$id").clickable { onCategory(id) }) {
                            MahavaIcon(pair.first)
                            Spacer(Modifier.height(8.dp))
                            Text(pair.second, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            MahavaCard(Modifier.testTag("care_guide_card").clickable(onClick = onCare)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MahavaIcon(R.drawable.ic_note)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("راهنمای مراقبت و علائم هشدار", style = MaterialTheme.typography.titleMedium)
                        QuietInfo("کی مراقبت در خانه کافی است و کی باید بررسی شوی.")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun BodyCategoryScreen(vm: AppViewModel, category: String, onBack: () -> Unit, onItem: (String) -> Unit) {
    val title = BodyCategories.all.find { it.first == category }?.second ?: category
    val items = vm.bodyItems(category)
    val ill = when (category) {
        BodyCategories.CYCLE_HORMONES -> R.drawable.ill_uterus_education
        BodyCategories.SLEEP_MOOD -> R.drawable.ill_rest_woman
        BodyCategories.FERTILITY_DISCHARGE -> R.drawable.ill_daily_woman
        BodyCategories.PATTERN_CARE -> R.drawable.ill_care_woman
        else -> R.drawable.ill_daily_woman
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("category_screen")) {
        ScreenHeader(title, onBack = onBack)
        Illustration(ill, Modifier.padding(16.dp).height(140.dp))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (category == BodyCategories.CYCLE_HORMONES) {
                SectionLabel("مرحله‌های چرخه، یکی‌یکی")
                vm.phaseItems().forEach { item ->
                    MahavaCard(Modifier.clickable { onItem(item.id) }) {
                        Text(item.phase?.cardTitleFa ?: item.titleFa, style = MaterialTheme.typography.titleMedium)
                        QuietInfo(item.summaryFa)
                    }
                }
                SectionLabel("مطالب کلی")
            }
            if (items.isEmpty()) QuietInfo("فعلاً مطلبی برای نشان دادن در این بخش نیست.")
            items.forEach { item ->
                MahavaCard(Modifier.clickable { onItem(item.id) }) {
                    Text(item.titleFa, style = MaterialTheme.typography.titleMedium)
                    QuietInfo(item.summaryFa)
                }
            }
            QuietInfo(vm.contentStatus())
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Kept for existing navigation: every content item now uses the same detail template. */
@Composable
fun BodyItemScreen(vm: AppViewModel, id: String, onBack: () -> Unit) = PhaseDetailScreen(vm, id, onBack)
