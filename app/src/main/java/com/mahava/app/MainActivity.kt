package com.mahava.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.fragment.app.FragmentActivity
import com.mahava.app.ui.AppViewModel
import com.mahava.app.ui.AppViewModelFactory
import com.mahava.app.ui.MahavaAppRoot
import com.mahava.app.ui.theme.MahavaBackground
import com.mahava.app.ui.theme.MahavaTheme

class MainActivity : FragmentActivity() {
    companion object {
        const val EXTRA_OPEN_TODAY = "open_today"
        const val EXTRA_OPEN_ACCOUNT = "open_account"
        const val EXTRA_OPEN_LOGIN = "open_login"
        const val EXTRA_OPEN_PARTNER = "open_partner"
    }

    private val deepLinkState = mutableStateOf<android.net.Uri?>(null)
    private val openTodayState = mutableStateOf(false)
    private val openAccountState = mutableStateOf(false)
    private val openLoginState = mutableStateOf(false)
    private val openPartnerState = mutableStateOf(false)
    private var viewModelRef: AppViewModel? = null

    override fun onStart() {
        super.onStart()
        com.mahava.app.notice.AppNotices.foreground = true
        viewModelRef?.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) com.mahava.app.notice.AppNotices.foreground = false
        // Ignore config changes (rotation); only a real trip to the background starts the lock timer.
        if (!isChangingConfigurations) viewModelRef?.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            MahavaTheme {
                Surface(Modifier.fillMaxSize(), color = MahavaBackground) {
                    val factory = remember { AppViewModelFactory(application as MahavaApplication) }
                    val vm: AppViewModel = viewModel(factory = factory)
                    viewModelRef = vm
                    val deepLink by deepLinkState
                    val openToday by openTodayState
                    val openAccount by openAccountState
                    val openLogin by openLoginState
                    val openPartner by openPartnerState
                    val useJalali = vm.state.collectAsState().value.profile?.calendarType != "gregorian"
                    androidx.compose.runtime.CompositionLocalProvider(com.mahava.app.ui.components.LocalUseJalali provides useJalali) {
                        MahavaAppRoot(
                            vm = vm,
                            activity = this,
                            deepLink = deepLink,
                            openToday = openToday,
                            onOpenTodayConsumed = { openTodayState.value = false },
                            openAccount = openAccount,
                            onOpenAccountConsumed = { openAccountState.value = false },
                            openLogin = openLogin,
                            onOpenLoginConsumed = { openLoginState.value = false },
                            openPartner = openPartner,
                            onOpenPartnerConsumed = { openPartnerState.value = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        deepLinkState.value = intent?.data
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            openTodayState.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_ACCOUNT, false) == true) {
            openAccountState.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_LOGIN, false) == true) {
            openLoginState.value = true
        }
        if (intent?.getBooleanExtra(EXTRA_OPEN_PARTNER, false) == true) {
            openPartnerState.value = true
        }
    }
}
