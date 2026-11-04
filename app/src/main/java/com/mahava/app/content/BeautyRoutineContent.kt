package com.mahava.app.content

/**
 * Phase-based skin & hair care, educational only. Local static bank (no AI / network).
 * Wording is deliberately cautious (اغلب / ممکن است); no brands, no diagnosis, no doses.
 * Base routine everywhere: mild cleanser + moisturizer + daytime sunscreen.
 */
data class BeautySection(
    val summaryFa: String,
    val morningFa: List<String>,
    val nightFa: List<String>,
    val doFa: List<String>,
    val avoidFa: List<String>,
    val whyFa: String
)

data class BeautyRoutine(
    val key: String,
    val phaseTitleFa: String,
    val skin: BeautySection,
    val hair: BeautySection,
    val homeSkinActionFa: String,
    val homeHairActionFa: String,
    val homeWhyFa: String,
    val disclaimerFa: String = BeautyRoutineContent.DISCLAIMER
)

object BeautyRoutineContent {

    const val DISCLAIMER =
        "این راهنما آموزشی است و جایگزین ویزیت پزشک پوست یا زنان نیست. الگوی پوست و مو برای همه یکسان نیست؛ ۱–۲ چرخه علائم واقعی‌تان را با فاز مقایسه کنید. جوش شدید، ریزش زیاد، یا علائم مشکوک به اختلال هورمونی را با متخصص بررسی کنید."

    const val BASE_FA = "پایهٔ ثابت همیشه: شوینده ملایم + مرطوب‌کننده + ضدآفتاب در روز. تنظیم فاز فقط لایهٔ دوم است."

    /** Maps the app's phaseGroup (CravingContent.phaseGroupOf / effectivePhaseGroup) to a bank key. */
    fun keyFor(phaseGroup: String): String = when (phaseGroup) {
        "menstrual" -> "menstrual"
        "follicular" -> "follicular"
        "fertile", "ovulatory" -> "ovulatory"
        "early_luteal", "late_luteal", "luteal" -> "luteal"
        else -> "general"
    }

    fun forPhaseGroup(phaseGroup: String): BeautyRoutine = bank.getValue(keyFor(phaseGroup))

    fun forPregnancy(): BeautyRoutine = pregnancy

    fun forState(phaseGroup: String, pregnant: Boolean): BeautyRoutine =
        if (pregnant) pregnancy else forPhaseGroup(phaseGroup)

    private val menstrual = BeautyRoutine(
        key = "menstrual",
        phaseTitleFa = "فاز قاعدگی",
        skin = BeautySection(
            summaryFa = "با افت استروژن و پروژسترون، پوست خیلی‌ها خشک‌تر، حساس‌تر یا کدرتر می‌شود و تحمل مواد قوی کمتر است.",
            morningFa = listOf(
                "شوینده ملایم بدون کف زیاد",
                "آبرسان/مرطوب‌کننده سبک با تمرکز روی سد پوست (مثلاً سرامید یا گلیسرین اگر محصولتان دارد)",
                "ضدآفتاب روزانه با بافت سبک",
                "آرایش کمتر و غیرجوش‌زا در صورت نیاز"
            ),
            nightFa = listOf(
                "پاکسازی ملایم",
                "مرطوب‌کننده ترمیمی",
                "در قرمزی از محصولات خیلی ساده استفاده کنید",
                "اکتیوهای قوی را موقتاً کم یا قطع کنید"
            ),
            doFa = listOf("روتین را ساده نگه دارید", "آب کافی بنوشید", "خواب کافی داشته باشید"),
            avoidFa = listOf("اسکراب زبر", "AHA/BHA قوی", "رتینوئید قوی", "محصول جدید تست‌نشده", "شستشوی بیش‌ازحد"),
            whyFa = "در این روزها سطح استروژن و پروژسترون پایین است؛ پوست ممکن است حساس‌تر و کم‌تحمل‌تر باشد، پس ساده‌سازی و حفظ سد پوست اولویت دارد."
        ),
        hair = BeautySection(
            summaryFa = "کف سر ممکن است حساس‌تر باشد؛ «روز موی بد» در قاعدگی شایع‌تر گزارش شده ولی الزاماً به چربی کف سر وابسته نیست.",
            morningFa = listOf("شامپوی ملایم", "آب ولرم نه داغ"),
            nightFa = listOf("نرم‌کننده فقط روی ساقه", "باز کردن گره‌ها با شانهٔ دندانه‌گشاد از پایین به بالا"),
            doFa = listOf("شستشوی ملایم و کوتاه", "حرارت کم هنگام خشک کردن"),
            avoidFa = listOf("رنگ/دکلره", "اتو و حرارت بالا", "بستن خیلی محکم مو", "شامپوی خیلی قوی هر روز"),
            whyFa = "حساسیت کف سر در این روزها ممکن است بیشتر باشد؛ کم کردن مواد شیمیایی و حرارت فشار روی مو و پوست سر را کم می‌کند."
        ),
        homeSkinActionFa = "پوست: روتین را ساده کنید و اسکراب/اکتیو قوی را کنار بگذارید",
        homeHairActionFa = "مو: شامپوی ملایم با آب ولرم؛ رنگ و حرارت بالا نه",
        homeWhyFa = "پوست در قاعدگی اغلب خشک‌تر و حساس‌تر است."
    )

