package com.mahava.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahava.app.R
import com.mahava.app.ui.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================================
// Login
// =====================================================================================

@Composable
fun LoginScreen(
    vm: AppViewModel,
    onBack: (() -> Unit)? = null,
    onGoRegister: () -> Unit,
    onForgotPassword: () -> Unit = {},
    onSuccess: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val submit: () -> Unit = {
        if (!busy) {
            busy = true; error = null
            scope.launch {
                val msg = vm.login(phone, password)
                busy = false
                if (msg == null) onSuccess() else error = msg
            }
        }
    }
    LoginContent(
        phone, { phone = it }, password, { password = it }, busy, error,
        onSubmit = submit, onForgotPassword = onForgotPassword, onGoRegister = onGoRegister, onBack = onBack,
        demo = com.mahava.app.demo.DemoMode.enabled,
        onDemo = { scope.launch { vm.reenterDemo() } }
    )
}

@Composable
internal fun LoginContent(
    phone: String, onPhone: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?,
    onSubmit: () -> Unit, onForgotPassword: () -> Unit, onGoRegister: () -> Unit,
    onBack: (() -> Unit)? = null,
    demo: Boolean = false, onDemo: () -> Unit = {}
) {
    MockScreen(Color.White, layers = { shift ->
        listOf(
            Layer(top = 0.dp) {
                MahTop(310.dp, shift) {
                    MahBlob(200.dp, MK.Pink, 0.18f, top = (-30).dp, left = (-40).dp)
                    Image(
                        painterResource(R.drawable.ill_rest_woman), null, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.align(AbsoluteAlignment.BottomLeft).absoluteOffset(x = (-46).dp, y = 4.dp).size(210.dp, 140.dp)
                    )
                    Row(
                        Modifier.padding(top = 40.dp + shift).fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MahLogo(44.dp, 14.dp)
                        Column {
                            MText("ماه", 20f, FontWeight.Bold, line = 32f)
                            MText("همراه چرخهٔ تو", 12f, color = MK.Muted)
                        }
                        if (onBack != null) {
                            Spacer(Modifier.weight(1f))
                            MahBack(onBack)
                        }
                    }
                    MahTitleBlock(
                        "خوش برگشتی",
                        AnnotatedString("با شماره موبایل وارد حسابت شو؛\nهمهٔ ثبت‌هایت منتظرت هستند."),
                        Modifier.padding(top = 136.dp + shift, start = 24.dp, end = 160.dp)
                    )
                }
            },
            Layer(top = 336.dp + shift) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("login_screen")) {
                    MahLabel("شماره موبایل")
                    MahField(phone, onPhone, MahFieldKind.Phone, "۰۹۱۲ ۳۴۵ ۶۷۸۹", enabled = !busy, previewKey = "phone", testTag = "login_phone")
                    MahLabel("رمز عبور")
                    MahField(
                        password, onPassword, MahFieldKind.Password, "رمز عبورت را بنویس", enabled = !busy,
                        previewKey = "password", imeAction = ImeAction.Done, onIme = onSubmit, testTag = "login_password"
                    )
                    MahError(error)
                    Box(Modifier.fillMaxWidth().pullUp(4.dp).padding(start = 4.dp, end = 4.dp, bottom = 20.dp), contentAlignment = AbsoluteAlignment.CenterLeft) {
                        MahLink("رمز را فراموش کردی؟", 13.5f, Modifier.testTag("go_password_recovery"), onForgotPassword)
                    }
                    MahButton("ورود", loading = busy, arrow = true, modifier = Modifier.fillMaxWidth().testTag("login_submit"), onClick = onSubmit)
                    MText(
                        "حساب نداری؟", 13.5f, color = MK.Muted, align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp)
                    )
                    MahButton2("ساخت حساب · یک ماه رایگان", Modifier.testTag("go_register"), onGoRegister)
                    if (demo) {
                        Spacer(Modifier.height(10.dp))
                        // Demo is offline: login can't reach a server, so offer to re-enter the fake account.
                        MahButton2("ورود دوباره به دمو", Modifier.testTag("demo_reenter"), onDemo)
                    }
                }
            },
            Layer(bottom = 26.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Feather("cloud", 14.dp, Color(0xFF9D9AB3))
                    MText("ثبت‌هایت با گوشی جدید هم برمی‌گردد", 12f, color = Color(0xFF9D9AB3))
                }
            }
        )
    })
}

