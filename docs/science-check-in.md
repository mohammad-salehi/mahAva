# مبنای علمی «حال امروز» و «علم فاز امروز»

این متن‌ها توضیح آموزشی دربارهٔ **ارتباط احتمالی** یک حس با زمان چرخه‌اند. تاریخ پریود، زمان دقیق تخمک‌گذاری یا علت یک علامت را ثابت نمی‌کند. برای نتیجه‌گیری فردی، ثبت روزانه در چند چرخه لازم است.

کد منبع‌ها: `app/src/main/java/com/mahava/app/content/ScienceSources.kt`
متن‌ها: `TodaySignalContent.kt` (هوس، بدن، حال) و `PhaseScienceBank.kt` (توضیح هر فاز).
هر صفحهٔ جزئیات، سطح اطمینان علمی و منبع‌هایش را با لینک قابل لمس نشان می‌دهد.

## سطح اطمینان علمی در اپ

| برچسب در اپ | معنی |
|---|---|
| اطمینان علمی: زیاد | راهنمای سازمان‌های معتبر سلامت یا مرور نظام‌مند/فراتحلیل |
| اطمینان علمی: متوسط | مرور روایی یا مطالعهٔ آینده‌نگر بزرگ؛ نتیجهٔ گروهی، نه فردی |
| اطمینان علمی: کم | مطالعهٔ کوچک یا مقطعی، یا نتیجه‌های متناقض |

## منبع‌های نمایش‌داده‌شده در اپ (همه در ۱۵ مهر ۱۴۰۵ / 2026-10-07 باز و خوانده شدند)

| شناسه | برچسب در اپ | آدرس | قوت شواهد | برای چه استفاده شد |
|---|---|---|---|---|
| acog_pms | ACOG · PMS | https://www.acog.org/womens-health/faqs/premenstrual-syndrome | زیاد | علائم PMS، تعریف (۵ روز قبل، تمام شدن تا ۴ روز بعد)، کربوهیدرات پیچیده، وعدهٔ کوچک، کلسیم، ورزش، هم‌پوشانی با افسردگی/اضطراب |
| acog_cramps | ACOG · درد پریود | https://www.acog.org/womens-health/faqs/dysmenorrhea-painful-periods | زیاد | پروستاگلاندین، اوج درد روز اول، مسکن ضدالتهاب، گرما، علامت‌های هشدار |
| nhs_pms | NHS · PMS | https://www.nhs.uk/conditions/pre-menstrual-syndrome/ | زیاد | فهرست علائم، علت نامعلوم، حساسیت به تغییر هورمون |
| nhs_breast | NHS · درد سینه | https://www.nhs.uk/conditions/breast-pain/ | زیاد | درد سینهٔ دوره‌ای تا ۲ هفته قبل از پریود |
| nhs_ovulation_pain | NHS · درد تخمک‌گذاری | https://www.nhs.uk/symptoms/ovulation-pain/ | زیاد | درد خفیف یک‌طرفه حوالی تخمک‌گذاری و زمان مراجعه |
| owh_pms | womenshealth.gov · PMS | https://www.womenshealth.gov/menstrual-cycle/premenstrual-syndrome | زیاد | بیشتر از ۹۰٪ علائمی دارند؛ PMDD؛ ورزش، خواب، کافئین/نمک/قند کمتر |
| owh_cycle | womenshealth.gov · چرخه | https://www.womenshealth.gov/menstrual-cycle/your-menstrual-cycle | زیاد | جهش LH حدود ۳۶ ساعت قبل از تخمک‌گذاری؛ عمر تخمک و اسپرم |
| medline_pms | MedlinePlus · PMS | https://medlineplus.gov/ency/article/001505.htm | زیاد | زمان علائم، دفترچهٔ ۳ ماهه |
| mayo_pms | Mayo Clinic · PMS | https://www.mayoclinic.org/diseases-conditions/premenstrual-syndrome/symptoms-causes/syc-20376780 | زیاد (منبع) / کم (سازوکار سروتونین) | سروتونین «احتمالاً» نقش دارد؛ ثابت‌شده نیست |
| cc_cycle | Cleveland Clinic · چرخه | https://my.clevelandclinic.org/health/articles/10132-menstrual-cycle | زیاد | هورمون‌های هر فاز؛ چرخهٔ ۲۱ تا ۳۵ روز؛ خون‌ریزی سنگین |
| cc_pmdd | Cleveland Clinic · PMDD | https://my.clevelandclinic.org/health/diseases/9132-premenstrual-dysphoric-disorder-pmdd | زیاد | PMDD و زمان کمک گرفتن |
| cc_ovulation | Cleveland Clinic · تخمک‌گذاری | https://my.clevelandclinic.org/health/articles/23439-ovulation | زیاد | قرص ترکیبی جلوی تخمک‌گذاری را می‌گیرد (توضیح عمومی برای مصرف هورمون) |
| energy_meta | Nutrition Reviews 2025 (PMID 39008822) | https://academic.oup.com/nutritionreviews/article/83/3/e866/7713894 | متوسط | فراتحلیل ۱۵ مجموعه‌داده: نیمهٔ دوم چرخه حدود ۱۶۸ کالری بیشتر در روز؛ ناهمگونی زیاد |
| energy_review | PMC10251302 | https://pmc.ncbi.nlm.nih.gov/articles/PMC10251302/ | متوسط | اشتها حوالی تخمک‌گذاری کمترین، در نیمهٔ دوم بیشتر |
| biocycle | Gorczyca 2016, BioCycle (PubMed 26043860) | https://pubmed.ncbi.nlm.nih.gov/26043860/ | متوسط | هوس شکلات، شیرینی و شوری در اواخر نیمهٔ دوم بیشتر |
| choc_lab | PubMed 22824054 | https://pubmed.ncbi.nlm.nih.gov/22824054/ | کم | آزمایش کوچک: تفاوت معنادار در هوس شکلات پیدا نشد (نتیجهٔ متناقض) |
| hartlage | Hartlage 2012 (PMC3370334) | https://pmc.ncbi.nlm.nih.gov/articles/PMC3370334/ | متوسط | اوج علائم از حدود ۴ روز قبل تا ۳ روز اول پریود |
| hengartner | Hengartner 2017 (PubMed 28712426) | https://pubmed.ncbi.nlm.nih.gov/28712426/ | متوسط | بدتر شدن حال در همه یکسان نیست |
| sleep_review | Alzueta & Baker 2023 (PMC11562818) | https://pmc.ncbi.nlm.nih.gov/articles/PMC11562818/ | متوسط | اوج پروژسترون ۵ تا ۷ روز بعد از تخمک‌گذاری؛ دمای بدن حدود ۰٫۴ درجه بالاتر؛ خواب |
| bloating_cohort | White 2011 (PMC3154522) | https://pmc.ncbi.nlm.nih.gov/articles/PMC3154522/ | متوسط | نفخ در روز اول پریود به اوج می‌رسد |
| gi_study | Bernstein 2014 (PMC3901893) | https://pmc.ncbi.nlm.nih.gov/articles/PMC3901893/ | کم | علائم گوارشی و خستگی دور پریود (مقطعی، بر پایهٔ یادآوری) |
| acne_study | PMC4142818 | https://pmc.ncbi.nlm.nih.gov/articles/PMC4142818/ | کم تا متوسط | ۶۵٪ با جوش، بدتر شدن دور پریود؛ ۵۶٪ از آن‌ها در هفتهٔ قبل |
| lumsden | Lumsden 1983 (PubMed 6577521) | https://pubmed.ncbi.nlm.nih.gov/6577521/ | کم | پروستاگلاندین و انقباض رحم در درد پریود (مطالعهٔ کوچک قدیمی؛ کنار ACOG) |

