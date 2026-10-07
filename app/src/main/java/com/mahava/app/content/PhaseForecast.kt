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
        val lines = listOf(base.moodLineFa, base.cravingLineFa)
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
            cravingLineFa = "ممکنه هوس شکلات، شیرینی، نان یا گوشت قرمز داشته باشی.",
            moodLineFa = "ممکنه حال‌ات آرام‌تر، خسته‌تر یا کمی حساس باشه.",
            detailCravingFa = "در روزهای پریود خیلی‌ها سراغ شکلات و شیرینی می‌روند. هوس گوشت قرمز هم گاهی دیده می‌شود. نان و کربوهیدرات برای جبران حس کمبود انرژی رایج است.",
            detailMoodFa = "خستگی، نیاز به سکوت، و حساس‌تر بودن به حرف‌ها در این روزها شایع است. غم یا بی‌حوصلگی خفیف هم ممکن است؛ اگر خیلی شدید یا طولانی شد، با پزشک حرف بزن.",
            scienceCravingFa = "با شروع خون‌ریزی، سطح استروژن (estrogen) و پروژسترون (progesterone) پایین است. بدن گاهی برای انرژی سریع سراغ قند و کربوهیدرات می‌رود. هوس شکلات گاهی با منیزیم (magnesium) یا حال روحی ربط داده می‌شود؛ هوس گوشت قرمز گاهی با نیاز به آهن (iron) پس از خون‌ریزی — ولی این‌ها قطعی و برای همه یکسان نیست.",
            scienceMoodFa = "افت هورمون‌های جنسی و آزاد شدن پروستاگلاندین (prostaglandin) — ماده‌ای که رحم را منقبض می‌کند و درد می‌سازد — می‌تواند خستگی، درد و بی‌حوصلگی بیاورد. کم‌خوابی روزهای اول هم روی خلق اثر می‌گذارد. این‌ها رایج‌اند، نه نشانهٔ بیماری به‌تنهایی.",
            hormoneSnapshotFa = "فردا (تخمینی): استروژن و پروژسترون پایین؛ پروستاگلاندین ممکن است درد و خستگی را بیشتر کند.",
            tipFa = "گرما، آب کافی و استراحت کوتاه گاهی کمک می‌کند. اگر هوس شیرینی داری، میوه یا کمی شکلات تلخ ملایم‌تر است."
        ),
        "follicular" to Base(
            cravingLineFa = "ممکنه اشتهایت متعادل‌تر باشه؛ گاهی هوس غذای سبک یا پروتئین داری.",
            moodLineFa = "ممکنه روحیه‌ات روشن‌تر، پرانرژی‌تر و امیدوارتر باشه.",
            detailCravingFa = "بعد از پریود انرژی و اشتها معمولاً پایدارتر می‌شود. هوس شدید شیرینی کمتر از روزهای پیش از پریود است؛ بیشتر سلیقه و عادت روزانه است تا کمبود.",
            detailMoodFa = "خیلی‌ها در این بازه حال اجتماعی‌تر و تمرکز بهتری حس می‌کنند. اضطراب کمتر و انگیزهٔ بیشتر رایج است — ولی اگر خسته ماندی، اشکالی ندارد؛ بدنت هنوز در حال برگشت است.",
            scienceCravingFa = "با رشد فولیکول، استروژن (estrogen / estradiol) کم‌کم بالا می‌رود و پوشش رحم ترمیم می‌شود. سطح انرژی پایدارتر است؛ هوس شدید کربوهیدرات معمولاً کمتر از فاز لوتئال گزارش می‌شود. اگر چیزی هوس کردی، بیشتر عادت، خواب یا فعالیت روزانه‌ات است تا یک «کمبود قطعی».",
            scienceMoodFa = "بالا رفتن تدریجی استروژن (estrogen / estradiol) با حس انرژی، خلق بهتر و گاهی اعتمادبه‌نفس بیشتر در خیلی از افراد هم‌زمان است (مکانیسم دقیق خلق پیچیده است و فقط هورمون نیست). سروتونین (serotonin) — مادهٔ شیمیایی مرتبط با حس خوب در مغز — هم از مسیرهای مرتبط با استروژن اثر می‌پذیرد؛ ولی استرس و خواب هنوز نقش بزرگی دارند.",
            hormoneSnapshotFa = "فردا (تخمینی): استروژن در حال بالا رفتن؛ انرژی و خلق معمولاً پایدارتر از روزهای پریود است.",
            tipFa = "اگر بدنت آماده‌ست، حرکت سبک و کار جدید معمولاً در این دوره راحت‌تر پیش می‌رود."
        ),
        "fertile" to Base(
            cravingLineFa = "ممکنه هوس خاصی نداشته باشی؛ گاهی کمی شیرینی یا غذای سیرکننده هوس می‌کنی.",
            moodLineFa = "ممکنه اجتماعی‌تر، بااعتمادبه‌نفس‌تر یا پرانرژی باشی.",
            detailCravingFa = "حوالی تخمک‌گذاری (تخمینی) هوس خوراکی معمولاً کمتر از پیش از پریود است. اگر چیزی هوس کردی، بیشتر عادت یا نوسان کوتاه انرژی است.",
            detailMoodFa = "حال خوب، حس جذابیت یا انرژی اجتماعی در این بازه برای خیلی‌ها گزارش شده. گاهی هم کمی بی‌قراری یا حساسیت هست — هر دو ممکن است.",
            scienceCravingFa = "نزدیک تخمک‌گذاری، استروژن معمولاً به اوج نزدیک می‌شود و بعد با جهش LH (luteinizing hormone) کمی جابه‌جا می‌شود. هوس شدید پیش‌از‌پریودی معمولاً اینجا کمتر است؛ اگر هوس آمد، اغلب کوتاه و وابسته به فعالیت و خواب است.",
            scienceMoodFa = "اوج نسبی استروژن قبل از تخمک‌گذاری با انرژی و خلق مثبت در بسیاری گزارش‌ها هم‌زمان است. کمی درد یک‌طرفه یا حساسیت سینه هم ممکن است از همان تغییرات هورمونی باشد. بی‌قراری خفیف هم گاهی دیده می‌شود — الزاماً مشکل نیست.",
            hormoneSnapshotFa = "فردا (تخمینی): حوالی اوج استروژن و آمادگی تخمک‌گذاری؛ انرژی اغلب بالاتر است.",
            tipFa = "خواب و آب را جدی بگیر. اگر برای بارداری اقدام می‌کنی، این بازه فقط تخمینی است."
        ),
        "early_luteal" to Base(
            cravingLineFa = "ممکنه کم‌کم هوس شیرینی، شکلات یا نان بیشتر بشه.",
            moodLineFa = "ممکنه هنوز نسبتاً آرام باشی، با کمی نوسان خلق.",
            detailCravingFa = "با شروع نیمهٔ دوم چرخه، گاهی اشتها به کربوهیدرات و شیرینی بیشتر می‌شود. این رایج است و به تنهایی بیماری نیست.",
            detailMoodFa = "اوایل این بازه خیلی‌ها هنوز حال متعادلی دارند؛ نزدیک‌تر به پریود، حساسیت و زودرنجی ممکن است بیشتر شود.",
            scienceCravingFa = "بعد از تخمک‌گذاری، جسم زرد پروژسترون (progesterone) می‌سازد. پروژسترون گاهی متابولیسم و اشتها را جابه‌جا می‌کند و تمایل به کربوهیدرات را بیشتر می‌کند. بدن ممکن است از راه قند، مسیرهای مرتبط با سروتونین (serotonin) را هم «تنظیم» کند — برای همین هوس شیرینی در نیمهٔ دوم شایع است، نه الزاماً نشانهٔ بیماری.",
            scienceMoodFa = "پروژسترون اثر آرام‌بخشی نسبی دارد؛ اوایل لوتئال خیلی‌ها هنوز متعادل‌اند. ولی حساسیت فردی به نوسان استروژن و پروژسترون فرق دارد. اگر خلق‌ات کمی موج برداشت، لزوماً یعنی «چیزی خراب است» نیست.",
            hormoneSnapshotFa = "فردا (تخمینی): پروژسترون در حال بالا رفتن؛ اشتها به کربوهیدرات گاهی بیشتر می‌شود.",
            tipFa = "وعده‌های منظم و کم کردن کافئین عصرگاهی گاهی نوسان حال و هوس را کمتر می‌کند."
        ),
        "late_luteal" to Base(
            cravingLineFa = "ممکنه هوس شکلات، شیرینی، شور یا نان داشته باشی.",
            moodLineFa = "ممکنه زودرنج‌تر، حساس‌تر یا کمی مضطرب باشی.",
            detailCravingFa = "پیش از پریود (گاهی به آن PMS می‌گویند) هوس شکلات و شیرینی خیلی شایع است. شور و نان هم رایج‌اند؛ گاهی با نفخ و نگه داشتن آب همراه می‌شود.",
            detailMoodFa = "زودرنجی، غم خفیف، اضطراب یا گریهٔ آسان در این روزها برای خیلی‌ها پیش می‌آید و معمولاً با شروع پریود بهتر می‌شود. اگر حال روحی‌ات زندگی روزمره را سخت کرده، ارزش دارد با پزشک مشورت کنی.",
            scienceCravingFa = "در روزهای آخر، اگر بارداری نباشد، استروژن و پروژسترون افت می‌کنند. این افت با هوس کربوهیدرات و شیرینی در بسیاری از افراد هم‌زمان است؛ یکی از فرض‌های رایج، تلاش بدن برای پشتیبانی از سروتونین (serotonin) از راه کربوهیدرات است. هوس شور گاهی با نفخ و نگه داشتن آب مرتبط است. مکانیسم کامل PMS هنوز کاملاً روشن نیست.",
            scienceMoodFa = "علائم پیش از قاعدگی (PMS) در نیمهٔ دوم چرخه دیده می‌شوند. علت دقیق کاملاً معلوم نیست؛ یکی از توضیح‌های رایج، تغییر حساسیت مغز به افت استروژن (estrogen) و پروژسترون (progesterone) است. زودرنجی و اضطراب خفیف شایع‌اند و اغلب با شروع خون‌ریزی کم می‌شوند. اگر علائم خیلی شدید یا طولانی‌اند، با پزشک حرف بزن.",
            hormoneSnapshotFa = "فردا (تخمینی): افت نزدیک استروژن و پروژسترون؛ علائم شبیه PMS در خیلی‌ها ممکن است.",
            tipFa = "با خودت مهربان باش. نمک و کافئین زیاد گاهی نفخ و اضطراب را بیشتر می‌کند؛ خواب منظم کمک می‌کند."
        ),
        "general" to Base(
            cravingLineFa = "ممکنه هوس خوراکی‌ات از روزی به روز دیگر فرق کنه.",
            moodLineFa = "ممکنه روحیه‌ات با خواب، استرس و عادت غذایی‌ات جابه‌جا بشه.",
            detailCravingFa = "هنوز مرحلهٔ چرخه‌ات را برای فردا دقیق تخمین نمی‌زنیم؛ هوس‌ها خیلی فردی‌اند و به خواب و استرس هم ربط دارند.",
            detailMoodFa = "بدون تخمین مرحله، پیش‌بینی حال روحی محدود است. با ثبت چند چرخه، الگوهای خودت روشن‌تر می‌شود.",
            scienceCravingFa = "بدون تخمین قابل‌اعتماد از مرحلهٔ چرخه، نمی‌توان هوس فردا را به یک هورمون خاص نسبت داد. خواب کم، استرس و عادت غذایی اغلب قوی‌تر از روز چرخه عمل می‌کنند.",
            scienceMoodFa = "خلق تحت تأثیر خواب، استرس، درد و هورمون‌ها با هم است. وقتی تاریخ‌ها کافی نباشد، بهتر است به‌جای حدس هورمونی، به ثبت منظم تکیه کنی.",
            hormoneSnapshotFa = "فردا: مرحلهٔ چرخه را مطمئن تخمین نمی‌زنیم؛ پیش‌بینی کلی و محتاطانه است.",
            tipFa = "تاریخ پریود و حال روزانه را منظم ثبت کن تا پیش‌بینی‌ها دقیق‌تر شوند."
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
