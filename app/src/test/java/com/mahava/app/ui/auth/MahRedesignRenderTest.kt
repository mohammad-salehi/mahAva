package com.mahava.app.ui.auth

import android.graphics.Bitmap
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the redesigned intro/auth screens at the mockup size (390x844 @3x) for a pixel
 * comparison against the approved designs. Opt-in (MAH_RENDER=1); never part of the normal suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MahRedesignRenderTest {
    @get:Rule val rule = androidx.compose.ui.test.junit4.createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private fun render(name: String, focus: String? = null, content: @Composable () -> Unit) {
        org.junit.Assume.assumeTrue(System.getenv("MAH_RENDER") == "1")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalPreviewFocus provides focus
            ) { content() }
        }
        repeat(8) { rule.mainClock.advanceTimeBy(250); Thread.sleep(100) }
        val dir = File("build/redesign-renders").apply { mkdirs() }
        val root = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun intro(i: Int) = render("0${i + 1}_onboarding_${i + 1}") {
        IntroContent(rememberPagerState(initialPage = i) { introPages.size }, onRegister = {}, onLogin = {})
    }

    @Test fun intro1() = intro(0)
    @Test fun intro2() = intro(1)
    @Test fun intro3() = intro(2)
    @Test fun intro4() = intro(3)

    private fun register(name: String, step: Int, focus: String?, otp: String = "", password: String = "", resend: Int = 0, cursor: Boolean = false) =
        render(name, focus) {
            RegisterContent(
                step = step, role = "female", onRole = {}, phone = "09123456789", onPhone = {},
                otp = otp, onOtp = {}, password = password, onPassword = {}, accepted = true, onAccepted = {},
                busy = false, error = null, info = null, fixedOtp = false, resendLeft = resend,
                onBack = {}, onSendOtp = {}, onVerify = {}, onResend = {}, onEditPhone = {}, onSubmit = {},
                onGoLogin = {}, onOpenPrivacy = {}, onOpenTerms = {}, previewCursor = cursor
            )
        }

    @Test fun signupPhone() = register("05_signup_phone", 0, "phone")
    @Test fun signupOtp() = register("06_signup_otp", 1, null, otp = "3916", resend = 42, cursor = true)
    @Test fun signupPassword() = register("07_signup_password", 2, "password", password = "sara1234")

    @Test fun login() = render("08_login", "password") {
        LoginContent("09123456789", {}, "sara12345", {}, busy = false, error = null, onSubmit = {}, onForgotPassword = {}, onGoRegister = {})
    }

    private fun recovery(name: String, step: Int, focus: String?, otp: String = "", password: String = "", resend: Int = 0) =
        render(name, focus) {
            RecoveryContent(
                step = step, phone = "09123456789", onPhone = {}, otp = otp, onOtp = {}, password = password, onPassword = {},
                busy = false, error = null, info = null, fixedOtp = false, resendLeft = resend,
                onBack = {}, onSendOtp = {}, onVerify = {}, onResend = {}, onSubmit = {}, onLogin = {}
            )
        }

    @Test fun forgotPhone() = recovery("09_forgot_phone", 0, "phone")
    @Test fun forgotOtp() = recovery("10_forgot_otp", 1, null, otp = "720581", resend = 29)
    @Test fun forgotNewPassword() = recovery("11_forgot_newpass", 2, "password", password = "sar@1234")
}
