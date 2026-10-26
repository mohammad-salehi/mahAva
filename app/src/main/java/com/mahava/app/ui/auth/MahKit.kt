package com.mahava.app.ui.auth

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.core.view.WindowCompat
import com.mahava.app.R
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle

// ---- Design tokens of the approved Mah auth/intro redesign ----
internal object MK {
    val Ink = Color(0xFF23243E)
    val Muted = Color(0xFF66687D)
    val Pink = Color(0xFFF34E88)
    val Deep = Color(0xFFCC255F)
    val Soft = Color(0xFFFFF0F6)
    val Lav = Color(0xFF8F83ED)
    val LavSoft = Color(0xFFF0EDFF)
    val Fert = Color(0xFF54B99E)
    val Green = Color(0xFF3C9A80)
    val Border = Color(0xFFECEBF4)
    val Label = Color(0xFF45465E)
    val IconGray = Color(0xFFA09DB5)
    val Placeholder = Color(0xFFB2B0C4)
    val FieldBg = Color(0xFFFCFBFE)
    val Track = Color(0xFFEEE9F6)
    val Dot = Color(0xFFE7E2F3)
    val Error = Color(0xFFC8344A)
    val TopGradient = cssLinear(160f, 0f to Color(0xFFFFE3EE), 0.45f to Color(0xFFFFF0F6), 1f to Color(0xFFF0EDFF))
    val BtnGradient = cssLinear(135f, 0f to Color(0xFFF34E88), 0.5f to Color(0xFFE8407D), 1f to Color(0xFFCC255F))
}

internal val MahFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

/** CSS `line-height: normal` for Vazirmatn (hhea 2100/-1100 @2048, each part rounded like Chrome). */
/** Vazirmatn Bold with Latin digits, for codes such as a partner invite code. */
internal val MahLatinBold = FontFamily(Font(R.font.vazirmatn_latin_bold, FontWeight.Bold))

internal fun cssNormalLine(size: Float): Float =
    (size * 2100f / 2048f).roundToInt().toFloat() + (size * 1100f / 2048f).roundToInt().toFloat()

internal fun mStyle(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = MK.Ink,
    line: Float? = null,
    align: TextAlign = TextAlign.Unspecified,
    letter: Float = 0f
) = TextStyle(
    fontFamily = MahFont,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    lineHeight = (line ?: cssNormalLine(size)).sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    textAlign = align,
    letterSpacing = letter.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    // Unhinted, sub-pixel glyph advances like the browser that drew the mockups.
    textMotion = TextMotion.Animated
)

@Composable
internal fun MText(
    text: String,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = MK.Ink,
    line: Float? = null,
    align: TextAlign = TextAlign.Unspecified,
    modifier: Modifier = Modifier
) = Text(text, modifier = modifier.cssLineTrim(size, line), style = mStyle(size, weight, color, line, align))

@Composable
internal fun MText(
    text: AnnotatedString,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = MK.Ink,
    line: Float? = null,
    align: TextAlign = TextAlign.Unspecified,
    modifier: Modifier = Modifier
) = Text(text, modifier = modifier.cssLineTrim(size, line), style = mStyle(size, weight, color, line, align))

/**
 * Compose never makes the first/last line shorter than the font's own ascent+descent, while CSS
 * does when `line-height` is below `normal`. Trim that surplus (half above, half below) so a
 * 22px/1.3 heading occupies exactly the mockup's line box.
 */
internal fun Modifier.cssLineTrim(size: Float, line: Float?): Modifier {
    // With `normal` line height CSS rounds ascent/descent separately; Compose keeps the unrounded sum.
    val surplus = if (line == null) size * 3200f / 2048f - cssNormalLine(size) else cssNormalLine(size) - line
    if (surplus <= 0f) return this
    return this.layout { measurable, constraints ->
        val p = measurable.measure(constraints)
        val cut = surplus.sp.toPx()
        val top = (cut / 2f).roundToInt()
        val h = (p.height - cut).roundToInt().coerceAtLeast(0)
        layout(p.width, h) { p.place(0, -top) }
    }
}