    private val follicular = BeautyRoutine(
        key = "follicular",
        phaseTitleFa = "فاز فولیکولی",
        skin = BeautySection(
            summaryFa = "با بالا رفتن تدریجی استروژن، پوست اغلب مقاوم‌تر و یکدست‌تر است؛ زمان مناسب‌تری برای اکتیوهایی است که از قبل تحمل می‌کنید — نه برای امتحان ناگهانی چند محصول با هم.",
            morningFa = listOf(
                "شوینده ملایم",
                "در صورت تحمل، آنتی‌اکسیدان مثل ویتامین C که قبلاً استفاده می‌کردید",
                "مرطوب‌کننده",
                "ضدآفتاب"
            ),
            nightFa = listOf(
                "پاکسازی ملایم + مرطوب‌کننده (پایه)",
                "در صورت تحمل: لایه‌بردار ملایم یا رتینوئید سبک چند شب در هفته (اگر تازه‌کارید نه هر شب)"
            ),
            doFa = listOf("ثبات روتین", "معرفی تدریجی فقط یک اکتیو جدید در هر بار"),
            avoidFa = listOf("ترکیب هم‌زمان چند لایه‌بردار قوی"),
            whyFa = "افزایش تدریجی استروژن اغلب با پوست مقاوم‌تر همراه است؛ برای همین اکتیوهای آشنا را بهتر تحمل می‌کنید، ولی افزودن چند محصول با هم هنوز ممکن است تحریک ایجاد کند."
        ),
        hair = BeautySection(
            summaryFa = "اغلب بازهٔ بهتر استحکام و درخشش مو است.",
            morningFa = listOf("روتین عادی شستشو"),
            nightFa = listOf("در صورت خشکی، ماسک مغذی فقط روی ساقه"),
            doFa = listOf(
                "اگر قرار است رنگ کنید، نسبت به روزهای خونریزی معمولاً تحمل بهتری دارید",
                "ادامهٔ روتینی که جواب داده"
            ),
            avoidFa = listOf("آسیب حرارتی بی‌دلیل؛ حتی در این بازه هم حرارت را محدود کنید"),
            whyFa = "در این بازه مو و کف سر اغلب وضعیت پایدارتری دارند؛ اگر کار شیمیایی لازم است، این زمان معمولاً مناسب‌تر از قاعدگی است."
        ),
        homeSkinActionFa = "پوست: اگر اکتیو آشنایی دارید، حالا زمان مناسب‌تری است؛ فقط یکی",
        homeHairActionFa = "مو: در صورت خشکی ماسک مغذی روی ساقه",
        homeWhyFa = "با بالا رفتن استروژن، پوست اغلب مقاوم‌تر است."
    )

