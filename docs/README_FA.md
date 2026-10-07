# ماه‌آوا — راهنمای اجرا و نصب

## پیش‌نیاز
- Android Studio یا JDK ۱۷+ و Android SDK (compileSdk 35)
- `JAVA_HOME` = Android Studio JBR
- `GRADLE_USER_HOME` = `C:\Users\A.R.I\.gradle`

## ساخت
```
cd C:\Users\A.R.I\Desktop\mahava
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set GRADLE_USER_HOME=C:\Users\A.R.I\.gradle
gradlew.bat :app:assembleDebug --offline
```
APK: `app\build\outputs\apk\debug\app-debug.apk` و کپی `Mahava-debug.apk` در ریشهٔ پروژه.

## امکانات جدید
هوس خوراکی + امکانات ویژه (بینش روزانه، الگوی علائم، روند چرخه، بررسی آموزشی). جزئیات: `docs/PREMIUM_FA.md`.

اطلاعات کاربر (پریودها، ثبت‌های روزانه، پروفایل، یادآوری‌ها و رضایت اشتراک با همراه) روی سرور ماه (`/api/mah/data`) ذخیره می‌شود و Room فقط کش آفلاین است؛ تغییرهای بدون اینترنت صف می‌شوند و بعداً فرستاده می‌شوند. جزئیات: `docs/partner-sync.md`.
