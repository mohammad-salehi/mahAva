package com.mahava.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mahava.app.data.db.DailyLogEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
/**
 * Compact on-device walk through the main flows. Touches are injected inside the app process by the
 * Compose test framework (no `adb shell input` needed). DB is in-memory, date fixed at 2026-10-06.
 */
@RunWith(AndroidJUnit4::class)
class MainFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun setUp() { Seed.reset() }

    @Test fun onboardingWithIDontKnow() {
        launchMain().use {
            compose.waitTag("onboarding")
            Shots.take(compose, "01_onboarding_welcome")
            compose.tap("goal_body_awareness")
            compose.tap("onb_start")
            compose.tap("onb_last_unknown")
            compose.tap("onb_cycle_unknown")
            compose.tap("onb_bleed_unknown")
            Shots.take(compose, "02_onboarding_dont_know")
            compose.tap("onb_next_cycle")
            compose.tap("onb_regular_unknown")
            compose.tap("onb_next_conditions")
            compose.tap("onb_enter")
            compose.waitTag("today_screen")
            compose.waitFor { Seed.profileNow()?.onboardingDone == true }
            val p = Seed.profileNow()!!
            assertNull(p.lastPeriodStartEpochDay)
            assertTrue(p.cycleLengthUnknown)
            assertTrue(Seed.periods().isEmpty())
            compose.waitTag("no_estimate_card")
            Shots.take(compose, "03_today_no_data")
        }
    }

    @Test fun onboardingWithDates() {
        launchMain().use {
            compose.tap("onb_start")
            compose.tap("onb_pick_date")        // month-grid picker, defaults to today
            compose.waitTag("date_picker")
            compose.tap("pick_${today.minusDays(6)}")  // six days ago
            assertFalse("future day must not be selectable", compose.exists("pick_${today.plusDays(1)}") &&
                runCatching { compose.onNodeWithTag("pick_${today.plusDays(1)}").assertHasClickAction() }.isSuccess)
            Shots.take(compose, "15_date_picker")
            compose.tap("pick_ok")
            compose.tap("cycle_plus")           // 28
            compose.tap("bleed_plus")           // 5
            compose.tap("onb_next_cycle")
            compose.tap("onb_regular")
            compose.tap("onb_next_conditions")
            compose.tap("onb_enter")
            compose.waitTag("today_screen")
            compose.waitFor { Seed.periods().isNotEmpty() }
            val per = Seed.periods().single()
            assertEquals(today.minusDays(6).toEpochDay(), per.startEpochDay)
            assertFalse(per.stillOngoing)
            assertEquals(28, Seed.profileNow()!!.typicalCycleLength)
            compose.waitTag("period_estimate_card")
        }
    }

    @Test fun todayPhaseDetailLogCalendarReports() {
        Seed.regularHistory(cycleDayToday = 25)   // pre-menstrual days of a 28-day cycle
        Seed.log(DailyLogEntity(epochDay = today.minusDays(27).toEpochDay(), physicalSymptoms = "bloating", painScore = 3, createdAt = 1, updatedAt = 1))
        launchMain().use {
            compose.waitTag("today_screen")
            Shots.take(compose, "04_today_top")
            compose.onNodeWithTag("phase_card").performScrollTo()
            Shots.take(compose, "05_today_phase_card")

            // Phase detail page with all template sections
            compose.tap("phase_card")
            compose.waitTag("detail_screen")
            listOf("امروز", "در سابقه تو", "توضیح علمی", "چه کاری می‌توانی انجام بدهی؟", "چه زمانی بررسی لازم است؟", "منبع",
                "مبتنی بر منابع؛ بازبینی پزشکی مستقل انجام نشده").forEach { assertTrue("missing $it", compose.hasText(it)) }
            assertTrue(compose.exists("hormone_chart"))
            assertTrue("history should use the real log", compose.hasText("نفخ"))
            Shots.take(compose, "06_phase_detail_top")
            compose.onAllNodesWithText("توضیح علمی", substring = true)[0].performScrollTo()
            Shots.take(compose, "07_phase_detail_science")
            compose.tap("detail_back")
            compose.waitTag("today_screen")

            // Daily log: save, edit, no duplicate
            compose.tap("today_log_button")
            compose.waitTag("daily_log_screen")
            compose.tap("mood_sad"); compose.tap("energy_low"); compose.tap("sym_bloating")
            Shots.take(compose, "08_daily_log")
            compose.tap("save_daily")
            compose.waitFor { Seed.logFor(today) != null }
            assertEquals("sad", Seed.logFor(today)!!.moods)
            compose.waitTag("today_screen")
            compose.tap("today_log_button")
            compose.tap("energy_medium")
            compose.tap("save_daily")
            compose.waitFor { Seed.logFor(today)?.energy == "medium" }
            assertEquals(2, Seed.logs().size)
            assertEquals("sad", Seed.logFor(today)!!.moods)

            // Calendar: month navigation, tap a day, edit it, future day blocked
            compose.tap("nav_calendar")
            compose.waitTag("calendar_screen")
            assertTrue(compose.hasText("مهر ۱۴۰۵"))
            compose.tap("cal_next"); assertTrue(compose.hasText("آبان"))
            compose.tap("cal_prev"); compose.tap("cal_prev"); assertTrue(compose.hasText("شهریور"))
            compose.tap("cal_today"); assertTrue(compose.hasText("مهر ۱۴۰۵"))
            compose.tap("day_${today.plusDays(3)}")
            assertFalse(compose.exists("cal_edit_day"))
            compose.tap("day_${today.minusDays(3)}")
            Shots.take(compose, "09_calendar")
            compose.tap("cal_edit_day")
            compose.waitTag("daily_log_screen")
            compose.tap("sym_headache")
            compose.tap("save_daily")
            compose.waitFor { Seed.logFor(today.minusDays(3))?.physicalSymptoms?.contains("headache") == true }

            // Reports with data, body section
            compose.tap("nav_reports")
            compose.waitTag("reports_lengths")
            assertTrue(compose.hasText("۲۸"))
            Shots.take(compose, "10_reports")
            compose.tap("nav_body")
            compose.waitTag("body_screen")
            Shots.take(compose, "11_body")
            compose.tap("cat_cycle_hormones")
            compose.waitTag("category_screen")
        }
    }

    @Test fun periodStartEndAndLatePeriod() {
        Seed.profile()
        Seed.period(today.minusDays(35), today.minusDays(31))   // 7 days late
        launchMain().use {
            compose.waitTag("late_card")
            Shots.take(compose, "12_today_late")
            compose.tap("late_open")
            compose.waitTag("late_screen")
            compose.tap("test_negative")
            compose.tap("save_test")
            compose.waitFor { Seed.logFor(today)?.pregnancyTest == "negative" }

            compose.waitTag("today_screen")
            compose.tap("today_period_button")
            compose.waitTag("period_log_screen")
            compose.tap("save_period")
            compose.waitFor { Seed.periods().any { it.startEpochDay == today.toEpochDay() && it.stillOngoing } }
            compose.waitTag("today_screen")
            compose.tap("today_period_button")
            compose.waitTag("period_log_screen")
            compose.tap("save_period")
            compose.waitFor { Seed.periods().any { it.startEpochDay == today.toEpochDay() && it.endEpochDay == today.toEpochDay() } }
            assertEquals(2, Seed.periods().size)
        }
    }

    @Test fun settingsPrivacyAndDelete() {
        Seed.regularHistory(cycleDayToday = 10)
        launchMain().use {
            compose.tap("open_settings")
            compose.waitTag("settings_screen")
            Shots.take(compose, "13_settings")
            compose.tap("set_private_notif")
            compose.waitFor { Seed.profileNow()?.privateNotifications == false }

            val before = Seed.periods().size
            compose.tap("delete_all")
            compose.waitTag("delete_confirm")
            Shots.take(compose, "14_delete_dialog")
            compose.tap("delete_cancel")
            assertFalse(compose.exists("delete_confirm"))
            assertEquals(before, Seed.periods().size)
            compose.tap("delete_all")
            compose.tap("delete_confirm")
            compose.waitTag("onboarding")
            compose.waitFor { Seed.periods().isEmpty() && Seed.profileNow()?.onboardingDone != true }
        }
    }
}