    private val ovulatory = BeautyRoutine(
        key = "ovulatory",
        phaseTitleFa = "حوالی تخمک‌گذاری",
        skin = BeautySection(
            summaryFa = "خیلی‌ها پوست را شفاف‌تر می‌بینند؛ به‌خاطر نوسان کوتاه هورمونی ممکن است کمی چربی یا جوش پراکنده هم بیاید.",
            morningFa = listOf("پاکسازی ملایم", "مرطوب‌کننده سبک و غیرکومدوژن", "ضدآفتاب با بافت سبک"),
            nightFa = listOf("پاکسازی ملایم", "مرطوب‌کننده سبک و غیرکومدوژن"),
            doFa = listOf("ادامهٔ روتینی که جواب داده"),
            avoidFa = listOf("کرم‌های خیلی سنگین روی صورت مستعد جوش"),
            whyFa = "اوج کوتاه استروژن اغلب پوست را درخشان‌تر نشان می‌دهد، اما نوسان هورمونی همین روزها ممکن است چربی را کمی بالا ببرد؛ بافت سبک تعادل را حفظ می‌کند."
        ),
        hair = BeautySection(
            summaryFa = "معمولاً ظاهر مو خوب است.",
            morningFa = listOf("شستشوی معمول با شامپوی متناسب با کف سر"),
            nightFa = listOf("محصولات سبک روی ریشه"),
            doFa = listOf("اگر کف سر چرب شد، فاصلهٔ شستشو را با نیاز تنظیم کنید نه با اجبار تقویم"),
            avoidFa = listOf("محصول خیلی سنگین روی ریشه"),
            whyFa = "در این بازه مو اغلب خوش‌حالت است؛ اگر چربی کف سر کمی بالا رفت، تنظیم شستشو کافی است."
        ),
        homeSkinActionFa = "پوست: بافت سبک و غیرکومدوژن؛ کرم سنگین نه",
        homeHairActionFa = "مو: شستشو را با نیاز کف سر تنظیم کنید",
        homeWhyFa = "پوست اغلب شفاف‌تر است ولی ممکن است کمی چرب شود."
    )

    private val luteal = BeautyRoutine(
        key = "luteal",
        phaseTitleFa = "فاز لوتئال",
        skin = BeautySection(
            summaryFa = "با پروژسترون و نوسان آندروژن، افزایش چربی، التهاب و جوش ناحیهٔ فک/چانه پیش از پریود شایع است.",
            morningFa = listOf(
                "شوینده ملایم",
                "نیاسینامید در صورت تحمل",
                "مرطوب‌کننده سبک غیرجوش‌زا",
                "ضدآفتاب"
            ),
            nightFa = listOf(
                "پاکسازی",
                "درمان نقطه‌ای فقط روی جوش (مثلاً اسید سالیسیلیک ملایم اگر تحمل دارید)",
                "مرطوب‌کننده برای حفظ سد پوست — پوست چرب هم به رطوبت نیاز دارد"
            ),
            doFa = listOf("به جوش دست نزنید", "روبالشی تمیز", "آرایش را شب کامل پاک کنید"),
            avoidFa = listOf("اسکراب خشن", "خشک‌کردن افراطی پوست با الکل", "قطع ناگهانی کل روتین"),
            whyFa = "پروژسترون و نوسان آندروژن ممکن است ترشح چربی را بیشتر کند؛ کنترل چربی بدون آسیب به سد پوست، جوش پیش از پریود را بهتر مهار می‌کند."
        ),
        hair = BeautySection(
            summaryFa = "کف سر ممکن است زودتر چرب شود.",
            morningFa = listOf("شستشو مطابق نیاز", "شامپوی خشک گاه‌به‌گاه در روزهای بین شستشو"),
            nightFa = listOf("ماساژ ملایم کف سر (اختیاری)"),
            doFa = listOf("شستشو را با میزان چربی تنظیم کنید"),
            avoidFa = listOf("شامپوی خیلی قوی روزانه که پوست سر را خشک کند"),
            whyFa = "افزایش چربی پوست در این فاز ممکن است کف سر را هم چرب‌تر کند؛ شستشوی متناسب بهتر از شوینده‌های قوی است."
        ),
        homeSkinActionFa = "پوست: درمان نقطه‌ای روی جوش؛ اسکراب و الکل نه",
        homeHairActionFa = "مو: شستشو مطابق نیاز؛ شامپوی خشک گاه‌به‌گاه",
        homeWhyFa = "پیش از پریود چربی و جوش فک/چانه شایع است."
    )

