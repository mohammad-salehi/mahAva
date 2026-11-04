package com.mahava.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahava.app.content.BeautyRoutineContent
import com.mahava.app.content.BeautySection
import com.mahava.app.health.models.AppHealthMode
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.*
import com.mahava.app.ui.theme.*

@Composable
fun BeautyRoutineScreen(vm: AppViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val pregnant = state.health?.appMode == AppHealthMode.PREGNANCY
    val group = com.mahava.app.content.TodaySignalContent.effectivePhaseGroup(
        ctx.subWindow, state.cycle?.phase, state.profile?.hormonalContraception == true, state.profile?.regularCycles
    )
    val r = BeautyRoutineContent.forState(group, pregnant)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MahavaBackground).verticalScroll(rememberScrollState()).padding(16.dp).testTag("beauty_routine_screen")) {
        ScreenHeader("روتین پوست و مو", onBack = onBack)
        QuietInfo(r.phaseTitleFa)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MahavaPrimarySoft).padding(4.dp)) {
            listOf("پوست", "مو").forEachIndexed { i, label ->
                val sel = tab == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (sel) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 10.dp).testTag(if (i == 0) "beauty_tab_skin" else "beauty_tab_hair"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = if (sel) MahavaPrimary else MahavaTextSecondary)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BeautySectionView(if (tab == 0) r.skin else r.hair)
        Spacer(Modifier.height(8.dp))
        MahavaCard {
            Text(BeautyRoutineContent.BASE_FA, style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(r.disclaimerFa, style = MaterialTheme.typography.bodySmall, color = MahavaTextSecondary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BeautySectionView(s: BeautySection) {
    MahavaCard { Text(s.summaryFa) }
    BeautyList("صبح", s.morningFa)
    BeautyList("شب", s.nightFa)
    BeautyList("انجام بده", s.doFa)
    BeautyList("پرهیز", s.avoidFa)
    Spacer(Modifier.height(8.dp))
    MahavaCard {
        Text("چرا", style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
        Spacer(Modifier.height(4.dp))
        Text(s.whyFa)
    }
}

@Composable
private fun BeautyList(title: String, items: List<String>) {
    if (items.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    MahavaCard {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MahavaPrimary)
        Spacer(Modifier.height(4.dp))
        items.forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}
