package com.mahava.app.content

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** The user's own current skin & hair situation (intake). Stored locally only. */
data class BeautyProfile(
    val skinType: String = "",            // dry, oily, combo, sensitive, unknown
    val skinIssues: Set<String> = emptySet(), // dryness, oily, acne, redness, spots, dull, flaky, none
    val acneZones: Set<String> = emptySet(),  // forehead, cheeks, jaw, nose
    val acneSeverity: String = "",        // mild, moderate, severe
    val routine: String = "",             // cleanser, cleanser_moist, full_spf, irregular
    val spf: String = "",                 // yes, no, sometimes
    val actives: Set<String> = emptySet(),// retinoid, acid, vitc, niacinamide, none
    val stinging: String = "",            // yes, no
    val scalp: String = "",               // dry, oily, balanced, itchy, unknown
    val lengths: String = "",             // dry_brittle, normal, oily_roots, damaged
    val hairConcerns: Set<String> = emptySet(), // shedding, breakage, oily_fast, dandruff, thinning, none
    val washesPerWeek: Int = 0,           // 1..7
    val chemical: String = "",            // recent, old, no
    val issueDuration: String = "",       // days, weeks, months, long
    val cyclical: String = "",            // yes, no, unsure
    val conditions: Set<String> = emptySet(), // eczema, rosacea, pcos, thyroid, anemia, none (self-reported only)
    val meds: Set<String> = emptySet(),   // isotretinoin, hormonal, steroid, other, none, skip
    val sleepStress: String = "",         // good, poor_sleep, high_stress, both
    val sheddingTiming: String = "",      // recent_months, after_event, long, none
    val updatedAt: Long = 0L
) {
    val hasAcne get() = "acne" in skinIssues
    val skinComplete get() = skinType.isNotEmpty() && skinIssues.isNotEmpty() && routine.isNotEmpty() &&
        spf.isNotEmpty() && actives.isNotEmpty() && stinging.isNotEmpty() &&
        (!hasAcne || (acneSeverity.isNotEmpty() && acneZones.isNotEmpty()))
    val hairComplete get() = scalp.isNotEmpty() && lengths.isNotEmpty() && hairConcerns.isNotEmpty() &&
        washesPerWeek in 1..7 && chemical.isNotEmpty()
    val historyComplete get() = issueDuration.isNotEmpty() && cyclical.isNotEmpty() && conditions.isNotEmpty() &&
        meds.isNotEmpty() && sleepStress.isNotEmpty() && sheddingTiming.isNotEmpty()
    val complete get() = skinComplete && hairComplete && historyComplete

    fun toJson(): String = JSONObject().apply {
        put("skinType", skinType); put("skinIssues", JSONArray(skinIssues.toList())); put("acneZones", JSONArray(acneZones.toList()))
        put("acneSeverity", acneSeverity); put("routine", routine); put("spf", spf); put("actives", JSONArray(actives.toList()))
        put("stinging", stinging); put("scalp", scalp); put("lengths", lengths); put("hairConcerns", JSONArray(hairConcerns.toList()))
        put("washesPerWeek", washesPerWeek); put("chemical", chemical); put("updatedAt", updatedAt)
        put("issueDuration", issueDuration); put("cyclical", cyclical); put("conditions", JSONArray(conditions.toList()))
        put("meds", JSONArray(meds.toList())); put("sleepStress", sleepStress); put("sheddingTiming", sheddingTiming)
    }.toString()

    companion object {
        private fun JSONObject.set(k: String): Set<String> =
            optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        fun fromJson(s: String): BeautyProfile = runCatching {
            val o = JSONObject(s)
            BeautyProfile(o.optString("skinType"), o.set("skinIssues"), o.set("acneZones"), o.optString("acneSeverity"),
                o.optString("routine"), o.optString("spf"), o.set("actives"), o.optString("stinging"), o.optString("scalp"),
                o.optString("lengths"), o.set("hairConcerns"), o.optInt("washesPerWeek"), o.optString("chemical"), o.optString("issueDuration"), o.optString("cyclical"),
                o.set("conditions"), o.set("meds"), o.optString("sleepStress"), o.optString("sheddingTiming"), o.optLong("updatedAt"))
        }.getOrDefault(BeautyProfile())
    }
}

object BeautyProfileStore {
    private const val PREFS = "mah_beauty_profile"
    private val _flow = MutableStateFlow<BeautyProfile?>(null)
    val flow: StateFlow<BeautyProfile?> = _flow
    fun load(ctx: Context): BeautyProfile {
        _flow.value?.let { return it }
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("profile", null)
            ?.let { BeautyProfile.fromJson(it) } ?: BeautyProfile()
        _flow.value = p; return p
    }
    fun save(ctx: Context, p: BeautyProfile) {
        val s = p.copy(updatedAt = System.currentTimeMillis())
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("profile", s.toJson()).apply()
        _flow.value = s
    }
}

