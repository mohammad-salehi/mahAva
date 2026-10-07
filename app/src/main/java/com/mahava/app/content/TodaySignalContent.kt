package com.mahava.app.content

import com.mahava.app.content.ScienceSources.Evidence

/**
 * Explanations for what the user picks on the home check-in card.
 * Home shows only [Explanation.teaserFa]; the detail screen shows the full sections,
 * the evidence level and the sources. Sources: docs/science-check-in.md.
 */
object TodaySignalContent {
    data class Section(val titleFa: String, val bodyFa: String)

    data class Explanation(
        /** One short line for the home card. */
        val teaserFa: String,
        val sections: List<Section>,
        val evidence: Evidence,
        val sourceIds: List<String>
    ) {
        val textFa: String get() = sections.joinToString("\n\n") { it.bodyFa }
        /** Short source labels, e.g. "ACOG · درد پریود". */
        val sources: List<String> get() = ScienceSources.list(sourceIds).map { it.labelFa }
    }

    val physical = listOf(
        "pain" to "دل‌درد", "bloating" to "نفخ", "headache" to "سردرد",
        "breast_tenderness" to "حساسیت سینه", "fatigue" to "خستگی",
        "nausea" to "حالت تهوع", "acne" to "جوش"
    )
    val moods = listOf(
        "happy" to "خوشحال", "calm" to "آرام", "anxious" to "نگران",
        "irritable" to "زودرنج", "sad" to "غمگین"
    )

    fun kindTitleFa(kind: String) = when (kind) {
        "food" -> "هوس خوراکی"
        "body" -> "حال جسمی"
        else -> "حال روحی"
    }

    fun label(kind: String, key: String): String = when (kind) {
        "food" -> FoodCravingKeys.labelFa(key)
        "body" -> physical.find { it.first == key }?.second ?: key
        else -> moods.find { it.first == key }?.second ?: key
    }

    fun explain(kind: String, key: String, phaseGroup: String): String =
        explainFull(kind, key, phaseGroup).textFa

    fun explainFull(kind: String, key: String, phaseGroup: String): Explanation = when (kind) {
        "food" -> food(key, phaseGroup)
        "body" -> body(key, phaseGroup)
        else -> mood(key, phaseGroup)
    }

    /**
     * Hormonal contraception, irregular cycles or an unknown stage -> general wording,
     * so we never pretend to know the phase.
     */
    fun effectivePhaseGroup(
        subWindow: com.mahava.app.cycle.CycleSubWindow?,
        phase: com.mahava.app.cycle.CyclePhase?,
        hormonalContraception: Boolean,
        regularCycles: Boolean?
    ): String {
        if (hormonalContraception) return "general"
        if (regularCycles == false) return "general"
        return CravingContent.phaseGroupOf(subWindow, phase)
    }

    /** What is happening in the body in this phase, in two or three short sentences. */
    fun bodyTodayFa(phaseGroup: String): String = when (phaseGroup) {
        "menstrual" -> "این روزها استروژن (هورمون اصلی زنانه) و پروژسترون (هورمونی که رحم را برای بارداری آماده نگه می‌دارد) پایین‌اند. رحم پوشش داخلی‌اش را بیرون می‌فرستد."
        "follicular" -> "استروژن کم‌کم بالا می‌رود و پوشش رحم دوباره ضخیم می‌شود. پروژسترون هنوز پایین است."
        "fertile" -> "استروژن به اوج می‌رسد. بعد هورمون LH (پیامی از مغز که دستور آزاد شدن تخمک را می‌دهد) ناگهان بالا می‌رود. حدود ۱ تا ۱٫۵ روز بعد، تخمک آزاد می‌شود."
        "early_luteal" -> "بعد از تخمک‌گذاری، پروژسترون بالا می‌رود و حدود یک هفته بعد به اوج می‌رسد. دمای بدن هم کمی (حدود نیم درجه) بالاتر می‌رود."
        "late_luteal" -> "اگر بارداری پیش نیاید، استروژن و پروژسترون چند روز قبل از پریود پایین می‌آیند. علائم پیش از پریود معمولاً همین روزها پیدا می‌شوند."
        else -> "مرحلهٔ امروزت معلوم نیست، یا قرص هورمونی یا چرخهٔ نامنظم ثبت کرده‌ای. پس توضیح کلی می‌دهیم. قرص ترکیبی جلوی تخمک‌گذاری را می‌گیرد؛ برای همین فازهای معمول چرخه با آن صدق نمی‌کند."
    }

