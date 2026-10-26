package com.mahava.app.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import kotlinx.coroutines.launch

/** Where the last intro page sends the user. */
enum class IntroExit { REGISTER, LOGIN }

internal class IntroPage(val title: String, val body: String)

internal val introPages = listOf(
    IntroPage(
        "ماه؛ همراه هر روزِ بدنت",
        "چرخه‌ات را ثبت کن؛ ماه روز پریود بعدی، روزهای باروری و مرحلهٔ امروزت را برایت تخمین می‌زند و هر روز یک توصیهٔ ساده می‌دهد."
    ),
    IntroPage(
        "حالت را ثبت کن، الگویت را ببین",
        "روزانه در چند ثانیه حال، علائم و هوس‌هایت را ثبت کن. ماه کم‌کم الگوی بدنت را پیدا می‌کند و برای ویزیت، گزارش PDF برای پزشک می‌سازد."
    ),
    IntroPage(
        "همسرت هم کنارت است",
        "با یک کد اتصال، همسرت در برنامهٔ ماه وضعیت امروزت و راهنمای همراهی را می‌بیند؛ فقط با تأیید خودت، و هر وقت بخواهی اتصال را قطع می‌کنی."
    ),
    IntroPage(
        "اطلاعاتت، فقط مال خودت",
        "ثبت‌هایت روی حسابت در سرور ماه می‌ماند و با گوشی جدید هم برمی‌گردد. هر وقت بخواهی، حسابت و همهٔ اطلاعاتش را کامل پاک می‌کنی."
    )
)

/** Four-page intro shown once before login/sign-up. */
@Composable
fun MahIntroScreen(onDone: (IntroExit) -> Unit) {
    val pager = rememberPagerState { introPages.size }
    IntroContent(pager, onRegister = { onDone(IntroExit.REGISTER) }, onLogin = { onDone(IntroExit.LOGIN) })
}

@Composable
internal fun IntroContent(pager: PagerState, onRegister: () -> Unit, onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val last = page == introPages.lastIndex
    MockScreen(Color.White, layers = { shift ->
        listOf(
            Layer(top = 0.dp) {
                HorizontalPager(pager, Modifier.fillMaxWidth().testTag("intro_pager")) { p ->
                    IntroPageBody(p, shift)
                }
            },
            Layer(bottom = if (last) 26.dp else 34.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
                    if (!last) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            MText(
                                "رد شدن", 15f, FontWeight.SemiBold, MK.Muted,
                                modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage(introPages.lastIndex) } }
                                    .padding(horizontal = 6.dp).testTag("intro_skip")
                            )
                            MahButton("بعدی", arrow = true, modifier = Modifier.width(150.dp).testTag("intro_next")) {
                                scope.launch { pager.animateScrollToPage(page + 1) }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            MahButton("ساخت حساب رایگان", modifier = Modifier.fillMaxWidth().testTag("intro_register"), onClick = onRegister)
                            Spacer(Modifier.height(14.dp))
                            MahFootLink("حساب داری؟", "ورود", testTag = "intro_login", onClick = onLogin)
                        }
                    }
                }
            }
        )
    })
}

