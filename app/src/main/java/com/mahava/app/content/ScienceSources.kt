package com.mahava.app.content

/**
 * Every source shown in the app. Each URL was opened and read while writing the text
 * (see docs/science-check-in.md). Never add a source here that nobody has opened.
 */
object ScienceSources {
    data class Source(val id: String, val labelFa: String, val url: String) {
        val host: String get() = url.removePrefix("https://").substringBefore('/')
    }

    /** How sure science is about a statement, in plain words. */
    enum class Evidence(val titleFa: String, val noteFa: String) {
        HIGH(
            "اطمینان علمی: زیاد",
            "راهنماهای معتبر پزشکی یا جمع‌بندی پژوهش‌های زیاد همین را می‌گویند."
        ),
        MEDIUM(
            "اطمینان علمی: متوسط",
            "پژوهش‌ها این الگو را در گروه‌ها دیده‌اند، اما برای هر نفر فرق دارد."
        ),
        LOW(
            "اطمینان علمی: کم",
            "پژوهش کم است یا نتیجه‌ها با هم فرق دارند. این را یک احتمال بدان، نه قطعی."
        )
    }

    private val all = listOf(
        Source("acog_pms", "ACOG · سندرم پیش از قاعدگی (PMS)", "https://www.acog.org/womens-health/faqs/premenstrual-syndrome"),
        Source("acog_cramps", "ACOG · درد پریود", "https://www.acog.org/womens-health/faqs/dysmenorrhea-painful-periods"),
        Source("nhs_pms", "NHS · سندرم پیش از قاعدگی", "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"),
        Source("nhs_breast", "NHS · درد سینه", "https://www.nhs.uk/conditions/breast-pain/"),
        Source("nhs_ovulation_pain", "NHS · درد تخمک‌گذاری", "https://www.nhs.uk/symptoms/ovulation-pain/"),
        Source("owh_pms", "womenshealth.gov · PMS", "https://www.womenshealth.gov/menstrual-cycle/premenstrual-syndrome"),
        Source("owh_cycle", "womenshealth.gov · چرخهٔ قاعدگی", "https://www.womenshealth.gov/menstrual-cycle/your-menstrual-cycle"),
        Source("medline_pms", "MedlinePlus · PMS", "https://medlineplus.gov/ency/article/001505.htm"),
        Source("mayo_pms", "Mayo Clinic · PMS", "https://www.mayoclinic.org/diseases-conditions/premenstrual-syndrome/symptoms-causes/syc-20376780"),
        Source("cc_cycle", "Cleveland Clinic · چرخهٔ قاعدگی", "https://my.clevelandclinic.org/health/articles/10132-menstrual-cycle"),
        Source("cc_pmdd", "Cleveland Clinic · PMDD", "https://my.clevelandclinic.org/health/diseases/9132-premenstrual-dysphoric-disorder-pmdd"),
        Source("cc_ovulation", "Cleveland Clinic · تخمک‌گذاری", "https://my.clevelandclinic.org/health/articles/23439-ovulation"),
        Source("energy_meta", "مرور نظام‌مند اشتها و چرخه (Nutrition Reviews 2025)", "https://academic.oup.com/nutritionreviews/article/83/3/e866/7713894"),
        Source("energy_review", "مرور اشتها در فازهای چرخه (PMC10251302)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC10251302/"),
        Source("biocycle", "پژوهش BioCycle دربارهٔ هوس (PubMed 26043860)", "https://pubmed.ncbi.nlm.nih.gov/26043860/"),
        Source("choc_lab", "آزمایش هوس شکلات (PubMed 22824054)", "https://pubmed.ncbi.nlm.nih.gov/22824054/"),
        Source("hartlage", "Hartlage 2012 · زمان علائم (PMC3370334)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC3370334/"),
        Source("hengartner", "Hengartner 2017 · حال در چرخه (PubMed 28712426)", "https://pubmed.ncbi.nlm.nih.gov/28712426/"),
        Source("sleep_review", "مرور خواب و چرخه (PMC11562818)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC11562818/"),
        Source("bloating_cohort", "پژوهش یک‌ساله دربارهٔ نفخ (PMC3154522)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC3154522/"),
        Source("gi_study", "علائم گوارشی دور پریود (PMC3901893)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC3901893/"),
        Source("acne_study", "جوش دور پریود (PMC4142818)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC4142818/"),
        Source("lumsden", "پروستاگلاندین و درد پریود (PubMed 6577521)", "https://pubmed.ncbi.nlm.nih.gov/6577521/")
    ).associateBy { it.id }

    fun get(id: String): Source? = all[id]
    fun list(ids: List<String>): List<Source> = ids.mapNotNull { all[it] }
    fun allSources(): List<Source> = all.values.toList()
}
