package com.mahava.app.content

import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow

/**
 * Educational food-craving meanings by cycle phase.
 * Plain Persian, non-diagnostic ("ممکنه" / "گاهی").
 */
object FoodCravingKeys {
    val ALL: List<Pair<String, String>> = listOf(
        "chocolate" to "شکلات",
        "sweet" to "شیرینی",
        "salty" to "شور",
        "carbs" to "نان و کربوهیدرات",
        "spicy" to "تند",
        "dairy" to "لبنیات",
        "red_meat" to "گوشت قرمز",
        "caffeine" to "قهوه و کافئین",
        "other" to "چیز دیگر"
    )

    fun labelFa(key: String): String = ALL.find { it.first == key }?.second ?: key
}

data class CravingMeaning(
    val cravingKey: String,
    val phaseGroup: String,
    val titleFa: String,
    val meaningFa: String,
    val tipFa: String
)

object CravingContent {
    private const val DISCLAIMER =
        "این‌ها توضیح آموزشی‌اند، نه تشخیص بیماری. هوس خوراکی برای هر کسی فرق می‌کند و ممکن است به عادت، استرس یا کمبود خواب هم ربط داشته باشد."

    fun disclaimerFa(): String = DISCLAIMER

    /** Map sub-window / phase to a coarse content group. */
    fun phaseGroupOf(sub: CycleSubWindow?, phase: CyclePhase?): String = when (sub) {
        CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE -> "menstrual"
        CycleSubWindow.FOLLICULAR_EARLY, CycleSubWindow.FOLLICULAR_LATE -> "follicular"
        CycleSubWindow.PERI_OVULATORY -> "fertile"
        CycleSubWindow.LUTEAL_EARLY -> "early_luteal"
        CycleSubWindow.LUTEAL_MID, CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> "late_luteal"
        CycleSubWindow.LATE_PERIOD -> "late_luteal"
        else -> when (phase) {
            CyclePhase.MENSTRUATION -> "menstrual"
            CyclePhase.FOLLICULAR -> "follicular"
            CyclePhase.OVULATION_WINDOW -> "fertile"
            CyclePhase.LUTEAL -> "late_luteal"
            else -> "general"
        }
    }

    fun meaning(cravingKey: String, phaseGroup: String): CravingMeaning? {
        val list = bank.filter { it.cravingKey == cravingKey }
        return list.find { it.phaseGroup == phaseGroup } ?: list.find { it.phaseGroup == "general" }
    }

    fun meaningsForPhase(phaseGroup: String): List<CravingMeaning> {
        val keys = FoodCravingKeys.ALL.map { it.first }
        return keys.mapNotNull { meaning(it, phaseGroup) }
    }

    fun shortTipFa(cravingKey: String, phaseGroup: String): String? {
        val m = meaning(cravingKey, phaseGroup) ?: return null
        return "اگه امروز «${FoodCravingKeys.labelFa(cravingKey)}» هوس کردی: ${m.meaningFa}"
    }