/** Persian labels for intake options (key to label). */
object BeautyLabels {
    val skinType = listOf("dry" to "خشک", "oily" to "چرب", "combo" to "مختلط", "sensitive" to "حساس", "unknown" to "نمی‌دانم")
    val skinIssues = listOf("dryness" to "خشکی", "oily" to "چربی زیاد", "acne" to "جوش", "redness" to "قرمزی / التهاب",
        "spots" to "لک", "dull" to "کدری", "flaky" to "پوسته‌پوسته", "none" to "بدون مشکل خاص")
    val acneZones = listOf("forehead" to "پیشانی", "cheeks" to "گونه", "jaw" to "چانه / فک", "nose" to "بینی")
    val acneSeverity = listOf("mild" to "خفیف", "moderate" to "متوسط", "severe" to "زیاد")
    val routine = listOf("cleanser" to "فقط شوینده", "cleanser_moist" to "شوینده + مرطوب‌کننده",
        "full_spf" to "کامل با ضدآفتاب", "irregular" to "نامنظم / تقریباً هیچ")
    val spf = listOf("yes" to "بله، هر روز", "sometimes" to "گاهی", "no" to "خیر")
    val actives = listOf("retinoid" to "رتینوئید", "acid" to "AHA / BHA", "vitc" to "ویتامین C",
        "niacinamide" to "نیاسینامید", "none" to "هیچ‌کدام / نمی‌دانم")
    val stinging = listOf("yes" to "بله، زیاد پیش می‌آید", "no" to "خیر")
    val scalp = listOf("dry" to "خشک", "oily" to "چرب", "balanced" to "متعادل", "itchy" to "حساس / خارش‌دار", "unknown" to "نمی‌دانم")
    val lengths = listOf("dry_brittle" to "خشک / شکننده", "normal" to "معمولی", "oily_roots" to "چرب نزدیک ریشه",
        "damaged" to "رنگ‌شده یا آسیب حرارت")
    val hairConcerns = listOf("shedding" to "ریزش بیشتر از قبل", "breakage" to "شکنندگی", "oily_fast" to "کف سر زود چرب می‌شود",
        "dandruff" to "شوره / خارش", "thinning" to "حس کم‌پشتی", "none" to "بدون نگرانی خاص")
    val chemical = listOf("recent" to "بله، اخیراً (۱–۲ ماه)", "old" to "قدیمی‌تر", "no" to "خیر")
    val issueDuration = listOf("days" to "چند روز", "weeks" to "چند هفته", "months" to "چند ماه", "long" to "بیش از یک سال / همیشه")
    val cyclical = listOf("yes" to "بله، قبل از پریود بدتر می‌شود", "no" to "خیر، ربطی ندارد", "unsure" to "مطمئن نیستم")
    val conditions = listOf("eczema" to "اگزما / درماتیت", "rosacea" to "روزاسه", "pcos" to "تخمدان پلی‌کیستیک",
        "thyroid" to "مشکل تیروئید", "anemia" to "کم‌خونی / کمبود آهن", "none" to "هیچ‌کدام / نمی‌دانم")
    val meds = listOf("isotretinoin" to "ایزوترتینوئین (قرص جوش)", "hormonal" to "داروی هورمونی / قرص ضدبارداری",
        "steroid" to "کرم یا قرص کورتونی", "other" to "داروی دیگر مرتبط با پوست", "none" to "هیچ‌کدام", "skip" to "ترجیح می‌دهم نگویم")
    val sleepStress = listOf("good" to "خواب و استرس معمولی", "poor_sleep" to "خواب کم یا بد", "high_stress" to "استرس زیاد", "both" to "هر دو")
    val sheddingTiming = listOf("recent_months" to "از ۱–۳ ماه اخیر", "after_event" to "بعد از بیماری، زایمان، رژیم یا استرس شدید",
        "long" to "مدت طولانی و تدریجی", "none" to "ریزش غیرعادی ندارم")
    fun of(list: List<Pair<String, String>>, k: String) = list.firstOrNull { it.first == k }?.second ?: ""
}

/** One personalized action: what to do, which of THEIR answers triggered it, and the plain-Persian science why. */
data class BeautyAction(val becauseFa: String, val doFa: String, val whyFa: String)

data class BeautyAdvice(
    val summaryFa: String,
    val priorities: List<BeautyAction>,
    val morning: List<String>,
    val night: List<String>,
    val avoid: List<String>,
    val phaseNoteFa: String,
    val learn: List<Pair<String, String>>, // short educational blocks (title, text) relevant to their answers
    val flags: List<String>,
    val disclaimerFa: String = BeautyRoutineContent.DISCLAIMER
)

/**
 * Local rule engine. Current complaints first; cycle phase only modulates.
 * Cycle-syncing evidence for skin care is limited: we say so and never present phase as certainty.
 */
object BeautyAdviceEngine {

    private const val CYCLE_EVIDENCE = "شواهد علمی برای «هماهنگ کردن کامل روتین با چرخه» محدود است؛ چیزی که بهتر ثابت شده این است که در برخی زنان چربی و جوش پیش از پریود بیشتر می‌شود. پس فاز را فقط برای تنظیم‌های کوچک در نظر می‌گیریم."

    private fun L(list: List<Pair<String, String>>, k: String) = BeautyLabels.of(list, k)
    private fun zones(p: BeautyProfile) = p.acneZones.joinToString("، ") { L(BeautyLabels.acneZones, it) }

