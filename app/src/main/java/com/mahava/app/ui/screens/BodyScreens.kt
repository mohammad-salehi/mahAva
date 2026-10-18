package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
    onPhaseDetail: (String) -> Unit = {},
    onAccount: () -> Unit = {},
    onHealthInsight: (String) -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val premium by vm.isPremium.collectAsState()
    val sel = vm.phaseTodaySelection()
    var query by rememberSaveable { mutableStateOf("") }
    var pregnancyTopic by rememberSaveable { mutableStateOf(state.profile?.pregnancyMode == true) }
    val week = state.health?.pregnancyProgress?.contentWeek?.coerceIn(1,42) ?: 4
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("body_screen")) {
        ScreenHeader("شناخت بدن", onSettings = onSettings)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("body_search"), placeholder = { Text("دنبال چه موضوعی هستی؟") }, leadingIcon = { MahavaIcon(R.drawable.design_search) }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), singleLine = true)
            MahavaCard(Modifier.testTag("body_phase_card").clickable { if (pregnancyTopic) onHealthInsight("preg_week_$week") else onPhaseDetail(sel.item.id) }, containerColor = MahavaMenstruationSoft.copy(alpha = 0.7f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        QuietInfo("متناسب با موضوع مطالعهٔ تو")
                        Text(if (pregnancyTopic) "همراه بارداری" else "چرخه و تغییرات بدن", style = MaterialTheme.typography.headlineLarge)
                        Text("توضیح ساده با منابع علمی")
                        MoreLink("بیشتر بخوان")
                    }
                    Illustration(if (pregnancyTopic) R.drawable.design_mother else R.drawable.design_uterus_botanical, Modifier.width(120.dp).height(125.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChipPill("چرخه", !pregnancyTopic) { pregnancyTopic = false }
                ChoiceChipPill("بارداری", pregnancyTopic) { pregnancyTopic = true }
            }
            QuietInfo("انتخاب موضوع مطالعه، وضعیت سلامت ثبت‌شده‌ات را تغییر نمی‌دهد.")
            if (query.isNotBlank()) {
                val results = vm.healthFeed().filter { (it.titleFa + it.summaryFa).contains(query.trim(), ignoreCase = true) }
                if (results.isEmpty()) QuietInfo("مطلبی پیدا نشد؛ عبارت کوتاه‌تری امتحان کن.")
                results.forEach { item -> HealthInsightCard(item) { onHealthInsight(item.id) } }
            } else {
                SectionLabel("موضوع‌ها")
                if (pregnancyTopic) {
                    listOf("رشد هفته‌به‌هفته" to "preg_week_$week", "تغییرات بدن مادر" to "preg_mother_$week", "تغذیه و فعالیت" to "preg_nutrition", "مراقبت‌ها و مراجعه‌ها" to "preg_care", "سلامت روان" to "preg_mental", "راهنمای پدر" to "preg_father_$week", "آمادگی تولد" to "preg_birth", "علائم هشدار" to "preg_warning").chunked(2).forEach { row ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (label,id) ->
                                MahavaCard(Modifier.weight(1f).fillMaxHeight().clickable { onHealthInsight(id) }, containerColor = MahavaPrimarySoft.copy(alpha = 0.45f)) {
                                    MahavaIcon(R.drawable.design_sprout)
                                    Spacer(Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                } else {
                    val cats = listOf(
                        Triple(BodyCategories.CYCLE_HORMONES, R.drawable.design_cycle, "چرخه و هورمون‌ها"),
                        Triple(BodyCategories.PERIOD_PAIN, R.drawable.design_droplet, "پریود و درد"),
                        Triple(BodyCategories.SLEEP_MOOD, R.drawable.design_moon, "خواب و حال روحی"),
                        Triple(BodyCategories.FERTILITY_DISCHARGE, R.drawable.design_sprout, "باروری و ترشحات"),
                        Triple(BodyCategories.DIGESTION_SKIN, R.drawable.design_heart, "پوست و گوارش"),
                        Triple(BodyCategories.PATTERN_CARE, R.drawable.design_leaf, "تغذیه و حرکت")
                    )
                    val colors = listOf(MahavaPrimarySoft, MahavaMenstruationSoft, androidx.compose.ui.graphics.Color(0xFFFFF0E7), androidx.compose.ui.graphics.Color(0xFFEAF4EF))
                    cats.chunked(2).forEachIndexed { i,row ->
                        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEachIndexed { j,(id,icon,label) ->
                                MahavaCard(Modifier.weight(1f).fillMaxHeight().testTag("cat_$id").clickable { onCategory(id) }, containerColor = colors[(i*2+j)%colors.size].copy(alpha = 0.6f)) {
                                    MahavaIcon(icon)
                                    Spacer(Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                SectionLabel("پیشنهاد برای تو")
                MahavaCard(Modifier.clickable { if (pregnancyTopic) onHealthInsight("preg_mother_$week") else onCategory(BodyCategories.PATTERN_CARE) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { SectionLabel("ثبت علائم چه کمکی می‌کند؟"); QuietInfo("آشنایی با الگوی خودت، از روی ثبت‌های واقعی") }
                        Illustration(R.drawable.design_woman_botanical, Modifier.width(100.dp).height(85.dp))
                    }
                }
            }
            MahavaCard(Modifier.testTag("care_guide_card").clickable { if (pregnancyTopic) onHealthInsight("preg_warning") else onCare() }) {
                CompanionAction("علائم هشدار و زمان مراجعه", "چه زمانی بهتر است بررسی شوی؟", R.drawable.design_warning) { if (pregnancyTopic) onHealthInsight("preg_warning") else onCare() }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun BodyCategoryScreen(
    vm: AppViewModel,
    category: String,
    onBack: () -> Unit,
    onAccount: () -> Unit = {},
    onItem: (String) -> Unit
) {
    val premium by vm.isPremium.collectAsState()
    if (!premium) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("category_screen")) {
            ScreenHeader("شناخت بدن", onBack = onBack)
            PremiumPaywallCard(
                vm = vm,
                titleFa = "مطالب شناخت بدن",
                benefitFa = "با اشتراک ماه باز می‌شود.",
                onOpenAccount = onAccount
            )
        }
        return
    }
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
