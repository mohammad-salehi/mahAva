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
        // Prefer the actionable home tip; fall back to rotating line + care tip.
        val practical = science.homePracticalFa.trim().ifBlank {
            buildString {
                append(tip)
                science.careTipsFa.firstOrNull()?.let { care ->
                    append(" ")
                    append(care)
                    if (!care.endsWith(".")) append(".")
                }
            }
        }
        val body = if (practical.length > 220) practical.take(217) + "…" else practical
        return Tip(title, body)
    }

    private fun phaseGroup(cycle: CycleEngineResult?): String {
        if (cycle == null) return "general"
        val ctx = CycleDayContextResolver.resolve(cycle)
        return CravingContent.phaseGroupOf(ctx.subWindow, cycle.phase)
    }

    private val tipsByGroup: Map<String, List<String>> = mapOf(
        "menstrual" to listOf(
            "الان رحم دارد پوشش داخلی‌اش را بیرون می‌فرستد؛ درد و خستگی روزهای اول عجیب نیست.",
            "پروستاگلاندین (ماده‌ای که عضلهٔ رحم را منقبض می‌کند) معمولاً اول پریود بیشتر است؛ برای همین درد بیشتر حس می‌شود.",
            "اگر بدنت اجازه می‌دهد، کمی راه رفتن یا کیسهٔ آب گرم روی شکم حال خیلی‌ها را بهتر می‌کند.",
            "امروز به خودت سخت نگیر. خواب و آب کافی از خیلی کارهای دیگر مهم‌تر است.",
            "اگر درد خیلی شدید است یا هر ساعت پد عوض می‌کنی، بهتر است با پزشک حرف بزنی."
        ),
        "follicular" to listOf(
            "استروژن کم‌کم بالا می‌رود و پوشش رحم دوباره ساخته می‌شود. خیلی‌ها این روزها حس سبکی دارند.",
            "این روزها معمولاً وقت خوبی برای ورزش منظم و خواب سر ساعت است.",
            "اشتها و هوس شیرینی در نیمهٔ اول چرخه معمولاً کمتر از روزهای قبل از پریود است.",
            "اگر هنوز خسته‌ای، اشکالی ندارد؛ بدن هر کسی ریتم خودش را دارد.",
            "وعده‌های منظم با پروتئین و سبزی کمک می‌کند انرژی‌ات پایدار بماند."
        ),
        "fertile" to listOf(
            "حوالی تخمک‌گذاری استروژن به اوج می‌رسد؛ این روزها فقط تخمینی‌اند، نه قطعی.",
            "تخمک حدود یک روز زنده می‌ماند، ولی اسپرم می‌تواند چند روز زنده بماند؛ برای همین بازهٔ باروری چندروزه است.",
            "بعضی‌ها درد خفیف یک‌طرفه در پایین شکم دارند؛ اگر شدید بود یا با مسکن آرام نشد، به پزشک سر بزن.",
            "اگر نمی‌خواهی باردار شوی، فقط به تاریخ تخمک‌گذاری تکیه نکن.",
            "خواب و ورزش منظم را ادامه بده؛ در این روزها انرژی بدن معمولاً تغییر خاصی نمی‌کند."
        ),
        "early_luteal" to listOf(
            "بعد از تخمک‌گذاری پروژسترون بالا می‌رود و دمای بدن کمی بالاتر می‌رود.",
            "نفخ یا درد سینه ممکن است کم‌کم شروع شود؛ برای خیلی‌ها طبیعی است.",
            "در نیمهٔ دوم چرخه بعضی‌ها کمی بیشتر غذا می‌خورند؛ لازم نیست خودت را محدود کنی.",
            "وعده‌های کوچک و منظم معمولاً از سه وعدهٔ سنگین بهتر جواب می‌دهد.",
            "اگر چند هفته است حالت بد است، آن را به پریود ربط نده و با پزشک حرف بزن."
        ),
        "late_luteal" to listOf(
            "نزدیک پریود استروژن و پروژسترون پایین می‌آیند؛ زودرنجی، نفخ یا هوس شیرینی رایج است.",
            "بیشتر زن‌ها گاهی علائم پیش از پریود را تجربه می‌کنند؛ تو تنها نیستی.",
            "هوس شکلات یا شوری در این روزها در پژوهش‌ها هم دیده شده و به‌تنهایی نشانهٔ کمبود چیزی نیست.",
            "پیاده‌روی تند، کم کردن نمک و خواب سر ساعت معمولاً حال خیلی‌ها را بهتر می‌کند.",
            "اگر علائم آن‌قدر شدید است که زندگی‌ات را به هم می‌زند، با پزشک حرف بزن؛ درمان دارد."
        ),
        "general" to listOf(
            "هنوز مرحلهٔ امروزت معلوم نیست؛ با ثبت چند پریود، راهنمایی‌ها دقیق‌تر می‌شوند.",
            "خواب منظم و کمی حرکت روزانه برای همهٔ روزهای چرخه مفید است.",
            "حال و علائمت را هر روز ثبت کن؛ بعد از چند ماه الگوی خودت معلوم می‌شود.",
            "بدن هر کسی فرق دارد؛ اگر چیزی نگرانت کرده، با پزشک حرف بزن.",
            "امروز یک لیوان آب و یک وعدهٔ ساده شروع خوبی است."
        )
    )
}
