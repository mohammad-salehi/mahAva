package com.mahava.app.content

import com.mahava.app.cycle.CyclePhase
import com.mahava.app.cycle.CycleSubWindow

/** The same keys are used by the daily log and the home check-in. */
object FoodCravingKeys {
    val ALL = listOf(
        "chocolate" to "شکلات", "sweet" to "شیرینی", "salty" to "خوراکی شور",
        "carbs" to "نان و برنج", "spicy" to "غذای تند", "dairy" to "لبنیات",
        "red_meat" to "گوشت قرمز", "caffeine" to "قهوه", "other" to "چیز دیگر"
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

/**
 * Educational associations, never a nutrient-deficiency diagnosis. A prospective BioCycle
 * cohort found more chocolate, sweet and salty cravings in the late luteal phase; a smaller
 * laboratory study did not find a phase difference in chocolate-cue responses. See
 * docs/science-check-in.md for study links and content limits.
 */
object CravingContent {
    fun disclaimerFa() =
        "از روی یک هوس نمی‌شود کمبود ماده‌ای در بدن را تشخیص داد یا علت دقیقش را فهمید. خواب، استرس و عادت غذایی هم اثر دارند. مرحلهٔ چرخه هم فقط تخمینی است."

    fun phaseGroupOf(sub: CycleSubWindow?, phase: CyclePhase?): String = when (sub) {
        CycleSubWindow.MENSTRUATION_EARLY, CycleSubWindow.MENSTRUATION_LATE -> "menstrual"
        CycleSubWindow.FOLLICULAR_EARLY, CycleSubWindow.FOLLICULAR_LATE -> "follicular"
        CycleSubWindow.PERI_OVULATORY -> "fertile"
        CycleSubWindow.LUTEAL_EARLY, CycleSubWindow.LUTEAL_MID -> "early_luteal"
        CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> "late_luteal"
        CycleSubWindow.LATE_PERIOD, CycleSubWindow.UNKNOWN, null -> "general"
    }

    fun meaning(cravingKey: String, phaseGroup: String): CravingMeaning? {
        if (FoodCravingKeys.ALL.none { it.first == cravingKey }) return null
        val label = FoodCravingKeys.labelFa(cravingKey)
        val studiedCraving = cravingKey in setOf("chocolate", "sweet", "salty")
        val cycleText = when {
            phaseGroup == "late_luteal" && studiedCraving ->
                "نزدیک پریود، بعضی‌ها بیشتر هوس $label می‌کنند. این یک الگوی دیده‌شده در پژوهش‌هاست؛ علت دقیق هوس تو را نشان نمی‌دهد."
            phaseGroup == "late_luteal" && cravingKey == "carbs" ->
                "نزدیک پریود ممکن است اشتها یا میل به خوراکی‌های سیرکننده بیشتر شود، ولی برای هوس نان و برنج علت مشخصی ثابت نشده."
            phaseGroup == "menstrual" && studiedCraving ->
                "این هوس گاهی از روزهای نزدیک پریود ادامه پیدا می‌کند. با یک بار ثبت، نمی‌شود گفت چرخه علتش بوده."
            phaseGroup == "early_luteal" && studiedCraving ->
                "در نیمهٔ دوم چرخه، اشتها برای بعضی‌ها تغییر می‌کند؛ شواهد دربارهٔ این روزهای زودتر به اندازهٔ روزهای نزدیک پریود روشن نیست."
            else -> "برای هوس $label در این مرحله دلیل مشخصی از روی چرخه پیدا نمی‌شود. سلیقه، عادت، خواب و استرس هم می‌توانند نقش داشته باشند."
        }
        val tip = when (cravingKey) {
            "chocolate", "sweet" -> "اگر دوستش داری، خوردن مقدار دلخواه در کنار وعده‌های منظم اشکالی ندارد."
            "salty" -> "اگر تشنه‌ای، آب هم بخور؛ لازم نیست از روی این هوس نتیجهٔ پزشکی بگیری."
            "caffeine" -> "اگر خوابت کم شده، زمان و مقدار قهوه را هم در نظر بگیر."
            else -> "اگر این هوس تکرار شد، آن را چند روز ثبت کن تا الگوی خودت روشن‌تر شود."
        }
        return CravingMeaning(cravingKey, phaseGroup, label, cycleText, tip)
    }

    fun meaningsForPhase(phaseGroup: String) =
        FoodCravingKeys.ALL.mapNotNull { meaning(it.first, phaseGroup) }

    fun shortTipFa(cravingKey: String, phaseGroup: String): String? =
        meaning(cravingKey, phaseGroup)?.meaningFa
}
