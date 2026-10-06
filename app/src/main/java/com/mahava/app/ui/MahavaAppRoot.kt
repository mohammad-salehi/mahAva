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
import androidx.compose.material3.Surface
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
import com.mahava.app.ui.theme.MahavaPrimary
import com.mahava.app.ui.theme.MahavaSurface
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary
import java.time.LocalDate

@Composable
fun MahavaAppRoot(vm: AppViewModel, activity: FragmentActivity, deepLink: android.net.Uri? = null) {
    val state by vm.state.collectAsState()
    if (!state.ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.locked) {
        LockScreen(onUnlock = { promptUnlock(activity, onOk = { vm.unlock() }, onFail = { }) })
        return
    }

    val onboardingDone = state.profile?.onboardingDone == true
    val nav = rememberNavController()

    LaunchedEffect(deepLink, state.ready) {
        if (!BuildConfig.DEBUG || !state.ready || deepLink == null) return@LaunchedEffect
        val host = deepLink.host ?: return@LaunchedEffect
        val path = deepLink.path?.trim('/') ?: ""
        if (host == "qa" && path == "seed") {
            // Debug builds only (intent filter lives in src/debug). Data is flagged qaSampleData and labeled on Today.
            vm.seedSampleData()
            kotlinx.coroutines.delay(500)
            nav.navigate(Routes.TODAY) { popUpTo(0) { inclusive = true } }
            return@LaunchedEffect
        }
        if (host == "screen" && onboardingDone) {
            // Debug-only navigation shortcuts for screenshots. Never seeds or changes data.
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
                else -> null
            }
            if (route != null) nav.navigate(route) { launchSingleTop = true }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBottom = onboardingDone && route in setOf(Routes.TODAY, Routes.CALENDAR, Routes.LOG_HUB, Routes.REPORTS, Routes.BODY)

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
                // Plain text (not a Surface) so it never swallows taps on what is underneath.
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
            startDestination = if (onboardingDone) Routes.TODAY else Routes.ONBOARDING,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingFlow(vm) {
                    nav.navigate(Routes.TODAY) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
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
                    onCareDetail = { id -> nav.navigate(Routes.phaseDetail(id)) }
                )
            }
            composable(Routes.CALENDAR) {
                CalendarScreen(vm, onSettings = { nav.navigate(Routes.SETTINGS) }) { day ->
                    nav.navigate(Routes.dailyLog(day.toEpochDay()))
                }
            }
            composable(Routes.LOG_HUB) {
                LogHubScreen(
                    vm,
                    onDaily = { nav.navigate(Routes.dailyLog(vm.today().toEpochDay())) },
                    onPeriod = { nav.navigate(Routes.PERIOD_LOG) },
                    onLate = { nav.navigate(Routes.LATE) },
                    onSettings = { nav.navigate(Routes.SETTINGS) }
                )
            }
            composable(
                route = Routes.DAILY_LOG,
                arguments = listOf(navArgument("day") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val dayArg = entry.arguments?.getLong("day") ?: -1L
                val day = if (dayArg >= 0) LocalDate.ofEpochDay(dayArg) else vm.today()
                DailyLogScreen(vm, day) { nav.popBackStack() }
            }
            composable(Routes.PERIOD_LOG) { PeriodLogScreen(vm) { nav.popBackStack() } }
            composable(Routes.REPORTS) {
                ReportsScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onDoctor = { nav.navigate(Routes.DOCTOR_PDF) },
                    onLate = { nav.navigate(Routes.LATE) }
                )
            }
            composable(Routes.DOCTOR_PDF) { DoctorReportScreen(vm) { nav.popBackStack() } }
            composable(Routes.BODY) {
                BodyHomeScreen(
                    vm,
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onCategory = { nav.navigate(Routes.bodyCat(it)) },
                    onCare = { nav.navigate(Routes.CARE) },
                    onPhaseDetail = { nav.navigate(Routes.phaseDetail(it)) }
                )
            }
            composable(
                Routes.BODY_CAT,
                arguments = listOf(navArgument("category") { type = NavType.StringType })
            ) { entry ->
                val cat = entry.arguments?.getString("category") ?: return@composable
                BodyCategoryScreen(vm, cat, onBack = { nav.popBackStack() }) { id ->
                    nav.navigate(Routes.phaseDetail(id))
                }
            }
            composable(
                Routes.BODY_ITEM,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PhaseDetailScreen(vm, id) { nav.popBackStack() }
            }
            composable(
                Routes.PHASE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                PhaseDetailScreen(vm, id) { nav.popBackStack() }
            }
            composable(Routes.LATE) {
                LateTestScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onLogPeriod = { nav.navigate(Routes.PERIOD_LOG) },
                    onOpenItem = { nav.navigate(Routes.phaseDetail(it)) }
                )
            }
            composable(Routes.CARE) {
                CareScreen(vm, onBack = { nav.popBackStack() }, onOpenItem = { nav.navigate(Routes.phaseDetail(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(vm, activity, onBack = { nav.popBackStack() }, onAfterDeleteAll = {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
                })
            }
        }
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
        Text("ماه‌آوا قفل است", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        QuietInfo("برای دیدن اطلاعاتت، با اثر انگشت یا رمز گوشی قفل را باز کن.")
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
