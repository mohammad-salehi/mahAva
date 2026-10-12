package com.mahava.app.content

import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.data.db.DailyLogEntity

/**
 * Educational forecast for **tomorrow**, grounded in common menstrual-cycle physiology.
 * Wording stays tentative («ممکنه» / «گاهی»); not a diagnosis.
 */
data class PhaseForecast(
    val phaseGroup: String,
    val phaseTitleFa: String,
    /** Short lines for the Today card (mood + craving). */
    val summaryLinesFa: List<String>,
    val cravingLineFa: String,
    val moodLineFa: String,
    val detailCravingFa: String,
    val detailMoodFa: String,
    /** Plain-Persian scientific reason if the craving shows up. */
    val scienceCravingFa: String,
    /** Plain-Persian scientific reason if the mood shows up. */
    val scienceMoodFa: String,
    /** One-line hormone / physiology snapshot for tomorrow. */
    val hormoneSnapshotFa: String,
    val tipFa: String,
    val personalNoteFa: String?,
    val forTomorrow: Boolean = true,
    val disclaimerFa: String = DISCLAIMER
) {
    companion object {
        const val DISCLAIMER =
            "بدن‌ها فرق دارند؛ این فقط یک پیش‌بینی آموزشی از روی مرحلهٔ تخمینی چرخه برای فرداست، نه تشخیص پزشکی و نه قطعی."
    }
}

object PhaseForecastEngine {

    fun build(
        subWindow: CycleSubWindow,
        phase: CyclePhase?,
        recentLogs: List<DailyLogEntity> = emptyList()
    ): PhaseForecast {
        val group = CravingContent.phaseGroupOf(subWindow, phase)
        val base = bank[group] ?: bank["general"]!!
        val personal = personalize(group, recentLogs)
        val lines = listOf("مرحلهٔ فردا از تاریخ‌ها تخمین زده می‌شود؛ حال و علائم تو ممکن است متفاوت باشد.", "ثبت‌های امروز و فردا برای شناخت الگوی شخصی تو مهم‌تر از میانگین‌ها هستند.")
        return PhaseForecast(
            phaseGroup = group,
            phaseTitleFa = subWindow.titleFa,
            summaryLinesFa = lines,
            cravingLineFa = base.cravingLineFa,
            moodLineFa = base.moodLineFa,
            detailCravingFa = base.detailCravingFa,
            detailMoodFa = base.detailMoodFa,
            scienceCravingFa = base.scienceCravingFa,
            scienceMoodFa = base.scienceMoodFa,
            hormoneSnapshotFa = base.hormoneSnapshotFa,
            tipFa = base.tipFa,
            personalNoteFa = personal
        )
    }

    private data class Base(
        val cravingLineFa: String,
        val moodLineFa: String,
        val detailCravingFa: String,
        val detailMoodFa: String,
        val scienceCravingFa: String,
        val scienceMoodFa: String,
        val hormoneSnapshotFa: String,
        val tipFa: String
    )

