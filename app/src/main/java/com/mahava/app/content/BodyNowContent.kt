package com.mahava.app.content

import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.data.db.DailyLogEntity

/**
 * The «الان توی بدنت چه خبره؟» box on Today.
 * For every estimated cycle sub-window and every pregnancy week range it gives short labelled rows:
 * hormones (with a plain meaning), what she may feel, what helps to eat, what helps to do,
 * what to cut down today and (where it matters) when to see a doctor.
 * A calendar estimate is not a hormone test, so the text stays hedged; what she logged today wins.
 * Research notes and links: docs/body-now-sources.md.
 */
object BodyNowContent {
    data class Row(val key: String, val label: String, val text: String)
    data class BodyNow(
        val points: List<String>,
        val basisFa: String,
        val rows: List<Row> = emptyList(),
        val sourceIds: List<String> = emptyList()
    )

    /** One state of the box. */
    data class State(
        val hormones: String,
        val feel: String,
        val eat: String,
        val doIt: String,
        val limit: String,
        val warn: String? = null,
        val sources: List<String> = emptyList()
    )

    const val LABEL_HORMONES = "هورمون‌ها"
    const val LABEL_FEEL = "احتمالاً حس می‌کنی"
    const val LABEL_EAT = "بهتره بخوری"
    const val LABEL_DO = "بهتره انجام بدی"
    const val LABEL_LIMIT = "امروز کمترش کن"
    const val LABEL_WARN = "اگه اینو دیدی، به پزشک بگو"

    /** Source id -> (title, url). The same list is in docs/body-now-sources.md. */
    val SOURCES: Map<String, Pair<String, String>> = linkedMapOf(
        "cc_cycle" to ("Cleveland Clinic: Menstrual cycle" to "https://my.clevelandclinic.org/health/articles/10132-menstrual-cycle"),
        "cc_follicular" to ("Cleveland Clinic: Follicular phase" to "https://my.clevelandclinic.org/health/body/23953-follicular-phase"),
        "cc_luteal" to ("Cleveland Clinic: Luteal phase" to "https://my.clevelandclinic.org/health/articles/24417-luteal-phase"),
        "cc_lh" to ("Cleveland Clinic: Luteinizing hormone" to "https://my.clevelandclinic.org/health/body/22255-luteinizing-hormone"),
        "acog_dysmenorrhea" to ("ACOG: Dysmenorrhea (painful periods)" to "https://www.acog.org/womens-health/faqs/dysmenorrhea-painful-periods"),
        "acog_heavy" to ("ACOG: Heavy menstrual bleeding" to "https://www.acog.org/womens-health/faqs/heavy-menstrual-bleeding"),
        "pmc_iron_hmb" to ("Review: iron deficiency in heavy menstrual bleeding (PMC7695235)" to "https://pmc.ncbi.nlm.nih.gov/articles/PMC7695235/"),
        "acog_pms" to ("ACOG: Premenstrual syndrome" to "https://www.acog.org/womens-health/faqs/premenstrual-syndrome"),
        "mayo_pms" to ("Mayo Clinic: PMS self care" to "https://www.mayoclinic.org/diseases-conditions/premenstrual-syndrome/diagnosis-treatment/drc-20376787"),
        "cc_pms" to ("Cleveland Clinic: PMS" to "https://my.clevelandclinic.org/health/diseases/24288-pms-premenstrual-syndrome"),
        "acog_fabm" to ("ACOG: Fertility awareness-based methods" to "https://www.acog.org/womens-health/faqs/fertility-awareness-based-methods-of-family-planning"),
        "nhs_late_period" to ("NHS: Stopped or missed periods" to "https://www.nhs.uk/conditions/stopped-or-missed-periods/"),
        "nhs_preg_test" to ("NHS: Pregnancy tests" to "https://www.nhs.uk/pregnancy/trying-for-a-baby/doing-a-pregnancy-test/"),
        "nhs_weeks" to ("NHS: Pregnancy week by week" to "https://www.nhs.uk/pregnancy/week-by-week/"),
        "nhs_week20" to ("NHS: 20 weeks pregnant" to "https://www.nhs.uk/pregnancy/week-by-week/13-to-27/20-weeks/"),
        "nhs_foods" to ("NHS: Foods to avoid in pregnancy" to "https://www.nhs.uk/pregnancy/keeping-well/foods-to-avoid/"),
        "nhs_sickness" to ("NHS: Vomiting and morning sickness" to "https://www.nhs.uk/pregnancy/common-symptoms/vomiting-and-morning-sickness/"),
        "nhs_get_help" to ("NHS: Pregnancy symptoms you need to get help for" to "https://www.nhs.uk/pregnancy/common-symptoms/pregnancy-symptoms-you-need-to-get-help-for/"),
        "nhs_movements" to ("NHS: Your baby's movements" to "https://www.nhs.uk/pregnancy/keeping-well/your-babys-movements/"),
        "nhs_vitamins" to ("NHS: Planning your pregnancy (folic acid 400 µg)" to "https://www.nhs.uk/pregnancy/trying-for-a-baby/planning-your-pregnancy/"),
        "acog_exercise_preg" to ("ACOG: Exercise during pregnancy" to "https://www.acog.org/womens-health/faqs/exercise-during-pregnancy"),
        "acog_nutrition_preg" to ("ACOG: Healthy eating during pregnancy" to "https://www.acog.org/womens-health/faqs/healthy-eating-during-pregnancy"),
        "rcog_sickness" to ("RCOG: Pregnancy sickness" to "https://www.rcog.org.uk/media/jg3ejmlr/pi_pregnancy-sickness_2024-v12.pdf"),
        "medline_hcg" to ("MedlinePlus: hCG blood test" to "https://medlineplus.gov/ency/article/003510.htm"),
        "who_anc" to ("WHO: Antenatal care recommendations" to "https://www.who.int/publications/i/item/9789241549912")
    )