    private val general = BeautyRoutine(
        key = "general",
        phaseTitleFa = "روتین پایه",
        skin = BeautySection(
            summaryFa = "فاز چرخه‌تان الان با اطمینان مشخص نیست (مثلاً به‌خاطر داده کم، چرخه نامنظم یا روش هورمونی)، پس فقط روتین پایه را پیشنهاد می‌کنیم.",
            morningFa = listOf("شوینده ملایم", "مرطوب‌کننده", "ضدآفتاب روزانه"),
            nightFa = listOf("پاکسازی ملایم", "مرطوب‌کننده"),
            doFa = listOf("ثبات روتین", "معرفی تدریجی فقط یک اکتیو جدید", "ثبت علائم پوست برای دیدن الگوی خودتان"),
            avoidFa = listOf("ترکیب هم‌زمان چند لایه‌بردار قوی", "اسکراب زبر", "محصول جدید تست‌نشده"),
            whyFa = BASE_FA
        ),
        hair = BeautySection(
            summaryFa = "روتین مو را با نیاز واقعی کف سر تنظیم کنید.",
            morningFa = listOf("شامپوی ملایم", "آب ولرم نه داغ"),
            nightFa = listOf("نرم‌کننده فقط روی ساقه"),
            doFa = listOf("فاصلهٔ شستشو را با میزان چربی کف سر تنظیم کنید"),
            avoidFa = listOf("حرارت بالا و مکرر", "بستن خیلی محکم مو", "شامپوی خیلی قوی هر روز"),
            whyFa = "تا الگوی چرخه مشخص شود، پایهٔ ملایم و ثابت کم‌ریسک‌ترین انتخاب است."
        ),
        homeSkinActionFa = "پوست: شوینده ملایم + مرطوب‌کننده + ضدآفتاب",
        homeHairActionFa = "مو: شامپوی ملایم و نرم‌کننده فقط روی ساقه",
        homeWhyFa = "فاز مشخص نیست؛ روتین پایه."
    )

    private val pregnancy = BeautyRoutine(
        key = "pregnancy",
        phaseTitleFa = "دوران بارداری",
        skin = BeautySection(
            summaryFa = "فقط مراقبت خیلی ملایم؛ اکتیوهای قوی (رتینوئید و بسیاری لایه‌بردارها) را بدون تأیید پزشک استفاده نکنید؛ تمرکز روی پاکسازی ملایم، مرطوب‌کننده، ضدآفتاب.",
            morningFa = listOf("شوینده ملایم", "مرطوب‌کننده", "ضدآفتاب روزانه"),
            nightFa = listOf("پاکسازی ملایم", "مرطوب‌کننده"),
            doFa = listOf("روتین را ساده نگه دارید", "قبل از هر اکتیو یا محصول جدید با پزشک چک کنید"),
            avoidFa = listOf("رتینوئیدها بدون تأیید پزشک", "بسیاری از لایه‌بردارها بدون تأیید پزشک", "محصول جدید تست‌نشده"),
            whyFa = "در بارداری، احتیاط دربارهٔ مواد فعال پوستی بیشتر است؛ پایهٔ ملایم کم‌ریسک‌ترین انتخاب است و بقیه را پزشک تعیین کند."
        ),
        hair = BeautySection(
            summaryFa = "مراقبت ملایم از مو و کف سر؛ کار شیمیایی را با پزشک هماهنگ کنید.",
            morningFa = listOf("شامپوی ملایم", "آب ولرم نه داغ"),
            nightFa = listOf("نرم‌کننده فقط روی ساقه"),
            doFa = listOf("قبل از رنگ یا دکلره با پزشک مشورت کنید"),
            avoidFa = listOf("رنگ/دکلره بدون مشورت پزشک", "حرارت بالا و مکرر", "بستن خیلی محکم مو"),
            whyFa = "در بارداری احتیاط بیشتر منطقی است؛ مراقبت پایه و ملایم را ادامه دهید."
        ),
        homeSkinActionFa = "پوست: فقط پاکسازی ملایم، مرطوب‌کننده و ضدآفتاب",
        homeHairActionFa = "مو: شامپوی ملایم؛ رنگ/دکلره با مشورت پزشک",
        homeWhyFa = "در بارداری اکتیوهای قوی بدون تأیید پزشک نه."
    )

    private val bank: Map<String, BeautyRoutine> = mapOf(
        "menstrual" to menstrual,
        "follicular" to follicular,
        "ovulatory" to ovulatory,
        "luteal" to luteal,
        "general" to general
    )

    val allKeys: List<String> get() = bank.keys.toList() + "pregnancy"
}
