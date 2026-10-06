# معماری ذخیره و پشتیبان

## محلی
- Room (`mahava.db`): پروفایل، پریود، لاگ روزانه، یادآوری.
- DataStore Preferences: پرچم‌های UI/نشست.
- تاریخ روزانه: `epochDay` / LocalDate.
- `android:allowBackup=false` + dataExtractionRules/fullBackupContent که همه را exclude می‌کند.

## رمزنگاری روی دستگاه
Android Keystore AES-GCM برای رازهای وابسته به دستگاه (در صورت نیاز فیلد/فایل).
SQLCipher در کش موجود نبود → انتخاب جایگزین مستند شد.

## پشتیبان قابل حمل (SAF)
- گذرواژه کاربر (ذخیره نمی‌شود)
- KDF: PBKDF2WithHmacSHA256، ۱۲۰٬۰۰۰ تکرار، salt ۱۶ بایتی
- AES-GCM، IV ۱۲ بایتی تصادفی
- قالب: magic `MAHAVABK1` | iterations | salt | iv | ciphertext
- بازیابی: decrypt+validate سپس `withTransaction` اتمیک؛ رمز اشتباه/فایل خراب داده فعلی را عوض نمی‌کند.

## خروجی JSON/CSV
جدا از پشتیبان رمزدار؛ درباره محتوای خصوصی هشدار UI دارد.
