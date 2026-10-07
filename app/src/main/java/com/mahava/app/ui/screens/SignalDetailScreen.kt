package com.mahava.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahava.app.content.ScienceSources
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.components.MahavaCard
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaPrimarySoft
import com.mahava.app.ui.theme.MahavaTextSecondary

/** Full, sourced explanation for one pick from the home check-in card. */
@Composable
fun SignalDetailScreen(vm: AppViewModel, kind: String, key: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val ctx = vm.dayContext()
    val profile = state.profile
    val phaseGroup = TodaySignalContent.effectivePhaseGroup(
        ctx.subWindow, state.cycle?.phase,
        hormonalContraception = profile?.hormonalContraception == true,
        regularCycles = profile?.regularCycles
    )
    val ex = TodaySignalContent.explainFull(kind, key, phaseGroup)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("signal_detail_screen")) {
        ScreenHeader(TodaySignalContent.label(kind, key), onBack = onBack)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietInfo(
                TodaySignalContent.kindTitleFa(kind) + " · " +
                    if (phaseGroup == "general") "توضیح کلی" else "امروز: ${ctx.subWindow.titleFa}"
            )
            Surface(shape = RoundedCornerShape(16.dp), color = MahavaPrimarySoft, modifier = Modifier.fillMaxWidth()) {
                Text(
                    ex.teaserFa,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MahavaPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            val personal = vm.personalSignal(kind, key)
            MahavaCard(Modifier.testTag("signal_personal_card")) {
                Text("بر اساس الگوی خودت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaPrimary)
                Spacer(Modifier.height(4.dp))
                if (personal != null) {
                    Text(personal.textFa, style = MaterialTheme.typography.bodyLarge)
                    if (personal.dominantPhase != null && personal.dominantPhase == phaseGroup) {
                        Spacer(Modifier.height(4.dp))
                        Text("امروز هم در همین بازه‌ای.", style = MaterialTheme.typography.bodyMedium, color = MahavaPrimary)
                    }
                    Spacer(Modifier.height(4.dp))
                    QuietInfo("از روی ثبت‌های خودت در ${com.mahava.app.util.PersianDigits.toPersian(personal.cyclesChecked)} چرخهٔ کامل اخیر. این الگو علت را نشان نمی‌دهد.")
                } else {
                    Text(com.mahava.app.pattern.PersonalPatternEngine.NOT_ENOUGH_FA, style = MaterialTheme.typography.bodyMedium)
                    if (vm.completeCycleCount() >= com.mahava.app.pattern.PersonalPatternEngine.MIN_COMPLETE_CYCLES) {
                        QuietInfo("چرخهٔ کافی ثبت کرده‌ای، ولی این مورد را هنوز دست‌کم در ۲ چرخه ثبت نکرده‌ای.")
                    }
                }
            }
            MahavaCard {
                Text("این روزها در بدنت", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaPrimary)
                Spacer(Modifier.height(4.dp))
                Text(TodaySignalContent.bodyTodayFa(phaseGroup), style = MaterialTheme.typography.bodyLarge)
            }
            ex.sections.forEach { section ->
                MahavaCard {
                    Text(section.titleFa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MahavaPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(section.bodyFa, style = MaterialTheme.typography.bodyLarge)
                }
            }
            EvidenceCard(ex.evidence)
            SourcesCard(ex.sourceIds)
            QuietInfo(TodaySignalContent.cautionFa)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun EvidenceCard(evidence: ScienceSources.Evidence) {
    MahavaCard(Modifier.testTag("evidence_card")) {
        Text(evidence.titleFa, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        QuietInfo(evidence.noteFa)
    }
}

/** Short source names; each opens the page in the browser. */
@Composable
fun SourcesCard(ids: List<String>) {
    val sources = ScienceSources.list(ids)
    if (sources.isEmpty()) return
    val uri = LocalUriHandler.current
    MahavaCard(Modifier.testTag("sources_card")) {
        Text("منبع‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        QuietInfo("برای خواندن بیشتر روی هر منبع بزن.")
        Spacer(Modifier.height(6.dp))
        sources.forEach { s ->
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { runCatching { uri.openUri(s.url) } }
                    .padding(vertical = 6.dp)
            ) {
                Text(s.labelFa, color = MahavaPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(s.host, color = MahavaTextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