/** Lets render tests show a field in its focused look without a blinking cursor. */
internal val LocalPreviewFocus = compositionLocalOf<String?> { null }

/** Status-bar band height used by the mockups (40px) or the real inset if taller. */
@Composable
internal fun designTopInset(): Dp {
    val d = LocalDensity.current
    val sb = with(d) { WindowInsets.statusBars.getTop(d).toDp() }
    return if (sb > 40.dp) sb else 40.dp
}

/** One absolutely positioned layer of a screen; `top` and `bottom` are mockup px (= dp). */
internal class Layer(
    val top: Dp? = null,
    val bottom: Dp? = null,
    /** Keep below the previous top layer (never overlap it when it grows). */
    val pushDown: Boolean = false,
    val content: @Composable () -> Unit
)

/**
 * Places layers like the mockup's `position:absolute` blocks on a 390x844 screen.
 * The frame is at least the viewport tall and grows (scrolls) instead of overlapping.
 */
@Composable
internal fun MockFrame(viewport: Dp, layers: List<Layer>, minGap: Dp = 12.dp) {
    Layout(contents = layers.map { it.content }) { measurables, constraints ->
        val w = constraints.maxWidth
        val c = Constraints(minWidth = w, maxWidth = w)
        val placeables = measurables.map { list -> list.map { it.measure(c) } }
        val gap = minGap.roundToPx()
        val tops = IntArray(layers.size)
        var topExtent = 0
        var bottomExtent = 0
        var lastBottom = 0
        layers.forEachIndexed { i, l ->
            val h = placeables[i].maxOfOrNull { it.height } ?: 0
            if (l.top != null) {
                var y = l.top.roundToPx()
                if (l.pushDown) y = maxOf(y, lastBottom + gap)
                tops[i] = y
                lastBottom = y + h
                topExtent = maxOf(topExtent, y + h)
            }
            if (l.bottom != null) bottomExtent = maxOf(bottomExtent, l.bottom.roundToPx() + h)
        }
        val needed = topExtent + (if (bottomExtent > 0) gap + bottomExtent else 0)
        val height = maxOf(viewport.roundToPx(), needed)
        layout(w, height) {
            layers.forEachIndexed { i, l ->
                placeables[i].forEach { p ->
                    val y = if (l.top != null) tops[i] else height - (l.bottom ?: 0.dp).roundToPx() - p.height
                    p.place(0, y)
                }
            }
        }
    }
}

/** Scrollable, keyboard-aware screen made of [Layer]s over [background]. */
@Composable
internal fun MockScreen(background: Color, layers: (topShift: Dp) -> List<Layer>, behind: @Composable BoxScope.() -> Unit = {}) {
    val topShift = designTopInset() - 40.dp
    Box(Modifier.fillMaxSize().background(background)) {
        behind()
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val d = LocalDensity.current
            val nav = with(d) { WindowInsets.navigationBars.getBottom(d).toDp() }
            val vh = maxHeight - nav
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MockFrame(vh, layers(topShift))
            }
        }
    }
}

/** CSS negative `margin-top`: draws [by] higher and takes [by] less height. */
internal fun Modifier.pullUp(by: Dp): Modifier = layout { m, c ->
    val p = m.measure(c)
    val d = by.roundToPx()
    layout(p.width, (p.height - d).coerceAtLeast(0)) { p.place(0, -d) }
}

// ---------------------------------------------------------------- shared pieces

internal fun lineOf(size: Float, factor: Float) = size * factor