## مصرف هورمون، چرخهٔ نامنظم یا مرحلهٔ نامعلوم

در این حالت‌ها توضیح‌ها «عمومی» می‌شوند (phase group = `general`). قرص ترکیبی جلوی تخمک‌گذاری را می‌گیرد (Cleveland Clinic · تخمک‌گذاری) و چرخهٔ کمتر از ۲۱ یا بیشتر از ۳۵ روز تخمین فاز را غیرقابل‌اعتماد می‌کند (Cleveland Clinic · چرخه).

## قاعدهٔ نوشتن متن‌ها

- از «ممکن است»، «برای بعضی‌ها» و «از روی یک ثبت نمی‌شود فهمید» استفاده شود.
- هوس خوراکی نباید به‌تنهایی به کمبود آهن، منیزیم، افت قند یا نیاز بدن نسبت داده شود.
- وقتی مرحلهٔ چرخه معلوم نیست، مصرف هورمون در تنظیمات ثبت شده، یا چرخه نامنظم است، توضیح مرحله‌ای نمایش داده نشود.
- قدرت شواهد دربارهٔ هوس و چرخه **متوسط و گاهی متناقض** است؛ در متن به کاربر گفته شود.
- علامت شدید، تازه، ماندگار یا مختل‌کنندهٔ کارهای روزانه نیاز به ارزیابی حرفه‌ای دارد.
- زبان ساده و روزمره؛ اصطلاح پزشکی در پرانتز در اولین بار.

## فهرست خواندنِ نسخهٔ قبلی

