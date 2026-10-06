# جدول ادعا ← منبع (بانک محتوای فاز/زیربازه)

تاریخ دسترسی منابع در توسعه: **۲۰۲۶-۱۰-۰۶**  
وضعیت بازبینی پزشکی همهٔ ادعاها: **مبتنی بر منابع؛ بازبینی پزشکی مستقل انجام نشده**

| ادعا (خلاصه آموزشی) | زیربازه/موضوع | منبع | URL | قدرت شواهد در اپ |
|---|---|---|---|---|
| افت استروژن/پروژسترون با شروع خون‌ریزی؛ ریزش آندومتر | menstruation_* | OWH Your menstrual cycle؛ Endotext Reed & Carr | https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle ؛ https://www.ncbi.nlm.nih.gov/books/NBK279054/ | قوی (فیزیولوژی) |
| مدت معمول خون‌ریزی حدود ۲–۷ روز؛ حجم متوسط حدود ۲۰–۹۰ml | menstruation_* | NHS fertility-in-cycle / Periods | https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/ | متوسط–قوی |
| FSH در انتقال لوتئال→فولیکولی فولیکول‌ها را فراخوانی می‌کند | follicular_* | Endotext | NBK279054 | قوی |
| استروژن مخاط را شفاف/کشسان می‌کند؛ پروژسترون غلیظ می‌کند | follicular_late / peri / luteal | Endotext؛ StatPearls Physiology Menstrual Cycle؛ OWH | NBK279054؛ NBK500020؛ OWH | متوسط–قوی |
| تخمک‌گذاری معمولاً ~۱۰–۱۶ روز قبل از پریود بعدی (نه لزوماً روز ۱۴) | peri_ovulatory؛ موتور | NHS | NHS fertility page | قوی (راهنما) |
| میانگین طول فولیکولی متغیر است (~۱۶٫۹d در نمونه بزرگ)؛ لوتئال ~۱۲٫۴d | مقیاس‌بندی زیربازه | Bull et al. 2019 npj Digit Med | https://www.nature.com/articles/s41746-019-0152-7 | قوی (داده واقعی) |
| پنجره باروری پژوهشی ~۶ روز منتهی به روز تخمک‌گذاری | peri_ovulatory | Wilcox et al. 1995 NEJM؛ Wilcox 2000 BMJ | https://www.nejm.org/doi/full/10.1056/NEJM199512073332301 | قوی در جمعیت مطالعه |
| اسپرم چند روز / تخمک ساعت‌ها زنده می‌ماند | peri_ovulatory | NHS؛ OWH | همان‌ها | متوسط–قوی |
| موج LH ~۳۴–۳۶h قبل از تخمک‌گذاری؛ اوج ~۱۰–۱۲h قبل | peri_ovulatory | Endotext | NBK279054 | قوی |
| BBT پس از تخمک‌گذاری با پروژسترون بالا می‌رود (~۰٫۳–۰٫۷°C در مرورها) | luteal_* | StatPearls BBT؛ مرورهای دمایی | NBK546686 | متوسط |
| علائم PMS (خلق، نفخ، خواب، پوست…) در برخی افراد | luteal_late | NHS PMS | https://www.nhs.uk/conditions/pre-menstrual-syndrome/ | متوسط (آموزش عمومی) |
| درد خفیف پریود: گرما/حرکت ملایم ممکن است کمک کند | care menstruation | NHS period pain | https://www.nhs.uk/symptoms/period-pain/ | متوسط |
| تقویم روش جلوگیری نیست؛ FAB نیاز به آموزش دارد | fertility UI | NHS؛ ACOG FAB FAQ | https://www.acog.org/womens-health/faqs/fertility-awareness-based-methods-of-family-planning | قوی (محدودیت) |
| تست بارداری از روز اول تأخیر قابل اعتمادتر | late_period | NHS pregnancy test (ارجاع در اپ Late) | NHS | متوسط–قوی |
| ادعای «امروز حتماً پرانرژی/با میل هستی» | — | **عمداً ذکر نشده** | — | ممنوع در اپ |

## لنگر زمانی موتور
- `nextPeriodCentral = lastStart + cycleLength` (میانه چرخه‌ها یا طول تأییدشده کاربر)
- `ovulationCentral ≈ nextPeriodCentral − ۱۴` با باند ±۳ روز (پوشش NHS ۱۰–۱۶)
- باروری نمایشی: `[ovEarliest−۵ ، ovLatest+۱]` با الهام از پنجرهٔ حدوداً ۶روزه Wilcox — **نه جلوگیری**
- زیربازه‌های لوتئال با `daysUntilCentralPeriod` تقسیم می‌شوند (نه day ثابت از ابتدای چرخه)

## منابع بازشده بدون ادعای بالینی اضافه
- ACOG infographic menstrual cycle — برای تأیید پیام عمومی؛ جزئیات هورمونی از Endotext/NHS گرفته شد.
- StatPearls NBK500020 — fetch مستقیم ۴۰۳ بود؛ از snippets فهرست NCBI و ارجاعات متقابل Endotext استفاده شد و در جدول مشخص است.
