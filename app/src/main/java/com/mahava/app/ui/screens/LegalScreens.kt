package com.mahava.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.MahavaApplication
import com.mahava.app.content.LegalDocuments
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocScreen(title = "حریم خصوصی و شرایط استفاده", privacy = true, testTag = "privacy_screen", onBack = onBack)
}

@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    LegalDocScreen(title = "حریم خصوصی و شرایط استفاده", privacy = false, testTag = "terms_screen", onBack = onBack)
}

/** The single combined Mah document (backend legal-mah-fa.md), cached for offline use. */
@Composable
private fun LegalDocScreen(title: String, privacy: Boolean, testTag: String, onBack: () -> Unit) {
    val context = LocalContext.current
    LegalDocuments.load(context)
    var body by remember { mutableStateOf(LegalDocuments.privacyFa) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val app = context.applicationContext as? MahavaApplication
        if (app != null) {
            LegalDocuments.refresh(context) { app.apiClient.extraCall("GET", "/api/mah/auth/legal", null, null) }
        }
        body = LegalDocuments.privacyFa
        loading = false
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(testTag)
    ) {
        ScreenHeader(title, onBack = onBack)
        if (loading && body.isBlank()) LinearProgressIndicator(Modifier.padding(vertical = 8.dp))
        if (!loading && body.isBlank()) QuietInfo(LegalDocuments.OFFLINE_FA)
        Spacer(Modifier.height(12.dp))
        body.lineSequence().forEach { line ->
            when {
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MahavaTextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium,
                    color = MahavaTextPrimary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                line.startsWith("### ") -> Text(
                    line.removePrefix("### "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MahavaTextPrimary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                line.startsWith("- ") -> Text(
                    "• ${line.removePrefix("- ").replace("**", "")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MahavaTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                else -> Text(
                    line.replace("**", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MahavaTextPrimary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