منبع‌های زیر در نسخهٔ قبلی این سند آمده بودند. آن‌هایی که در جدول بالا نیستند، دیگر داخل اپ نمایش داده نمی‌شوند.

### مطالعات پژوهشی

- [Gorczyca و همکاران، ۲۰۱۶، BioCycle](https://pubmed.ncbi.nlm.nih.gov/26043860/): در ۲۵۹ زن با چرخهٔ منظم، هوس شکلات، شیرینی و مزهٔ شور و اشتها در اواخر نیمهٔ دوم چرخه بیشتر گزارش شد. این نتیجه گروهی است و برای یک نفر علت قطعی نمی‌سازد. (شواهد متوسط)
- [مطالعهٔ آزمایشگاهی هوس خوراکی، ۲۰۱۲](https://pubmed.ncbi.nlm.nih.gov/22824054/): در یک آزمایش کوچک، واکنش به نشانهٔ شکلات و مقدار شکلات خورده‌شده بین دو مرحله تفاوت معنادار نداشت. برای همین توضیح‌ها نباید هوس شکلات را به کمبود منیزیم یا تغییر هورمون مشخص نسبت دهند. (شواهد مختلط)
- [Hormes و همکاران، ۲۰۱۷](https://pubmed.ncbi.nlm.nih.gov/28723930/): نقش زمینهٔ فرهنگی در هوس شکلات نزدیک پریود را بررسی کرد. هوس شکلات آزمون کمبود مواد مغذی نیست.
- [Lumsden و همکاران، ۱۹۸۳](https://pubmed.ncbi.nlm.nih.gov/6577521/): در یک مطالعهٔ کوچک، مادهٔ پروستاگلاندین با شدت انقباض رحم در درد پریود مرتبط بود. این توضیح فقط برای دل‌درد هم‌زمان با خون‌ریزی استفاده می‌شود.
- [Hartlage و همکاران، ۲۰۱۲](https://pmc.ncbi.nlm.nih.gov/articles/PMC3370334/): ثبت‌های روزانه در دو گروه نشان دادند که بعضی نشانه‌های روحی و جسمی در روزهای نزدیک پریود پررنگ‌تر می‌شوند. ثبت یک روز برای شناخت الگوی شخصی کافی نیست.
- [Hengartner و همکاران، ۲۰۱۷](https://pubmed.ncbi.nlm.nih.gov/28712426/) (در نسخهٔ قبلی این سند به اشتباه Schweizer-Schubert نوشته شده بود): در مطالعهٔ دو چرخه، افزایش یکنواخت حال بد در همهٔ افراد دیده نشد. به همین دلیل برنامه حال روحی را برای همه از روی مرحلهٔ چرخه پیش‌بینی قطعی نمی‌کند.

### راهنماهای معتبر سلامت

- [ACOG — Premenstrual Syndrome (PMS)](https://www.acog.org/womens-health/faqs/premenstrual-syndrome): علائم جسمی و روحی رایج قبل از پریود (از جمله تغییر اشتها / هوس خوراکی، نفخ، حساسیت سینه، سردرد، زودرنجی). تشخیص الگوی چندماهه با ثبت روزانه.
- [NHS — PMS](https://www.nhs.uk/conditions/pre-menstrual-syndrome/): فهرست علائم رایج؛ علت دقیق معلوم نیست؛ ثبت حداقل دو چرخه؛ مراجعه اگر زندگی روزمره مختل شود.
- [Office on Women’s Health — PMS](https://www.womenshealth.gov/menstrual-cycle/premenstrual-syndrome): تعریف PMS، علائم جسمی/عاطفی، نقش احتمالی افت هورمون‌ها، اهمیت ثبت.
- [Cleveland Clinic — PMS](https://my.clevelandclinic.org/health/diseases/24288-pms-premenstrual-syndrome): علائم جسمی و عاطفی؛ تشخیص بر پایهٔ زمان‌بندی؛ درمان‌های سبک زندگی و دارویی.
- [Cleveland Clinic — Period cravings](https://health.clevelandclinic.org/period-cravings): هوس نزدیک پریود به‌عنوان پاسخ فیزیولوژیک به نوسان هورمون‌ها (نه الزاماً کمبود مواد مغذی مشخص).
- [Mayo Clinic — Menstrual cramps](https://www.mayoclinic.org/diseases-conditions/menstrual-cramps/symptoms-causes/syc-20374938): نقش پروستاگلاندین در انقباض رحم و درد پریود؛ تهوع و سردرد همراه ممکن است.
- [MedlinePlus — Painful menstrual periods](https://medlineplus.gov/ency/article/003150.htm): دیسمنوره (درد قاعدگی)؛ نقش پروستاگلاندین؛ مراقبت خانگی و زمان مراجعه.

