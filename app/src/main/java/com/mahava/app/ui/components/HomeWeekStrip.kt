package com.mahava.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import com.mahava.app.util.JalaliDate
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

@Composable
fun HomeWeekStrip(today: LocalDate, onCalendar: () -> Unit) {
    val saturday = today.minusDays(((today.dayOfWeek.value + 1) % 7).toLong())
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    Row(Modifier.fillMaxWidth().testTag("home_week_strip"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(7) { index ->
            val date = saturday.plusDays(index.toLong())
            val selected = date == today
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = onCalendar),
                shape = RoundedCornerShape(18.dp),
                color = if (selected) MahavaPrimary else Color.Transparent
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(labels[index], style = MaterialTheme.typography.bodySmall,
                        color = if (selected) Color.White else MahavaTextSecondary)
                    Text(PersianDigits.toPersian(JalaliDate.from(date).day),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}