// =====================================================================================
// Register (phone + role -> OTP -> password + legal)
// =====================================================================================

@Composable
fun RegisterScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onGoLogin: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onSuccess: () -> Unit = onBack
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) } // 0 phone, 1 otp, 2 password+legal
    var role by remember { mutableStateOf("female") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var acceptedLegal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var fixedOtp by remember { mutableStateOf(false) }
    val resend = rememberResendCountdown()

    val sendOtp: () -> Unit = {
        if (!busy) {
            busy = true; error = null; info = null
            scope.launch {
                val r = vm.requestOtp(phone, "register")
                busy = false
                if (r.cooldownSec > 0) resend.intValue = r.cooldownSec
                if (r.error == null) {
                    fixedOtp = r.fixedOtp
                    info = if (r.fixedOtp) "کد آماده است. فعلاً از ۰۰۰۰۰۰ استفاده کن." else null
                    step = 1
                } else error = r.error
            }
        }
    }
    val verify: () -> Unit = {
        if (!busy && otp.length == 6) {
            busy = true; error = null; info = null
            scope.launch {
                val msg = vm.verifyOtp(phone, "register", otp)
                busy = false
                if (msg == null) step = 2 else error = msg
            }
        }
    }
    val resendOtp: () -> Unit = {
        busy = true; error = null
        scope.launch {
            val r = vm.requestOtp(phone, "register")
            busy = false
            if (r.cooldownSec > 0) resend.intValue = r.cooldownSec
            if (r.error == null) {
                fixedOtp = r.fixedOtp
                info = if (r.fixedOtp) "کد دوباره آماده شد." else "کد دوباره به شمارهٔ ${maskedPhone(phone)} پیامک شد."
            } else {
                info = null
                error = r.error
            }
        }
    }
    val submit: () -> Unit = {
        if (!busy && acceptedLegal && password.length >= 6) {
            busy = true; error = null; info = null
            scope.launch {
                val msg = vm.register(
                    phone = phone,
                    password = password,
                    role = role,
                    otp = otp,
                    acceptedPrivacy = acceptedLegal,
                    acceptedTerms = acceptedLegal
                )
                busy = false
                if (msg == null) onSuccess() else error = msg
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step -= 1; error = null }
    RegisterContent(
        step = step, role = role, onRole = { role = it },
        phone = phone, onPhone = { phone = it },
        otp = otp, onOtp = { otp = it },
        password = password, onPassword = { password = it },
        accepted = acceptedLegal, onAccepted = { acceptedLegal = it },
        busy = busy, error = error, info = info, fixedOtp = fixedOtp, resendLeft = resend.intValue,
        onBack = { if (step == 0) onBack() else { step -= 1; error = null } },
        onSendOtp = sendOtp, onVerify = verify, onResend = resendOtp,
        onEditPhone = { step = 0; otp = ""; error = null },
        onSubmit = submit, onGoLogin = onGoLogin, onOpenPrivacy = onOpenPrivacy, onOpenTerms = onOpenTerms
    )
}

@Composable
internal fun RegisterContent(
    step: Int, role: String, onRole: (String) -> Unit,
    phone: String, onPhone: (String) -> Unit,
    otp: String, onOtp: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    accepted: Boolean, onAccepted: (Boolean) -> Unit,
    busy: Boolean, error: String?, info: String?, fixedOtp: Boolean, resendLeft: Int,
    onBack: () -> Unit, onSendOtp: () -> Unit, onVerify: () -> Unit, onResend: () -> Unit,
    onEditPhone: () -> Unit, onSubmit: () -> Unit, onGoLogin: () -> Unit,
    onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit,
    previewCursor: Boolean = false
) {
    val title = when (step) { 0 -> "ساخت حساب ماه"; 1 -> "کد را وارد کن"; else -> "یک رمز امن بگذار" }
    val sub = when (step) {
        0 -> AnnotatedString("شماره موبایلت را بزن تا کد تأیید برایت بیاید.")
        1 -> AnnotatedString(
            if (fixedOtp) "کد ۶ رقمی را وارد کن؛ تا راه‌اندازی پیامک، کد ۰۰۰۰۰۰ است."
            else "کد ۶ رقمی پیامک شد به\n${maskedPhone(phone)}"
        )
        else -> AnnotatedString("بعد از این با شماره و همین رمز وارد ماه می‌شوی.")
    }
    MockScreen(Color.White, layers = { shift ->
        buildList {
            add(Layer(top = 0.dp) { MahStepHeader(shift, step, onBack, title, sub, MahArt.WomanBotanical) })
            when (step) {
                0 -> {
                    add(Layer(top = 316.dp + shift) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("register_screen")) {
                            MahLabel("این حساب برای کیست؟")
                            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                RoleCard("خانم هستم", R.drawable.design_mother, role == "female", "register_role_female") { onRole("female") }
                                RoleCard("آقا هستم (همسر)", R.drawable.design_father, role == "male", "register_role_male") { onRole("male") }
                            }
                            if (role == "male") MahNote("حساب آقا وضعیت همسرش را می‌بیند. بعد از ثبت‌نام، کد اتصال را از همسرت بگیر.", icon = "link", bottom = 16.dp)
                            MahLabel("شماره موبایل")
                            MahField(
                                phone, onPhone, MahFieldKind.Phone, "۰۹۱۲ ۳۴۵ ۶۷۸۹", enabled = !busy, previewKey = "phone",
                                imeAction = ImeAction.Done, onIme = onSendOtp, testTag = "register_phone"
                            )
                            MahError(error)
                            MahButton("ارسال کد", loading = busy, arrow = true, modifier = Modifier.fillMaxWidth().testTag("register_send_otp"), onClick = onSendOtp)
                        }
                    })
                    add(Layer(bottom = 56.dp) { MahFootLink("قبلاً ثبت‌نام کردی؟", "ورود", onClick = onGoLogin) })
                    add(Layer(bottom = 26.dp) {
                        MText("ثبت‌نام جدید یک ماه رایگان است ✿", 12.5f, FontWeight.SemiBold, MK.Lav, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    })
                }
                1 -> add(Layer(top = 330.dp + shift) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("register_screen")) {
                        MahOtp(otp, onOtp, enabled = !busy, previewCursor = previewCursor, testTag = "register_otp", onDone = onVerify)
                        MahResendRow(resendLeft, busy, "register_resend", onResend)
                        MahError(error)
                        MahInfo(info)
                        MahButton(
                            "تأیید کد", enabled = otp.length == 6, loading = busy,
                            modifier = Modifier.fillMaxWidth().testTag("register_verify_otp"), onClick = onVerify
                        )
                        MahLink("ویرایش شماره", modifier = Modifier.padding(top = 18.dp).align(Alignment.CenterHorizontally), onClick = onEditPhone)
                    }
                })
                else -> add(Layer(top = 316.dp + shift) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("register_screen")) {
                        MahLabel("رمز عبور")
                        MahField(
                            password, onPassword, MahFieldKind.Password, "حداقل ۶ کاراکتر", enabled = !busy,
                            previewKey = "password", imeAction = ImeAction.Done, onIme = onSubmit, testTag = "register_password"
                        )
                        MahStrength(password)
                        MahMinHint(password.length >= 6, Modifier.padding(bottom = 16.dp))
                        LegalCard(accepted, onAccepted, onOpenPrivacy, onOpenTerms)
                        MahError(error)
                        MahButton(
                            "ثبت‌نام و شروع", enabled = accepted && password.length >= 6, loading = busy,
                            modifier = Modifier.fillMaxWidth().testTag("register_submit"), onClick = onSubmit
                        )
                    }
                })
            }
        }
    })
}