    fun skin(p: BeautyProfile, phaseGroup: String, pregnant: Boolean): BeautyAdvice {
        val key = if (pregnant) "pregnancy" else BeautyRoutineContent.keyFor(phaseGroup)
        val irritated = p.stinging == "yes" || p.skinType == "sensitive" || "redness" in p.skinIssues ||
            "eczema" in p.conditions || "rosacea" in p.conditions
        val dry = p.skinType == "dry" || "dryness" in p.skinIssues || "flaky" in p.skinIssues
        val oily = p.skinType == "oily" || "oily" in p.skinIssues
        val acne = p.hasAcne
        val strongActive = p.actives.any { it == "retinoid" || it == "acid" }
        val onIso = "isotretinoin" in p.meds
        val hormonalPattern = acne && ("jaw" in p.acneZones || p.cyclical == "yes")
        val pri = mutableListOf<BeautyAction>(); val am = mutableListOf<String>(); val pm = mutableListOf<String>()
        val avoid = linkedSetOf<String>(); val flags = mutableListOf<String>(); val learn = mutableListOf<Pair<String, String>>()

        if (onIso) pri += BeautyAction(
            "چون گفتی ایزوترتینوئین مصرف می‌کنی",
            "روتین را فقط شوینده خیلی ملایم، مرطوب‌کنندهٔ غنی، ضدآفتاب و بالم لب نگه دار؛ هیچ اسید، اسکراب یا رتینوئید موضعی اضافه نکن.",
            "این دارو ترشح چربی را به‌شدت کم می‌کند و پوست و لب اغلب خیلی خشک و آسیب‌پذیر می‌شوند؛ هر اکتیو اضافه ممکن است سوزش و ترک ایجاد کند. تنظیم روتین را با پزشک معالج هماهنگ کن.")
        if (pregnant && "retinoid" in p.actives) pri += BeautyAction(
            "چون باردار هستی و گفتی رتینوئید می‌زنی",
            "رتینوئید را کنار بگذار تا با پزشک مشورت کنی.",
            "رتینوئیدهای خوراکی در بارداری ممنوع‌اند و برای نوع موضعی هم احتیاط توصیه می‌شود؛ تصمیم نهایی با پزشک است.")
        if (irritated && !onIso) pri += BeautyAction(
            buildString {
                append("چون گفتی ")
                append(listOfNotNull(
                    if (p.stinging == "yes") "با محصولات سوزش می‌گیری" else null,
                    if ("redness" in p.skinIssues) "قرمزی داری" else null,
                    if (p.skinType == "sensitive") "پوستت حساس است" else null,
                    if ("eczema" in p.conditions) "سابقهٔ اگزما داری" else null,
                    if ("rosacea" in p.conditions) "سابقهٔ روزاسه داری" else null).joinToString(" و "))
            },
            if (strongActive) "رتینوئید و اسیدها را ۱–۲ هفته متوقف کن و روتین را به شوینده ملایم، مرطوب‌کننده و ضدآفتاب کم کن."
            else "روتین را به سه قدم کم کن: شوینده ملایم، مرطوب‌کننده بدون عطر، ضدآفتاب.",
            "سوزش و قرمزی اغلب نشانهٔ آسیب «سد پوست» است — لایهٔ بیرونی که آب را نگه می‌دارد و محرک را بیرون. وقتی این لایه آسیب دیده، هر اکتیوی بیشتر نفوذ و تحریک می‌کند؛ اول ترمیم، بعد درمان.")
        if (p.spf != "yes") pri += BeautyAction(
            if (p.spf == "no") "چون گفتی ضدآفتاب نمی‌زنی" + (if ("spots" in p.skinIssues) " و لک داری" else if (acne) " و جوش داری" else "")
            else "چون گفتی فقط گاهی ضدآفتاب می‌زنی",
            "هر صبح ضدآفتاب طیف وسیع (حداقل SPF 30) به اندازهٔ کافی بزن، حتی روزهای ابری یا کنار پنجره؛ اگر بیرون می‌مانی هر ۲–۳ ساعت تجدید کن.",
            "اشعهٔ UV حتی از ابر و شیشه رد می‌شود؛ رنگدانه‌سازی را تحریک می‌کند و لک و جای قرمز/قهوه‌ای جوش را ماندگارتر می‌کند. بیشترین شواهد علمی در مراقبت پوست مربوط به همین قدم است" +
                (if (strongActive) "؛ با رتینوئید یا اسید، پوست به آفتاب حساس‌تر هم می‌شود." else "."))
        if (acne && !onIso) pri += when {
            p.acneSeverity == "severe" -> BeautyAction("چون گفتی جوش‌هایت زیاد است",
                "به‌جای امتحان چند محصول، برای ویزیت پزشک پوست وقت بگیر؛ تا آن موقع فقط روتین ملایم.",
                "جوش زیاد یا عمیق ممکن است جای ماندگار بگذارد و معمولاً به درمان تجویزی بهتر جواب می‌دهد؛ ترکیب خودسرانهٔ اکتیوها اغلب فقط التهاب را بیشتر می‌کند.")
            hormonalPattern -> BeautyAction(
                "چون گفتی جوش در ${zones(p).ifEmpty { "صورت" }}" + (if (p.cyclical == "yes") " داری و قبل از پریود بدتر می‌شود" else " داری"),
                "شب‌ها درمان نقطه‌ای ملایم (مثلاً اسید سالیسیلیک یا بنزوئیل پراکساید کم‌غلظت اگر تحمل داری) فقط روی خود جوش؛ از ۳–۵ روز قبل از پریود منظم‌ترش کن. دست نزن و فشار نده.",
                "جوش ناحیهٔ فک و چانه و جوشی که با چرخه تکرار می‌شود اغلب به نوسان هورمون‌ها (پروژسترون و آندروژن‌ها) و افزایش چربی ربط دارد. درمان نقطه‌ای التهاب را کم می‌کند بدون اینکه کل صورت خشک شود. فشار دادن، التهاب را عمیق‌تر و احتمال جای جوش را بیشتر می‌کند" +
                    (if ("pcos" in p.conditions) ". چون سابقهٔ تخمدان پلی‌کیستیک گفتی، کنترل جوش را با پزشکت هم هماهنگ کن." else "."))
            else -> BeautyAction("چون گفتی جوش ${L(BeautyLabels.acneSeverity, p.acneSeverity)} در ${zones(p)} داری",
                "درمان نقطه‌ای شبانه فقط روی جوش‌ها و مرطوب‌کنندهٔ سبک غیرکومدوژن؛ اسکراب نکن.",
                "جوش التهاب منفذ است؛ اسکراب زبر روی پوست ملتهب پارگی‌های ریز ایجاد می‌کند و التهاب را پخش می‌کند. ثبات چند هفته‌ای مهم‌تر از قدرت محصول است — اثر اغلب اکتیوهای جوش بعد از ۶–۸ هفته دیده می‌شود.")
        }
        if (dry && !onIso) pri += BeautyAction(
            "چون گفتی پوستت " + listOfNotNull(if (p.skinType == "dry" || "dryness" in p.skinIssues) "خشک" else null,
                if ("flaky" in p.skinIssues) "پوسته‌پوسته" else null).joinToString(" و ") + " است",
            "بعد از شستشو، روی پوست کمی نمناک مرطوب‌کنندهٔ دارای سرامید یا گلیسرین بزن؛ آب ولرم نه داغ.",
            "گلیسرین آب را جذب می‌کند و سرامیدها بخشی از ساختار طبیعی سد پوست‌اند. زدن روی پوست نمناک آب را «قفل» می‌کند؛ آب داغ چربی‌های محافظ را می‌شوید.")
        if (oily && !acne) pri += BeautyAction("چون گفتی پوستت چرب است",
            "صورت را حداکثر دو بار در روز با شوینده ملایم بشوی و مرطوب‌کنندهٔ ژلی سبک را حذف نکن.",
            "چربی پوست (سبوم) را عمدتاً هورمون‌ها و ژنتیک تعیین می‌کنند، نه کمبود شستشو. شستن زیاد و حذف مرطوب‌کننده ممکن است سد پوست را خشک و تحریک کند و پوست براق‌تر به نظر برسد.")
        if ("spots" in p.skinIssues) pri += BeautyAction("چون گفتی لک داری",
            if (pregnant) "فعلاً فقط ضدآفتاب منظم؛ هر روشن‌کننده را با پزشک چک کن."
            else "ضدآفتاب منظم را پایه کن و فقط یک روشن‌کننده (ویتامین C یا نیاسینامید) را تدریجی اضافه کن.",
            "لک از رنگدانه‌سازی اضافه است که آفتاب و التهاب آن را تحریک می‌کنند؛ بدون ضدآفتاب، روشن‌کننده‌ها اثر کمی دارند. در بارداری لک (ملاسما) شایع است و اغلب بعد از زایمان کمتر می‌شود.")
        if ("dull" in p.skinIssues && !irritated && !onIso && !pregnant) pri += BeautyAction("چون گفتی پوستت کدر است",
            "هفته‌ای ۱–۲ بار لایه‌بردار شیمیایی ملایم، نه اسکراب دانه‌دار.",
            "کدری اغلب از تجمع سلول‌های مرده و کم‌آبی است؛ لایه‌برداری ملایم آن را بهتر می‌کند ولی زیاده‌روی سد پوست را آسیب می‌زند و کدری را بدتر.")
        if (p.routine == "irregular" || p.routine == "cleanser") pri += BeautyAction(
            "چون گفتی روتینت " + L(BeautyLabels.routine, p.routine) + " است",
            "اول همین سه قدم را هر روز ثابت کن، بعد هر محصول اضافه را جداگانه و با فاصلهٔ ۲ هفته معرفی کن.",
            "پوست به تغییر مداوم محصول واکنش نشان می‌دهد؛ ثبات و معرفی یکی‌یکی کمک می‌کند بفهمی کدام محصول کمک کرده یا تحریک.")
        if (p.sleepStress != "good" && (acne || irritated)) pri += BeautyAction(
            "چون گفتی " + L(BeautyLabels.sleepStress, p.sleepStress) + " داری",
            "اگر ممکن است ساعت خواب را منظم کن؛ در روزهای پراسترس روتین را ساده نگه دار و محصول جدید امتحان نکن.",
            "استرس و کم‌خوابی ممکن است با افزایش کورتیزول، التهاب و جوش را بدتر و ترمیم سد پوست را کندتر کنند؛ شواهد متوسط است ولی ارتباط بارها گزارش شده.")
        if (pri.isEmpty()) pri += BeautyAction("چون گفتی پوستت مشکل خاصی ندارد",
            "همین روتین را ثابت نگه دار؛ ضدآفتاب روزانه را حفظ کن.", "وقتی پوست متعادل است، تغییر غیرضروری بیشتر از فایده، ریسک تحریک دارد.")

        am += if (oily || acne) "شوینده ملایم ژلی" else if (dry || irritated || onIso) "آب ولرم یا شوینده کرمی خیلی ملایم" else "شوینده ملایم"
        if (!pregnant && !irritated && !onIso && ("spots" in p.skinIssues || "dull" in p.skinIssues) && "vitc" in p.actives) am += "ویتامین C که از قبل تحمل می‌کنی"
        if (!pregnant && !irritated && !onIso && "niacinamide" in p.actives) am += "نیاسینامید (همان که استفاده می‌کنی)"
        am += when { dry || onIso -> "مرطوب‌کنندهٔ کرمی"; oily || acne -> "مرطوب‌کنندهٔ ژلی سبک غیرکومدوژن"; else -> "مرطوب‌کنندهٔ سبک" }
        am += "ضدآفتاب طیف وسیع SPF 30+" + if (oily || acne) " با بافت سبک/مات" else ""
        pm += "پاکسازی کامل (اگر آرایش یا ضدآفتاب داری، اول پاک‌کنندهٔ ملایم)"
        when {
            pregnant || onIso -> {}
            irritated -> pm += "اکتیوها فعلاً متوقف؛ فقط ترمیم"
            acne -> pm += "درمان نقطه‌ای فقط روی جوش‌ها"
            strongActive && key == "menstrual" -> pm += "اکتیو را این چند روز کم کن (یکی دو شب در هفته یا هیچ)"
            strongActive -> pm += "اکتیو آشنا ۲–۳ شب در هفته، نه با اکتیو دیگر در همان شب"
        }
        pm += if (dry || irritated || onIso) "مرطوب‌کنندهٔ ترمیمی غنی‌تر" else "مرطوب‌کنندهٔ سبک — پوست چرب هم رطوبت می‌خواهد"
        if (onIso) pm += "بالم لب"

        if (irritated) { avoid += "محصول جدید تا آرام شدن پوست"; avoid += "اسکراب و برس صورت"; avoid += "عطر و الکل زیاد روی صورت" }
        if (acne) { avoid += "دست زدن و فشار دادن جوش"; avoid += "کرم‌های خیلی سنگین روی ناحیهٔ جوش"; avoid += "اسکراب روی جوش ملتهب" }
        if (oily) avoid += "شستن صورت بیش از دو بار در روز"
        if (dry) avoid += "آب داغ و شوینده‌های کف‌زای قوی"
        if (p.actives.count { it == "retinoid" || it == "acid" || it == "vitc" } >= 2) avoid += "زدن هم‌زمان چند اکتیو در یک شب"
        if (pregnant) { avoid += "رتینوئیدها بدون تأیید پزشک"; avoid += "بسیاری از لایه‌بردارها بدون تأیید پزشک" }
        if (onIso) { avoid += "اسید، رتینوئید موضعی و اسکراب"; avoid += "اپیلاسیون با موم و لیزر بدون نظر پزشک" }
        if ("steroid" in p.meds) avoid += "قطع یا مصرف خودسرانهٔ کرم کورتونی روی صورت"
        if (key == "menstrual") avoid += "شروع محصول جدید در روزهای پریود"

        if (p.acneSeverity == "severe") flags += "جوش زیاد، دردناک یا زیرپوستی را با پزشک پوست بررسی کن."
        if (pregnant && strongActive) flags += "در بارداری قبل از ادامهٔ رتینوئید یا اسید با پزشک چک کن."
        if (p.issueDuration == "long" && (acne || irritated)) flags += "مشکلی که بیش از یک سال طول کشیده ارزش یک ویزیت تخصصی را دارد؛ ممکن است درمان تجویزی لازم باشد."
        if ("steroid" in p.meds) flags += "کرم کورتونی روی صورت در مصرف طولانی ممکن است پوست را نازک یا مستعد جوش کند؛ با پزشک هماهنگ کن."
        if (acne && p.cyclical == "yes" && "pcos" in p.conditions) flags += "جوش چرخه‌ای همراه با موی زائد یا پریود نامنظم ارزش بررسی هورمونی توسط پزشک را دارد."

        learn += "سد پوست" to "لایهٔ بیرونی پوست مثل دیوار آجر و ملات است: سلول‌ها و چربی‌هایی مثل سرامید. وقتی آسیب ببیند، پوست هم خشک می‌شود و هم حساس. مرطوب‌کننده این دیوار را ترمیم می‌کند."
        if (p.spf != "yes" || "spots" in p.skinIssues) learn += "چرا ضدآفتاب هر روز؟" to "UVA در تمام سال و حتی پشت شیشه وجود دارد و عامل اصلی لک و پیری زودرس است. ضدآفتاب منظم بیشترین پشتوانهٔ علمی را بین همهٔ محصولات پوست دارد."
        if (oily || acne) learn += "چرا پوست چرب هم مرطوب‌کننده می‌خواهد؟" to "چربی (سبوم) با آب فرق دارد. پوست چرب هم ممکن است کم‌آب باشد؛ خشک کردنش با الکل یا شستشوی زیاد سد پوست را آسیب می‌زند و التهاب و جوش را بدتر می‌کند."
        if (acne) learn += "جوش و هورمون‌ها" to "آندروژن‌ها غدد چربی را فعال‌تر می‌کنند. در فاز لوتئال (حدود ۱۰ روز قبل از پریود) نسبت هورمون‌ها تغییر می‌کند و در بسیاری از زنان جوش بیشتر می‌شود، به‌خصوص در فک و چانه."
        learn += "پوست و چرخه" to CYCLE_EVIDENCE

        val summary = buildString {
            append("پوست ${L(BeautyLabels.skinType, p.skinType)}")
            val issues = p.skinIssues.filter { it != "none" }.map { L(BeautyLabels.skinIssues, it) }
            if (issues.isNotEmpty()) append(" با ${issues.joinToString("، ")}") else append(" بدون مشکل خاص")
            if (acne) append(" (جوش ${L(BeautyLabels.acneSeverity, p.acneSeverity)} در ${zones(p)}" +
                (if (p.cyclical == "yes") "، بدتر قبل از پریود" else "") + ")")
            if (issues.isNotEmpty()) append("، از ${L(BeautyLabels.issueDuration, p.issueDuration)} پیش")
            append("؛ ضدآفتاب: ${L(BeautyLabels.spf, p.spf)}")
            if (p.stinging == "yes") append("؛ حساس به محصولات")
            val c = p.conditions.filter { it != "none" }.map { L(BeautyLabels.conditions, it) }
            if (c.isNotEmpty()) append("؛ سابقه‌ای که گفتی: ${c.joinToString("، ")}")
            append(".")
        }
        return BeautyAdvice(summary, pri.take(3), am, pm, avoid.toList(), skinPhaseNote(key, p), learn, flags)
    }