/** Brand app icon tile (`.mlogo`). */
@Composable
internal fun MahLogo(size: Dp, radius: Dp, shadow: Boolean = true) {
    val shape = RoundedCornerShape(radius)
    Box(
        Modifier.size(size)
            .then(if (shadow) Modifier.cssShadow(MK.Lav.copy(alpha = 0.35f), blur = 16.dp, y = 6.dp, radius = radius) else Modifier)
            .clip(shape)
    ) {
        Image(
            painterResource(R.drawable.mah_brand_icon), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
    }
}

/** Soft radial blob (`.blob`), positioned by physical left/right/top/bottom inside a Box. */
@Composable
internal fun BoxScope.MahBlob(size: Dp, color: Color, alpha: Float, top: Dp? = null, bottom: Dp? = null, left: Dp? = null, right: Dp? = null) {
    val align = when {
        top != null && left != null -> AbsoluteAlignment.TopLeft
        top != null -> AbsoluteAlignment.TopRight
        left != null -> AbsoluteAlignment.BottomLeft
        else -> AbsoluteAlignment.BottomRight
    }
    Box(
        Modifier.align(align)
            .absoluteOffset(
                x = if (left != null) left else -(right ?: 0.dp),
                y = if (top != null) top else -(bottom ?: 0.dp)
            )
            .size(size)
            .background(cssRadialCircle(color.copy(alpha = alpha), color.copy(alpha = 0f)), CircleShape)
    )
}

/** Gradient top panel (`.top`) of [height] mockup px plus the extra status-bar shift. */
@Composable
internal fun MahTop(height: Dp, topShift: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(height + topShift)
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(MK.TopGradient),
        content = content
    )
}

@Composable
internal fun MahBack(onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(42.dp)
            .cssShadow(MK.Ink.copy(alpha = 0.08f), blur = 12.dp, y = 4.dp, radius = 14.dp)
            .clip(shape).background(Color.White).clickable(onClick = onClick)
            .testTag("auth_back"),
        contentAlignment = Alignment.Center
    ) { Feather("back", 20.dp, MK.Ink) }
}

/** `.steps`: 3 bars, [active] is wide pink, earlier ones done. */
@Composable
internal fun MahSteps(active: Int, count: Int = 3) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier.width(if (i == active) 40.dp else 24.dp).height(6.dp).clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            i == active -> MK.Pink
                            i < active -> Color(0xFFF9A8C7)
                            else -> MK.Track
                        }
                    )
            )
        }
    }
}

internal enum class MahArt { WomanBotanical, Shield, RestWoman }

/**
 * Step screens' 290px header: back button + steps, title block on the right, illustration on the left.
 */
@Composable
internal fun MahStepHeader(topShift: Dp, step: Int, onBack: () -> Unit, title: String, sub: AnnotatedString, art: MahArt) {
    MahTop(290.dp, topShift) {
        MahBlob(200.dp, MK.Pink, 0.18f, top = (-30).dp, left = (-40).dp)
        when (art) {
            MahArt.WomanBotanical -> Image(
                painterResource(R.drawable.design_woman_botanical), null,
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = 8.dp).height(185.dp).width((185f * 362f / 500f).dp)
            )
            MahArt.Shield -> Image(
                painterResource(R.drawable.ill_privacy_shield), null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = (-54).dp, y = (-8).dp).width(240.dp).height((240f * 887f / 1774f).dp)
            )
            MahArt.RestWoman -> Unit
        }
        Row(
            Modifier.padding(top = 40.dp + topShift).fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MahBack(onBack)
            MahSteps(step)
            Spacer(Modifier.width(42.dp))
        }
        MahTitleBlock(title, sub, Modifier.padding(top = 124.dp + topShift, start = 24.dp, end = 180.dp))
    }
}

@Composable
internal fun MahTitleBlock(title: String, sub: AnnotatedString, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        MText(title, 25f, FontWeight.Bold, line = 37.5f)
        Spacer(Modifier.height(4.dp))
        MText(sub, 14f, color = MK.Muted, line = 25.9f)
    }
}

@Composable
internal fun MahLabel(text: String) =
    MText(text, 13f, FontWeight.SemiBold, MK.Label, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 7.dp))

internal enum class MahFieldKind { Phone, Password }

