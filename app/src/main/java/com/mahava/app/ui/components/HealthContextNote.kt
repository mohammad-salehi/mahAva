package com.mahava.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mahava.app.ui.theme.MahavaTextSecondary

enum class HealthNote(val textFa: String) {
    GENERAL("این مطلب برای آگاهی عمومی است و جایگزین نظر پزشک یا ماما نیست. ممکن است با شرایط شما سازگار نباشد؛ برای تصمیم‌های پزشکی با متخصص مشورت کنید."),
    PREDICTION("این پیش‌بینی بر اساس اطلاعات ثبت‌شده و تخمینی است؛ زمان واقعی پریود و تخمک‌گذاری ممکن است متفاوت باشد. برای جلوگیری از بارداری به این پیش‌بینی تکیه نکنید."),
    FETAL_GROWTH("این اطلاعات، رشد معمول جنین در این هفته را توضیح می‌دهد و وضعیت یا اندازهٔ واقعی جنین شما را مشخص نمی‌کند. ارزیابی رشد و سلامت جنین نیازمند مراقبت پزشکی است.")
}

@Composable
fun HealthContextNote(note: HealthNote) {
    Text(
        text = note.textFa,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 20.sp),
        color = MahavaTextSecondary
    )
}
