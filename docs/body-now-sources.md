# «الان توی بدنت چه خبره؟» — research notes and sources

Content lives in `app/src/main/java/com/mahava/app/content/BodyNowContent.kt` (ids below = `BodyNowContent.SOURCES`).
Every state shows: hormones (plain meaning) · what she may feel · what helps to eat · what helps to do · what to cut down · (where it matters) when to see a doctor.
The app only *estimates* the phase from the calendar, so every line is hedged ("احتمالاً", "شاید", "معمولاً"). Logged symptoms replace the generic "feel" line.
Hormonal contraception / irregular cycles get the UNKNOWN (no phase guess) text.

## Cycle (CycleSubWindow)
| Sub-window | Hormones (summary) | Key advice | Sources |
|---|---|---|---|
| MENSTRUATION_EARLY | E2 & P4 at their lowest; the drop triggers bleeding | heat, gentle exercise, sleep; iron + vitamin C foods; tea/coffee away from meals (block iron); see doctor if soaking a pad every 1–2 h or pain not relieved by painkillers | cc_cycle, acog_dysmenorrhea, acog_heavy, pmc_iron_hmb |
| MENSTRUATION_LATE | estrogen starts rising | keep iron foods, restart exercise | cc_follicular, pmc_iron_hmb |
| FOLLICULAR_EARLY | FSH grows follicles, estrogen rises, lining rebuilds | protein/whole foods, more intense exercise ok | cc_follicular |
| FOLLICULAR_LATE | estrogen near peak, clearer slippery mucus | fertile window; calendar is not contraception | cc_follicular, acog_fabm |
| PERI_OVULATORY | LH surge releases the egg; possible one-sided twinges, egg-white mucus | folic acid if trying; calendar ≠ contraception; severe one-sided pain/fever → doctor | cc_lh, acog_fabm |
| LUTEAL_EARLY | corpus luteum makes progesterone (+ some estrogen) | regular complex-carb meals, moderate exercise, sleep schedule | cc_luteal |
| LUTEAL_MID | progesterone peak, second estrogen rise | calcium foods, small frequent meals, less salt/caffeine | cc_luteal, acog_pms |
| LUTEAL_LATE_PREMENSTRUAL | E2 & P4 fall | ACOG/Mayo PMS self care: complex carbs, calcium, smaller meals, 30 min aerobic exercise, regular sleep; limit salt, sugar, caffeine, alcohol | acog_pms, mayo_pms, cc_pms |
| LATE_PERIOD | unknown from calendar: stress, weight change, sleep, pregnancy | home test from first missed day; avoid alcohol/smoking/OTC meds until sure; severe one-sided pain/bleeding/dizziness → doctor | nhs_late_period, nhs_preg_test |
| UNKNOWN | no guess | balanced plate, 30 min walking, less sugary drinks/caffeine | cc_cycle |

## Pregnancy (weeks)
| Weeks | Hormones | Key points | Sources |
|---|---|---|---|
| 1–4 | hCG starts rising, progesterone high | folic acid 400 µg; no alcohol/smoking; caffeine < 200 mg; ask before any medicine; severe one-sided pain/heavy bleeding → urgent | medline_hcg, nhs_vitamins, nhs_foods, nhs_get_help |
| 5–8 | hCG rising fast | nausea any time of day: small frequent meals, dry crackers, ginger, sips; avoid raw/undercooked meat, eggs, fish, unpasteurised dairy; can't keep fluids 24 h / dark urine / bleeding → doctor | nhs_sickness, rcog_sickness, nhs_foods |
| 9–13 | hCG peaks ~9–12 then falls; placenta takes over progesterone | fibre + water for constipation; pasteurised dairy; avoid swordfish/shark/marlin, liver, cold cured meats; caffeine < 200 mg | nhs_weeks, nhs_foods, acog_exercise_preg |
| 14–17 | hCG falls, E2/P4 from placenta rise | energy often better; iron & protein; 150 min/week moderate exercise if no contraindication; avoid fall/contact sports; gum care | acog_exercise_preg, acog_nutrition_preg |
| 18–22 | E2/P4 still rising | first movements; leg cramps (stretch); calcium; oily/low-mercury fish ≤ 2 portions/week, cooked; anomaly scan 18–22 wk | nhs_week20, nhs_foods, who_anc |
| 23–27 | blood volume up; placental hormones raise blood sugar | heartburn, swelling; glucose test 24–28 wk; sudden swelling, severe headache, blurred vision, fewer movements → urgent | acog_nutrition_preg, nhs_get_help, nhs_movements |
| 28–32 | E2/P4 near peak, relaxin | sleep on side (left), track movements daily; reduced movements, bleeding, fluid, regular contractions → maternity unit now | nhs_movements, nhs_get_help |
| 33–36 | body preparing for labour | hospital bag, breathing practice, light meals | nhs_weeks, nhs_movements |
| 37–42 | oxytocin role grows near labour | know labour signs; regular contractions, waters breaking, bleeding, fewer movements → call now | nhs_weeks, nhs_get_help |