@Composable
private fun IntroPageBody(p: Int, shift: Dp) {
    Box(Modifier.fillMaxWidth()) {
        MahTop(480.dp, shift) {
            MahBlob(220.dp, MK.Pink, 0.20f, top = (-40).dp, right = (-60).dp)
            MahBlob(240.dp, MK.Lav, 0.25f, bottom = (-60).dp, left = (-70).dp)
            // Illustration area starts 34px below the panel top (below the brand row).
            Box(Modifier.fillMaxSize().padding(top = 34.dp + shift)) { IntroArt(p) }
            Row(
                Modifier.padding(top = 40.dp + shift).fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahLogo(36.dp, 12.dp)
                    MText("ماه", 17f, FontWeight.Bold)
                }
                MText(
                    "${faDigits((p + 1).toString())} از ۴", 13f, color = MK.Muted,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Column(
            Modifier.padding(top = 500.dp + shift, start = 26.dp, end = 26.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(introPages.size) { i ->
                    Box(Modifier.width(if (i == p) 26.dp else 8.dp).height(8.dp).clip(RoundedCornerShape(8.dp)).background(if (i == p) MK.Pink else MK.Dot))
                }
            }
            Spacer(Modifier.height(16.dp))
            MText(introPages[p].title, 24f, FontWeight.Bold, line = 36f, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            MText(introPages[p].body, 14.5f, color = MK.Muted, line = 26.825f, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BoxScope.IntroArt(p: Int) {
    // Soft white disc behind the illustration.
    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(50.dp, 110.dp).size(290.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.65f)))
    when (p) {
        0 -> {
            Image(painterResource(R.drawable.ill_welcome_woman), null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(30.dp, 70.dp).size(330.dp, 220.dp))
            MahFloatCard(Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-16).dp, y = 70.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CycleDial()
                    Column {
                        MText("روز ۱۱ از چرخه", 13f, FontWeight.Bold)
                        MText("نزدیک تخمک‌گذاری", 12f, color = MK.Muted)
                    }
                }
            }
            MahFloatCard(Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = 16.dp, y = (-26).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahIconTile("drop", MK.Soft, MK.Pink)
                    Column {
                        MText("پریود بعدی (حدودی)", 12f, color = MK.Muted)
                        MText("حدود ۱۴ آبان", 13f, FontWeight.Bold)
                    }
                }
            }
        }
        1 -> {
            Image(painterResource(R.drawable.ill_daily_woman), null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(25.dp, 80.dp).size(340.dp, (340f * 1024f / 1536f).dp))
            MahFloatCard(Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-16).dp, y = 66.dp).width(200.dp)) {
                Column {
                    MText("حالت امروز چطوره؟", 12.5f, FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("خوبم" to Color(0xFFDBF7EF), "خستگی" to MK.LavSoft, "درد" to Color(0xFFFFE3EE)).forEach { (t, c) ->
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(c).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                MText(t, 11.5f, FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            MahFloatCard(Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = 16.dp, y = (-24).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahIconTile("file", MK.LavSoft, MK.Lav)
                    Column {
                        MText("گزارش برای پزشک", 12.5f, FontWeight.Bold)
                        MText("۳ چرخهٔ اخیر · PDF", 12f, color = MK.Muted)
                    }
                }
            }
        }
        2 -> {
            Image(painterResource(R.drawable.design_mother), null, contentScale = ContentScale.FillHeight,
                modifier = Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-70).dp, y = 96.dp).size((230f * 459f / 644f).dp, 230.dp))
            Image(painterResource(R.drawable.design_father), null, contentScale = ContentScale.FillHeight,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 62.dp, y = 110.dp).size((215f * 440f / 644f).dp, 215.dp))
            Box(
                Modifier.align(Alignment.TopCenter).absoluteOffset(y = 150.dp).size(52.dp)
                    .cssShadow(MK.Pink.copy(alpha = 0.3f), blur = 20.dp, y = 8.dp, radius = 26.dp)
                    .clip(CircleShape).background(Color.White),
                contentAlignment = Alignment.Center
            ) { Feather("heart", 24.dp, MK.Pink) }
            MahFloatCard(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(x = 16.dp, y = 64.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahIconTile("link", MK.LavSoft, MK.Lav)
                    Column {
                        MText("کد اتصال", 12f, color = MK.Muted)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            // Latin-digit cut of Vazirmatn: the app font is the Farsi-digit (FD) build, the code must stay "3"/"7".
                            androidx.compose.material3.Text("AB3K7Q", style = mStyle(13f, FontWeight.Bold, letter = 1.5f).copy(fontFamily = MahLatinBold))
                        }
                    }
                }
            }
            MahFloatCard(Modifier.align(AbsoluteAlignment.BottomRight).absoluteOffset(x = (-16).dp, y = (-22).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahCheck(true, 26.dp, 9.dp, MK.Fert)
                    MText("اتصال با تأیید تو", 12f, FontWeight.SemiBold)
                }
            }
        }
        else -> {
            Image(painterResource(R.drawable.ill_privacy_shield), null, contentScale = ContentScale.FillWidth,
                modifier = Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset(0.dp, 96.dp).size(390.dp, 195.dp))
            MahFloatCard(Modifier.align(AbsoluteAlignment.TopRight).absoluteOffset(x = (-16).dp, y = 64.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahIconTile("cloud", Color(0xFFDBF7EF), MK.Green)
                    MText("پشتیبان روی حساب تو", 12.5f, FontWeight.Bold)
                }
            }
            MahFloatCard(Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = 16.dp, y = (-24).dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MahIconTile("star", MK.Soft, MK.Pink)
                    Column {
                        MText("یک ماه رایگان", 12.5f, FontWeight.Bold)
                        MText("برای ثبت‌نام جدید", 12f, color = MK.Muted)
                    }
                }
            }
        }
    }
}

/** 46px cycle ring: track + 37/94 arc from 12 o'clock, day number in the middle. */
@Composable
private fun CycleDial() {
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(46.dp)) {
            val k = size.width / 36f
            val r = 15f * k
            val sw = 4f * k
            val tl = Offset(18f * k - r, 18f * k - r)
            val sz = Size(2 * r, 2 * r)
            drawArc(Color(0xFFFFE3EE), 0f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(MK.Pink, -90f, 37f / (2f * Math.PI.toFloat() * 15f) * 360f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        MText("۱۱", 12f * 46f / 36f, FontWeight.Bold, MK.Deep)
    }
}
