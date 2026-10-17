package com.mahava.app.sync

import com.mahava.app.util.PersianDigits

/** Short, honest Persian lines for a sync result (never blames the internet for a server answer). */
object SyncMessages {
    private fun hasPersian(s: String?) = s != null && s.any { it in '\u0600'..'\u06FF' }

    /** One short reason, e.g. «سرور خطا داد (کد ۵۰۰)». */
    fun reasonFa(f: SyncOutcome.Failed): String = when (f.reason) {
        SyncFailure.NETWORK -> "به سرور وصل نشد؛ اینترنت یا فیلترشکن را بررسی کن"
        SyncFailure.TIMEOUT -> "سرور دیر جواب داد؛ اتصال کند است"
        SyncFailure.RATE_LIMIT -> "سرور فعلاً درخواست زیادی گرفته؛ چند دقیقهٔ دیگر دوباره امتحان کن"
        SyncFailure.AUTH -> "نشست منقضی شده؛ یک بار خارج و دوباره وارد شو"
        SyncFailure.UPDATE_REQUIRED -> "برای همگام‌سازی، برنامه را به‌روز کن"
        SyncFailure.SERVER ->
            f.httpCode?.let { "سرور خطا داد (کد ${PersianDigits.toPersian(it.toString())})" } ?: "پاسخ سرور قابل خواندن نبود"
        SyncFailure.REJECTED ->
            f.message?.takeIf { hasPersian(it) } ?: "سرور بخشی از اطلاعات را نپذیرفت"
        SyncFailure.UNKNOWN ->
            f.message?.takeIf { hasPersian(it) } ?: "خطای پیش‌بینی‌نشده در همگام‌سازی"
    }

    /** Banner after login when her data did not come back. */
    fun restoreFailedFa(f: SyncOutcome.Failed): String =
        "وارد شدی، ولی اطلاعاتت هنوز از سرور برنگشت: ${reasonFa(f)}."

    /** Data is back; only some of this phone's changes are still waiting to upload. */
    const val RESTORED_PENDING = "اطلاعاتت از سرور برگشت. چند تغییر این گوشی هنوز به سرور نرسیده و بعداً خودکار فرستاده می‌شود."

    const val RESTORE_RUNNING = "وارد شدی. دریافت اطلاعاتت از سرور ادامه دارد…"
    const val RESTORE_DONE = "اطلاعاتت از سرور برگشت."

    /** Whether trying again soon by itself makes sense (not for auth, update or rate limit). */
    fun autoRetry(f: SyncOutcome.Failed) = f.reason !in setOf(SyncFailure.AUTH, SyncFailure.UPDATE_REQUIRED, SyncFailure.RATE_LIMIT)
}
