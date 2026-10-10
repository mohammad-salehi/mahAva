package com.mahava.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mahava.app.BuildConfig
import com.mahava.app.R
import com.mahava.app.ui.components.Illustration
import com.mahava.app.ui.components.PrimaryButton
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.navigation.Routes
import com.mahava.app.ui.screens.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mahava.app.ui.theme.MahavaBackground
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaSurface
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import java.time.LocalDate

@Composable
fun MahavaAppRoot(
    vm: AppViewModel,
    activity: FragmentActivity,
    deepLink: android.net.Uri? = null,
    openToday: Boolean = false,
    onOpenTodayConsumed: () -> Unit = {},
    openAccount: Boolean = false,
    onOpenAccountConsumed: () -> Unit = {},
    openLogin: Boolean = false,
    onOpenLoginConsumed: () -> Unit = {},
    openPartner: Boolean = false,
    onOpenPartnerConsumed: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    if (!state.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.locked) {
        LockScreen(onUnlock = { promptUnlock(activity, onOk = { vm.unlock() }, onFail = { }) })
        return
    }

    val forceUpdate by vm.forceUpdate.collectAsState()
    if (forceUpdate?.mustUpdate == true) {
        ForceUpdateScreen(forceUpdate!!)
        return
    }

    val onboardingDone = state.profile?.onboardingDone == true
    val loggedIn by vm.isLoggedIn.collectAsState()
    val nav = rememberNavController()
    val goAccountOrLogin: () -> Unit = {
        if (loggedIn) nav.navigate(Routes.ACCOUNT) { launchSingleTop = true }
        else nav.navigate(Routes.LOGIN) { launchSingleTop = true }
    }
    val accountRole by vm.accountRole.collectAsState()
    val isPartnerAccount = loggedIn && accountRole == "male"
    // Cycle questions come only after login and only for a woman's account; the husband skips them.
    val setupDone = isPartnerAccount || onboardingDone
    val rootScope = rememberCoroutineScope()
    val goHomeAfterAuth: (justRegistered: Boolean) -> Unit = { justRegistered ->
        rootScope.launch {
            val target = vm.homeRouteAfterAuth(justRegistered = justRegistered)
            nav.navigate(target) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    val goPartner: () -> Unit = {
        nav.navigate(if (vm.accountRole.value == "male") Routes.PARTNER_HOME else Routes.PARTNER_HUB) { launchSingleTop = true }
    }

    // A male (partner) account never sees the cycle-owner home; send it to the partner home.
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route

    // Saved session: restore server profile before deciding onboarding vs today.
    LaunchedEffect(loggedIn, isPartnerAccount, state.ready) {
        if (!state.ready || !loggedIn || isPartnerAccount) return@LaunchedEffect
        if (state.profile?.onboardingDone == true) return@LaunchedEffect
        vm.restoreSessionProfileIfNeeded()
    }

    // If sync finishes while the onboarding screen is open, leave it (login must not re-ask).
    LaunchedEffect(onboardingDone, loggedIn, currentRoute) {
        if (loggedIn && onboardingDone && currentRoute == Routes.ONBOARDING) {
            nav.navigate(Routes.TODAY) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(isPartnerAccount, currentRoute) {
        if (isPartnerAccount && currentRoute in Routes.womanMain) {
            nav.navigate(Routes.PARTNER_HOME) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openPartner, state.ready, setupDone, loggedIn) {
        if (openPartner && state.ready && setupDone && loggedIn) {
            nav.navigate(if (isPartnerAccount) Routes.PARTNER_HOME else Routes.PARTNER_HUB) { launchSingleTop = true }
            onOpenPartnerConsumed()
        } else if (openPartner && state.ready) {
            onOpenPartnerConsumed()
        }
    }

    // Logged out (or never logged in): only Login, Register and Recovery.
    LaunchedEffect(loggedIn, state.ready) {
        if (!state.ready || loggedIn) return@LaunchedEffect
        val current = nav.currentBackStackEntry?.destination?.route
        if (current == null || current !in Routes.authOnly) {
            nav.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openToday, state.ready, setupDone, loggedIn) {
        if (openToday && state.ready && setupDone && loggedIn) {
            nav.navigate(if (isPartnerAccount) Routes.PARTNER_HOME else Routes.TODAY) { launchSingleTop = true }
            onOpenTodayConsumed()
        } else if (openToday && state.ready) {
            onOpenTodayConsumed()
        }
    }

    LaunchedEffect(openAccount, state.ready, setupDone, loggedIn) {
        if (openAccount && state.ready && (setupDone || !loggedIn)) {
            goAccountOrLogin()
            onOpenAccountConsumed()
        }
    }

    LaunchedEffect(openLogin, state.ready) {
        if (openLogin && state.ready) {
            nav.navigate(Routes.LOGIN) { launchSingleTop = true }
            onOpenLoginConsumed()
        }
    }

    LaunchedEffect(deepLink, state.ready, loggedIn, onboardingDone) {
        if (!BuildConfig.DEBUG || !state.ready || deepLink == null) return@LaunchedEffect
        val host = deepLink.host ?: return@LaunchedEffect
        val path = deepLink.path?.trim('/') ?: ""
        if (host == "qa" && path == "seed") {
            if (!loggedIn) return@LaunchedEffect
            vm.seedSampleData()
            kotlinx.coroutines.delay(500)
            nav.navigate(Routes.TODAY) { popUpTo(0) { inclusive = true } }
            return@LaunchedEffect
        }
        if (host == "screen" && setupDone && loggedIn) {
            val route = when (path) {
                "today" -> Routes.TODAY
                "calendar" -> Routes.CALENDAR
                "log" -> Routes.LOG_HUB
                "daily" -> Routes.dailyLog(vm.today().toEpochDay())
                "period" -> Routes.PERIOD_LOG
                "reports" -> Routes.REPORTS
                "body" -> Routes.BODY
                "late" -> Routes.LATE
                "care" -> Routes.CARE
                "settings" -> Routes.SETTINGS
                "doctor" -> Routes.DOCTOR_PDF
                "phase" -> Routes.phaseDetail(vm.phaseTodaySelection().item.id)
                "care_today" -> Routes.phaseDetail(vm.careToday().item.id)
                "signal" -> deepLink.getQueryParameter("kind")?.let { k ->
                    deepLink.getQueryParameter("key")?.let { Routes.signalDetail(k, it) }
                }
                "science" -> Routes.PHASE_SCIENCE
                "partner" -> Routes.PARTNER_HUB
                else -> null
            }
            if (route != null) nav.navigate(route) { launchSingleTop = true }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = onboardingDone && loggedIn && !isPartnerAccount &&
        route in setOf(Routes.TODAY, Routes.CALENDAR, Routes.LOG_HUB, Routes.REPORTS, Routes.BODY)

    val startDestination = when {
        !loggedIn -> Routes.LOGIN
        isPartnerAccount -> Routes.PARTNER_HOME
        !onboardingDone -> Routes.ONBOARDING
        else -> Routes.TODAY
    }

    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar(containerColor = MahavaSurface) {
                    NavItem(nav, Routes.TODAY, "امروز", R.drawable.ic_home, route)
                    NavItem(nav, Routes.CALENDAR, "تقویم", R.drawable.ic_calendar, route)
                    NavItem(nav, Routes.LOG_HUB, "ثبت", R.drawable.ic_plus, route)
                    NavItem(nav, Routes.REPORTS, "گزارش‌ها", R.drawable.ic_reports, route)
                    NavItem(nav, Routes.BODY, "شناخت بدن", R.drawable.ic_leaf, route)
                }
            }
        },
        snackbarHost = {
            state.message?.let { msg ->
                Text(
                    msg, color = Color.White,
                    modifier = Modifier.padding(16.dp)
                        .background(MahavaTextPrimary, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("app_message")
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingFlow(vm) {
                    nav.navigate(Routes.TODAY) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(Routes.TODAY) {
                TodayScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onDailyLog = { nav.navigate(Routes.dailyLog(vm.today().toEpochDay())) },
                    onPeriod = { nav.navigate(Routes.PERIOD_LOG) },
                    onBody = { nav.navigate(Routes.BODY) },
                    onLate = { nav.navigate(Routes.LATE) },
                    onPhaseDetail = { id -> nav.navigate(Routes.phaseDetail(id)) },
                    onCareDetail = { id -> nav.navigate(Routes.phaseDetail(id)) },
                    onDailyInsight = { nav.navigate(Routes.DAILY_INSIGHT) },
                    onCravings = { nav.navigate(Routes.CRAVING_DETAIL) },
                    onPatterns = { nav.navigate(Routes.SYMPTOM_PATTERNS) },
                    onTrends = { nav.navigate(Routes.CYCLE_TRENDS) },
                    onChecker = { nav.navigate(Routes.SYMPTOM_CHECKER) },
                    onForecast = { nav.navigate(Routes.PHASE_FORECAST) },
                    onCalendar = { nav.navigate(Routes.CALENDAR) },
                    onAccount = { goAccountOrLogin() },
                    onQuickLog = { nav.navigate(Routes.QUICK_LOG) },
                    onFoodTips = { nav.navigate(Routes.FOOD_TIPS) },
                    onFertility = { nav.navigate(Routes.FERTILITY_WINDOW) },
                    onDoctor = { nav.navigate(Routes.DOCTOR_PDF) },
                    onScience = { nav.navigate(Routes.PHASE_SCIENCE) },
                    onSignal = { kind, key -> nav.navigate(Routes.signalDetail(kind, key)) },
                    onPartner = { nav.navigate(Routes.PARTNER_HUB) { launchSingleTop = true } },
                    onNotifications = { nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } },
                    onMore = { nav.navigate(Routes.LOG_HUB) }
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onAccount = { goAccountOrLogin() }
                ) { day ->
                    nav.navigate(Routes.dailyLog(day.toEpochDay()))
                }
            }
            composable(Routes.LOG_HUB) {
                LogHubScreen(
                    vm,
                    onDaily = { nav.navigate(Routes.dailyLog(vm.today().toEpochDay())) },
                    onPeriod = { nav.navigate(Routes.PERIOD_LOG) },
                    onLate = { nav.navigate(Routes.LATE) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(
                route = Routes.DAILY_LOG,
                arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val dayArg = entry.arguments?.getLong("day") ?: -1L
                val day = if (dayArg >= 0) LocalDate.ofEpochDay(dayArg) else vm.today()
                DailyLogScreen(vm, day, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.PERIOD_LOG) { PeriodLogScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onDoctor = { nav.navigate(Routes.DOCTOR_PDF) },
                    onLate = { nav.navigate(Routes.LATE) },
                    onPatterns = { nav.navigate(Routes.SYMPTOM_PATTERNS) },
                    onTrends = { nav.navigate(Routes.CYCLE_TRENDS) },
                    onChecker = { nav.navigate(Routes.SYMPTOM_CHECKER) },
                    onInsight = { nav.navigate(Routes.DAILY_INSIGHT) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(Routes.DAILY_INSIGHT) {
                DailyInsightScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.CRAVING_DETAIL) {
                CravingDetailScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.PHASE_FORECAST) {
                PhaseForecastScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onCravings = { nav.navigate(Routes.CRAVING_DETAIL) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(Routes.SYMPTOM_PATTERNS) {
                SymptomPatternsScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.CYCLE_TRENDS) {
                CycleTrendsScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.SYMPTOM_CHECKER) {
                SymptomCheckerScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.DOCTOR_PDF) {
                DoctorReportScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.BODY) {
                BodyHomeScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onCategory = { nav.navigate(Routes.bodyCat(it)) },
                    onCare = { nav.navigate(Routes.CARE) },
                    onPhaseDetail = { nav.navigate(Routes.phaseDetail(it)) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(
                Routes.BODY_CAT,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { entry ->
                val cat = entry.arguments?.getString("category") ?: return@composable
                BodyCategoryScreen(
                    vm, cat,
                    onBack = { nav.popBackStack() },
                    onAccount = { goAccountOrLogin() }
                ) { id ->
                    nav.navigate(Routes.phaseDetail(id))
                }
            }
            composable(
                Routes.BODY_ITEM,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PhaseDetailScreen(vm, id, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(
                Routes.PHASE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PhaseDetailScreen(vm, id, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.LATE) {
                LateTestScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onLogPeriod = { nav.navigate(Routes.PERIOD_LOG) },
                    onOpenItem = { nav.navigate(Routes.phaseDetail(it)) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(Routes.PARTNER_HUB) {
                PartnerHubScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.PARTNER_HOME) {
                PartnerHomeScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onAccount = { goAccountOrLogin() },
                    onNotifications = { nav.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true } }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(vm, onBack = { nav.popBackStack() }, onOpenPartner = goPartner)
            }
            composable(Routes.CARE) {
                CareScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onOpenItem = { nav.navigate(Routes.phaseDetail(it)) },
                    onAccount = { goAccountOrLogin() }
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    vm,
                    // No back when login is required; user must sign in or register.
                    onBack = if (loggedIn) ({ nav.popBackStack() }) else null,
                    onGoRegister = { nav.navigate(Routes.REGISTER) { launchSingleTop = true } },
                    onForgotPassword = { nav.navigate(Routes.PASSWORD_RECOVERY) { launchSingleTop = true } },
                    onSuccess = { goHomeAfterAuth(false) }
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onGoLogin = { nav.navigate(Routes.LOGIN) { launchSingleTop = true } },
                    onSuccess = { goHomeAfterAuth(true) }
                )
            }
            composable(Routes.PASSWORD_RECOVERY) {
                PasswordRecoveryScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onLogin = { nav.navigate(Routes.LOGIN) },
                    onRegister = { nav.navigate(Routes.REGISTER) }
                )
            }
            composable(
                route = Routes.SIGNAL_DETAIL,
                arguments = listOf(
                    navArgument("kind") { type = NavType.StringType },
                    navArgument("key") { type = NavType.StringType }
                )
            ) { entry ->
                SignalDetailScreen(
                    vm,
                    kind = entry.arguments?.getString("kind") ?: "mood",
                    key = entry.arguments?.getString("key") ?: "",
                    onBack = { nav.popBackStack() }
                )
            }
            composable(Routes.PHASE_SCIENCE) {
                PhaseScienceScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.QUICK_LOG) {
                QuickSymptomLogScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.FOOD_TIPS) {
                PhaseFoodTipsScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.FERTILITY_WINDOW) {
                FertilityWindowScreen(vm, onBack = { nav.popBackStack() }, onAccount = { goAccountOrLogin() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    vm, activity,
                    onBack = { nav.popBackStack() },
                    onAccount = { goAccountOrLogin() },
                    onPartner = { nav.navigate(Routes.PARTNER_HUB) { launchSingleTop = true } },
                    onAfterDeleteAll = {
                        nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                    }
                )
            }
        }
        AppOverlays(
            vm,
            active = loggedIn && route != null && route !in Routes.authOnly && route != Routes.ONBOARDING,
            onOpenPartner = goPartner
        )
        }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            vm.clearMessage()
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("lock_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Illustration(R.drawable.ill_privacy_shield, Modifier.height(160.dp))
        Spacer(Modifier.height(16.dp))
        Text("ماه قفل است", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        QuietInfo("برای دیدن اطلاعاتت، با اثر انگشت یا رمز گوشی قفل رو باز کن.")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("باز کردن قفل", onClick = onUnlock)
    }
}

@Composable
private fun RowScope.NavItem(nav: androidx.navigation.NavHostController, route: String, label: String, icon: Int, current: String?) {
    val selected = current == route
    NavigationBarItem(
        selected = selected,
        onClick = {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        modifier = Modifier.testTag("nav_$route"),
        icon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (selected) MahavaPrimary else MahavaTextSecondary
            )
        },
        label = { Text(label, color = if (selected) MahavaPrimary else MahavaTextSecondary, maxLines = 1) },
        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFEEE8FB))
    )
}

@Composable
private fun ForceUpdateScreen(info: com.mahava.app.network.MahForceUpdateDto) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(MahavaBackground).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("به‌روزرسانی لازم است", style = MaterialTheme.typography.headlineLarge, color = MahavaTextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            QuietInfo(info.messageFa.ifBlank { "نسخهٔ اپ قدیمی است. لطفاً آخرین نسخه را نصب کن." })
            Spacer(Modifier.height(20.dp))
            if (info.updateDownloadUrl.isNotBlank()) {
                PrimaryButton("دانلود آخرین نسخه") {
                    try {
                        val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.updateDownloadUrl))
                        ctx.startActivity(i)
                    } catch (_: Throwable) { }
                }
            }
        }
    }
}