    fun hair(p: BeautyProfile, phaseGroup: String, pregnant: Boolean): BeautyAdvice {
        val key = if (pregnant) "pregnancy" else BeautyRoutineContent.keyFor(phaseGroup)
        val oilyScalp = p.scalp == "oily" || "oily_fast" in p.hairConcerns || p.lengths == "oily_roots"
        val dryLengths = p.lengths == "dry_brittle" || p.lengths == "damaged" || "breakage" in p.hairConcerns
        val itchy = p.scalp == "itchy" || "dandruff" in p.hairConcerns
        val shedding = "shedding" in p.hairConcerns || "thinning" in p.hairConcerns
        val recentChem = p.chemical == "recent"
        val pri = mutableListOf<BeautyAction>(); val wash = mutableListOf<String>(); val care = mutableListOf<String>()
        val avoid = linkedSetOf<String>(); val flags = mutableListOf<String>(); val learn = mutableListOf<Pair<String, String>>()
        val w = PersianNum(p.washesPerWeek)

        if (shedding) pri += when (p.sheddingTiming) {
            "after_event" -> BeautyAction("چون گفتی ریزش بعد از بیماری، زایمان، رژیم یا استرس شدید شروع شده",
                "شستشو را کم نکن، مو را محکم نبند و پروتئین و آهن کافی در غذا داشته باش؛ اگر بعد از ۶ ماه بهتر نشد یا شدید است به پزشک مراجعه کن.",
                "بعد از یک شوک به بدن، بخش بزرگی از فولیکول‌ها هم‌زمان وارد مرحلهٔ استراحت می‌شوند و حدود ۲–۳ ماه بعد می‌ریزند (تلوژن افلوویوم). این نوع ریزش اغلب موقت است و خودبه‌خود برمی‌گردد.")
            "long" -> BeautyAction("چون گفتی کم‌پشتی/ریزش تدریجی و طولانی داری",
                "برای ارزیابی (آزمایش آهن/فریتین، تیروئید و بررسی الگوی ریزش) به پزشک مراجعه کن؛ محصولات تبلیغاتی جایگزین درمان نیستند.",
                "کم‌پشتی تدریجی ممکن است ژنتیکی-هورمونی باشد و درمان‌های مؤثر آن تجویزی‌اند؛ هرچه زودتر شروع شود نتیجه بهتر است.")
            else -> BeautyAction("چون گفتی ریزشت از چند ماه اخیر بیشتر شده",
                "اگر ریزش بیش از ۲–۳ ماه ادامه دارد یا ناگهانی است، با پزشک بررسی کن (آهن، تیروئید)" + (if ("anemia" in p.conditions || "thyroid" in p.conditions) "؛ مخصوصاً چون سابقهٔ ${p.conditions.filter { it == "anemia" || it == "thyroid" }.joinToString(" و ") { L(BeautyLabels.conditions, it) }} گفتی." else "."),
                "روزی حدود ۵۰ تا ۱۰۰ تار مو ریختن طبیعی است. موهایی که در روزهای بدون شستشو جمع می‌شوند هنگام شستشو یک‌جا می‌ریزند؛ پس کم شستن ریزش را کم نمی‌کند، فقط آن را جابه‌جا می‌کند.")
        }
        if (oilyScalp && dryLengths) pri += BeautyAction("چون گفتی کف سرت چرب است ولی ساقه‌ات " + L(BeautyLabels.lengths, p.lengths),
            "شامپو فقط روی کف سر و ریشه؛ نرم‌کننده فقط از نیمهٔ مو تا انتها.",
            "کف سر غدد چربی دارد ولی ساقهٔ مو ندارد؛ چربی به انتهای موهای بلند نمی‌رسد. پس هر بخش مراقبت جدا می‌خواهد.")
        else if (oilyScalp) pri += BeautyAction("چون گفتی کف سرت زود چرب می‌شود و حدود $w بار در هفته می‌شویی",
            if (p.washesPerWeek <= 2) "هر وقت کف سر چرب شد با شامپوی ملایم بشوی؛ احتمالاً بیشتر از $w بار در هفته لازم داری."
            else "با شامپوی ملایم هر وقت لازم بود بشوی و نرم‌کننده را به ریشه نزن.",
            "اینکه «شستشوی زیاد کف سر را چرب‌تر می‌کند» شواهد قوی ندارد. چربی ماندهٔ کف سر ممکن است خارش و شوره را بدتر کند.")
        else if (dryLengths) pri += BeautyAction("چون گفتی ساقهٔ مویت " + L(BeautyLabels.lengths, p.lengths) + " است",
            "بعد از هر شستشو نرم‌کننده روی ساقه و هفته‌ای یک بار ماسک مغذی؛ قبل از سشوار محافظ حرارت.",
            "لایهٔ بیرونی مو (کوتیکول) با حرارت و مواد شیمیایی باز و آسیب‌دیده می‌شود؛ نرم‌کننده آن را صاف می‌کند و اصطکاک و شکستن را کم.")
        if (itchy) pri += BeautyAction("چون گفتی " + (if ("dandruff" in p.hairConcerns) "شوره/خارش" else "کف سر حساس و خارش‌دار") + " داری",
            "شامپوی ملایم و ماساژ آرام با نوک انگشت؛ اگر پوسته‌های چرب و زرد یا قرمزی بیش از چند هفته ادامه داشت با پزشک بررسی شود.",
            "شوره اغلب با نوعی مخمر طبیعی پوست و چربی کف سر مرتبط است و شستشوی منظم کمک می‌کند. خاراندن با ناخن پوست سر را زخمی می‌کند.")
        if (p.scalp == "dry" && p.washesPerWeek >= 5) pri += BeautyAction("چون گفتی کف سرت خشک است و $w بار در هفته می‌شویی",
            "دفعات شستشو را کمی کم کن و آب را ولرم نگه دار.", "شستشوی مکرر با آب داغ ممکن است چربی محافظ پوست سر را بشوید و خشکی و خارش را بیشتر کند.")
        if (recentChem) pri += BeautyAction("چون گفتی اخیراً رنگ/دکلره/کراتین کرده‌ای",
            "تا چند هفته حرارت را کم کن و کار شیمیایی دوباره انجام نده.", "مواد شیمیایی ساختار پروتئینی مو را ضعیف می‌کنند و مو تا مدتی آسیب‌پذیرتر است؛ حرارت روی موی ضعیف شکستن را بیشتر می‌کند.")
        if (pri.isEmpty()) pri += BeautyAction("چون گفتی نگرانی خاصی دربارهٔ مو نداری", "همین روتین را ادامه بده و حرارت را محدود کن.", "تغییر بی‌دلیل محصول اغلب فایده‌ای ندارد.")

        wash += "دفعات شستشو: " + when {
            oilyScalp -> "هر وقت کف سر چرب شد (برای شما احتمالاً ${PersianNum(maxOf(p.washesPerWeek, 3))}+ بار در هفته)"
            p.scalp == "dry" -> "۲–۳ بار در هفته کافی است"
            else -> "همان $w بار در هفته، مطابق نیاز"
        }
        wash += "شامپوی ملایم روی کف سر، آب ولرم نه داغ"
        wash += if (dryLengths) "نرم‌کننده از نیمهٔ مو تا انتها، ۲–۳ دقیقه بماند" else "نرم‌کنندهٔ سبک فقط روی انتهای مو"
        if (itchy) wash += "ماساژ نوک انگشت (نه ناخن) هنگام شستشو"
        care += "خشک کردن با حوله نرم با فشار دادن، نه مالیدن"
        if (dryLengths || recentChem) { care += "ماسک مغذی هفته‌ای یک بار روی ساقه"; care += "اسپری محافظ حرارت قبل از سشوار/اتو" }
        care += "باز کردن گره‌ها با شانهٔ دندانه‌گشاد از پایین به بالا"
        if (oilyScalp) care += "شامپوی خشک فقط گاه‌به‌گاه بین شستشوها، نه جای شستشو"
        if (shedding) care += "غذای کافی از پروتئین و آهن؛ رژیم‌های خیلی کم‌کالری ممکن است ریزش بیاورند"

        avoid += "اتو و سشوار داغ هر روز"
        if (recentChem || key == "menstrual" || pregnant) avoid += "رنگ/دکلرهٔ دوباره" + if (pregnant) " بدون مشورت پزشک" else " در این بازه"
        if (dryLengths || shedding) avoid += "بستن محکم و کش‌های فلزی (ریزش کششی)"
        if (oilyScalp) avoid += "روغن و نرم‌کننده روی ریشه"
        if (p.scalp == "dry" || itchy) avoid += "شامپوی خیلی قوی روزانه"
        if (shedding) { avoid += "مکمل خودسرانه با دوز بالا"; flags += "ریزش زیاد یا ناگهانی، یا لکهٔ بی‌مو را با پزشک بررسی کن." }

        learn += "چرخهٔ رشد مو" to "هر تار مو مرحلهٔ رشد چندساله، مرحلهٔ کوتاه گذار و مرحلهٔ استراحت حدود ۳ ماهه دارد. به همین دلیل ریزش ناشی از استرس یا بیماری معمولاً ۲–۳ ماه بعد دیده می‌شود."
        learn += "مو و چرخه" to "شواهد مستقیم برای تغییر مو در فازهای چرخهٔ ماهانه محدود است؛ تغییر چربی کف سر پیش از پریود گزارش شده ولی برای همه یکسان نیست."

        val summary = buildString {
            append("کف سر ${L(BeautyLabels.scalp, p.scalp)}، ساقه ${L(BeautyLabels.lengths, p.lengths)}")
            val c = p.hairConcerns.filter { it != "none" }.map { L(BeautyLabels.hairConcerns, it) }
            if (c.isNotEmpty()) append("؛ نگرانی: ${c.joinToString("، ")}")
            if (shedding && p.sheddingTiming.isNotEmpty() && p.sheddingTiming != "none") append(" (${L(BeautyLabels.sheddingTiming, p.sheddingTiming)})")
            append("؛ شستشو حدود $w بار در هفته")
            if (recentChem) append("؛ کار شیمیایی اخیر")
            append(".")
        }
        return BeautyAdvice(summary, pri.take(3), wash, care, avoid.toList(), hairPhaseNote(key, oilyScalp), learn, flags)
    }