    /**
     * Mechanisms summarized from well-known cycle physiology (OWH / Endotext / NHS / ACOG / MedlinePlus PMS),
     * in plain Persian. First hormone mention includes Latin/English in parentheses.
     */
    private val bank: Map<String, Base> = mapOf(
        "menstrual" to Base(
            cravingLineFa = "اگر هوس شیرینی داشتی اشکالی ندارد؛ کنارش یک وعدهٔ آهن‌دار هم بگذار.",
            moodLineFa = "فردا ممکن است خسته‌تر یا کمی کم‌حوصله باشی؛ درد روزهای اول هم روی حال اثر می‌گذارد.",
            detailCravingFa = "هوس شیرینی و شکلات برای بعضی‌ها نزدیک پریود بیشتر می‌شود و ممکن است چند روز ادامه پیدا کند. از روی یک هوس نمی‌شود کمبود غذایی را تشخیص داد.",
            detailMoodFa = "خستگی، نیاز به سکوت، و حساس‌تر بودن به حرف‌ها در این روزها شایع است. غم یا بی‌حوصلگی خفیف هم ممکن است؛ اگر خیلی شدید یا طولانی شد، با پزشک حرف بزن.",
            scienceCravingFa = "اوایل پریود، استروژن و پروژسترون پایین‌اند. هوس خوراکی ممکن است با روزهای پیش از پریود، خواب، استرس یا عادت ربط داشته باشد. از روی هوس شکلات یا گوشت نمی‌شود کمبود منیزیم یا آهن را فهمید.",
            scienceMoodFa = "افت هورمون‌های جنسی و آزاد شدن پروستاگلاندین (prostaglandin) — ماده‌ای که رحم را منقبض می‌کند و درد می‌سازد — می‌تواند خستگی، درد و بی‌حوصلگی بیاورد. کم‌خوابی روزهای اول هم روی خلق اثر می‌گذارد. این‌ها رایج‌اند، نه نشانهٔ بیماری به‌تنهایی.",
            hormoneSnapshotFa = "فردا (تخمینی): خون‌ریزی و انقباض رحم ادامه دارد؛ بدن ممکن است آهن و انرژی بیشتری مصرف کند.",
            tipFa = "فردا گرما روی شکم، مسکن مناسب در صورت نیاز (اگر منع پزشکی نداری)، و غذای آهن‌دار برنامه‌ات را سبک‌تر می‌کند. (ACOG)"
        ),
        "follicular" to Base(
            cravingLineFa = "هوس‌ها معمولاً کمتر از روزهای قبل از پریود است؛ روی وعدهٔ متعادل تمرکز کن.",
            moodLineFa = "فردا خیلی‌ها حس سبکی یا انرژی بیشتری دارند؛ اگر هنوز خسته‌ای، اجباری نیست.",
            detailCravingFa = "در یک پژوهش، هوس شیرینی بعد از پریود کمتر از روزهای نزدیک پریود بود. این یک میانگین گروهی است؛ هوس تو می‌تواند فرق کند.",
            detailMoodFa = "بعضی‌ها بعد از پریود حال بهتری دارند و بعضی‌ها تغییری حس نمی‌کنند. خواب و اتفاق‌های روز هم روی حالت اثر دارند.",
            scienceCravingFa = "در این روزها استروژن (estrogen) کم‌کم بالا می‌رود. پژوهش‌ها هوس کمتری نسبت به روزهای نزدیک پریود دیده‌اند، اما برای همه صادق نیست. خواب، استرس و عادت غذایی را هم در نظر بگیر.",
            scienceMoodFa = "علائم پیش از پریود معمولاً تا ۴ روز بعد از شروع پریود تمام می‌شوند (ACOG). برای همین خیلی‌ها در این روزها حس سبکی دارند. ولی حال روحی فقط به هورمون‌ها بستگی ندارد؛ خواب و استرس هم نقش بزرگی دارند.",
            hormoneSnapshotFa = "فردا (تخمینی): زمان آرامی‌تر چرخه برای خیلی‌ها؛ فرصت خوب برای حرکت منظم.",
            tipFa = "اگر بدنت آماده‌ست، فردا پیاده‌روی یا ورزش سبک را در برنامه بگذار و ساعت خواب را ثابت نگه دار. (OWH / بررسی انرژی چرخه)"
        ),
        "fertile" to Base(
            cravingLineFa = "اشتها معمولاً متوسط است؛ آب کافی و وعدهٔ متعادل کافی است.",
            moodLineFa = "برای بیشتر آدم‌ها حال روحی فردا تغییر خاصی ندارد؛ درد خفیف یک‌طرفه گاهی پیش می‌آید.",
            detailCravingFa = "حوالی زمان تخمک‌گذاری، هوس یک خوراکی خاص معنی پزشکی مشخصی ندارد. این زمان هم فقط از روی تاریخ‌ها تخمین زده شده.",
            detailMoodFa = "بعضی‌ها این روزها پرانرژی‌ترند و بعضی‌ها نه. نمی‌شود از روی تاریخ، حال فردا را قطعی گفت.",
            scienceCravingFa = "این بازه از روی تاریخ‌ها تخمینی است. در پژوهش‌ها هوس بعضی خوراکی‌ها نزدیک پریود بیشتر از حوالی تخمک‌گذاری بوده، اما علت هوس فردی را نمی‌شود از روی روز چرخه گفت.",
            scienceMoodFa = "حوالی تخمک‌گذاری (تخمینی) بیشتر آدم‌ها تغییر خاصی در حال روحی حس نمی‌کنند. کمی درد یک‌طرفه ممکن است (NHS). از روی تاریخ نمی‌شود حال کسی را قطعی گفت.",
            hormoneSnapshotFa = "فردا (تخمینی): حوالی تخمک‌گذاریِ تخمینی — فقط تخمین از روی تاریخ است.",
            tipFa = "اگر قصد بارداری نداری پیشگیری را فراموش نکن. درد خفیف کوتاه معمولاً بی‌خطر است؛ درد شدید را جدی بگیر. (NHS)"
        ),
        "early_luteal" to Base(
            cravingLineFa = "ممکن است کم‌کم هوس شیرینی یا نان بیشتر شود؛ کربوهیدرات پیچیده بهتر از قند ساده است.",
            moodLineFa = "فردا ممکن است هنوز نسبتاً آرام باشی؛ نفخ یا اشتهای بیشتر برای بعضی‌ها شروع می‌شود.",
            detailCravingFa = "در نیمهٔ دوم چرخه اشتها برای بعضی‌ها عوض می‌شود. تغییر هوس‌ها در روزهای نزدیک پریود روشن‌تر دیده شده، نه لزوماً در همین روزها.",
            detailMoodFa = "اوایل این بازه خیلی‌ها هنوز حال متعادلی دارند؛ نزدیک‌تر به پریود، حساسیت و زودرنجی ممکن است بیشتر شود.",
            scienceCravingFa = "بعد از تخمک‌گذاری پروژسترون بالا می‌رود و اشتها در بعضی‌ها تغییر می‌کند. هنوز نمی‌شود گفت یک هوس مشخص فقط به این هورمون یا سروتونین مربوط است.",
            scienceMoodFa = "بعد از تخمک‌گذاری، پروژسترون (progesterone) بالا می‌رود. بعضی‌ها هنوز حال متعادلی دارند؛ بعضی‌ها نوسان حس می‌کنند. حساسیت فردی فرق دارد و یک حس مشخص را نمی‌شود فقط به این هورمون نسبت داد.",
            hormoneSnapshotFa = "فردا (تخمینی): اشتها و دمای بدن برای بعضی‌ها کمی بالاتر می‌رود.",
            tipFa = "وعده‌های منظم و کوچک، و کم کردن کافئین عصرگاهی، گاهی نوسان حال و نفخ را کمتر می‌کند. (ACOG)"
        ),
        "late_luteal" to Base(
            cravingLineFa = "ممکن است هوس شکلات یا شور داشته باشی؛ کنارش ماست یا سبزی برگ‌دار هم بگذار.",
            moodLineFa = "فردا ممکن است زودرنج‌تر، حساس‌تر یا کمی مضطرب باشی؛ معمولاً با شروع پریود بهتر می‌شود.",
            detailCravingFa = "در یک پژوهش، هوس شکلات، شیرینی و شور نزدیک پریود بیشتر بود. این نتیجه برای همه یکسان نیست و دلیل دقیق یک هوس را نشان نمی‌دهد.",
            detailMoodFa = "زودرنجی، غم خفیف، اضطراب یا گریهٔ آسان در این روزها برای خیلی‌ها پیش می‌آید و معمولاً با شروع پریود بهتر می‌شود. اگر حال روحی‌ات زندگی روزمره را سخت کرده، ارزش دارد با پزشک مشورت کنی.",
            scienceCravingFa = "نزدیک پریود، استروژن و پروژسترون تغییر می‌کنند. در یک پژوهش هوس شکلات، شیرینی و شور بیشتر بود؛ پژوهش دیگری چنین تفاوتی برای شکلات پیدا نکرد. علت دقیق هوس هر نفر هنوز روشن نیست.",
            scienceMoodFa = "علائم پیش از قاعدگی یا PMS (مثل زودرنجی، نگرانی، نفخ) در نیمهٔ دوم چرخه برای بعضی‌ها دیده می‌شود و معمولاً با شروع پریود کم می‌شود. علت دقیق کاملاً معلوم نیست (NHS و ACOG هم همین را می‌گویند). اگر علائم زندگی‌ات را سخت کرده، با پزشک حرف بزن.",
            hormoneSnapshotFa = "فردا (تخمینی): روزهای شبیه PMS برای خیلی‌ها — خستگی، نفخ یا نوسان خلق ممکن است.",
            tipFa = "خواب منظم، پیاده‌روی کوتاه، و کم کردن نمک و کافئین فردا را راحت‌تر می‌کند. اگر علائم شدید است، با پزشک مشورت کن. (NHS/ACOG)"
        ),
        "general" to Base(
            cravingLineFa = "هوس‌ها خیلی فردی‌اند؛ روی خواب و وعدهٔ منظم تمرکز کن.",
            moodLineFa = "فردا حال‌ات بیشتر به خواب، استرس و درد بستگی دارد تا به حدس هورمونی.",
            detailCravingFa = "هنوز مرحلهٔ چرخه‌ات را برای فردا دقیق تخمین نمی‌زنیم؛ هوس‌ها خیلی فردی‌اند و به خواب و استرس هم ربط دارند.",
            detailMoodFa = "بدون تخمین مرحله، پیش‌بینی حال روحی محدود است. با ثبت چند چرخه، الگوهای خودت روشن‌تر می‌شود.",
            scienceCravingFa = "بدون تخمین قابل‌اعتماد از مرحلهٔ چرخه، نمی‌توان هوس فردا را به یک هورمون خاص نسبت داد. خواب کم، استرس و عادت غذایی اغلب قوی‌تر از روز چرخه عمل می‌کنند.",
            scienceMoodFa = "خلق تحت تأثیر خواب، استرس، درد و هورمون‌ها با هم است. وقتی تاریخ‌ها کافی نباشد، بهتر است به‌جای حدس هورمونی، به ثبت منظم تکیه کنی.",
            hormoneSnapshotFa = "فردا: مرحله را مطمئن تخمین نمی‌زنیم؛ ثبت امروز دقیق‌تر از حدس است.",
            tipFa = "فردا هم حال و خواب را ثبت کن تا بعد از چند چرخه الگوی خودت معلوم شود."
        )
    )

