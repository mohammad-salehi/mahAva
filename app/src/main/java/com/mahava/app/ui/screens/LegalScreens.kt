package com.mahava.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mahava.app.content.LegalDocuments
import com.mahava.app.ui.components.QuietInfo
import com.mahava.app.ui.components.ScreenHeader
import com.mahava.app.ui.theme.MahavaTextPrimary
import com.mahava.app.ui.theme.MahavaTextSecondary

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocScreen(
        title = "سیاست حفظ حریم خصوصی",
        body = LegalDocuments.privacyFa,
        testTag = "privacy_screen",
        onBack = onBack
    )
}

@Composable
fun TermsOfUseScreen(onBack: () -> Unit) {
    LegalDocScreen(
        title = "شرایط استفاده",
        body = LegalDocuments.termsFa,
        testTag = "terms_screen",
        onBack = onBack
    )
}

@Composable
private fun LegalDocScreen(title: String, body: String, testTag: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(testTag)
    ) {
        ScreenHeader(title, onBack = onBack)
        QuietInfo("آخرین به‌روزرسانی: ${LegalDocuments.UPDATED_AT_FA}")
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
                    "• ${line.removePrefix("- ")}",
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