    private fun PersianNum(n: Int) = n.toString().map { if (it.isDigit()) '۰' + (it - '0') else it }.joinToString("")

    private fun skinPhaseNote(key: String, p: BeautyProfile): String = when (key) {
        "menstrual" -> "در روزهای پریود، با پایین بودن استروژن، پوست برخی زنان خشک‌تر و حساس‌تر است" + (if (p.skinType == "sensitive" || p.stinging == "yes") "؛ با حساسیتی که گفتی، این چند روز اکتیو قوی نزن." else "؛ روتین را ساده نگه دار.")
        "follicular" -> "بعد از پریود، با بالا رفتن استروژن، پوست اغلب مقاوم‌تر است؛ اگر قرار است یک اکتیو آشنا را برگردانی یا اضافه کنی، این بازه مناسب‌تر است — فقط یکی."
        "ovulatory" -> "حوالی تخمک‌گذاری ممکن است کمی چربی بیاید؛ بافت سبک‌تر انتخاب کن."
        "luteal" -> "در فاز لوتئال چربی و جوش فک/چانه در بسیاری از زنان بیشتر می‌شود" +
            (if (p.cyclical == "yes") "؛ خودت هم گفتی جوشت قبل از پریود بدتر می‌شود، پس درمان نقطه‌ای را از همین حالا منظم کن." else if (p.hasAcne) "؛ درمان نقطه‌ای را منظم نگه دار." else "؛ اگر جوش آمد فقط نقطه‌ای درمان کن.")
        "pregnancy" -> "در بارداری تغییر هورمون‌ها ممکن است لک، جوش یا حساسیت بیاورد؛ فقط مراقبت ملایم و هر اکتیو را با پزشک چک کن."
        else -> "فاز چرخه با اطمینان مشخص نیست؛ توصیه‌ها فقط بر اساس وضعیتی است که گفتی."
    } + (if (p.meds.contains("hormonal")) " چون داروی هورمونی/قرص مصرف می‌کنی، نوسان‌های فاز ممکن است کمتر یا متفاوت باشد." else "")

    private fun hairPhaseNote(key: String, oilyScalp: Boolean): String = when (key) {
        "menstrual" -> "در پریود کف سر ممکن است حساس‌تر باشد؛ رنگ و حرارت بالا را به بعد موکول کن."
        "follicular" -> "اگر کار شیمیایی لازم داری، معمولاً این بازه از روزهای پریود مناسب‌تر است."
        "ovulatory" -> "معمولاً ظاهر مو خوب است؛ شستشو را با نیاز تنظیم کن."
        "luteal" -> "پیش از پریود کف سر ممکن است زودتر چرب شود" + (if (oilyScalp) "؛ برای تو احتمالاً شستشوی بیشتری لازم است." else ".")
        "pregnancy" -> "در بارداری ریزش معمولاً کمتر و بعد از زایمان بیشتر می‌شود (اغلب موقت)؛ رنگ و دکلره را با پزشک هماهنگ کن."
        else -> "فاز مشخص نیست؛ توصیه‌ها فقط بر اساس وضعیت موی توست."
    }

    fun homePreview(p: BeautyProfile, phaseGroup: String, pregnant: Boolean): String =
        skin(p, phaseGroup, pregnant).priorities.first().doFa
}
