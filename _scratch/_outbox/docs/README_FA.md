# ماه‌آوا — راهنمای اجرا و نصب

## چیست؟
اپ اندرویدی فارسی/RTL آفلاین برای پیگیری پریود، شناخت چرخه و محتوای آموزشی بدن. شناسه: `com.mahava.app`.

## پیش‌نیاز ساخت
- پوشه پروژه: `C:\Users\A.R.I\Desktop\app\mahava`
- Android SDK در `local.properties` (`sdk.dir=C:\\Users\\A.R.I\\AppData\\Local\\Android\\Sdk`)
- JDK: در این ساخت از Android Studio JBR ۲۱ استفاده شد (`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`). بایت‌کد هدف ۱۷ است.
- وابستگی‌ها از کش Gradle کاربر (`C:\Users\A.R.I\.gradle`)؛ پوشه `finance-manager\gradle-cache-v730` به‌خاطر metadata خراب transforms استفاده نشد.

## ساخت APK دیباگ
```bat
cd C:\Users\A.R.I\Desktop\app\mahava
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat :app:assembleDebug
```
خروجی: `app\build\outputs\apk\debug\app-debug.apk` و کپی `Mahava-debug.apk` در ریشه پروژه.

## نصب
- امولاتور: `adb install -r Mahava-debug.apk`
- گوشی واقعی: فقط با تأیید صریح کاربر.

## نکات
- بدون مجوز INTERNET؛ بدون analytics/تبلیغات/حساب.
- فونت Vazirmatn (OFL) محلی است.
- پشتیبان: SAF + PBKDF2WithHmacSHA256 + AES-GCM.
