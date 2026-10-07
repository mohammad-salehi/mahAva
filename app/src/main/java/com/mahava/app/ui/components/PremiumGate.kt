package com.mahava.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary

/**
 * Paywall when a feature needs an active subscription
 * (server hasActiveSubscription or local demo unlock).
 */
@Composable
fun PremiumPaywallCard(
    titleFa: String,
    benefitFa: String = "با اشتراک ماه باز می‌شه.",
    isLoggedIn: Boolean = false,
    onOpenAccount: (() -> Unit)? = null,
    onOpenLogin: (() -> Unit)? = null,
    onUnlockDemo: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    MahavaCard(Modifier.testTag("premium_paywall")) {
        Text("با اشتراک ماه باز می‌شه", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
        Text(titleFa, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(benefitFa, style = MaterialTheme.typography.bodyMedium, color = MahavaTextSecondary)
        Spacer(Modifier.height(8.dp))
        QuietInfo("بدون اشتراک فقط ثبت پریود، تقویم و پیش‌بینی پریود بعدی کار می‌کند. ویجت صفحهٔ اصلی هم فقط با ورود و اشتراک فعال می‌شود.")
        Spacer(Modifier.height(8.dp))
        when {
            onOpenAccount != null || onOpenLogin != null ->
                PrimaryButton(
                    if (isLoggedIn) "حساب و اشتراک" else "ورود برای اشتراک",
                    modifier = Modifier.testTag(if (isLoggedIn) "premium_open_account" else "premium_open_login"),
                    onClick = {
                        if (isLoggedIn) onOpenAccount?.invoke()
                        else (onOpenLogin ?: onOpenAccount)?.invoke()
                    }
                )
            onOpenSettings != null ->
                SecondaryButton("رفتن به تنظیمات", onClick = onOpenSettings)
        }
        if (onUnlockDemo != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("فعال‌سازی آزمایشی", modifier = Modifier.testTag("premium_unlock_demo"), onClick = onUnlockDemo)
        }
    }
}

/** Compact locked teaser for home / lists. */
@Composable
fun PremiumTeaserCard(
    titleFa: String,
    hintFa: String = "با اشتراک ماه باز می‌شه",
    onClick: () -> Unit
) {
    MahavaCard(Modifier.testTag("premium_teaser").clickable(onClick = onClick)) {
        Text(titleFa, style = MaterialTheme.typography.titleMedium)
        Text(hintFa, color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun PremiumBadge() {
    Text("اشتراک", color = MahavaPrimary, style = MaterialTheme.typography.labelLarge)
}

@Composable
fun PremiumGate(
    premium: Boolean,
    locked: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (premium) content() else locked()
}

/**
 * On entering a premium screen: re-check auth tokens. If invalid, [onNeedLogin].
 * Pass [isLoggedIn] into [PremiumPaywallCard] when showing the lock state.
 */
@Composable
fun PremiumSessionGuard(vm: com.mahava.app.ui.AppViewModel, onNeedLogin: () -> Unit) {
    val premium by vm.isPremium.collectAsState()
    LaunchedEffect(premium) {
        if (premium) vm.revalidatePremiumAccess(onNeedLogin)
    }
}


@Composable
fun PremiumPaywallCard(
    vm: com.mahava.app.ui.AppViewModel,
    titleFa: String,
    benefitFa: String = "با اشتراک ماه باز می‌شه.",
    onOpenAccount: () -> Unit,
    onUnlockDemo: (() -> Unit)? = null
) {
    val loggedIn by vm.isLoggedIn.collectAsState()
    PremiumSessionGuard(vm, onNeedLogin = onOpenAccount)
    PremiumPaywallCard(
        titleFa = titleFa,
        benefitFa = benefitFa,
        isLoggedIn = loggedIn,
        onOpenAccount = onOpenAccount,
        onUnlockDemo = onUnlockDemo
    )
}
