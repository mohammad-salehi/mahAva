package com.mahava.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Version policy returned by the backend (admin-managed). */
data class AppVersionInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val minSupportedVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val mustUpdate: Boolean = false,
    val shouldUpdate: Boolean = false,
    val messageFa: String = "",
    val updateMessageFa: String = "",
    val bazaarUrl: String = "",
    val webUrl: String = ""
)

private data class UpgradeRequiredBody(val message: String? = null, val update: AppVersionInfo? = null)

/**
 * App-wide update state. [forced] = blocking screen (below minimum or HTTP 426),
 * [available] = dismissible prompt (below latest).
 */
object AppUpdateState {
    const val HEADER_CODE = "X-App-Version"
    const val HEADER_NAME = "X-App-Version-Name"
    val forced = MutableStateFlow<AppVersionInfo?>(null)
    val available = MutableStateFlow<AppVersionInfo?>(null)
    private val gson = Gson()

    /** Called by the HTTP layer whenever the server answers 426 Upgrade Required. */
    fun onUpgradeRequired(body: String?) {
        val parsed = runCatching { gson.fromJson(body, UpgradeRequiredBody::class.java) }.getOrNull()
        val info = parsed?.update ?: AppVersionInfo()
        forced.value = info.copy(mustUpdate = true, messageFa = info.messageFa.ifBlank { parsed?.message.orEmpty() })
    }

    suspend fun check(baseUrl: String, path: String, versionCode: Int, versionName: String) {
        val info = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(baseUrl.trimEnd('/') + path + "?versionCode=" + versionCode).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(HEADER_CODE, versionCode.toString())
                    setRequestProperty(HEADER_NAME, versionName)
                }
                try {
                    if (conn.responseCode !in 200..299) null
                    else conn.inputStream.bufferedReader(Charsets.UTF_8).use { gson.fromJson(it.readText(), AppVersionInfo::class.java) }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        } ?: return
        if (info.mustUpdate) {
            forced.value = info
        } else {
            forced.value = null
            available.value = if (info.shouldUpdate) info else null
        }
    }
}

fun openBazaar(ctx: Context, info: AppVersionInfo) {
    val pkg = ctx.packageName
    val bazaar = info.bazaarUrl.ifBlank { "bazaar://details?id=$pkg" }
    val web = info.webUrl.ifBlank { "https://cafebazaar.ir/app/$pkg" }
    val viaBazaar = Intent(Intent.ACTION_VIEW, Uri.parse(bazaar))
        .setPackage("com.farsitel.bazaar")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { ctx.startActivity(viaBazaar) }.isFailure) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** Draw last in setContent: checks on every start/foreground and overlays the update UI. */
@Composable
fun AppUpdateOverlay(baseUrl: String, path: String, versionCode: Int, versionName: String) {
    val scope = rememberCoroutineScope()
    LifecycleStartEffect(Unit) {
        val job = scope.launch { AppUpdateState.check(baseUrl, path, versionCode, versionName) }
        onStopOrDispose { job.cancel() }
    }
    val forced by AppUpdateState.forced.collectAsState()
    val available by AppUpdateState.available.collectAsState()
    var dismissedFor by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val f = forced
        if (f != null) {
            BackHandler { (ctx as? Activity)?.finish() }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("به‌روزرسانی لازم است", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        f.messageFa.ifBlank { "این نسخه دیگر پشتیبانی نمی‌شود. لطفاً نسخهٔ جدید را از کافه‌بازار نصب کنید." },
                        textAlign = TextAlign.Center
                    )
                    if (f.latestVersionName.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("نسخهٔ جدید: ${f.latestVersionName}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { openBazaar(ctx, f) }, modifier = Modifier.fillMaxWidth()) {
                        Text("نصب نسخهٔ جدید از کافه‌بازار")
                    }
                }
            }
        } else {
            val a = available
            if (a != null && dismissedFor != a.latestVersionCode) {
                AlertDialog(
                    onDismissRequest = { dismissedFor = a.latestVersionCode },
                    title = { Text("نسخهٔ جدید آماده است") },
                    text = { Text(a.updateMessageFa.ifBlank { "نسخهٔ ${a.latestVersionName} منتشر شده است. پیشنهاد می‌کنیم به‌روزرسانی کنید." }) },
                    confirmButton = { TextButton(onClick = { dismissedFor = a.latestVersionCode; openBazaar(ctx, a) }) { Text("به‌روزرسانی") } },
                    dismissButton = { TextButton(onClick = { dismissedFor = a.latestVersionCode }) { Text("بعداً") } }
                )
            }
        }
    }
}
