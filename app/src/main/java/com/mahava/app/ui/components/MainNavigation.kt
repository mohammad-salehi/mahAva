package com.mahava.app.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.mahava.app.R
import com.mahava.app.ui.navigation.Routes
import com.mahava.app.ui.theme.*

@Composable
fun MainNavigation(current: String?, onNavigate: (String) -> Unit) {
    NavigationBar(containerColor = MahavaSurface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        listOf(Triple(Routes.TODAY,"امروز",R.drawable.design_home),
            Triple(Routes.CALENDAR,"تقویم",R.drawable.design_calendar),
            Triple(Routes.LOG_HUB,"ثبت",R.drawable.design_plus),
            Triple(Routes.REPORTS,"گزارش‌ها",R.drawable.design_chart),
            Triple(Routes.BODY,"شناخت بدن",R.drawable.design_book)).forEach { (route,label,icon) ->
            val selected = current == route || when(route) {
                Routes.LOG_HUB -> current?.startsWith("daily_log") == true || current in listOf(Routes.PERIOD_LOG,Routes.LATE)
                Routes.BODY -> current?.startsWith("body_") == true || current?.startsWith("health_insight") == true
                Routes.REPORTS -> current in listOf(Routes.HEALTH_ANALYTICS,Routes.DOCTOR_PDF,Routes.CYCLE_TRENDS,Routes.SYMPTOM_PATTERNS)
                else -> false
            }
            NavigationBarItem(selected = selected, onClick = { onNavigate(route) }, modifier = Modifier.testTag("nav_$route"),
                icon = { Icon(painterResource(icon),contentDescription=label) },
                label = { Text(label,style=MaterialTheme.typography.bodySmall) },
                colors=NavigationBarItemDefaults.colors(selectedIconColor=MahavaPrimaryDeep,selectedTextColor=MahavaPrimaryDeep,
                    unselectedIconColor=MahavaTextSecondary,unselectedTextColor=MahavaTextSecondary,indicatorColor=MahavaPrimarySoft))
        }
    }
}
