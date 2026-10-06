# گزارش تست ماه‌آوا

تاریخ: 2026-10-06 (Asia/Tehran)

## ساخت
- assembleDebug: موفق
- APK: C:\Users\A.R.I\Desktop\app\mahava\Mahava-debug.apk
- JAVA_HOME: Android Studio JBR 21
- Gradle: 8.13 / AGP 8.13.0

## Unit tests
- اجرا شد: 25
- ناموفق: 0
- خطا: 0

جزئیات: Jalali، CycleEngine، Pattern، ContentBank schema، Persistence/Backup (درست/غلط/خراب)، Crypto PBKDF2، ScreenshotSmoke (تلاش capture؛ فایل واقعی به‌خاطر ComposeTimeout ثبت نشد — CAPTURE_FAILED.txt).

## Lint
- lintDebug شروع شد در یک اجرا؛ به‌خاطر شکست تست در همان فرمان کامل گزارش lint در همان اجرا قطع شد. برای گزارش نهایی در صورت نیاز جداگانه gradlew :app:lintDebug را اجرا کنید.

## Manifest (merged)
مجوزها پس از حذف ادغام WorkManager: POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, VIBRATE, USE_BIOMETRIC, USE_FINGERPRINT, WAKE_LOCK, DYNAMIC_RECEIVER_NOT_EXPORTED.
بدون INTERNET و بدون ACCESS_NETWORK_STATE / FOREGROUND_SERVICE / SCHEDULE_EXACT_ALARM.

## Screenshot
- Robolectric captureToImage: ناموفق (ComposeTimeoutException پس از 2s) — فایل CAPTURE_FAILED.txt
- adb devices: خالی؛ امولاتور/گوشی نصب‌نشده (طبق درخواست روی گوشی واقعی بدون تأیید نصب نشد)
- مقایسه پیکسلی با board مرجع انجام نشد

## اجرا نشده / محدودیت
- تست ابزاری روی دستگاه
- lint گزارش HTML کامل در آخرین verify ترکیبی تضمین نشده
- Roborazzi در کش نبود

## Lint (نهایی)
- **۰ خطا، ۵۵ هشدار** (lintDebug پس از رفع MissingPermission و PropertyEscape)
