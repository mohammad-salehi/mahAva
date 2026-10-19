package com.mahava.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.content.FoodCravingKeys
import com.mahava.app.content.TodaySignalContent
import com.mahava.app.data.db.DailyLogEntity
import com.mahava.app.ui.theme.MahavaLavenderSoft
import com.mahava.app.ui.theme.MahavaPrimarySoft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayCheckIn(
    log: DailyLogEntity?,
    phaseGroup: String,
    onSave: (Set<String>, Set<String>, String?) -> Unit,
    onDetail: (String, String) -> Unit
) {
    val food = log?.foodCravings.orEmpty().split(',').filter(String::isNotBlank).toSet()
    val body = log?.physicalSymptoms.orEmpty().split(',').filter(String::isNotBlank).toSet()
    val mood = log?.moods
    var selectedFood by remember { mutableStateOf(food) }
    var selectedBody by remember { mutableStateOf(body) }
    var selectedMood by remember { mutableStateOf(mood) }
    LaunchedEffect(log?.foodCravings) { selectedFood = food }
    LaunchedEffect(log?.physicalSymptoms) { selectedBody = body }
    LaunchedEffect(log?.moods) { selectedMood = mood }
    var picked by remember { mutableStateOf<Pair<String, String>?>(null) }
    MahavaCard(Modifier.testTag("today_check_in"), containerColor = MahavaPrimarySoft) {
        Text("الان چی هوس کردی؟", style = MaterialTheme.typography.titleLarge)
        Text("هرچی به حالت می‌خوره بزن؛ می‌تونی چندتا انتخاب کنی.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FoodCravingKeys.ALL.forEach { (key, label) ->
                ChoiceChipPill(label, key in selectedFood, tag = "checkin_food_$key") {
                    selectedFood = if (key in selectedFood) selectedFood - key else selectedFood + key
                    onSave(selectedFood, selectedBody, selectedMood)
                    picked = "food" to key
                }
            }
        }
    }
    MahavaCard(Modifier.testTag("today_body_mood"), containerColor = MahavaLavenderSoft) {
        Text("بدنت امروز چطوره؟", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TodaySignalContent.physical.forEach { (key, label) ->
                ChoiceChipPill(label, key in selectedBody, tag = "checkin_body_$key") {
                    selectedBody = if (key in selectedBody) selectedBody - key else selectedBody + key
                    onSave(selectedFood, selectedBody, selectedMood)
                    picked = "body" to key
                }
            }
        }
    }
    picked?.let { (kind, key) ->
        val explanation = TodaySignalContent.explainFull(kind, key, phaseGroup)
        MahavaCard(Modifier.testTag("today_checkin_explanation"), containerColor = MahavaPrimarySoft) {
            Text("${TodaySignalContent.label(kind, key)}؛ چرا ممکنه پیش بیاد؟", style = MaterialTheme.typography.titleMedium)
            Text(explanation.teaserFa)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = { onDetail(kind, key) }) {
                Text("بیشتر بدون")
            }
        }
    }
}
