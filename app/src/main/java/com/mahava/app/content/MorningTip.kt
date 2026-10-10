package com.mahava.app.content

import com.mahava.app.cycle.CycleDayContextResolver
import com.mahava.app.cycle.CycleEngineResult
import java.time.LocalDate

/**
 * Short, plain-Persian morning tips for women, matched to today's cycle phase.
 * Sounds like a friend, not a textbook. Rotates by day so it doesn't repeat every morning.
 */
object MorningTip {

    data class Tip(val title: String, val body: String)

    fun forCycle(cycle: CycleEngineResult?, today: LocalDate): Tip {
        val group = phaseGroup(cycle)
        val science = PhaseScienceBank.forGroup(group)
        val pool = tipsByGroup[group] ?: tipsByGroup["general"]!!
        val dayIndex = ((today.toEpochDay() % pool.size) + pool.size) % pool.size
        val tip = pool[dayIndex.toInt()]
        val title = when (group) {
            "menstrual" -> "صبح بخیر — روزهای پریود"
            "follicular" -> "صبح بخیر — بعد از پریود"
            "fertile" -> "صبح بخیر — حوالی تخمک‌گذاری"
            "early_luteal" -> "صبح بخیر — نیمهٔ دوم چرخه"
            "late_luteal" -> "صبح بخیر — نزدیک پریود"
            else -> "صبح بخیر"
        }
        // Keep one short science line + one care line so the notif stays scannable.
        val body = buildString {
            append(tip)
            val care = science.careTipsFa.firstOrNull()
            if (care != null) {
                append(" ")
                append(care)
                if (!care.endsWith(".")) append(".")
            }
        }
        return Tip(title, body)
    }

    private fun phaseGroup(cycle: CycleEngineResult?): String {
        if (cycle == null) return "general"
        val ctx = CycleDayContextResolver.resolve(cycle)
        return CravingContent.phaseGroupOf(ctx.subWindow, cycle.phase)
    }

    private val tipsByGroup: Map<String, List<String>> = mapOf(
        "menstrual" to listOf(
            "الان رحم داره پوشش داخلش رو بیرون می‌فرسته؛ درد و خستگی روزهای اول عجیبه نیست.",
            "پروستاگلاندین (همون ماده‌ای که عضله رو منقبض می‌کنه) معمولاً اول پریود بیشتره؛ برای همین درد بیشتر حس می‌شه.",
            "اگه بدنت اجازه می‌ده، کمی راه رفتن یا کیسهٔ آب گرم روی شکم خیلی‌ها رو آروم‌تر می‌کنه.",
            "امروز به خودت سخت نگیر. خواب و آب کافی از خیلی کارای دیگه مهم‌تره.",
            "اگه درد خیلی شدیده یا هر ساعت پد عوض می‌کنی، بهتره با پزشک حرف بزنی."
        ),
        "follicular" to listOf(
            "استروژن کم‌کم بالا می‌ره و پوشش رحم دوباره ساخته می‌شه. خیلی‌ها این روزا سبک‌تر حس می‌کنن.",
            "این بازه معمولاً وقت خوبیه برای ورزش منظم و خواب سر ساعت.",
            "اشتها و هوس شیرینی توی نیمهٔ اول چرخه معمولاً کمتر از روزای قبل پریوده.",
            "اگه هنوز خسته‌ای، اشکالی نداره؛ بدن هر کسی ریتم خودش رو داره.",
            "وعده‌های منظم با پروتئین و سبزی کمکت می‌کنه انرژی‌ت پایدار بمونه."
        ),
        "fertile" to listOf(
            "حوالی تخمک‌گذاری استروژن به اوج می‌رسه؛ این روزا فقط تخمینی‌ان، نه قطعی.",
            "تخمک حدود یک روز زنده می‌مونه، ولی اسپرم می‌تونه چند روز بمونه؛ برای همین بازهٔ باروری چند روزه است.",
            "بعضی‌ها درد خفیف یک‌طرفه توی پایین شکم دارن؛ اگه شدید بود یا با مسکن آروم نشد، به پزشک سر بزن.",
            "اگه نمی‌خوای باردار شی، فقط به تاریخ تخمک‌گذاری تکیه نکن.",
            "خواب و ورزش منظم رو ادامه بده؛ بدن توی این روزا معمولاً تغییر خاصی توی انرژی نمی‌ده."
        ),
        "early_luteal" to listOf(
            "بعد از تخمک‌گذاری پروژسترون بالا می‌ره و دمای بدن کمی گرم‌تر می‌شه.",
            "نفخ یا درد سینه ممکنه کم‌کم شروع بشه؛ برای خیلی‌ها طبیعیه.",
            "توی نیمهٔ دوم چرخه بعضی‌ها کمی بیشتر غذا می‌خورن؛ اجباری نیست خودتو محدود کنی.",
            "وعده‌های کوچک و منظم معمولاً از سه وعدهٔ سنگین بهتر جواب می‌ده.",
            "اگه حالت چند هفته‌ست بده، به پریود ربطش نده و با پزشک حرف بزن."
        ),
        "late_luteal" to listOf(
            "نزدیک پریود استروژن و پروژسترون پایین میان؛ زودرنجی، نفخ یا هوس شیرینی رایجه.",
            "بیشتر زن‌ها گاهی علائم پیش از پریود رو تجربه می‌کنن؛ تو تنها نیستی.",
            "هوس شکلات یا شوری تو این روزا تو پژوهش‌ها هم دیده شده؛ یعنی کمبودی رو ثابت نمی‌کنه.",
            "پیاده‌روی تند، کم کردن نمک و خواب سر ساعت معمولاً حال خیلی‌ها رو بهتر می‌کنه.",
            "اگه علائم اون‌قدر شدیده که زندگی‌ت رو به هم می‌زنه، با پزشک حرف بزن؛ درمان داره."
        ),
        "general" to listOf(
            "هنوز مرحلهٔ امروزت معلوم نیست؛ با ثبت چند پریود، راهنمایی‌ها دقیق‌تر می‌شن.",
            "خواب منظم و کمی حرکت روزانه برای هر روزی از چرخه مفیده.",
            "حالت و علائمت رو هر روز ثبت کن؛ بعد از چند ماه الگوی خودت معلوم می‌شه.",
            "بدن هر کسی فرق داره؛ اگه چیزی نگران‌ت کرده، با پزشک حرف بزن.",
            "امروز یه لیوان آب و یه وعدهٔ ساده نقطهٔ شروع خوبیه."
        )
    )
}