    // ---------------------------------------------------------------- food

    private val studiedFood = setOf("chocolate", "sweet", "salty", "carbs")

    private fun food(key: String, phase: String): Explanation {
        val label = FoodCravingKeys.labelFa(key)
        val tip = Section("چه کمکی می‌کند؟", foodTip(key))
        val notDeficiency = "یادت باشد: هوس خوراکی نشانهٔ کمبود ماده‌ای در بدن نیست. عادت، فرهنگ، خواب و استرس هم روی هوس اثر دارند."
        if (phase == "general") return Explanation(
            teaserFa = "هوس امروزت را نمی‌شود به روز خاصی از چرخه ربط داد.",
            sections = listOf(
                Section("چرا؟", "از روی یک روز نمی‌شود فهمید هوس $label از چرخه است یا نه. پژوهش‌ها نشان می‌دهند اشتها در نیمهٔ دوم چرخه کمی بیشتر می‌شود، ولی وقتی مرحلهٔ امروزت معلوم نیست، این را به تو نسبت نمی‌دهیم."),
                Section("خوب است بدانی", notDeficiency),
                tip
            ),
            evidence = Evidence.MEDIUM,
            sourceIds = listOf("energy_meta", "acog_pms")
        )
        if (key !in studiedFood) return Explanation(
            teaserFa = "برای هوس $label ربط روشنی با چرخه پیدا نشده.",
            sections = listOf(
                Section("چرا؟", "پژوهش‌ها بیشتر دربارهٔ شیرینی، شکلات، شوری و کل اشتها بوده‌اند. برای هوس $label شواهد روشنی پیدا نکردیم. سلیقه، گرسنگی، خواب و استرس احتمالاً نقش بیشتری دارند."),
                Section("خوب است بدانی", notDeficiency),
                tip
            ),
            evidence = Evidence.LOW,
            sourceIds = listOf("biocycle", "energy_review")
        )
        return when (phase) {
            "late_luteal" -> Explanation(
                teaserFa = "نزدیک پریود، هوس شیرینی و شوری برای خیلی‌ها بیشتر می‌شود.",
                sections = listOf(
                    Section("چرا؟", "نزدیک پریود اشتهای خیلی‌ها بیشتر می‌شود. یک مرور نظام‌مند (پژوهشی که نتیجهٔ چند پژوهش را با هم جمع می‌کند) ۱۵ پژوهش را بررسی کرد. نتیجه: در نیمهٔ دوم چرخه، زن‌ها به‌طور میانگین روزی حدود ۱۷۰ کالری بیشتر می‌خورند. در پژوهش بزرگ BioCycle هم هوس شکلات، شیرینی و خوراکی شور در روزهای آخر چرخه بیشتر بود."),
                    Section("علتش چیست؟", "هنوز دقیق معلوم نیست. تغییر هورمون‌ها احتمالاً نقش دارد. بعضی منابع به سروتونین (ماده‌ای در مغز که روی حال و اشتها اثر دارد) هم اشاره می‌کنند، اما این ثابت نشده. یک آزمایش کوچک هم تفاوتی در هوس شکلات بین دو مرحله ندید."),
                    Section("خوب است بدانی", notDeficiency),
                    tip
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("energy_meta", "biocycle", "choc_lab", "acog_pms", "mayo_pms")
            )
            "menstrual" -> Explanation(
                teaserFa = "این هوس گاهی از روزهای قبل از پریود ادامه پیدا می‌کند.",
                sections = listOf(
                    Section("چرا؟", "هوس شیرینی و شوری بیشتر در روزهای آخر چرخه دیده شده و ممکن است تا اول پریود ادامه پیدا کند. طبق ACOG، علائم پیش از پریود معمولاً تا ۴ روز بعد از شروع پریود تمام می‌شوند. پس احتمالاً این هوس هم به‌زودی کمتر می‌شود."),
                    Section("خوب است بدانی", notDeficiency),
                    tip
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("biocycle", "acog_pms")
            )
            "early_luteal" -> Explanation(
                teaserFa = "بعد از تخمک‌گذاری، اشتها برای بعضی‌ها کمی بیشتر می‌شود.",
                sections = listOf(
                    Section("چرا؟", "در نیمهٔ دوم چرخه (بعد از تخمک‌گذاری) اشتها به‌طور میانگین کمی بالا می‌رود. ولی بیشترین هوس شیرینی و شوری در روزهای نزدیک پریود دیده شده، نه همین روزها. پس هوس امروزت ممکن است به چرخه ربط داشته باشد یا نداشته باشد."),
                    Section("خوب است بدانی", notDeficiency),
                    tip
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("energy_meta", "biocycle")
            )
            else -> Explanation(
                teaserFa = "این روزها هوس‌ها معمولاً کمتر از روزهای قبل از پریود است.",
                sections = listOf(
                    Section("چرا؟", "پژوهش‌ها نشان می‌دهند اشتها حوالی تخمک‌گذاری معمولاً در کمترین حد است. هوس شیرینی و شوری هم بیشتر در روزهای آخر چرخه دیده شده. پس هوس امروزت احتمالاً بیشتر به گرسنگی، خواب، استرس یا سلیقه برمی‌گردد."),
                    Section("خوب است بدانی", notDeficiency),
                    tip
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("energy_review", "biocycle")
            )
        }
    }

    private fun foodTip(key: String): String = when (key) {
        "chocolate", "sweet" -> "اگر دلت خواست، کمی بخور؛ اشکالی ندارد. وعده‌های کوچک‌تر و منظم‌تر بخور. غلات سبوس‌دار، عدس و لوبیا (کربوهیدرات پیچیده، یعنی قندی که آرام جذب می‌شود) به گفتهٔ ACOG هوس را کمتر می‌کند."
        "salty" -> "نمک زیاد نفخ را بیشتر می‌کند. اگر هوس شوری داری، آب کافی بنوش و به‌جای خوراکی خیلی شور، مقدار کم بخور."
        "carbs" -> "نان و برنج سبوس‌دار، جو و حبوبات انتخاب بهتری‌اند. این‌ها کربوهیدرات پیچیده (قندی که آرام جذب می‌شود) دارند و ACOG می‌گوید برای هوس و حال پیش از پریود مفیدند."
        "caffeine" -> "ACOG و womenshealth.gov برای علائم پیش از پریود پیشنهاد می‌کنند کافئین را کم کنی. اگر قهوه می‌خوری، مقدارش را کم کن."
        else -> "این هوس را چند روز ثبت کن تا ببینی هر ماه تکرار می‌شود یا نه."
    }

    // ---------------------------------------------------------------- body

    private val perimenstrual = setOf("late_luteal", "menstrual")

    private fun body(key: String, phase: String): Explanation = when (key) {
        "pain" -> when (phase) {
            "menstrual" -> Explanation(
                teaserFa = "دل‌درد پریود از انقباض رحم است و معمولاً بعد از روزهای اول کم می‌شود.",
                sections = listOf(
                    Section("چرا؟", "پوشش داخلی رحم موادی به نام پروستاگلاندین (موادی شبیه هورمون که عضله را منقبض می‌کنند) می‌سازد. این مواد باعث انقباض رحم می‌شوند تا پوشش آن بیرون بیاید. روز اول پریود مقدارشان از همه بیشتر است. برای همین درد معمولاً روزهای اول بیشتر است و بعد کم می‌شود."),
                    Section("چقدر رایج است؟", "بیشتر از نیمی از زن‌ها هر ماه ۱ تا ۲ روز درد پریود دارند. بیشتر وقت‌ها درد خفیف است."),
                    Section("چه کمکی می‌کند؟", "کیسهٔ آب گرم روی شکم یا دوش آب گرم. ورزش هوازی منظم مثل پیاده‌روی تند. خواب کافی. مسکن‌های ضدالتهاب مثل ایبوپروفن اگر از اولین نشانهٔ درد خورده شوند بهتر اثر می‌کنند. اگر زخم معده، آسم یا مشکل خونریزی داری، قبلش با پزشک حرف بزن."),
                    Section("کی به پزشک سر بزنی؟", "اگر درد خیلی شدید است، هر ماه بدتر می‌شود، چند روز قبل از پریود شروع می‌شود یا بعد از پریود هم ادامه دارد. این‌ها ممکن است نشانهٔ بیماری‌هایی مثل اندومتریوز (رشد بافتی شبیه پوشش رحم، بیرون از رحم) باشند.")
                ),
                evidence = Evidence.HIGH,
                sourceIds = listOf("acog_cramps", "lumsden", "nhs_pms")
            )
            "late_luteal" -> Explanation(
                teaserFa = "گرفتگی خفیف شکم قبل از پریود هم جزو علائم رایج است.",
                sections = listOf(
                    Section("چرا؟", "ACOG دل‌درد را جزو علائم پیش از پریود می‌داند. در یک پژوهش، بیشتر از نیمی از زن‌های سالم در ۵ روز قبل از پریود دل‌درد داشتند. پروستاگلاندین (موادی که عضلهٔ رحم و روده را منقبض می‌کنند) احتمالاً نقش دارد."),
                    Section("چه کمکی می‌کند؟", "گرما، حرکت سبک و خواب کافی. اگر درد اذیتت می‌کند، مسکن ساده کمک می‌کند."),
                    Section("کی به پزشک سر بزنی؟", "اگر درد شدید است، فقط یک طرف است، با تب همراه است یا ممکن است باردار باشی.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("acog_pms", "gi_study", "acog_cramps")
            )
            "fertile" -> Explanation(
                teaserFa = "درد خفیف یک‌طرفه حوالی تخمک‌گذاری برای بعضی‌ها پیش می‌آید.",
                sections = listOf(
                    Section("چرا؟", "وقتی تخمدان تخمک را آزاد می‌کند، بعضی‌ها درد مبهم یا تیر کشیدن کوتاه در یک طرف پایین شکم حس می‌کنند. این درد معمولاً چند دقیقه تا ۱ یا ۲ روز طول می‌کشد و هر ماه ممکن است طرفش عوض شود."),
                    Section("کی به پزشک سر بزنی؟", "اگر درد شدید است و با مسکن بهتر نمی‌شود، مدام برمی‌گردد یا ممکن است باردار باشی.")
                ),
                evidence = Evidence.HIGH,
                sourceIds = listOf("nhs_ovulation_pain")
            )
            else -> Explanation(
                teaserFa = "این دل‌درد ربط روشنی به این روزهای چرخه ندارد.",
                sections = listOf(
                    Section("چرا؟", "دل‌درد علت‌های زیادی دارد؛ مثل گوارش، استرس یا عفونت. در این روزها، چرخه توضیح خوبی برایش نیست."),
                    Section("کی به پزشک سر بزنی؟", "اگر درد شدید است، تکرار می‌شود، با تب همراه است یا ممکن است باردار باشی.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("acog_cramps", "nhs_ovulation_pain")
            )
        }
        "bloating" -> when (phase) {
            in perimenstrual -> Explanation(
                teaserFa = "نفخ معمولاً روز اول پریود به اوج می‌رسد و بعد کم می‌شود.",
                sections = listOf(
                    Section("چرا؟", "نفخ یکی از علائم رایج سندرم پیش از قاعدگی (PMS، یعنی علائمی که چند روز قبل از پریود می‌آیند) است. یک پژوهش یک‌ساله روی ۶۲ زن نشان داد حس نفخ و ورم معمولاً روز اول پریود از همه بیشتر است. بعد کم می‌شود و وسط نیمهٔ اول چرخه به کمترین حد می‌رسد."),
                    Section("علتش چیست؟", "دقیق معلوم نیست. در همان پژوهش، مقدار هورمون‌ها با شدت نفخ ارتباط روشنی نداشت. خوراک شور و گوارش هم اثر دارند."),
                    Section("چه کمکی می‌کند؟", "نمک کمتر. آب کافی و نوشیدنی کافئین‌دار کمتر. وعده‌های کوچک‌تر. ورزش منظم در تمام ماه.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("bloating_cohort", "acog_pms", "nhs_pms", "medline_pms")
            )
            "early_luteal", "fertile" -> Explanation(
                teaserFa = "بعد از تخمک‌گذاری، نفخ برای بعضی‌ها کم‌کم بیشتر می‌شود.",
                sections = listOf(
                    Section("چرا؟", "در یک پژوهش یک‌ساله، حس نفخ از چند روز قبل تا چند روز بعد از تخمک‌گذاری آرام بالا رفت. البته هنوز خفیف بود و اوجش روز اول پریود بود."),
                    Section("چه کمکی می‌کند؟", "نمک کمتر، آب کافی و حرکت روزانه.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("bloating_cohort", "medline_pms")
            )
            else -> Explanation(
                teaserFa = "این روزها نفخ معمولاً کم است؛ احتمالاً علت دیگری دارد.",
                sections = listOf(
                    Section("چرا؟", "پژوهش‌ها نشان می‌دهند نفخِ مربوط به چرخه در نیمهٔ اول چرخه در کمترین حد است. پس نفخ امروزت احتمالاً بیشتر به غذا، گوارش یا استرس برمی‌گردد."),
                    Section("چه کمکی می‌کند؟", "غذاهای نفاخ و شور را کم کن و آب کافی بنوش. اگر نفخ ادامه داشت، با پزشک حرف بزن.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("bloating_cohort")
            )
        }
        "breast_tenderness" -> if (phase in perimenstrual || phase == "early_luteal") Explanation(
            teaserFa = "درد سینه قبل از پریود رایج است و با تمام شدن پریود می‌رود.",
            sections = listOf(
                Section("چرا؟", "درد سینهٔ مربوط به پریود معمولاً تا ۲ هفته قبل از پریود شروع می‌شود. کم‌کم بیشتر می‌شود و وقتی پریود تمام شد، از بین می‌رود. معمولاً هر دو سینه را می‌گیرد و حس سنگینی یا درد مبهم دارد. گاهی تا زیر بغل هم می‌رسد. این درد به تغییر هورمون‌ها در طول چرخه ربط دارد."),
                Section("چه کمکی می‌کند؟", "سوتین اندازه و راحت در روز و سوتین نرم موقع خواب. مسکن ساده مثل استامینوفن یا ایبوپروفن. شواهد کمی هست که ویتامین E یا روغن گل مغربی کمک کنند."),
                Section("کی به پزشک سر بزنی؟", "اگر درد بهتر نمی‌شود، فقط یک طرف است یا توده، قرمزی، تب یا ترشح از نوک سینه داری.")
            ),
            evidence = Evidence.HIGH,
            sourceIds = listOf("nhs_breast", "hartlage", "acog_pms")
        ) else Explanation(
            teaserFa = "درد سینه در این روزها کمتر به چرخه ربط دارد.",
            sections = listOf(
                Section("چرا؟", "درد سینهٔ مربوط به پریود معمولاً در ۲ هفتهٔ قبل از پریود است. در این روزها علت‌های دیگر هم ممکن است؛ مثل سوتین نامناسب، کشیدگی عضله یا بعضی داروها مثل قرص ضدبارداری."),
                Section("کی به پزشک سر بزنی؟", "اگر درد بهتر نمی‌شود، فقط یک طرف است یا توده، قرمزی یا ترشح داری.")
            ),
            evidence = Evidence.HIGH,
            sourceIds = listOf("nhs_breast")
        )
        "headache" -> if (phase in perimenstrual) Explanation(
            teaserFa = "سردرد نزدیک پریود برای بعضی‌ها پیش می‌آید.",
            sections = listOf(
                Section("چرا؟", "ACOG و NHS سردرد را جزو علائم رایج پیش از پریود می‌دانند. علت دقیقش معلوم نیست؛ تغییر هورمون‌ها احتمالاً نقش دارد. اگر میگرن داری، ممکن است نزدیک پریود بدتر شود."),
                Section("چه کمکی می‌کند؟", "خواب منظم، آب کافی و وعده‌های منظم. مسکن ساده مثل ایبوپروفن یا استامینوفن."),
                Section("کی به پزشک سر بزنی؟", "اگر سردرد خیلی شدید است، ناگهانی شروع شده یا با تاری دید، ضعف یا تب همراه است.")
            ),
            evidence = Evidence.MEDIUM,
            sourceIds = listOf("acog_pms", "nhs_pms", "owh_pms")
        ) else Explanation(
            teaserFa = "سردرد امروز احتمالاً به چرخه ربطی ندارد.",
            sections = listOf(
                Section("چرا؟", "سردردِ مربوط به چرخه بیشتر در روزهای نزدیک پریود است. در این روزها کم‌خوابی، کم‌آبی، گرسنگی و استرس علت‌های رایج‌تری‌اند."),
                Section("کی به پزشک سر بزنی؟", "اگر سردرد خیلی شدید یا ناگهانی است، یا با تاری دید، ضعف یا تب همراه است.")
            ),
            evidence = Evidence.MEDIUM,
            sourceIds = listOf("acog_pms")
        )
        "fatigue" -> when (phase) {
            in perimenstrual -> Explanation(
                teaserFa = "خستگی قبل و اول پریود خیلی رایج است.",
                sections = listOf(
                    Section("چرا؟", "در یک پژوهش، حدود نصف زن‌ها قبل و موقع پریود خسته بودند. خواب هم در این روزها برای بعضی‌ها بدتر می‌شود؛ به‌خصوص اگر علائم پیش از پریود یا درد پریود دارند. علت دقیق خستگی معلوم نیست."),
                    Section("چه کمکی می‌کند؟", "ورزش هوازی منظم در تمام ماه، خستگی و حال بد پیش از پریود را کمتر می‌کند. سعی کن حدود ۸ ساعت بخوابی و ساعت خوابت هر روز یکی باشد."),
                    Section("کی به پزشک سر بزنی؟", "اگر خستگی خیلی شدید است، تمام ماه ادامه دارد یا پریودت خیلی سنگین است.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("gi_study", "sleep_review", "acog_pms", "owh_pms")
            )
            "early_luteal" -> Explanation(
                teaserFa = "بعد از تخمک‌گذاری بعضی‌ها کمی خسته‌ترند؛ برای همه نیست.",
                sections = listOf(
                    Section("چرا؟", "بعد از تخمک‌گذاری پروژسترون بالا می‌رود و دمای بدن کمی گرم‌تر می‌شود. خواب عمیق در بیشتر زن‌های جوان تغییر زیادی نمی‌کند. پس اگر خسته‌ای، خواب، کار و استرس را هم در نظر بگیر."),
                    Section("چه کمکی می‌کند؟", "خواب منظم، حرکت روزانه و وعده‌های منظم.")
                ),
                evidence = Evidence.LOW,
                sourceIds = listOf("sleep_review")
            )
            else -> Explanation(
                teaserFa = "خستگی امروز احتمالاً بیشتر به خواب و کار برمی‌گردد.",
                sections = listOf(
                    Section("چرا؟", "خستگیِ مربوط به چرخه بیشتر در روزهای نزدیک پریود و روزهای اول آن دیده شده. در این روزها خواب، استرس و تغذیه علت‌های محتمل‌تری‌اند."),
                    Section("کی به پزشک سر بزنی؟", "اگر خستگی چند هفته ادامه دارد یا خیلی شدید است.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("gi_study", "sleep_review")
            )
        }
        "nausea" -> if (phase in perimenstrual) Explanation(
            teaserFa = "حالت تهوع گاهی همراه درد پریود پیش می‌آید.",
            sections = listOf(
                Section("چرا؟", "در درد شدید پریود، بعضی‌ها حالت تهوع، اسهال یا سرگیجه هم دارند. پروستاگلاندین (ماده‌ای که رحم را منقبض می‌کند) روی روده هم اثر دارد. در یک پژوهش، حدود ۱۵٪ زن‌ها دور پریود حالت تهوع داشتند."),
                Section("چه کمکی می‌کند؟", "غذای سبک و کم‌حجم، آب کافی و استراحت. درمان درد پریود معمولاً تهوع را هم کمتر می‌کند."),
                Section("کی به پزشک سر بزنی؟", "اگر استفراغ مداوم داری، نمی‌توانی آب بنوشی یا ممکن است باردار باشی.")
            ),
            evidence = Evidence.MEDIUM,
            sourceIds = listOf("acog_cramps", "gi_study")
        ) else Explanation(
            teaserFa = "حالت تهوع امروز ربط روشنی به چرخه ندارد.",
            sections = listOf(
                Section("چرا؟", "تهوعِ مربوط به چرخه بیشتر همراه درد پریود است. در روزهای دیگر، گوارش، غذا، استرس یا بارداری علت‌های محتمل‌تری‌اند."),
                Section("کی به پزشک سر بزنی؟", "اگر تهوع ادامه دارد یا ممکن است باردار باشی، تست بارداری بده و با پزشک حرف بزن.")
            ),
            evidence = Evidence.MEDIUM,
            sourceIds = listOf("acog_cramps")
        )
        "acne" -> if (phase in perimenstrual) Explanation(
            teaserFa = "جوش قبل از پریود برای خیلی‌ها بیشتر می‌شود.",
            sections = listOf(
                Section("چرا؟", "در یک نظرسنجی، ۶۵٪ زن‌هایی که جوش داشتند گفتند جوششان با پریود بدتر می‌شود؛ بیشترشان در هفتهٔ قبل از پریود. هورمون‌هایی به نام آندروژن (هورمون‌هایی که در زن‌ها هم کمی هست) غده‌های چربی پوست را فعال‌تر می‌کنند. یک توضیح احتمالی این است که نزدیک پریود، اثر آن‌ها نسبت به استروژن بیشتر می‌شود. این هنوز کامل ثابت نشده."),
                Section("چه کمکی می‌کند؟", "صورت را روزی دو بار با شوینده ملایم بشوی. جوش را فشار نده. اگر جوش زیاد یا دردناک است، متخصص پوست درمان‌های مؤثری دارد.")
            ),
            evidence = Evidence.LOW,
            sourceIds = listOf("acne_study", "nhs_pms", "mayo_pms")
        ) else Explanation(
            teaserFa = "جوش امروز را نمی‌شود فقط به چرخه نسبت داد.",
            sections = listOf(
                Section("چرا؟", "بدتر شدن جوش بیشتر در هفتهٔ قبل از پریود گزارش شده. در روزهای دیگر، مراقبت پوست، استرس، خواب و ژنتیک نقش بیشتری دارند.")
            ),
            evidence = Evidence.LOW,
            sourceIds = listOf("acne_study")
        )
        else -> Explanation(
            teaserFa = "این حس را چند روز ثبت کن تا الگویت معلوم شود.",
            sections = listOf(Section("چرا؟", "از یک روز نمی‌شود علت دقیق این حس را گفت.")),
            evidence = Evidence.LOW,
            sourceIds = listOf("acog_pms")
        )
    }

    // ---------------------------------------------------------------- mood

    private const val CRISIS_FA =
        "اگر فکر آسیب زدن به خودت را داری، همین حالا با اورژانس ۱۱۵ یا صدای مشاور بهزیستی ۱۴۸۰ تماس بگیر."

    private fun mood(key: String, phase: String): Explanation {
        val negative = key in setOf("anxious", "irritable", "sad")
        val label = label("mood", key)
        if (!negative) return when (phase) {
            "follicular", "fertile" -> Explanation(
                teaserFa = "بعد از پریود خیلی‌ها حس سبکی دارند. لذتش را ببر!",
                sections = listOf(
                    Section("چرا؟", "علائم پیش از پریود معمولاً تا ۴ روز بعد از شروع پریود تمام می‌شوند. برای همین خیلی‌ها در این روزها حس بهتری دارند. ولی حال خوب فقط به هورمون‌ها بستگی ندارد؛ خواب، آدم‌ها و اتفاق‌های روز هم مهم‌اند."),
                    Section("چه کمکی می‌کند؟", "عادت‌های خوبت مثل ورزش و خواب منظم را ادامه بده. این عادت‌ها روزهای سخت‌تر قبل از پریود را هم آسان‌تر می‌کنند.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("acog_pms", "owh_pms")
            )
            else -> Explanation(
                teaserFa = "خیلی خوب! همه قبل یا موقع پریود حال بد ندارند.",
                sections = listOf(
                    Section("چرا؟", "خیلی از زن‌ها در تمام چرخه حالشان تغییر زیادی نمی‌کند. در یک پژوهش، حال بد قبل از پریود به‌طور میانگین بالا نرفت. پس اینکه امروز $label هستی کاملاً طبیعی است."),
                    Section("چه کمکی می‌کند؟", "عادت‌های خوبت مثل ورزش منظم و خواب کافی را ادامه بده.")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("hengartner", "owh_pms")
            )
        }
        return when (phase) {
            in perimenstrual -> Explanation(
                teaserFa = "حال سخت‌تر قبل از پریود رایج است و معمولاً با شروع پریود بهتر می‌شود.",
                sections = listOf(
                    Section("چرا؟", "چند روز قبل از پریود، استروژن و پروژسترون پایین می‌آیند. مغز بعضی‌ها به این تغییر حساس‌تر است. این حساسیت ممکن است روی سروتونین (ماده‌ای در مغز که روی حال، خواب و اشتها اثر دارد) اثر بگذارد. علت دقیقش هنوز کامل معلوم نیست."),
                    Section("چقدر رایج است؟", "بیشتر از ۹۰٪ زن‌ها گاهی علائمی قبل از پریود دارند. این علائم معمولاً از حدود ۴ روز قبل تا ۳ روز اول پریود بیشترند. بالا و پایین شدن ناگهانی حال از همه رایج‌تر است. ولی همه این‌طور نیستند؛ در یک پژوهش، حال بد به‌طور میانگین بالا نرفت."),
                    Section("چه کمکی می‌کند؟", "ورزش هوازی منظم در تمام ماه. خواب منظم، حدود ۸ ساعت. کافئین، نمک و قند کمتر در ۲ هفتهٔ قبل از پریود. تنفس عمیق، یوگا یا مدیتیشن. حرف زدن با یک دوست یا نوشتن هم کمک می‌کند."),
                    Section("کی کمک بگیری؟", "اگر این حال هر ماه شدید است و کار، درس یا رابطه‌هایت را به هم می‌زند، ممکن است PMDD باشد (نوع شدید PMS). PMDD قابل درمان است؛ با پزشک حرف بزن. $CRISIS_FA")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("owh_pms", "hartlage", "hengartner", "acog_pms", "mayo_pms", "cc_pmdd")
            )
            "early_luteal" -> Explanation(
                teaserFa = "این روزها هنوز زود است که حالت را به پریود ربط بدهی.",
                sections = listOf(
                    Section("چرا؟", "تغییر حالِ مربوط به چرخه بیشتر در چند روز آخر قبل از پریود دیده می‌شود، نه درست بعد از تخمک‌گذاری. پس حس $label امروز احتمالاً بیشتر به خواب، فشار کار یا اتفاق‌های روز برمی‌گردد."),
                    Section("چه کمکی می‌کند؟", "ورزش، خواب منظم و حرف زدن با آدم‌های نزدیک."),
                    Section("کی کمک بگیری؟", "اگر این حال بیشتر از ۲ هفته ادامه دارد، با پزشک یا مشاور حرف بزن. $CRISIS_FA")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("hartlage", "acog_pms")
            )
            "follicular", "fertile" -> Explanation(
                teaserFa = "این روزها حال بد معمولاً به چرخه ربطی ندارد.",
                sections = listOf(
                    Section("چرا؟", "علائم پیش از پریود معمولاً تا ۴ روز بعد از شروع پریود تمام می‌شوند. پس حس $label در این روزها احتمالاً علت دیگری دارد؛ مثل خواب، فشار کار یا اتفاق‌های زندگی."),
                    Section("خوب است بدانی", "اگر حال بد تمام ماه هست و فقط قبل از پریود بدتر می‌شود، ممکن است افسردگی یا اضطراب باشد. طبق ACOG، حدود نیمی از کسانی که برای PMS کمک می‌گیرند یکی از این دو را هم دارند."),
                    Section("کی کمک بگیری؟", "اگر این حال بیشتر از ۲ هفته ادامه دارد، با پزشک یا مشاور حرف بزن. $CRISIS_FA")
                ),
                evidence = Evidence.HIGH,
                sourceIds = listOf("acog_pms", "medline_pms")
            )
            else -> Explanation(
                teaserFa = "از روی یک روز نمی‌شود گفت این حال از چرخه است.",
                sections = listOf(
                    Section("چرا؟", "از روی یک روز نمی‌شود گفت این حس به چرخه مربوط است. وقتی مرحلهٔ امروزت معلوم نیست، آن را به هورمون‌ها نسبت نمی‌دهیم. خواب، فشار روزانه و اتفاق‌های زندگی هم مهم‌اند. اگر قرص ضدبارداری هورمونی می‌خوری، این قرص می‌تواند علائم پیش از پریود را کمتر یا گاهی بیشتر کند."),
                    Section("چه کمکی می‌کند؟", "حالت را چند هفته ثبت کن تا الگویت معلوم شود. ورزش منظم و خواب کافی به حال روحی کمک می‌کنند."),
                    Section("کی کمک بگیری؟", "اگر این حال بیشتر از ۲ هفته ادامه دارد، با پزشک یا مشاور حرف بزن. $CRISIS_FA")
                ),
                evidence = Evidence.MEDIUM,
                sourceIds = listOf("acog_pms", "owh_pms", "medline_pms")
            )
        }
    }

    const val cautionFa =
        "این توضیح‌ها آموزشی‌اند، نه تشخیص. بدن هر کس فرق دارد. اگر درد یا حال بدت شدید است یا ادامه دارد، با پزشک حرف بزن."
}
