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
    private val deepLinkState = mutableStateOf<android.net.Uri?>(null)
    private var viewModelRef: AppViewModel? = null

    override fun onStart() {
        super.onStart()
        viewModelRef?.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        // Ignore config changes (rotation); only a real trip to the background starts the lock timer.
        if (!isChangingConfigurations) viewModelRef?.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkState.value = intent?.data
        setContent {
            MahavaTheme {
                Surface(Modifier.fillMaxSize(), color = MahavaBackground) {
                    val factory = remember { AppViewModelFactory(application as MahavaApplication) }
                    val vm: AppViewModel = viewModel(factory = factory)
                    viewModelRef = vm
                    val deepLink by deepLinkState
                    val useJalali = vm.state.collectAsState().value.profile?.calendarType != "gregorian"
                    androidx.compose.runtime.CompositionLocalProvider(com.mahava.app.ui.components.LocalUseJalali provides useJalali) {
                        MahavaAppRoot(vm = vm, activity = this, deepLink = deepLink)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkState.value = intent.data
    }
}
