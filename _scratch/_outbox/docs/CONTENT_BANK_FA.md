# بانک محتوا و منابع

## وضعیت بازبینی پزشکی
**مبتنی بر منابع؛ بازبینی پزشکی مستقل انجام نشده**

## نسخه
`1.0.0` — تاریخ بررسی منابع توسعه: ۲۰۲۶-۱۰-۰۶

## منابع واقعاً خوانده‌شده
1. OWH — Your menstrual cycle — https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle
2. NHS — Fertility in the menstrual cycle — https://www.nhs.uk/conditions/periods/fertility-in-the-menstrual-cycle/
3. NHS — Natural family planning — https://www.nhs.uk/contraception/methods-of-contraception/natural-family-planning/
4. NHS — PMS — https://www.nhs.uk/conditions/pre-menstrual-syndrome/
5. NHS — Period pain — https://www.nhs.uk/symptoms/period-pain/
6. NHS — Heavy periods — https://www.nhs.uk/conditions/heavy-periods/
7. NHS — Vaginal discharge — https://www.nhs.uk/symptoms/vaginal-discharge/
8. NHS — Ovulation pain — https://www.nhs.uk/symptoms/ovulation-pain/
9. NHS — Pregnancy test — https://www.nhs.uk/pregnancy/trying-for-a-baby/doing-a-pregnancy-test/
10. NHS — Emergency contraception — https://www.nhs.uk/contraception/emergency-contraception/
11. NHS — Ectopic pregnancy symptoms — https://www.nhs.uk/conditions/ectopic-pregnancy/symptoms/

## منبع بازشده ولی بدون متن بالینی قابل اتکا
- ACOG Women's Health hub — https://www.acog.org/womens-health — در زمان fetch بیشتر ناوبری/فهرست بود؛ **ادعای بالینی از آن نقل نشد**.

## قواعد نمایش (displayRules)
هر آیتم: `showWhenPhase`, `hideWhenPregnancyMode`, `requiresFertilityEnabled`, `minCycleDay`, `maxCycleDay`.
فیلتر در `ContentRepository.visibleItems` با فاز موتور، حالت بارداری، باروری فعال و دسته‌های پنهان کاربر.

## فایل
`app/src/main/assets/content/content_bank.json` + `content_schema.json`
