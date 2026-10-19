package com.mahava.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.R
import com.mahava.app.ui.components.DatePickerMath
import com.mahava.app.ui.components.LocalUseJalali
import com.mahava.app.ui.components.MonthDatePickerDialog
import com.mahava.app.util.PersianDigits
import java.time.LocalDate

/** Section title of the kit log screens (bold ink, optional info icon). */
@Composable
fun KitSectionTitle(text: String, info: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Kit.cardTitle.copy(fontSize = 16.sp))
        if (info) { Spacer(Modifier.width(8.dp)); Icon(painterResource(R.drawable.kit_info), null, tint = Kit.muted, modifier = Modifier.size(18.dp)) }
    }
}

/** Centred date line under the kit log header; tap to pick a day. */
@Composable
fun KitDateRow(date: LocalDate, max: LocalDate, min: LocalDate, onChange: (LocalDate) -> Unit, extra: String? = null) {
    val jalali = LocalUseJalali.current
    val picking = remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.clip(CircleShape).clickable { picking.value = true }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("date_pick"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.kit_calendar), null, tint = Kit.ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(DatePickerMath.formatFa(date, jalali), style = Kit.body.copy(color = Kit.ink), modifier = Modifier.testTag("date_value"))
        }
        if (extra != null) Text(extra, style = Kit.small, textAlign = TextAlign.Center)
    }
    if (picking.value) MonthDatePickerDialog(date, min, max, onPick = { onChange(if (it.isAfter(max)) max else if (it.isBefore(min)) min else it); picking.value = false }, onDismiss = { picking.value = false })
}

/** Selectable tile (faces, bleeding levels): soft fill, pink 1.5dp border + soft pink when on. */
@Composable
fun KitOptionTile(label: String, on: Boolean, bg: Color, tag: String, modifier: Modifier = Modifier, art: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        modifier.height(78.dp).clip(RoundedCornerShape(16.dp)).background(if (on) Kit.softPink else bg)
            .border(if (on) 1.5.dp else 0.dp, if (on) Kit.pink else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { selected = on }.testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        art()
        Spacer(Modifier.height(6.dp))
        Text(label, style = Kit.label.copy(fontSize = 13.sp, color = if (on) Kit.ink else Kit.ink), maxLines = 1, softWrap = false)
    }
}

@Composable
fun KitDrops(n: Int, tint: Color = Kit.pink) = Row {
    repeat(n) { Icon(painterResource(R.drawable.kit_drop_filled), null, tint = tint, modifier = Modifier.size(16.dp)) }
}

/** Outlined symptom chip with a small icon; selected = soft pink fill and pink border. */
@Composable
fun KitSymptomChip(label: String, @DrawableRes icon: Int?, on: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color(0xFFFCE4EE) else Color.White)
            .border(1.dp, if (on) Kit.pink else Kit.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { selected = on }.testTag(tag).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) { Icon(painterResource(icon), null, tint = Kit.pink, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(label, style = Kit.body.copy(color = Kit.ink, fontSize = 13.sp), maxLines = 1, softWrap = false)
    }
}

/** «مدت خواب دیشب» / «میزان آب امروز» card: title, − value + , unit. */
@Composable
fun KitStepperCard(title: String, value: String, unit: String, @DrawableRes icon: Int, iconTint: Color, tag: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Kit.cardRadius)).background(Kit.cardLavender).padding(horizontal = 10.dp, vertical = 12.dp).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = Kit.label.copy(fontSize = 13.sp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitRoundStep(R.drawable.kit_plus, "${tag}_plus", onPlus)
            Spacer(Modifier.weight(1f))
            Text(value, style = Kit.title.copy(fontSize = 20.sp))
            Spacer(Modifier.width(6.dp))
            Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.weight(1f))
            KitRoundStep(R.drawable.kit_minus, "${tag}_minus", onMinus)
        }
        Text(unit, style = Kit.small)
    }
}

@Composable
private fun KitRoundStep(@DrawableRes icon: Int, tag: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White).clickable(role = Role.Button, onClick = onClick).testTag(tag), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = Kit.pink, modifier = Modifier.size(18.dp))
    }
}

fun faFmt(v: Float): String = PersianDigits.toPersian(if (v % 1f == 0f) v.toInt().toString() else v.toString())
