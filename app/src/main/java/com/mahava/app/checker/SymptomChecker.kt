package com.mahava.app.checker

import com.mahava.app.util.PersianDigits

/**
 * Educational self-assessment inspired by common symptom overlap lists
 * for PCOS and endometriosis. NEVER diagnoses.
 */
enum class CheckerTopic(val id: String, val titleFa: String) {
    PCOS("pcos", "علائم هم‌پوشان با تنبلی تخمدان (PCOS)"),
    ENDO("endo", "علائم هم‌پوشان با اندومتریوز")
}

data class CheckerQuestion(
    val id: String,
    val textFa: String,
    val weight: Int = 1
)

data class CheckerAnswer(val questionId: String, val yes: Boolean)

enum class CheckerBand { LOW, MODERATE, HIGHER }

data class CheckerResult(
    val topic: CheckerTopic,
    val score: Int,
    val maxScore: Int,
    val band: CheckerBand,
    val summaryFa: String,
    val discussFa: List<String>,
    val disclaimerFa: String,
    val answeredAtEpochDay: Long
)

object SymptomChecker {
    val DISCLAIMER_FA =
        "این پرسشنامه تشخیص پزشکی نیست و جای آزمایش و ویزیت پزشک را نمی‌گیرد. " +
        "خیلی از این علائم در آدم‌های سالم هم دیده می‌شود. " +
        "اگر نگران هستی یا علائم شدید/ناگهانی داری، به پزشک زنان یا ماما مراجعه کن."

    fun questions(topic: CheckerTopic): List<CheckerQuestion> = when (topic) {
        CheckerTopic.PCOS -> listOf(
            CheckerQuestion("p1", "پریودهایم معمولاً نامنظم است یا چند ماه یک‌بار می‌آید.", 2),
            CheckerQuestion("p2", "موهای زائد صورت یا بدنم بیشتر از قبل شده.", 2),
            CheckerQuestion("p3", "ریزش موی سر به‌صورت الگوی مردانه دارم.", 1),
            CheckerQuestion("p4", "جوش‌های مقاوم، مخصوصاً روی فک و چانه دارم.", 1),
            CheckerQuestion("p5", "افزایش وزن دور شکم دارم یا کم کردن وزن برایم سخت است.", 1),
            CheckerQuestion("p6", "لکه‌های تیره روی گردن، زیر بغل یا کشالهٔ ران دارم.", 1),
            CheckerQuestion("p7", "در سونوگرافی یا حرف پزشک قبلاً به کیست/تنبلی اشاره شده (اگر می‌دانی).", 2),
            CheckerQuestion("p8", "خستگی زیاد یا سیاه‌شدن دور چشم با خواب کافی دارم.", 1)
        )
        CheckerTopic.ENDO -> listOf(
            CheckerQuestion("e1", "درد پریودم آن‌قدر شدید است که کار روزمره‌ام مختل می‌شود.", 2),
            CheckerQuestion("e2", "درد لگنی در روزهای غیر پریود هم دارم.", 2),
            CheckerQuestion("e3", "هنگام رابطهٔ جنسی درد عمیق دارم.", 2),
            CheckerQuestion("e4", "هنگام اجابت مزاج یا ادرار در روزهای پریود درد دارم.", 1),
            CheckerQuestion("e5", "خون‌ریزی خیلی زیاد یا لخته‌های بزرگ دارم.", 1),
            CheckerQuestion("e6", "نفخ شدید و درد گوارشی که با پریود بدتر می‌شود.", 1),
            CheckerQuestion("e7", "خستگی شدید همراه با دردهای دوره‌ای دارم.", 1),
            CheckerQuestion("e8", "برای بارداری مشکل داشته‌ام یا پزشک به اندومتریوز اشاره کرده.", 2)
        )
    }

    fun score(topic: CheckerTopic, answers: List<CheckerAnswer>, epochDay: Long): CheckerResult {
        val qs = questions(topic)
        val byId = answers.associateBy { it.questionId }
        var score = 0
        var max = 0
        for (q in qs) {
            max += q.weight
            if (byId[q.id]?.yes == true) score += q.weight
        }
        val ratio = if (max == 0) 0f else score.toFloat() / max
        val band = when {
            ratio < 0.34f -> CheckerBand.LOW
            ratio < 0.60f -> CheckerBand.MODERATE
            else -> CheckerBand.HIGHER
        }
        val summary = when (topic) {
            CheckerTopic.PCOS -> when (band) {
                CheckerBand.LOW ->
                    "بر اساس جواب‌هایت، هم‌پوشانی کمی با فهرست شایع علائم تنبلی تخمدان دیدی. این یعنی تشخیص نیست و خیالت را کامل راحت نمی‌کند؛ فقط یک غربال آموزشی است."
                CheckerBand.MODERATE ->
                    "چند مورد از علائم شایع مرتبط با تنبلی تخمدان را علامت زدی. این می‌تواند به معنی نیاز به حرف زدن با پزشک باشد، نه این‌که حتماً PCOS داری."
                CheckerBand.HIGHER ->
                    "تعداد بیشتری از علائم هم‌پوشان را انتخاب کردی. فقط پزشک با معاینه و در صورت نیاز آزمایش/سونوگرافی می‌تواند نظر بدهد. خودتشخیصی نکن."
            }
            CheckerTopic.ENDO -> when (band) {
                CheckerBand.LOW ->
                    "هم‌پوشانی کمی با فهرست شایع علائم اندومتریوز دیدی. درد خفیف پریود رایج است؛ اگر نگران نیستی، باز هم در ثبت‌ها مراقب تغییرها باش."
                CheckerBand.MODERATE ->
                    "چند علامت هم‌پوشان را گزارش کردی. ارزش دارد با پزشک دربارهٔ درد و تأثیرش روی زندگی‌ات حرف بزنی."
                CheckerBand.HIGHER ->
                    "علائم بیشتری از فهرست هم‌پوشان را داری. این پرسشنامه تشخیص اندومتریوز نیست؛ برای بررسی باید به متخصص زنان مراجعه کنی."
            }
        }
        val discuss = when (topic) {
            CheckerTopic.PCOS -> listOf(
                "نظم پریود در ۶–۱۲ ماه اخیر",
                "رشد مو، جوش، و تغییر وزن",
                "سابقهٔ خانوادگی دیابت یا تنبلی تخمدان",
                "سؤال دربارهٔ آزمایش هورمون و سونوگرافی"
            )
            CheckerTopic.ENDO -> listOf(
                "شدت درد و اینکه آیا با مسکن معمولی ساکت می‌شود",
                "درد در رابطه، اجابت مزاج یا ادرار",
                "تأثیر درد روی کار، خواب و خلق",
                "سؤال دربارهٔ معاینه و در صورت نیاز تصویربرداری/لاپاراسکوپی"
            )
        }
        return CheckerResult(
            topic = topic,
            score = score,
            maxScore = max,
            band = band,
            summaryFa = summary + " امتیاز آموزشی: ${PersianDigits.toPersian(score)} از ${PersianDigits.toPersian(max)}.",
            discussFa = discuss,
            disclaimerFa = DISCLAIMER_FA,
            answeredAtEpochDay = epochDay
        )
    }

    fun bandLabelFa(band: CheckerBand): String = when (band) {
        CheckerBand.LOW -> "هم‌پوشانی کم"
        CheckerBand.MODERATE -> "هم‌پوشانی متوسط"
        CheckerBand.HIGHER -> "هم‌پوشانی بیشتر"
    }
}