    /** Three moods on Today; stored in the daily log's overallFeeling field (synced). */
    val MOODS = listOf("good" to "خوبم", "ordinary" to "معمولی‌ام", "bad" to "حال ندارم")

    /** Which of the three Today moods a saved log shows (null = nothing logged). */
    fun moodOf(log: DailyLogEntity?): String? {
        if (log == null) return null
        return when (log.overallFeeling) {
            "great", "good" -> "good"
            "ordinary" -> "ordinary"
            "bad", "very_bad" -> "bad"
            else -> null
        }
    }

    fun moodLabel(key: String?): String? = MOODS.find { it.first == key }?.second

    private val cycleSrc = listOf("cc_cycle")

    val cycleStates: Map<CycleSubWindow, State> = linkedMapOf(
        CycleSubWindow.MENSTRUATION_EARLY to State(
            hormones = "استروژن و پروژسترون (دو هورمون اصلی چرخه) الان پایین‌ترین حدشونن؛ همین افت باعث شده پریود شروع بشه.",
            feel = "احتمالاً دل‌درد، کمردرد یا خستگی داری و انرژیت از همیشه کمتره.",
            eat = "غذای گرم و آهن‌دار مثل عدسی، خوراک لوبیا، گوشت یا مرغ و اسفناج، با یه کم آب‌لیمو یا نارنج که آهن بهتر جذب بشه.",
            doIt = "کیسهٔ آب گرم روی شکم، پیاده‌روی آروم یا کشش سبک، و هر وقت شد یه چرت کوتاه.",
            limit = "چای و قهوهٔ پررنگ رو درست کنار غذا نخور، چون جذب آهن رو کم می‌کنه؛ غذای خیلی شور و فست‌فود رو هم کمتر کن.",
            warn = "اگه هر یکی دو ساعت پد عوض می‌کنی یا درد با مسکن هم آروم نمی‌شه.",
            sources = cycleSrc + listOf("acog_dysmenorrhea", "acog_heavy", "pmc_iron_hmb")
        ),
        CycleSubWindow.MENSTRUATION_LATE to State(
            hormones = "استروژن کم‌کم داره دوباره بالا می‌ره؛ این هورمون معمولاً حال و انرژی رو بهتر می‌کنه.",
            feel = "معمولاً دردها کمتر شده و کم‌کم سرحال‌تر می‌شی، ولی شاید هنوز یه کم خسته باشی.",
            eat = "هنوز آهن لازم داری: عدس، نخود، گوشت کم‌چرب یا تخم‌مرغ، کنارش سبزی تازه یا پرتقال.",
            doIt = "اگه حالت خوبه، پیاده‌روی تندتر یا ورزش سبکت رو دوباره شروع کن.",
            limit = "یه‌دفعه کار خیلی سنگین نکن؛ بدنت هنوز داره خونی رو که از دست داده جبران می‌کنه.",
            sources = cycleSrc + listOf("cc_follicular", "pmc_iron_hmb")
        ),
        CycleSubWindow.FOLLICULAR_EARLY to State(
            hormones = "استروژن داره بالا می‌ره؛ این هورمون دیوارهٔ رحم رو دوباره می‌سازه و معمولاً حالت رو هم بهتر می‌کنه.",
            feel = "خیلی‌ها این روزها انرژی بیشتر، خواب بهتر و ذهن بازتری دارن.",
            eat = "پروتئین و غذای کامل: تخم‌مرغ، ماست، حبوبات، نون سبوس‌دار و کلی میوه و سبزی رنگی.",
            doIt = "وقت خوبیه برای ورزش پرانرژی‌تر یا کارهایی که تمرکز می‌خوان.",
            limit = "شیرینی و نوشابه رو کمتر کن تا انرژیت یهو بالا و پایین نشه.",
            sources = cycleSrc + listOf("cc_follicular")
        ),
        CycleSubWindow.FOLLICULAR_LATE to State(
            hormones = "استروژن نزدیک اوجشه و بدنت داره برای تخمک‌گذاری (آزاد شدن تخمک) آماده می‌شه.",
            feel = "احتمالاً سرحالی و ترشحاتت کمی شفاف‌تر و لغزنده‌تر شده؛ این طبیعیه.",
            eat = "آب کافی، سبزی برگ‌دار، گردو و بادام، و ماهی یا تخم‌مرغ برای پروتئین.",
            doIt = "ورزشی که دوست داری رو بکن؛ اگه دنبال بارداری هستی، این روزها و چند روز بعد مهم‌ترن.",
            limit = "اگه بارداری نمی‌خوای، برای پیشگیری به این تخمین تکیه نکن و از روش مطمئن استفاده کن.",
            sources = cycleSrc + listOf("cc_follicular", "acog_fabm")
        ),
        CycleSubWindow.PERI_OVULATORY to State(
            hormones = "احتمالاً هورمون LH (هورمونی که دستور آزاد شدن تخمک رو می‌ده) یهو بالا رفته و تخمک‌گذاری نزدیکه.",
            feel = "شاید یه درد خفیف یه طرف زیر شکم، ترشحات کش‌دار شبیه سفیدهٔ تخم‌مرغ یا میل جنسی بیشتری حس کنی.",
            eat = "آب زیاد، میوه و سبزی و یه منبع پروتئین مثل مرغ، عدس یا پنیر؛ اگه دنبال بارداری هستی، دربارهٔ اسید فولیک با پزشکت حرف بزن.",
            doIt = "اگه دنبال بارداری هستی، این چند روز بهترین زمانه؛ برای درد خفیف هم کیسهٔ آب گرم کمک می‌کنه.",
            limit = "برای پیشگیری از بارداری به این تخمین تکیه نکن؛ تقویم، آزمایش هورمون نیست.",
            warn = "اگه درد یه طرف شکم شدید شد یا با تب همراه بود.",
            sources = cycleSrc + listOf("cc_lh", "acog_fabm")
        ),
        CycleSubWindow.LUTEAL_EARLY to State(
            hormones = "بعد از تخمک‌گذاری، پروژسترون (هورمونی که رحم رو آروم و آماده نگه می‌داره) بالا می‌ره.",
            feel = "شاید کمی خواب‌آلودتر بشی، اشتهات بیشتر بشه یا بدنت یه کم گرم‌تر باشه.",
            eat = "وعده‌های منظم با نون سبوس‌دار، جو یا برنج قهوه‌ای، حبوبات و ماست؛ میان‌وعده هم یه مشت آجیل خام.",
            doIt = "ورزش متوسط مثل پیاده‌روی تند یا دوچرخه، و شب‌ها سر یه ساعت ثابت بخواب.",
            limit = "خوراکی‌های خیلی شیرین و شور رو کمتر کن؛ معمولاً نفخ و گرسنگی رو بیشتر می‌کنن.",
            sources = cycleSrc + listOf("cc_luteal")
        ),
        CycleSubWindow.LUTEAL_MID to State(
            hormones = "پروژسترون حدوداً تو اوجشه و استروژن هم یه بار دیگه بالا رفته.",
            feel = "شاید نفخ، حساسیت سینه یا خستگی بیشتری حس کنی و زودتر خسته بشی.",
            eat = "خوراکی‌های کلسیم‌دار مثل ماست، شیر و پنیر کم‌نمک، سبزی برگ‌دار، و چند وعدهٔ کوچیک به‌جای یه وعدهٔ سنگین.",
            doIt = "ورزش ملایم تا متوسط، یوگا یا نفس عمیق، و برای استراحت هم وقت بذار.",
            limit = "نمک، چیپس و پفک و نوشیدنی‌های کافئین‌دار رو کمتر کن؛ نفخ و بدخوابی رو بیشتر می‌کنن.",
            sources = cycleSrc + listOf("cc_luteal", "acog_pms")
        ),
        CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL to State(
            hormones = "استروژن و پروژسترون دارن پایین میان تا پریود بعدی شروع بشه؛ همین افت ممکنه روی خواب و حالت اثر بذاره.",
            feel = "احتمالاً زودتر خسته می‌شی، شاید زودرنج‌تر باشی و نفخ یا حساسیت سینه داشته باشی.",
            eat = "کربوهیدرات سالم مثل جو، نون سبوس‌دار، سیب‌زمینی آب‌پز و حبوبات، به‌علاوهٔ ماست؛ اگه هوس شیرینی کردی، با خرما یا میوه شروع کن.",
            doIt = "یه پیاده‌روی ۳۰ دقیقه‌ای، دوش آب گرم و زودتر خوابیدن؛ کارهای امروز رو هم سبک‌تر بچین.",
            limit = "قهوه، نوشابه، غذای شور و شیرینی زیاد رو کمتر کن؛ معمولاً بی‌قراری، نفخ و حساسیت سینه رو بیشتر می‌کنن.",
            sources = cycleSrc + listOf("acog_pms", "mayo_pms", "cc_pms")
        ),
        CycleSubWindow.LATE_PERIOD to State(
            hormones = "وقتی پریود دیر می‌کنه، از روی تقویم نمی‌شه فهمید هورمون‌هات چه وضعی دارن؛ استرس، کم‌خوابی، کاهش وزن یا بارداری می‌تونن دلیلش باشن.",
            feel = "شاید نگران باشی یا علائمی شبیه قبل از پریود داشته باشی؛ این‌ها هم عادی‌ان.",
            eat = "غذای ساده و منظم و آب کافی؛ اگه احتمال بارداری هست، دربارهٔ اسید فولیک با پزشکت حرف بزن.",
            doIt = "اگه احتمال بارداری هست، تست بارداری خونگی بزن؛ از روزی که پریود عقب افتاده معمولاً جواب درست می‌ده.",
            limit = "تا وقتی مطمئن نشدی باردار نیستی، الکل، سیگار و داروهای بدون نسخه رو کنار بذار.",
            warn = "اگه درد شدید یه طرف شکم، خون‌ریزی غیرعادی یا سرگیجه داشتی.",
            sources = cycleSrc + listOf("nhs_late_period", "nhs_preg_test")
        ),
        CycleSubWindow.UNKNOWN to State(
            hormones = "با اطلاعات فعلی نمی‌شه گفت امروز کدوم هورمون بالاست؛ وقتی پریودهات رو ثبت کنی، دقیق‌تر می‌گیم.",
            feel = "به بدنت دقت کن و هر چی حس کردی ثبت کن؛ همین ثبت‌ها کم‌کم الگوی خودت رو نشون می‌دن.",
            eat = "یه بشقاب متعادل: نصفش سبزی و میوه، یه کم پروتئین مثل حبوبات یا مرغ، و یه کم نون یا برنج سبوس‌دار.",
            doIt = "روزی ۳۰ دقیقه پیاده‌روی و خواب منظم، برای هر روز ماه خوبه.",
            limit = "نوشیدنی‌های شیرین و کافئین زیاد رو، به‌خصوص عصرها، کمتر کن.",
            sources = cycleSrc
        )
    )

