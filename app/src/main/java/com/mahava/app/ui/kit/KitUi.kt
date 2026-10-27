package com.mahava.app.ui.kit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.R
import com.mahava.app.ui.theme.*

/** Values measured from Mah-Redesign-Kit/designs (approved-home.png and sets A–E). */
object Kit {
    val ink = Color(0xFF23243E)
    val muted = Color(0xFF66687D)
    val pink = Color(0xFFF34E88)
    val pinkButton = Color(0xFFEF5B8D)
    val pinkLight = Color(0xFFF8B4D2)
    val lavender = Color(0xFF8F83ED)
    val softPink = Color(0xFFFFF0F6)
    val softLavender = Color(0xFFF0EDFF)
    val cardLavender = Color(0xFFF4F2FD)
    val cardPink = Color(0xFFFDF0F5)
    val cardBlue = Color(0xFFEEF2FD)
    val iconCircle = Color(0xFFF2F0FA)
    val track = Color(0xFFEDE9FB)
    val line = Color(0xFFECEBF4)
    val pagePad = 20.dp
    val gap = 12.dp
    val cardRadius = 20.dp

    val title = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ink)
    val cardTitle = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ink)
    val body = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 23.sp, color = Color(0xFF4A4B63))
    val small = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Normal, fontSize = 12.sp, color = muted)
    val label = TextStyle(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ink)
}

/** Round soft icon button used in every kit header (44dp, ice-lavender fill). */
@Composable
fun KitCircleButton(@DrawableRes icon: Int, modifier: Modifier = Modifier, dot: Boolean = false, tag: String? = null, contentDescription: String? = null, onClick: () -> Unit) {
    // 48dp touch target around the 38dp visible circle.
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Kit.iconCircle), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription, tint = Kit.ink, modifier = Modifier.size(21.dp))
        }
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp).size(8.dp).clip(CircleShape).background(Kit.pink))
    }
}

/** Kit header: centred bold title, a round button on each side (start = right in RTL). */
@Composable
fun KitHeader(
    title: String,
    subtitle: String? = null,
    start: (@Composable () -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = Kit.title
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 6.dp, bottom = 2.dp).heightIn(min = 48.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { start?.invoke() }
        Column(Modifier.align(Alignment.Center).padding(horizontal = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = titleStyle, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Kit.small, textAlign = TextAlign.Center)
        }
        Box(Modifier.align(Alignment.CenterEnd)) { end?.invoke() }
    }
}

/** Back button in kit headers: a chevron pointing right (back in RTL) on the soft circle. */
@Composable
fun KitBack(onBack: () -> Unit) = KitCircleButton(R.drawable.kit_chevron_right, tag = "kit_back", onClick = onBack)

/** Full-width capsule button, kit pink, white bold label and an optional leading white-circle icon. */
@Composable
fun KitPillButton(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = R.drawable.kit_plus, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(54.dp).shadow(6.dp, CircleShape, ambientColor = Kit.pink.copy(alpha = .35f), spotColor = Kit.pink.copy(alpha = .35f))
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(Color(0xFFF26B9A), Color(0xFFEC5486))).let { it })
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = Kit.cardTitle.copy(color = Color.White, fontSize = 17.sp))
        if (icon != null) {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = Kit.pink, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Rounded square tile holding an icon (the kit's list-row and card leading art). */
@Composable
fun KitIconTile(@DrawableRes icon: Int, tint: Color = Kit.pink, bg: Color = Color.White, size: Dp = 56.dp, iconSize: Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Card row: art at the start (right), title + text, chevron at the end (left). */
@Composable
fun KitRowCard(
    title: String,
    text: String?,
    bg: Color,
    modifier: Modifier = Modifier,
    tag: String? = null,
    chevron: Boolean = true,
    art: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Kit.cardRadius)).background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (art != null) { art(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = Kit.cardTitle)
            if (text != null) {
                Spacer(Modifier.height(4.dp))
                Text(text, style = Kit.body) // full text; the card grows to fit
            }
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.kit_chevron_left), null, tint = Kit.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** Two overlapping filled hearts (pink + lavender), from the kit heart path. */
@Composable
fun KitHearts(modifier: Modifier = Modifier.size(width = 52.dp, height = 40.dp)) {
    Canvas(modifier) {
        fun heart(ox: Float, oy: Float, s: Float): Path = Path().apply {
            // kit heart.svg: M12 20S3 14 3 8a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6-9 12-9 12z (approximated with cubics)
            moveTo(ox + 12 * s, oy + 20 * s)
            cubicTo(ox + 12 * s, oy + 20 * s, ox + 3 * s, oy + 14 * s, ox + 3 * s, oy + 8 * s)
            cubicTo(ox + 3 * s, oy + 3.5f * s, ox + 9 * s, oy + 1.8f * s, ox + 12 * s, oy + 5 * s)
            cubicTo(ox + 15 * s, oy + 1.8f * s, ox + 21 * s, oy + 3.5f * s, ox + 21 * s, oy + 8 * s)
            cubicTo(ox + 21 * s, oy + 14 * s, ox + 12 * s, oy + 20 * s, ox + 12 * s, oy + 20 * s)
            close()
        }
        val s = size.height / 22f
        drawPath(heart(0f, 0f, s * 1.05f), Brush.linearGradient(listOf(Color(0xFFFF8DB3), Kit.pink)))
        drawPath(heart(size.width - 21.5f * s, size.height * .18f, s * .95f), Brush.linearGradient(listOf(Color(0xFFB5A9F7), Color(0xFF8F83ED))), alpha = .85f)
    }
}

/** Soft pink / lavender background blobs behind kit hero rings. */
@Composable
fun KitBlobs(modifier: Modifier) {
    Canvas(modifier) {
        fun blob(c: Color, x: Float, y: Float, r: Float) =
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y))
        val w = size.width; val h = size.height
        blob(Color(0x66F8B4D2), w * .12f, h * .36f, w * .30f)
        blob(Color(0x55C9C1F7), w * .10f, h * .80f, w * .28f)
        blob(Color(0x55C9C1F7), w * .95f, h * .40f, w * .32f)
        blob(Color(0x55F8B4D2), w * .86f, h * .86f, w * .26f)
    }
}

/** Debug-only: `mahava://screen/<x>?scroll=<px>` scrolls the open kit screen (for adb screenshots; no data changes). */
object KitDebug { val scroll = androidx.compose.runtime.mutableStateOf<Pair<Int, Long>?>(null) }

@Composable
fun rememberKitScroll(): androidx.compose.foundation.ScrollState {
    val s = androidx.compose.foundation.rememberScrollState()
    val req = KitDebug.scroll.value
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(req) {
        req ?: return@LaunchedEffect
        kotlinx.coroutines.delay(900)
        // Only the screen that is on top takes the request, then it is used up.
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && KitDebug.scroll.value == req) {
            s.scrollTo(req.first); KitDebug.scroll.value = null
        }
    }
    return s
}
