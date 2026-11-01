package com.mahava.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mahava.app.ui.AppViewModel
import kotlinx.coroutines.launch

/** Guest -> phone + OTP + password before buying. Same account on the server, so nothing is lost. */
@Composable
fun MahGuestClaimDialog(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (busy) return
        if (done) { vm.dismissGuestClaim(); return }
        busy = true
        error = null
        scope.launch {
            if (!otpSent) {
                val r = vm.requestOtp(phone, "register")
                if (r.error == null) otpSent = true else error = r.error
            } else {
                val err = vm.claimGuest(phone, otp, password)
                if (err == null) done = true else error = err
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) vm.dismissGuestClaim() },
        title = { Text("ثبت شماره همراه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (done) {
                    Text("شماره‌ات ثبت شد و اطلاعاتت سر جاشه. حالا می‌تونی اشتراک رو بخری.")
                } else {
                    Text("برای خرید اشتراک، شماره همراهت رو ثبت کن. اطلاعاتی که تا الان وارد کردی حفظ می‌شه.")
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; if (otpSent) { otpSent = false; otp = "" } },
                        label = { Text("شماره همراه") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (otpSent) {
                        OutlinedTextField(
                            value = otp,
                            onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                            label = { Text("کد تأیید") },
                            singleLine = true,
                            enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("رمز عبور (حداقل ۶ کاراکتر)") },
                            singleLine = true,
                            enabled = !busy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { submit() }) {
                Text(if (done) "باشه" else if (otpSent) "تأیید" else "ارسال کد تأیید")
            }
        },
        dismissButton = {
            if (!done) TextButton(enabled = !busy, onClick = { vm.dismissGuestClaim() }) { Text("انصراف") }
        }
    )
}