    /** Pregnancy week ranges (inclusive upper bound) and their state. */
    val pregnancyStates: List<Pair<IntRange, State>> = listOf(
        (0..4) to State(
            hormones = "هورمون بارداری hCG (همونی که تست بارداری نشونش می‌ده) تازه شروع کرده به بالا رفتن و پروژسترون هم بالاست.",
            feel = "شاید هنوز چیز خاصی حس نکنی، یا خستگی، حساسیت سینه و یه لکه‌بینی خیلی کم داشته باشی.",
            eat = "اسید فولیک (معمولاً روزی ۴۰۰ میکروگرم، با نظر پزشکت)، سبزی برگ‌دار، حبوبات و تخم‌مرغ کاملاً پخته.",
            doIt = "یه نوبت پیش پزشک یا ماما بگیر و داروهایی رو که مصرف می‌کنی بهش بگو.",
            limit = "الکل و سیگار رو کامل کنار بذار، کافئین رو زیر ۲۰۰ میلی‌گرم (حدود دو لیوان چای یا یه فنجون قهوه) نگه دار و هیچ دارویی رو بدون پرسیدن نخور.",
            warn = "اگه درد شدید یه طرف شکم یا خون‌ریزی زیاد داشتی، همون موقع.",
            sources = listOf("medline_hcg", "nhs_vitamins", "nhs_foods", "nhs_get_help")
        ),
        (5..8) to State(
            hormones = "hCG خیلی سریع داره بالا می‌ره و پروژسترون هم بالاست؛ این‌ها احتمالاً دلیل تهوع و خستگی‌ان.",
            feel = "شاید حالت تهوع (هر ساعتی از روز)، خستگی زیاد، حساسیت به بوها و زیاد دستشویی رفتن داشته باشی.",
            eat = "وعده‌های کوچیک و زیاد، یه چیز خشک مثل بیسکویت ساده یا نون برشته قبل از بلند شدن، زنجبیل، و آب جرعه‌جرعه.",
            doIt = "هر وقت تونستی استراحت کن؛ اگه حالت خوبه، یه پیاده‌روی سبک هم خوبه.",
            limit = "گوشت، مرغ، تخم‌مرغ و ماهی خام یا نیم‌پز، شیر و پنیر غیرپاستوریزه (مثل پنیر محلی)، و غذای چرب که تهوع رو بدتر می‌کنه.",
            warn = "اگه یه روز کامل هیچی تو معده‌ت نموند، ادرارت خیلی تیره شد یا خون‌ریزی داشتی.",
            sources = listOf("medline_hcg", "nhs_sickness", "rcog_sickness", "nhs_foods")
        ),
        (9..13) to State(
            hormones = "hCG حدود هفته‌های ۹ تا ۱۲ به اوجش می‌رسه و بعد کم‌کم پایین میاد؛ جفت هم داره ساختن پروژسترون رو به عهده می‌گیره.",
            feel = "شاید تهوع و خستگی هنوز باشه، ولی خیلی‌ها کم‌کم بهتر می‌شن؛ یبوست و نفخ هم زیاد پیش میاد.",
            eat = "غذای فیبردار مثل نون سبوس‌دار، سبزی، آلو و انجیر خشک، کلی آب، و لبنیات پاستوریزه برای کلسیم.",
            doIt = "اگه حالت خوبه، پیاده‌روی، شنا یا یوگای مخصوص بارداری؛ آزمایش‌ها و سونوگرافی سه‌ماههٔ اول رو هم با پزشکت هماهنگ کن.",
            limit = "ماهی‌های پرجیوه مثل اره‌ماهی و کوسه، جگر، سوسیس و کالباسی که داغ نشده، و بیشتر از ۲۰۰ میلی‌گرم کافئین.",
            warn = "خون‌ریزی، درد شدید شکم یا تب بالا.",
            sources = listOf("medline_hcg", "nhs_weeks", "nhs_foods", "acog_exercise_preg")
        ),
        (14..17) to State(
            hormones = "hCG پایین اومده و استروژن و پروژسترون، که حالا بیشترش رو جفت می‌سازه، آروم‌آروم بالا می‌رن.",
            feel = "خیلی‌ها حالا انرژی بیشتری دارن و تهوع کمتر شده؛ شاید گرفتگی بینی یا خون‌ریزی لثه هم داشته باشی.",
            eat = "آهن و پروتئین: گوشت و مرغ کاملاً پخته، عدس، لوبیا، تخم‌مرغ پخته، کنارش سبزی و مرکبات.",
            doIt = "اگه پزشکت منعی نکرده، هفته‌ای حدود ۱۵۰ دقیقه پیاده‌روی تند یا شنا؛ مسواک و نخ دندون رو هم جدی بگیر.",
            limit = "ورزش‌هایی که خطر زمین خوردن یا ضربه به شکم دارن، و غذای آماده‌ای که معلوم نیست کاملاً پخته شده.",
            warn = "خون‌ریزی، آبریزش یا درد شدید شکم.",
            sources = listOf("nhs_weeks", "acog_exercise_preg", "acog_nutrition_preg", "nhs_foods")
        ),
        (18..22) to State(
            hormones = "استروژن و پروژسترون هنوز دارن بالا می‌رن و هورمون‌های جفت به رشد جنین کمک می‌کنن.",
            feel = "شاید کم‌کم حرکت‌های جنین رو حس کنی؛ گرفتگی ساق پا، کمردرد و تیر کشیدن پهلوها هم زیاد پیش میاد.",
            eat = "لبنیات پاستوریزه، کنجد، بادام و سبزی برگ‌دار برای کلسیم؛ ماهی کم‌جیوه مثل قزل‌آلا، کاملاً پخته و نهایتاً دو بار در هفته.",
            doIt = "روزی یه پیاده‌روی، کشش ساق پا قبل از خواب، و سونوگرافی غربالگری حدود هفتهٔ ۱۸ تا ۲۲ رو از دست نده.",
            limit = "بلند کردن بار سنگین، طولانی به پشت خوابیدن، و ماهی و گوشت خام یا نیم‌پز.",
            warn = "خون‌ریزی، آبریزش، دردهای منظم شکم یا سردرد شدید.",
            sources = listOf("nhs_week20", "nhs_foods", "acog_exercise_preg", "who_anc")
        ),
        (23..27) to State(
            hormones = "هورمون‌ها بالان و بدنت داره خون بیشتری می‌سازه؛ هورمون‌های جفت قند خون رو هم یه کم تغییر می‌دن.",
            feel = "شاید سوزش سر دل، ورم پا، کمردرد یا خستگی بیشتری داشته باشی؛ حرکت‌های جنین هم واضح‌تر شده.",
            eat = "وعده‌های کوچیک، کمتر سرخ‌کرده، پروتئین و فیبر کافی؛ غذای آهن‌دار رو کنار میوه یا سبزی ویتامین‌سی‌دار بخور.",
            doIt = "آزمایش قند بارداری (معمولاً هفتهٔ ۲۴ تا ۲۸) رو با پزشکت هماهنگ کن و موقع نشستن پاهات رو بالا بذار.",
            limit = "شیرینی و نوشابهٔ زیاد، و شام تند و سنگین که سوزش سر دل رو بدتر می‌کنه.",
            warn = "ورم یهویی صورت و دست، سردرد شدید، تاری دید یا کمتر شدن حرکت جنین؛ این‌ها رو فوری بگو.",
            sources = listOf("nhs_weeks", "acog_nutrition_preg", "nhs_get_help", "nhs_movements")
        ),
        (28..32) to State(
            hormones = "استروژن و پروژسترون نزدیک بالاترین حدشونن و هورمون ریلکسین مفصل‌ها رو شل‌تر می‌کنه.",
            feel = "شاید زودتر خسته بشی، نفست زودتر بند بیاد، کمرت درد بگیره یا گاهی سفت شدن بی‌درد شکم رو حس کنی.",
            eat = "غذای آهن‌دار مثل گوشت کم‌چرب، عدس و لوبیا، لبنیات پاستوریزه، و وعده‌های کوچیک‌تر چون معده جای کمتری داره.",
            doIt = "به پهلو، ترجیحاً چپ، بخواب، هر روز حواست به حرکت‌های جنین باشه و پیاده‌روی سبک رو ادامه بده.",
            limit = "طولانی به پشت خوابیدن، زیاد سرپا موندن و بلند کردن چیز سنگین.",
            warn = "اگه حرکت جنین کمتر از همیشه شد، خون‌ریزی یا آبریزش داشتی یا انقباض‌ها منظم شد، فوری به پزشک یا زایشگاه خبر بده.",
            sources = listOf("nhs_weeks", "nhs_movements", "acog_exercise_preg", "nhs_get_help")
        ),
        (33..36) to State(
            hormones = "بدنت کم‌کم داره برای زایمان آماده می‌شه؛ هورمون‌هایی مثل ریلکسین لگن رو برای این کار نرم‌تر می‌کنن.",
            feel = "شاید خوابت سخت‌تر بشه، زیاد دستشویی بری و فشار پایین شکم یا انقباض‌های تمرینی حس کنی.",
            eat = "چند وعدهٔ کوچیک، خرما، میوه و سبزی، آب کافی، و غذای ساده‌ای که سوزش سر دل نده.",
            doIt = "ساک بیمارستان رو آماده کن، تمرین تنفس زایمان کن و هر روز کمی راه برو.",
            limit = "سفر طولانی بدون مشورت با پزشک، شام سنگین، و خوردن هر دارویی بدون پرسیدن.",
            warn = "کمتر شدن حرکت جنین، خون‌ریزی، آبریزش یا انقباض‌های منظم؛ فوری به زایشگاه خبر بده.",
            sources = listOf("nhs_weeks", "nhs_movements", "nhs_get_help")
        ),
        (37..45) to State(
            hormones = "نزدیک زایمان، اکسی‌توسین (هورمونی که انقباض‌های زایمان رو راه می‌ندازه) کم‌کم نقش بیشتری پیدا می‌کنه.",
            feel = "شاید انقباض‌های بیشتر، فشار لگن، ترشحات بیشتر یا بی‌قراری برای زایمان حس کنی.",
            eat = "غذای سبک و انرژی‌زا مثل خرما، نون و پنیر پاستوریزه، میوه و کلی آب.",
            doIt = "استراحت کن، نشونه‌های شروع زایمان رو بدون و شمارهٔ پزشک یا زایشگاه رو دم دستت بذار.",
            limit = "غذای سنگین و چرب، دور شدن طولانی از بیمارستان، و بی‌توجهی به کم شدن حرکت جنین.",
            warn = "انقباض‌های منظم، پاره شدن کیسهٔ آب، خون‌ریزی یا کمتر شدن حرکت جنین یعنی همین الان به زایشگاه زنگ بزن.",
            sources = listOf("nhs_weeks", "nhs_movements", "nhs_get_help")
        )
    )

