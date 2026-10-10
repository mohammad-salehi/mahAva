امضای انتشار ماه‌آوا (Mahava)
================================
package: com.mahava.app
alias: mahava
فایل کلید: mahava-release.jks
رمزها: فایل keystore.properties همین پوشه (و ریشهٔ پروژه)

بکاپ بیرون از گیت:
  Desktop\release-signing-keys\mahava\

برای آپدیت پلی‌استور همیشه با همین کلید بیلد بگیر:
  gradlew.bat assembleRelease   → APK
  gradlew.bat bundleRelease     → AAB (گوگل پلی)

این پوشه و keystore.properties در گیت commit نمی‌شوند.
SHA256 گواهی را با این دستور ببین:
  keytool -list -v -keystore mahava-release.jks -alias mahava