@Composable
private fun RowScope.RoleCard(text: String, image: Int, on: Boolean, tag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.weight(1f)
            .then(if (on) Modifier.cssShadow(MK.Pink.copy(alpha = 0.15f), blur = 16.dp, y = 6.dp, radius = 20.dp) else Modifier)
            .clip(shape)
            .background(if (on) MK.Soft else Color.White)
            .border(1.5.dp, if (on) MK.Pink else MK.Border, shape)
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(13.5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(painterResource(image), null, contentScale = ContentScale.Fit, modifier = Modifier.size(76.dp))
        MText(text, 14f, FontWeight.SemiBold)
    }
}

@Composable
private fun LegalCard(accepted: Boolean, onAccepted: (Boolean) -> Unit, onOpenPrivacy: () -> Unit, onOpenTerms: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.padding(bottom = 18.dp).fillMaxWidth().clip(shape).background(MK.FieldBg).border(1.5.dp, MK.Border, shape)
            .clickable { onAccepted(!accepted) }
            .testTag("register_legal_row")
            .padding(15.5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MahCheck(accepted, modifier = Modifier.testTag("register_legal_check"))
        Column(Modifier.weight(1f)) {
            MText("سیاست حریم خصوصی و شرایط استفاده را خواندم و می‌پذیرم.", 13f, FontWeight.SemiBold, line = 24.7f)
            Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MText("حریم خصوصی", 12.5f, FontWeight.SemiBold, MK.Pink, line = 23.75f,
                    modifier = Modifier.clickable(onClick = onOpenPrivacy).testTag("open_privacy"))
                MText("شرایط استفاده", 12.5f, FontWeight.SemiBold, MK.Pink, line = 23.75f,
                    modifier = Modifier.clickable(onClick = onOpenTerms).testTag("open_terms"))
            }
        }
    }
}

