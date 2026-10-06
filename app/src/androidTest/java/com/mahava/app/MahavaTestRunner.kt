package com.mahava.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.test.runner.AndroidJUnitRunner
import com.mahava.app.data.db.MahavaDatabase
import com.mahava.app.util.AppClock
import com.mahava.app.util.FakeAppClock
import java.time.LocalDate

/** Swaps in [MahavaTestApplication] so every instrumented test gets an isolated in-memory DB and a fixed date. */
class MahavaTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, MahavaTestApplication::class.java.name, context)
}

/**
 * Test-only application:
 *  - in-memory Room DB (never touches the real user database file),
 *  - clock fixed at 2026-10-06 (Asia/Tehran) so dates in assertions are stable,
 *  - activities shown over the keyguard and kept awake, ONLY in this test build.
 */
class MahavaTestApplication : MahavaApplication() {
    val fakeClock = FakeAppClock(TEST_TODAY)

    override fun createClock(): AppClock = fakeClock
    override fun createDatabase(): MahavaDatabase = MahavaDatabase.build(this, inMemory = true)

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) {
                if (Build.VERSION.SDK_INT >= 27) { a.setShowWhenLocked(true); a.setTurnScreenOn(true) }
                @Suppress("DEPRECATION")
                a.window.addFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    companion object { val TEST_TODAY: LocalDate = LocalDate.of(2026, 10, 6) }
}