    private val symptomLines = mapOf(
        "pain" to "امروز دل‌درد ثبت کردی؛ یه کیسهٔ آب گرم ممکنه کمکت کنه.",
        "bloating" to "امروز نفخ ثبت کردی؛ آب کافی و یه کم راه رفتن معمولاً کمک می‌کنه.",
        "headache" to "امروز سردرد ثبت کردی؛ آب بخور و کمی استراحت کن.",
        "breast_tenderness" to "امروز حساسیت سینه ثبت کردی؛ لباس زیر راحت‌تر ممکنه بهتر باشه.",
        "fatigue" to "امروز خستگی ثبت کردی؛ اگه می‌تونی برای استراحتت وقت بذار.",
        "nausea" to "امروز حالت تهوع ثبت کردی؛ وعده‌های کوچیک‌تر معمولاً راحت‌ترن.",
        "acne" to "امروز جوش ثبت کردی؛ صورتت رو با یه شویندهٔ ملایم بشور."
    )

    private fun groupDefault(phaseGroup: String): CycleSubWindow = when (phaseGroup) {
        "menstrual" -> CycleSubWindow.MENSTRUATION_EARLY
        "follicular" -> CycleSubWindow.FOLLICULAR_EARLY
        "fertile" -> CycleSubWindow.PERI_OVULATORY
        "early_luteal" -> CycleSubWindow.LUTEAL_EARLY
        "late_luteal" -> CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL
        else -> CycleSubWindow.UNKNOWN
    }