// =====================================================================================
// Password recovery (phone -> OTP -> new password)
// =====================================================================================

@Composable
fun PasswordRecoveryScreen(vm: AppViewModel, onBack: () -> Unit, onDone: () -> Unit = onBack) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var fixedOtp by remember { mutableStateOf(false) }
    val resend = rememberResendCountdown()

    val sendOtp: () -> Unit = {
        if (!busy) {
            busy = true; error = null; info = null
            scope.launch {
                val r = vm.requestOtp(phone, "reset")
                busy = false
                if (r.cooldownSec > 0) resend.intValue = r.cooldownSec
                if (r.error == null) {
                    fixedOtp = r.fixedOtp
                    info = if (r.fixedOtp) "اگر شماره ثبت شده باشد، کد آماده است. فعلاً از ۰۰۰۰۰۰ استفاده کن."
                           else r.serverMessage
                    step = 1
                } else error = r.error
            }
        }
    }
    val verify: () -> Unit = {
        if (!busy && otp.length == 6) {
            busy = true; error = null
            scope.launch {
                val msg = vm.verifyOtp(phone, "reset", otp)
                busy = false
                if (msg == null) step = 2 else error = msg
            }
        }
    }
    val resendOtp: () -> Unit = {
        busy = true; error = null
        scope.launch {
            val r = vm.requestOtp(phone, "reset")
            busy = false
            if (r.cooldownSec > 0) resend.intValue = r.cooldownSec
            if (r.error == null) {
                fixedOtp = r.fixedOtp
                info = if (r.fixedOtp) "کد دوباره آماده شد."
                       else r.serverMessage ?: "اگر شماره ثبت شده باشد، کد دوباره پیامک می‌شود."
            } else {
                info = null
                error = r.error
            }
        }
    }
    val submit: () -> Unit = {
        if (!busy && password.length >= 6) {
            busy = true; error = null
            scope.launch {
                val msg = vm.resetPassword(phone, otp, password)
                busy = false
                if (msg == null) {
                    info = "رمز عوض شد. حالا وارد شو."
                    onDone()
                } else error = msg
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step -= 1; error = null }
    RecoveryContent(
        step = step, phone = phone, onPhone = { phone = it }, otp = otp, onOtp = { otp = it },
        password = password, onPassword = { password = it },
        busy = busy, error = error, info = info, fixedOtp = fixedOtp, resendLeft = resend.intValue,
        onBack = { if (step == 0) onBack() else { step -= 1; error = null } },
        onSendOtp = sendOtp, onVerify = verify, onResend = resendOtp, onSubmit = submit, onLogin = onDone
    )
}

@Composable
internal fun RecoveryContent(
    step: Int, phone: String, onPhone: (String) -> Unit, otp: String, onOtp: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    busy: Boolean, error: String?, info: String?, fixedOtp: Boolean, resendLeft: Int,
    onBack: () -> Unit, onSendOtp: () -> Unit, onVerify: () -> Unit, onResend: () -> Unit,
    onSubmit: () -> Unit, onLogin: () -> Unit
) {
    val title = when (step) { 0 -> "رمز را فراموش کردی؟"; 1 -> "کد بازیابی"; else -> "رمز تازه" }
    val sub = when (step) {
        0 -> AnnotatedString("اشکالی ندارد؛ شماره موبایل حسابت را بزن.")
        1 -> AnnotatedString(
            if (fixedOtp) "کد ۶ رقمی را وارد کن؛ تا راه‌اندازی پیامک، کد ۰۰۰۰۰۰ است."
            else "کد پیامک‌شده به\n${maskedPhone(phone)} را وارد کن."
        )
        else -> AnnotatedString("رمز جدیدت را بنویس؛ دفعهٔ بعد با همین وارد می‌شوی.")
    }
    MockScreen(Color.White, layers = { shift ->
        listOf(
            Layer(top = 0.dp) { MahStepHeader(shift, step, onBack, title, sub, MahArt.Shield) },
            when (step) {
                0 -> Layer(top = 316.dp + shift) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("password_recovery_screen")) {
                        MahLabel("شماره موبایل")
                        MahField(
                            phone, onPhone, MahFieldKind.Phone, "۰۹۱۲ ۳۴۵ ۶۷۸۹", enabled = !busy, previewKey = "phone",
                            imeAction = ImeAction.Done, onIme = onSendOtp, testTag = "recovery_phone"
                        )
                        MahError(error)
                        MahNote("کد ۶ رقمی بازیابی به همین شماره پیامک می‌شود.")
                        MahButton("ارسال کد", loading = busy, modifier = Modifier.fillMaxWidth().testTag("recovery_send_otp"), onClick = onSendOtp)
                    }
                }
                1 -> Layer(top = 330.dp + shift) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("password_recovery_screen")) {
                        MahOtp(otp, onOtp, enabled = !busy, testTag = "recovery_otp", onDone = onVerify)
                        MahResendRow(resendLeft, busy, "recovery_resend", onResend)
                        MahError(error)
                        MahInfo(info)
                        MahButton(
                            "تأیید کد", enabled = otp.length == 6, loading = busy,
                            modifier = Modifier.fillMaxWidth().testTag("recovery_verify_otp"), onClick = onVerify
                        )
                    }
                }
                else -> Layer(top = 316.dp + shift) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).testTag("password_recovery_screen")) {
                        MahLabel("رمز عبور جدید")
                        MahField(
                            password, onPassword, MahFieldKind.Password, "حداقل ۶ کاراکتر", enabled = !busy,
                            previewKey = "password", imeAction = ImeAction.Done, onIme = onSubmit, testTag = "recovery_password"
                        )
                        MahError(error)
                        MahMinHint(password.length >= 6, Modifier.pullUp(4.dp).padding(bottom = 22.dp))
                        MahButton(
                            "ذخیرهٔ رمز و ورود", enabled = password.length >= 6, loading = busy,
                            modifier = Modifier.fillMaxWidth().testTag("recovery_submit"), onClick = onSubmit
                        )
                    }
                }
            },
            Layer(bottom = 30.dp) { MahFootLink(null, "بازگشت به ورود", testTag = "recovery_back_to_login", onClick = onLogin) }
        )
    })
}

/** Counts [MutableIntState.intValue] seconds down to zero, one tick per second. */
@Composable
private fun rememberResendCountdown(): MutableIntState {
    val left = remember { mutableIntStateOf(0) }
    LaunchedEffect(left.intValue) {
        if (left.intValue > 0) {
            delay(1000)
            left.intValue = (left.intValue - 1).coerceAtLeast(0)
        }
    }
    return left
}
