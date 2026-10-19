package com.mahava.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.R
import com.mahava.app.ui.kit.Kit
import com.mahava.app.ui.navigation.Routes

/** Kit bottom bar: white, five tabs, a raised pink «ثبت» circle in the middle, pink active tab with a short underline. */
@Composable
fun MainNavigation(current: String?, onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple(Routes.TODAY, "امروز", R.drawable.kit_home),
        Triple(Routes.CALENDAR, "تقویم", R.drawable.kit_calendar),
        Triple(Routes.LOG_HUB, "ثبت", R.drawable.kit_plus),
        Triple(Routes.REPORTS, "گزارش", R.drawable.kit_chart),
        Triple(Routes.TOOLS, "ابزارها", R.drawable.kit_grid)
    )
    // The bar has a concave notch around the raised «ثبت» circle; nothing clips the circle (clip = false).
    val barShape = remember { NotchedBarShape() }
    Box(
        Modifier.fillMaxWidth()
            .drawBehind {
                val outline = barShape.createOutline(size, layoutDirection, this) as androidx.compose.ui.graphics.Outline.Generic
                drawIntoCanvas { c ->
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.WHITE
                        setShadowLayer(14.dp.toPx(), 0f, -2.dp.toPx(), android.graphics.Color.argb(40, 96, 88, 144))
                    }
                    c.nativeCanvas.drawPath(outline.path.asAndroidPath(), paint)
                }
            }
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 6.dp)) {
            items.forEach { (route, label, icon) ->
                val selected = current == route || when (route) {
                    Routes.LOG_HUB -> current?.startsWith("daily_log") == true || current in listOf(Routes.PERIOD_LOG, Routes.LATE)
                    Routes.TOOLS -> current?.startsWith("tools/") == true || current == Routes.BODY || current?.startsWith("body_") == true || current?.startsWith("health_insight") == true || current in listOf(Routes.PREGNANCY, Routes.PREGNANCY_SETUP)
                    Routes.REPORTS -> current in listOf(Routes.HEALTH_ANALYTICS, Routes.DOCTOR_PDF, Routes.CYCLE_TRENDS, Routes.SYMPTOM_PATTERNS)
                    else -> false
                }
                val color = if (selected) Kit.pink else Kit.ink
                Box(
                    Modifier.weight(1f).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onNavigate(route) }
                        .semantics { this.selected = selected; contentDescription = label }
                        .testTag("nav_$route")
                ) {
                    if (route == Routes.LOG_HUB) {
                        Box(
                            Modifier.align(Alignment.TopCenter).offset(y = (-17).dp).size(58.dp)
                                .shadow(10.dp, CircleShape, ambientColor = Kit.pink, spotColor = Kit.pink)
                                .background(Color.White, CircleShape).testTag("nav_fab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.size(48.dp).background(Kit.pinkButton, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp))
                    } else {
                        Column(Modifier.align(Alignment.TopCenter).padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(if (selected && route == Routes.TODAY) R.drawable.kit_home_filled else icon), null, tint = color, modifier = Modifier.size(25.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, softWrap = false)
                            Spacer(Modifier.height(2.dp))
                            Box(Modifier.size(width = 14.dp, height = 3.dp).clip(CircleShape).background(if (selected) Kit.pink else Color.Transparent))
                        }
                    }
                }
            }
        }
    }
}

/** Rounded-top bar minus a circle around the centre button (button: 58dp, top at -17dp → centre 12dp below the bar top). */
private class NotchedBarShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
        with(density) {
            val corner = 24.dp.toPx()
            val bar = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height,
                    topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(corner), topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)))
            }
            val r = 29.dp.toPx() + 5.dp.toPx()
            val cy = 12.dp.toPx()
            val notch = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset(size.width / 2, cy), r))
            }
            val out = androidx.compose.ui.graphics.Path()
            out.op(bar, notch, androidx.compose.ui.graphics.PathOperation.Difference)
            return androidx.compose.ui.graphics.Outline.Generic(out)
        }
    }
}
