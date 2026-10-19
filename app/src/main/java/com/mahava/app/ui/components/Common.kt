package com.mahava.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.mahava.app.ui.theme.*

@Composable
fun MahavaCard(
    modifier: Modifier = Modifier,
    accent: Color = MahavaPrimary,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    // Flat fill only — Material Surface elevation/outline looked like a thick border on device.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .border(1.dp, MahavaBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MahavaPrimary, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(color = Color.White))
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MahavaPrimaryDeep,
            containerColor = MahavaPrimarySoft.copy(alpha = 0.55f)
        )
    ) { Text(text) }
}

@Composable
fun MahavaIcon(@DrawableRes res: Int, tint: Color = MahavaPrimary, size: Dp = 24.dp) {
    Icon(
        painter = painterResource(res),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

/**
 * "More / details" link: the text, then a chevron pointing LEFT (forward in this RTL app).
 * Never put "‹" / ">" characters in Persian text: bidi mirroring flips them the wrong way.
 */
@Composable
fun MoreLink(
    text: String,
    color: Color = MahavaPrimaryDeep,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = color, style = style, fontWeight = fontWeight)
        Spacer(Modifier.width(2.dp))
        MahavaIcon(com.mahava.app.R.drawable.design_chevron_left, color, 18.dp)
    }
}

@Composable
fun Illustration(@DrawableRes res: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.fillMaxWidth(),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, onSettings: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                MahavaIcon(com.mahava.app.R.drawable.ic_chevron_right, MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(com.mahava.app.R.drawable.ic_settings), contentDescription = "تنظیمات", tint = MahavaTextPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    selectedColor: Color = MahavaPrimary,
    tag: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Selected = solid colour with white text, so the choice is obvious at a glance.
    val bg = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, if (selected) selectedColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, softWrap = false)
    }
}

/** Small label for one question inside a [FormSection]. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
}

/** A titled, collapsible group of related choices (tap the title to fold it). */
@Composable
fun FormSection(title: String, modifier: Modifier = Modifier, initiallyOpen: Boolean = true, tag: String? = null,
                content: @Composable ColumnScope.() -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(initiallyOpen) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = MahavaPrimaryDeep)
        }
        if (open) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp), content = content)
    }
}

/** A 1..n scale on one line: equal-width segments instead of wrapping chips. */
@Composable
fun ScaleRow(options: List<Pair<String, String>>, selected: String?, tagPrefix: String? = null, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (k, label) ->
            ChoiceChipPill(label, selected == k, tag = tagPrefix?.let { "${it}_$k" }, modifier = Modifier.weight(1f)) { onSelect(k) }
        }
    }
}
@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun QuietInfo(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Bullet list; accepts either a list or a text with "• " lines. */
@Composable
fun Bullets(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.filter { it.isNotBlank() }.forEach { line ->
            Row {
                Text("•  ", style = MaterialTheme.typography.bodyLarge, color = MahavaPrimary)
                Text(line.removePrefix("• ").trim(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

fun splitBullets(text: String?): List<String> =
    text?.split('\n')?.map { it.trim() }?.filter { it.isNotBlank() }?.map { it.removePrefix("•").trim() } ?: emptyList()

/** Chips that wrap onto new lines (works with large font sizes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsFlow(options: List<Pair<String, String>>, selected: String?, tagPrefix: String? = null, onSelect: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (k, label) -> ChoiceChipPill(label, selected == k, tag = tagPrefix?.let { "${it}_$k" }) { onSelect(k) } }
    }
}

/** Multi-select variant. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChipsFlow(options: List<Pair<String, String>>, selected: Set<String>, tagPrefix: String? = null, onToggle: (String) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (k, label) -> ChoiceChipPill(label, k in selected, tag = tagPrefix?.let { "${it}_$k" }) { onToggle(k) } }
    }
}

/**
 * Date chooser: shows the date (Shamsi or Gregorian, per settings) and a large
 * "pick from calendar" button that opens the Jalali/Gregorian month grid.
 * Dates after [max] or before [min] are not allowed.
 */
@Composable
fun DateStepper(
    date: java.time.LocalDate,
    max: java.time.LocalDate,
    min: java.time.LocalDate,
    onChange: (java.time.LocalDate) -> Unit,
    testTagPrefix: String = "date"
) {
    val jalali = LocalUseJalali.current
    val picking = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    fun set(d: java.time.LocalDate) { onChange(if (d.isAfter(max)) max else if (d.isBefore(min)) min else d) }
    OutlinedButton(onClick = { picking.value = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${testTagPrefix}_pick"),
        shape = RoundedCornerShape(16.dp), border = BorderStroke(0.dp, Color.Transparent),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF4F0F8), contentColor = MahavaTextPrimary)) {
        MahavaIcon(com.mahava.app.R.drawable.design_calendar, MahavaPrimaryDeep)
        Spacer(Modifier.width(10.dp))
        Text(DatePickerMath.formatFa(date, jalali), modifier = Modifier.weight(1f).testTag("${testTagPrefix}_value"))
        MahavaIcon(com.mahava.app.R.drawable.design_chevron_left, MahavaTextSecondary)
    }
    if (picking.value) MonthDatePickerDialog(date, min, max, onPick = { set(it); picking.value = false }, onDismiss = { picking.value = false })
}

