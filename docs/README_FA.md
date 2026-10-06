# ماه‌آوا — راهنمای اجرا و نصب

## پیش‌نیاز
- Android Studio یا JDK ۱۷+ و Android SDK (compileSdk 35)
- در این محیط از `JAVA_HOME` مربوط به Android Studio JBR استفاده شد.

## ساخت
```
cd C:\Users\A.R.I\Desktop\app\mahava
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_USER_HOME=C:\Users\A.R.I\Desktop\app\finance-manager\gradle-cache-v730
gradlew.bat :app:assembleDebug --offline
```
APK خروجی: `app\build\outputs\apk\debug\app-debug.apk` و کپی `Mahava-debug.apk` در ریشه پروژه.

## نصب
`adb install -r Mahava-debug.apk` روی امولاتور یا با تأیید کاربر روی گوشی.

اپ کاملاً آفلاین است و مجوز اینترنت ندارد.