    private fun personalize(phaseGroup: String, recentLogs: List<DailyLogEntity>): String? {
        if (recentLogs.isEmpty()) return null
        val cravingCounts = mutableMapOf<String, Int>()
        val moodCounts = mutableMapOf<String, Int>()
        recentLogs.forEach { log ->
            log.foodCravings?.split(',')?.filter { it.isNotBlank() && it != "other" }?.forEach {
                cravingCounts[it] = (cravingCounts[it] ?: 0) + 1
            }
            log.moods?.split(',')?.filter { it.isNotBlank() }?.forEach {
                moodCounts[it] = (moodCounts[it] ?: 0) + 1
            }
        }
        val topCraving = cravingCounts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
        val topMood = moodCounts.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key
        return when {
            topCraving != null && topMood != null ->
                "قبلاً چند بار «${FoodCravingKeys.labelFa(topCraving)}» و حال «${moodFa(topMood)}» ثبت کرده بودی."
            topCraving != null ->
                "قبلاً چند بار «${FoodCravingKeys.labelFa(topCraving)}» هوس کرده بودی."
            topMood != null ->
                "در ثبت‌های اخیرت حال «${moodFa(topMood)}» بیشتر تکرار شده."
            else -> null
        }
    }

    private fun moodFa(k: String) = when (k) {
        "happy" -> "خوشحال"; "calm" -> "آرام"; "sad" -> "غمگین"
        "anxious" -> "نگران"; "irritable" -> "زودرنج"; else -> k
    }
}