@Composable
internal fun MahField(
    value: String,
    onValueChange: (String) -> Unit,
    kind: MahFieldKind,
    placeholder: String,
    enabled: Boolean = true,
    previewKey: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
    testTag: String? = null,
    bottom: Dp = 14.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focusedReal by interaction.collectIsFocusedAsState()
    val focused = focusedReal || (previewKey != null && LocalPreviewFocus.current == previewKey)
    var visible by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val hidden = kind == MahFieldKind.Password && !visible && value.isNotEmpty()
    val textStyle = when {
        hidden -> mStyle(20f, FontWeight.Medium, MK.Ink, letter = 3f)
        kind == MahFieldKind.Phone -> mStyle(15f, FontWeight.Medium, MK.Ink).copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Right)
        else -> mStyle(15f, FontWeight.Medium, MK.Ink)
    }
    Box(Modifier.padding(bottom = bottom)) {
        Row(
            Modifier.fillMaxWidth().height(54.dp)
                .then(if (focused) Modifier.cssRing(MK.Pink.copy(alpha = 0.12f), 4.dp, 18.dp) else Modifier)
                .clip(shape)
                .background(if (focused) Color.White else MK.FieldBg)
                .border(1.5.dp, if (focused) MK.Pink else MK.Border, shape)
                .padding(horizontal = 17.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Feather(if (kind == MahFieldKind.Phone) "phone" else "lock", 20.dp, MK.IconGray)
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = value,
                    onValueChange = { raw -> onValueChange(if (kind == MahFieldKind.Phone) digitsOnly(raw, 11) else raw) },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MK.Pink),
                    interactionSource = interaction,
                    visualTransformation = when {
                        kind == MahFieldKind.Phone -> PhoneGroupingTransformation
                        hidden -> PasswordVisualTransformation('•')
                        else -> VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (kind == MahFieldKind.Phone) KeyboardType.Phone else KeyboardType.Password,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(onAny = { onIme() }),
                    modifier = Modifier.fillMaxWidth().then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
                    decorationBox = { inner ->
                        Box(contentAlignment = if (kind == MahFieldKind.Phone) AbsoluteAlignment.CenterRight else Alignment.CenterStart) {
                            if (value.isEmpty()) MText(placeholder, 15f, color = MK.Placeholder, modifier = Modifier.fillMaxWidth())
                            inner()
                        }
                    }
                )
            }
            if (kind == MahFieldKind.Password) {
                Box(Modifier.clip(CircleShape).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                    Feather("eye", 20.dp, MK.IconGray)
                }
            }
        }
    }
}

@Composable
internal fun MahButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    arrow: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val on = enabled
    Row(
        modifier.height(56.dp)
            .then(if (on) Modifier.cssShadow(MK.Pink.copy(alpha = 0.35f), blur = 26.dp, y = 12.dp, radius = 20.dp) else Modifier)
            .clip(shape)
            .then(if (on) Modifier.background(MK.BtnGradient) else Modifier.background(Color(0xFFF3D2DF)))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MText(text, 16f, FontWeight.Bold, Color.White)
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else if (arrow) Feather("next", 20.dp, Color.White)
    }
}

@Composable
internal fun MahButton2(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(shape).background(Color(0xFFF7F5FF))
            .border(1.5.dp, Color(0xFFE4DFFB), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { MText(text, 15f, FontWeight.SemiBold, Color(0xFF6E62D9)) }
}

/** Six-box OTP (LTR) driven by one hidden text field. */
@Composable
internal fun MahOtp(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    previewCursor: Boolean = false,
    testTag: String? = null,
    onDone: () -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val focusedReal by interaction.collectIsFocusedAsState()
    val focused = focusedReal || previewCursor
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(digitsOnly(it, 6)) },
        enabled = enabled,
        singleLine = true,
        interactionSource = interaction,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        decorationBox = { _ ->
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(6) { i ->
                        val ch = value.getOrNull(i)
                        val cur = focused && i == value.length
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier.size(47.dp, 58.dp)
                                .then(if (cur) Modifier.cssRing(MK.Pink.copy(alpha = 0.12f), 4.dp, 16.dp) else Modifier)
                                .clip(shape)
                                .background(when { cur -> Color.White; ch != null -> Color(0xFFFFF5F9); else -> MK.FieldBg })
                                .border(1.5.dp, when { cur -> MK.Pink; ch != null -> Color(0xFFF7C6D9); else -> MK.Border }, shape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ch != null) MText(faDigits(ch.toString()), 24f, FontWeight.Bold, MK.Deep)
                            else if (cur) Box(Modifier.size(2.dp, 26.dp).clip(RoundedCornerShape(2.dp)).background(MK.Pink))
                        }
                    }
                }
            }
        }
    )
}