    /** The sub-window whose text is shown: the exact one when it agrees with the (policy-checked) phase group. */
    fun pickSubWindow(phaseGroup: String, subWindow: CycleSubWindow?): CycleSubWindow = when {
        phaseGroup == "general" -> if (subWindow == CycleSubWindow.LATE_PERIOD) CycleSubWindow.LATE_PERIOD else CycleSubWindow.UNKNOWN
        subWindow != null && CravingContent.phaseGroupOf(subWindow, null) == phaseGroup -> subWindow
        else -> groupDefault(phaseGroup)
    }

    private fun rowsOf(s: State, feelOverride: String? = null): List<Row> = buildList {
        add(Row("hormones", LABEL_HORMONES, s.hormones))
        add(Row("feel", LABEL_FEEL, feelOverride ?: s.feel))
        add(Row("eat", LABEL_EAT, s.eat))
        add(Row("do", LABEL_DO, s.doIt))
        add(Row("limit", LABEL_LIMIT, s.limit))
        s.warn?.let { add(Row("warn", LABEL_WARN, it)) }
    }

    fun forCycle(phaseGroup: String, today: DailyLogEntity?, subWindow: CycleSubWindow? = null): BodyNow {
        val s = cycleStates.getValue(pickSubWindow(phaseGroup, subWindow))
        val symptoms = today?.physicalSymptoms.orEmpty().split(',').map { it.trim() }.filter { it in symptomLines }
        val mood = moodOf(today)
        // What she logged today replaces the generic «احتمالاً حس می‌کنی» guess.
        val logged = mutableListOf<String>()
        if (symptoms.isNotEmpty()) logged += symptomLines.getValue(symptoms.first())
        if (mood == "bad") logged += "اگه امروز حال نداری، کارهات رو سبک‌تر بگیر."
        val feel = if (logged.isEmpty()) null else logged.joinToString(" ")
        val isLogged = today != null && (symptoms.isNotEmpty() || mood != null)
        return BodyNow(
            points = listOf(s.hormones, feel ?: s.feel),
            basisFa = if (isLogged) "بر اساس ثبت امروزت و زمان تقریبی چرخه‌ت" else "بر اساس زمان تقریبی چرخه‌ت",
            rows = rowsOf(s, feel),
            sourceIds = s.sources
        )
    }

    fun pregnancyState(week: Int): State = pregnancyStates.firstOrNull { week in it.first }?.second ?: pregnancyStates.last().second

    fun forPregnancy(week: Int?): BodyNow {
        if (week == null) {
            val line = "برای این‌که بدونیم کجای بارداری هستی، تاریخ مبنا رو ثبت کن."
            return BodyNow(listOf(line), "بر اساس هفتهٔ بارداریت", listOf(Row("feel", "هفتهٔ بارداری", line)))
        }
        val s = pregnancyState(week)
        return BodyNow(listOf(s.hormones, s.feel), "بر اساس هفتهٔ بارداریت", rowsOf(s), s.sources)
    }

    /** Last [days] days, oldest first; null = nothing logged that day (never counted as «معمولی»). */
    fun moodSeries(logs: List<DailyLogEntity>, todayEpoch: Long, days: Int = 14): List<Pair<Long, String?>> {
        val byDay = logs.associateBy { it.epochDay }
        return ((todayEpoch - days + 1)..todayEpoch).map { d -> d to moodOf(byDay[d]) }
    }
}