## Source list
- `cc_cycle` — Cleveland Clinic: Menstrual cycle: https://my.clevelandclinic.org/health/articles/10132-menstrual-cycle
- `cc_follicular` — Cleveland Clinic: Follicular phase: https://my.clevelandclinic.org/health/body/23953-follicular-phase
- `cc_luteal` — Cleveland Clinic: Luteal phase: https://my.clevelandclinic.org/health/articles/24417-luteal-phase
- `cc_lh` — Cleveland Clinic: Luteinizing hormone: https://my.clevelandclinic.org/health/body/22255-luteinizing-hormone
- `acog_dysmenorrhea` — ACOG: Dysmenorrhea (painful periods): https://www.acog.org/womens-health/faqs/dysmenorrhea-painful-periods
- `acog_heavy` — ACOG: Heavy menstrual bleeding: https://www.acog.org/womens-health/faqs/heavy-menstrual-bleeding
- `pmc_iron_hmb` — Review: iron deficiency in heavy menstrual bleeding (PMC7695235): https://pmc.ncbi.nlm.nih.gov/articles/PMC7695235/
- `acog_pms` — ACOG: Premenstrual syndrome: https://www.acog.org/womens-health/faqs/premenstrual-syndrome
- `mayo_pms` — Mayo Clinic: PMS self care: https://www.mayoclinic.org/diseases-conditions/premenstrual-syndrome/diagnosis-treatment/drc-20376787
- `cc_pms` — Cleveland Clinic: PMS: https://my.clevelandclinic.org/health/diseases/24288-pms-premenstrual-syndrome
- `acog_fabm` — ACOG: Fertility awareness-based methods: https://www.acog.org/womens-health/faqs/fertility-awareness-based-methods-of-family-planning
- `nhs_late_period` — NHS: Stopped or missed periods: https://www.nhs.uk/conditions/stopped-or-missed-periods/
- `nhs_preg_test` — NHS: Pregnancy tests: https://www.nhs.uk/pregnancy/trying-for-a-baby/doing-a-pregnancy-test/
- `nhs_weeks` — NHS: Pregnancy week by week: https://www.nhs.uk/pregnancy/week-by-week/
- `nhs_week20` — NHS: 20 weeks pregnant: https://www.nhs.uk/pregnancy/week-by-week/13-to-27/20-weeks/
- `nhs_foods` — NHS: Foods to avoid in pregnancy: https://www.nhs.uk/pregnancy/keeping-well/foods-to-avoid/
- `nhs_sickness` — NHS: Vomiting and morning sickness: https://www.nhs.uk/pregnancy/common-symptoms/vomiting-and-morning-sickness/
- `nhs_get_help` — NHS: Pregnancy symptoms you need to get help for: https://www.nhs.uk/pregnancy/common-symptoms/pregnancy-symptoms-you-need-to-get-help-for/
- `nhs_movements` — NHS: Your baby's movements: https://www.nhs.uk/pregnancy/keeping-well/your-babys-movements/
- `nhs_vitamins` — NHS: Planning your pregnancy (folic acid 400 µg): https://www.nhs.uk/pregnancy/trying-for-a-baby/planning-your-pregnancy/
- `acog_exercise_preg` — ACOG: Exercise during pregnancy: https://www.acog.org/womens-health/faqs/exercise-during-pregnancy
- `acog_nutrition_preg` — ACOG: Healthy eating during pregnancy: https://www.acog.org/womens-health/faqs/healthy-eating-during-pregnancy
- `rcog_sickness` — RCOG: Pregnancy sickness: https://www.rcog.org.uk/media/jg3ejmlr/pi_pregnancy-sickness_2024-v12.pdf
- `medline_hcg` — MedlinePlus: hCG blood test: https://medlineplus.gov/ency/article/003510.htm
- `who_anc` — WHO: Antenatal care recommendations: https://www.who.int/publications/i/item/9789241549912