/** "کد نرسید؟ … ارسال دوباره (n)" row under the OTP boxes. */
@Composable
internal fun MahResendRow(left: Int, busy: Boolean, testTag: String?, onResend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        MText("کد نرسید؟", 13f, color = MK.Muted)
        val can = left <= 0 && !busy
        MText(
            if (left > 0) "ارسال دوباره (${faDigits(if (left < 100) left.toString() else "%d:%02d".format(left / 60, left % 60))})" else "ارسال دوباره کد",
            13f, FontWeight.SemiBold, if (left > 0) MK.Muted else MK.Pink,
            modifier = Modifier.clickable(enabled = can, onClick = onResend)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
internal fun MahStrength(password: String) {
    val score = passwordScore(password)
    Row(
        Modifier.fillMaxWidth().pullUp(6.dp).padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(4) { i ->
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(4.dp)).background(if (i < score) MK.Fert else MK.Track))
        }
    }
}

/** "✓ حداقل ۶ کاراکتر" hint; green once satisfied. */
@Composable
internal fun MahMinHint(ok: Boolean, modifier: Modifier) {
    val c = if (ok) MK.Green else MK.Muted
    Row(modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Feather("check", 14.dp, c, strokeWidth = 3f)
        MText("حداقل ۶ کاراکتر", 13f, color = c)
    }
}

/** `.chk` square check (filled pink when on). */
@Composable
internal fun MahCheck(checked: Boolean, size: Dp = 22.dp, radius: Dp = 7.dp, color: Color = MK.Pink, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.size(size).clip(shape)
            .then(if (checked) Modifier.background(color) else Modifier.background(Color.White).border(1.5.dp, Color(0xFFD9D6E6), shape)),
        contentAlignment = Alignment.Center
    ) { if (checked) Feather("check", 14.dp, Color.White, strokeWidth = 3f) }
}

/** `.note`: lavender info line with an icon. */
@Composable
internal fun MahNote(text: String, icon: String = "shield", bottom: Dp = 20.dp) {
    Row(
        Modifier.padding(bottom = bottom).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MK.LavSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Feather(icon, 18.dp, MK.Lav)
        MText(text, 12.5f, color = Color(0xFF4B4770), line = 23.125f)
    }
}

@Composable
internal fun MahError(text: String?) {
    if (text.isNullOrBlank()) return
    MText(text, 12.5f, color = MK.Error, modifier = Modifier.pullUp(4.dp).padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
}

@Composable
internal fun MahInfo(text: String?) {
    if (text.isNullOrBlank()) return
    MText(text, 12.5f, color = MK.Deep, modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
}

@Composable
internal fun MahLink(text: String, size: Float = 14f, modifier: Modifier = Modifier, onClick: () -> Unit) =
    MText(text, size, FontWeight.SemiBold, MK.Pink, modifier = modifier.clickable(onClick = onClick))

/** Centered "question + link" footer (`.foot`). */
@Composable
internal fun MahFootLink(question: String?, link: String, testTag: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (question != null) MText("$question ", 14f, color = MK.Muted)
        MahLink(link, modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier, onClick = onClick)
    }
}

/** White floating card (`box-shadow:0 12px 28px rgba(35,36,62,.12)`), padding 10/12. */
@Composable
internal fun MahFloatCard(modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.cssShadow(MK.Ink.copy(alpha = 0.12f), blur = 28.dp, y = 12.dp, radius = 18.dp)
            .clip(shape).background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp)
    ) { content() }
}

@Composable
internal fun MahIconTile(icon: String, bg: Color, fg: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
        Feather(icon, 16.dp, fg)
    }
}