    private val bank: List<CravingMeaning> = listOf(
        // —— menstrual ——
        CravingMeaning("chocolate", "menstrual", "شکلات در روزهای پریود",
            "گاهی به کاهش منیزیم (ماده‌ای که به آرامش عضله و حال روحی کمک می‌کند) یا افت انرژی مربوط می‌شود؛ ممکنه بدن دنبال یک منبع سریع انرژی و حس خوب باشه.",
            "اگر دوست داری، کمی شکلات تلخ، موز، یا آجیل امتحان کن. آب و استراحت هم کمک می‌کند."),
        CravingMeaning("sweet", "menstrual", "شیرینی در روزهای پریود",
            "ممکنه به افت قند خون یا خستگی مربوط باشد؛ بدن گاهی سراغ انرژی سریع می‌رود.",
            "اگر هوس کردی، میوه یا یک میان‌وعدهٔ ترکیبی (مثل نان و پنیر) معمولاً پایدارتر از شیرینی خالی است."),
        CravingMeaning("salty", "menstrual", "شور در روزهای پریود",
            "گاهی با تغییر تعادل آب و نمک بدن یا خستگی همراه است.",
            "کم‌کم نمک را کم کن و آب بنوش؛ سوپ سبک یا غذاهای خانگی بهتر از اسنک خیلی شور است."),
        CravingMeaning("carbs", "menstrual", "نان و کربوهیدرات در پریود",
            "ممکنه بدن برای جبران انرژی از دست‌رفته سراغ کربوهیدرات برود.",
            "نان سبوس‌دار، برنج، یا سیب‌زمینی با کمی پروتئین معمولاً سیرکننده‌تر است."),
        CravingMeaning("spicy", "menstrual", "تند در روزهای پریود",
            "گاهی فقط عادت یا حس گرما و تحریک اشتهاست؛ الزاماً معنی خاصی ندارد.",
            "اگر معده‌ات حساس است، تندی را کم کن تا نفخ یا درد بیشتر نشود."),
        CravingMeaning("dairy", "menstrual", "لبنیات در پریود",
            "ممکنه به نیاز کلسیم یا فقط عادت غذایی مربوط باشد.",
            "اگر با لبنیات حال معده‌ات بد می‌شود، جایگزین‌های گیاهی غنی‌شده را ببین."),
        CravingMeaning("red_meat", "menstrual", "گوشت قرمز در پریود",
            "گاهی به نیاز آهن (مادهٔ مهم خون) مربوط می‌شود، به‌خصوص اگر خون‌ریزی زیاد باشد.",
            "گوشت کم‌چرب، عدس، یا سبزیجات برگ‌دار تیره گزینه‌های خوب‌اند. اگر خستگی شدید داری، با پزشک حرف بزن."),
        CravingMeaning("caffeine", "menstrual", "قهوه در پریود",
            "ممکنه برای مبارزه با خستگی باشد؛ کافئین زیاد گاهی گرفتگی را بیشتر می‌کند.",
            "اگر درد داری، کمی کمتر قهوه بنوش و آب را جایگزین کن."),
        CravingMeaning("other", "menstrual", "هوس دیگر در پریود",
            "هوس‌ها خیلی فردی‌اند؛ ممکنه به خواب، استرس یا عادت مربوط باشند.",
            "همان چیزی را که دوست داری در حد متعادل بخور و به بدنت گوش بده."),

        // —— follicular ——
        CravingMeaning("chocolate", "follicular", "شکلات بعد از پریود",
            "در این دوره انرژی معمولاً بهتر می‌شود؛ هوس شکلات ممکنه بیشتر عادت یا حال روحی باشد تا کمبود.",
            "اگر هوس کردی، مقدار کم اشکالی ندارد؛ میوه و آجیل هم جایگزین خوبی‌اند."),
        CravingMeaning("sweet", "follicular", "شیرینی بعد از پریود",
            "گاهی با بازگشت اشتها و انرژی همراه است؛ الزاماً نشانهٔ مشکل نیست.",
            "میان‌وعدهٔ متعادل انتخاب کن تا انرژی‌ات پایدار بماند."),
        CravingMeaning("salty", "follicular", "شور بعد از پریود",
            "ممکنه به عادت یا تعریق و فعالیت بیشتر مربوط باشد.",
            "آب کافی بنوش؛ اگر فشار خونت بالاست، شور را محدود کن."),
        CravingMeaning("carbs", "follicular", "کربوهیدرات بعد از پریود",
            "بدن در حال ساخت انرژی برای نیمهٔ اول چرخه است؛ هوس نان گاهی طبیعی است.",
            "کربوهیدرات کامل (سبوس‌دار) با پروتئین ترکیب کن."),
        CravingMeaning("spicy", "follicular", "تند بعد از پریود",
            "اغلب فقط سلیقه است؛ معنی پزشکی خاصی ندارد.",
            "هر طور که راحت هستی بخور."),
        CravingMeaning("dairy", "follicular", "لبنیات بعد از پریود",
            "ممکنه به عادت یا نیاز کلسیم مربوط باشد.",
            "اگر تحمل می‌کنی، ماست و پنیر گزینه‌های خوبی‌اند."),
        CravingMeaning("red_meat", "follicular", "گوشت بعد از پریود",
            "گاهی بدن در حال جبران آهن از دست‌رفته در پریود است.",
            "منبع آهن را با ویتامین C (مثل لیمو یا فلفل) همراه کن تا جذب بهتر شود."),
        CravingMeaning("caffeine", "follicular", "قهوه بعد از پریود",
            "انرژی معمولاً بالاتر است؛ هوس قهوه بیشتر عادت روزانه است.",
            "زیاده‌روی نکن تا خواب شب خراب نشود."),
        CravingMeaning("other", "follicular", "هوس دیگر بعد از پریود",
            "در این دوره خیلی‌ها اشتهای متعادل‌تری دارند؛ هوس خاص الزاماً نشانه نیست.",
            "به تنوع غذایی فکر کن و به حس بدنت توجه کن."),

        // —— fertile / ovulatory ——
        CravingMeaning("chocolate", "fertile", "شکلات حوالی تخمک‌گذاری",
            "گاهی با نوسان حال روحی یا عادت همراه است؛ معنی بیماری ندارد.",
            "مقدار کم مشکلی نیست؛ اگر اضطراب داری، کمی پیاده‌روی هم کمک می‌کند."),
        CravingMeaning("sweet", "fertile", "شیرینی حوالی تخمک‌گذاری",
            "ممکنه به تغییر انرژی یا هورمون‌ها مربوط باشد؛ قطعی نیست.",
            "میوهٔ تازه جایگزین ملایم‌تری است."),
        CravingMeaning("salty", "fertile", "شور حوالی تخمک‌گذاری",
            "گاهی با نفخ خفیف یا عادت همراه است.",
            "آب بنوش و غذاهای خیلی فرآوری‌شده را کم کن."),
        CravingMeaning("carbs", "fertile", "کربوهیدرات حوالی تخمک‌گذاری",
            "بدن ممکن است برای فعالیت بیشتر انرژی بخواهد.",
            "وعده‌های متعادل با پروتئین و سبزیجات انتخاب کن."),
        CravingMeaning("spicy", "fertile", "تند حوالی تخمک‌گذاری",
            "اغلب سلیقه است.",
            "اگر ترشحاتت تغییر کرده و نگران هستی، با پزشک مشورت کن — نه به‌خاطر تندی غذا."),
        CravingMeaning("dairy", "fertile", "لبنیات حوالی تخمک‌گذاری",
            "معمولاً عادت غذایی است.",
            "اگر جوش می‌زنی و شک داری، چند روز لبنیات را کم کن و ببین فرقی می‌کند یا نه."),
        CravingMeaning("red_meat", "fertile", "گوشت حوالی تخمک‌گذاری",
            "نیاز پروتئین و آهن گاهی بیشتر حس می‌شود.",
            "پروتئین متنوع (حبوبات، تخم‌مرغ، ماهی) هم خوب است."),
        CravingMeaning("caffeine", "fertile", "قهوه حوالی تخمک‌گذاری",
            "اگر برای بارداری اقدام می‌کنی، بعضی راهنماها کافئین را محدود پیشنهاد می‌کنند.",
            "زیاده‌روی نکن؛ آب و خواب را جدی بگیر."),
        CravingMeaning("other", "fertile", "هوس دیگر حوالی تخمک‌گذاری",
            "هوس‌ها فردی‌اند و به تنهایی تشخیص نیستند.",
            "به بدنت گوش بده و در حد تعادل پیش برو."),

        // —— early luteal ——
        CravingMeaning("chocolate", "early_luteal", "شکلات اوایل نیمهٔ دوم",
            "با شروع تغییر هورمون‌ها، گاهی هوس شکلات بیشتر می‌شود؛ ممکنه به حال روحی ربط داشته باشد.",
            "شکلات تلخ کم‌شکر یا میوهٔ شیرین گزینه‌های ملایم‌تری‌اند."),
        CravingMeaning("sweet", "early_luteal", "شیرینی اوایل نیمهٔ دوم",
            "گاهی بدن به دنبال سروتونین (مادهٔ مرتبط با حس خوب) از راه قند می‌گردد.",
            "خواب منظم و یک پیاده‌روی کوتاه گاهی هوس را کمتر می‌کند."),
        CravingMeaning("salty", "early_luteal", "شور اوایل نیمهٔ دوم",
            "ممکنه با نگه داشتن آب در بدن یا عادت همراه باشد.",
            "نمک اضافه را کم کن؛ خیار و هندوانه هم برای حس تازگی خوب‌اند."),
        CravingMeaning("carbs", "early_luteal", "کربوهیدرات اوایل نیمهٔ دوم",
            "هورمون پروژسترون (یکی از هورمون‌های چرخه) گاهی اشتها به کربوهیدرات را بیشتر می‌کند.",
            "نان سبوس‌دار و وعدهٔ منظم بهتر از ریزه‌خواری شیرین است."),
        CravingMeaning("spicy", "early_luteal", "تند اوایل نیمهٔ دوم",
            "سلیقه است؛ اگر معده‌ات حساس شده، تندی را کم کن.",
            "غذای ملایم و گرم گاهی راحت‌تر هضم می‌شود."),
        CravingMeaning("dairy", "early_luteal", "لبنیات اوایل نیمهٔ دوم",
            "ممکنه عادت یا نیاز کلسیم باشد.",
            "اگر نفخ داری، ببین کم کردن لبنیات فرقی می‌کند یا نه."),
        CravingMeaning("red_meat", "early_luteal", "گوشت اوایل نیمهٔ دوم",
            "اشتها به پروتئین گاهی طبیعی است.",
            "پروتئین کافی به پایداری انرژی کمک می‌کند."),
        CravingMeaning("caffeine", "early_luteal", "قهوه اوایل نیمهٔ دوم",
            "اگر خوابت خراب شده، هوس قهوه بیشتر می‌شود و ممکنه چرخه را بدتر کند.",
            "بعد از ظهر کافئین را کم کن."),
        CravingMeaning("other", "early_luteal", "هوس دیگر اوایل نیمهٔ دوم",
            "نوسان اشتها در این دوره رایج است و به تنهایی بیماری نیست.",
            "وعده‌های منظم و آب کافی را فراموش نکن."),

        // —— late luteal / PMS ——
        CravingMeaning("chocolate", "late_luteal", "شکلات پیش از پریود",
            "خیلی‌ها پیش از پریود شکلات هوس می‌کنند؛ گاهی به منیزیم، حال روحی یا عادت مربوط است.",
            "مقدار کم اشکالی ندارد. موز، بادام و شکلات تلخ جایگزین‌های ملایم‌تری‌اند."),
        CravingMeaning("sweet", "late_luteal", "شیرینی پیش از پریود",
            "ممکنه بدن برای بالا بردن انرژی و حس خوب سراغ قند برود؛ رایج است و الزاماً نشانهٔ بیماری نیست.",
            "میوه، خرما، یا ماست با کمی عسل معمولاً بهتر از شیرینی صنعتی زیاد است."),
        CravingMeaning("salty", "late_luteal", "شور پیش از پریود",
            "گاهی با نفخ و نگه داشتن آب همراه است.",
            "نمک را کم کن و آب بنوش؛ پاها را کمی بالاتر بگذار اگر ورم حس می‌کنی."),
        CravingMeaning("carbs", "late_luteal", "کربوهیدرات پیش از پریود",
            "پیش از پریود هوس نان و برنج خیلی شایع است؛ ممکنه به تغییر هورمون و سروتونین مربوط باشد.",
            "کربوهیدرات کامل را با پروتئین بخور تا نوسان قند کمتر شود."),
        CravingMeaning("spicy", "late_luteal", "تند پیش از پریود",
            "گاهی فقط سلیقه است؛ اگر رفلاکس یا درد داری، تندی کمک نمی‌کند.",
            "غذای ملایم‌تر انتخاب کن."),
        CravingMeaning("dairy", "late_luteal", "لبنیات پیش از پریود",
            "ممکنه عادت باشد؛ در بعضی‌ها نفخ را بیشتر می‌کند.",
            "اگر نفخ داری، یک هفته لبنیات را کم کن و مقایسه کن."),
        CravingMeaning("red_meat", "late_luteal", "گوشت پیش از پریود",
            "بدن ممکن است برای پریود بعدی آهن ذخیره کند؛ قطعی نیست.",
            "منبع آهن گیاهی یا حیوانی را در برنامه بگذار."),
        CravingMeaning("caffeine", "late_luteal", "قهوه پیش از پریود",
            "خستگی و اضطراب پیش از پریود گاهی هوس قهوه را زیاد می‌کند؛ کافئین زیاد ممکن است اضطراب و تپش را بیشتر کند.",
            "نصف فنجان یا چای کم‌کافئین را امتحان کن."),
        CravingMeaning("other", "late_luteal", "هوس دیگر پیش از پریود",
            "نوسان اشتها پیش از پریود خیلی شایع است و به تنهایی تشخیص نیست.",
            "با خودت مهربان باش؛ وعدهٔ منظم بهتر از حذف کامل است."),

        // —— general fallback ——
        CravingMeaning("chocolate", "general", "شکلات",
            "گاهی به حال روحی، منیزیم یا عادت مربوط می‌شود.",
            "مقدار کم مشکلی نیست؛ به تنوع غذایی هم فکر کن."),
        CravingMeaning("sweet", "general", "شیرینی",
            "ممکنه به افت انرژی یا استرس مربوط باشد.",
            "میوه و میان‌وعدهٔ ترکیبی را امتحان کن."),
        CravingMeaning("salty", "general", "شور",
            "گاهی با تعادل آب و نمک یا عادت همراه است.",
            "آب بنوش و نمک اضافه را کم کن."),
        CravingMeaning("carbs", "general", "کربوهیدرات",
            "بدن برای انرژی به کربوهیدرات نیاز دارد؛ هوس آن لزوماً بد نیست.",
            "نوع سبوس‌دار را ترجیح بده."),
        CravingMeaning("spicy", "general", "تند", "اغلب سلیقه است.", "اگر معده‌ات حساس است، کم کن."),
        CravingMeaning("dairy", "general", "لبنیات", "ممکنه عادت یا نیاز کلسیم باشد.", "اگر نفخ می‌آورد، جایگزین ببین."),
        CravingMeaning("red_meat", "general", "گوشت قرمز", "گاهی به نیاز آهن یا پروتئین مربوط است.", "منابع متنوع پروتئین را امتحان کن."),
        CravingMeaning("caffeine", "general", "کافئین", "معمولاً برای رفع خستگی است.", "زیاده‌روی خواب را خراب می‌کند."),
        CravingMeaning("other", "general", "هوس دیگر", "هوس‌ها خیلی فردی‌اند.", "به بدنت گوش بده و در تعادل بمان.")
    )
}
