package com.mahava.app.demo

import android.content.Context
import com.google.gson.Gson
import com.mahava.app.BuildConfig
import com.mahava.app.MahavaApplication
import com.mahava.app.data.db.*
import com.mahava.app.health.DailyRecommendation
import com.mahava.app.network.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.random.Random

/**
 * Debug-only demo: active only when the build sets BuildConfig.MAH_DEMO (an uncommitted init
 * script does that for the ".demo" package). Seeds a female premium account with fake data,
 * works offline (the API client refuses all requests in demo) and can preview the husband view.
 * This file lives in src/debug, so release builds use the no-op stub.
 */
object DemoMode {
    val enabled: Boolean get() = BuildConfig.MAH_DEMO
    private const val MARK = "demo_seeded_v1"
    private val gson = Gson()

    fun onAppStart(app: MahavaApplication) {
        if (!enabled) return
        val sp = app.getSharedPreferences("mahava_demo", Context.MODE_PRIVATE)
        if (sp.getBoolean(MARK, false)) return
        runBlocking { seed(app) }
        sp.edit().putBoolean(MARK, true).apply()
    }

    /** mahava://demo/<husband|wife|pregnancy|cycle|reseed> */
    suspend fun handle(app: MahavaApplication, path: String): Boolean {
        if (!enabled) return false
        when (path) {
            "husband" -> { writePartner(app, husband = true); app.prefs.setAccountRole("male") }
            "wife" -> { app.prefs.setAccountRole("female"); writePartner(app, husband = false) }
            "pregnancy" -> {
                val t = app.clock.today()
                if (app.repository.getActivePregnancy() == null)
                    app.repository.upsertPregnancy(PregnancyEntity(lmpEpochDay = t.minusDays(98).toEpochDay(), estimatedDueEpochDay = t.minusDays(98).plusDays(280).toEpochDay(), createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                app.repository.saveProfile(app.repository.ensureProfile().copy(pregnancyMode = true, fertilityTrackingEnabled = false))
            }
            "cycle" -> {
                app.repository.getActivePregnancy()?.let { app.repository.endPregnancy(it.id, "demo") }
                app.repository.saveProfile(app.repository.ensureProfile().copy(pregnancyMode = false, fertilityTrackingEnabled = true))
            }
            "reseed" -> seed(app)
            else -> return false
        }
        return true
    }

    private suspend fun seed(app: MahavaApplication) {
        val repo = app.repository
        val t = app.clock.today()
        val now = System.currentTimeMillis()
        repo.deleteAllData()
        // Six real cycles; today is day 12 of the current one.
        val lengths = listOf(29, 27, 31, 28, 30, 28)
        val bleeds = listOf(5, 6, 4, 5, 5, 6)
        val current = t.minusDays(11)
        val starts = mutableListOf(current)
        lengths.reversed().forEach { starts.add(0, starts.first().minusDays(it.toLong())) }
        repo.saveProfile(UserProfileEntity(
            onboardingDone = true, goal = "track_period", lastPeriodStartEpochDay = current.toEpochDay(),
            typicalCycleLength = 29, typicalBleedLength = 5, regularCycles = true, fertilityTrackingEnabled = true,
            qaSampleData = false, createdAt = now, updatedAt = now
        ))
        starts.forEachIndexed { i, s ->
            val b = bleeds.getOrElse(i) { 5 }
            repo.upsertPeriod(PeriodEventEntity(startEpochDay = s.toEpochDay(), endEpochDay = s.plusDays(b - 1L).toEpochDay(), stillOngoing = false, createdAt = now, updatedAt = now))
        }
        val rnd = Random(42)
        val notes = listOf("امروز پیاده‌روی کردم و حالم بهتر شد.", "کمی استرس کاری داشتم.", "چای بابونه کمک کرد.", "خواب خوبی نداشتم.", "با دوستم بیرون رفتم.")
        var d = starts.first()
        while (!d.isAfter(t)) {
            val ci = starts.indexOfLast { !it.isAfter(d) }
            val start = starts[ci]
            val len = lengths.getOrElse(ci) { 29 }
            val bleed = bleeds.getOrElse(ci) { 5 }
            val cd = (d.toEpochDay() - start.toEpochDay()).toInt() + 1
            val logged = d == t || rnd.nextFloat() < 0.82f
            if (logged) {
                val ov = len - 14
                val phase = when { cd <= bleed -> "m"; cd > len - 5 -> "pre"; cd in (ov - 5)..(ov + 1) -> "f"; cd < ov - 5 -> "fol"; else -> "lut" }
                val pain = when (phase) { "m" -> if (cd <= 2) 5 + rnd.nextInt(3) else 2 + rnd.nextInt(3); "pre" -> if (rnd.nextFloat() < 0.5f) 2 + rnd.nextInt(3) else 0; else -> if (rnd.nextFloat() < 0.1f) 2 else 0 }
                val sym = mutableSetOf<String>()
                if (pain > 0) sym += "pain"
                when (phase) {
                    "m" -> { sym += "cramps"; if (rnd.nextBoolean()) sym += "back_pain"; if (rnd.nextFloat() < 0.4f) sym += "fatigue" }
                    "pre" -> { if (rnd.nextFloat() < 0.75f) sym += "bloating"; if (rnd.nextFloat() < 0.6f) sym += "breast_tenderness"; if (rnd.nextFloat() < 0.4f) sym += "headache"; if (rnd.nextFloat() < 0.3f) sym += "acne" }
                    "lut" -> if (rnd.nextFloat() < 0.25f) sym += "bloating"
                }
                val moods = when (phase) {
                    "m" -> listOf("sensitive", "calm", "bored").shuffled(rnd).take(1)
                    "pre" -> listOf("irritable", "anxious", "sad", "sensitive").shuffled(rnd).take(1 + rnd.nextInt(2))
                    "f" -> listOf("happy", "focused")
                    "fol" -> listOf("happy", "calm", "focused").shuffled(rnd).take(1 + rnd.nextInt(2))
                    else -> listOf("calm", "happy", "bored").shuffled(rnd).take(1)
                }
                val energy = (when (phase) { "m" -> 2; "pre" -> 2; "f" -> 5; "fol" -> 4; else -> 3 } + listOf(-1, 0, 0, 1).random(rnd)).coerceIn(1, 5)
                val feeling = when (energy) { 1 -> "bad"; 2 -> "ordinary"; 3 -> "good"; 4 -> "good"; else -> "great" }.let { if (phase == "pre" && rnd.nextFloat() < 0.4f) "bad" else it }
                val sleep = (when (phase) { "pre" -> 6.0f; "m" -> 7.5f; else -> 7.0f } + listOf(-1f, -0.5f, 0f, 0.5f, 1f).random(rnd)).coerceIn(4f, 10f)
                val bleeding = if (phase == "m") when (cd) { 1 -> "medium"; 2 -> "heavy"; 3 -> "medium"; else -> "light" } else if (cd == bleed + 1 && rnd.nextBoolean()) "spotting" else "none"
                repo.upsertDailyLog(DailyLogEntity(
                    epochDay = d.toEpochDay(), bleeding = bleeding, noSymptoms = sym.isEmpty(),
                    painScore = pain.takeIf { it > 0 || rnd.nextFloat() < 0.3f },
                    painLocations = if (pain > 0) (if (phase == "m") "lower_abdomen,back" else if ("headache" in sym) "head" else "lower_abdomen") else null,
                    painType = if (pain > 0) (if ("headache" in sym && phase != "m") "dull" else "cramping") else null,
                    painDurationMinutes = if (pain > 0) listOf(30, 120, 300).random(rnd) else null,
                    painAction = if (pain >= 5) "heat,painkiller" else if (pain > 0) listOf("rest", "heat", "walk").random(rnd) else null,
                    painResult = if (pain > 0) listOf("better", "better", "same").random(rnd) else null,
                    physicalSymptoms = sym.joinToString(",").ifBlank { null },
                    moods = moods.joinToString(","), overallFeeling = feeling, energyScore = energy,
                    energy = if (energy <= 2) "low" else if (energy == 3) "medium" else "high",
                    sleepMinutes = Math.round(sleep * 60), sleepHours = sleep,
                    sleepQuality = if (sleep >= 7.5f) "good" else if (sleep >= 6.5f) "ok" else "poor",
                    discharge = when (phase) { "f" -> "eggwhite"; "fol" -> "sticky"; "lut" -> "creamy"; "pre" -> "creamy"; else -> null },
                    waterIntakeMl = listOf(1500, 2000, 2500).random(rnd),
                    appetite = if (phase == "pre") "high" else "normal",
                    foodCravings = if (phase == "pre" && rnd.nextBoolean()) "sweet" else null,
                    note = if (rnd.nextFloat() < 0.12f) notes.random(rnd) else null,
                    createdAt = now, updatedAt = now
                ))
            }
            d = d.plusDays(1)
        }
        val dao = app.database.toolRecordDao()
        fun tool(id: String, type: String, day: Long?, payload: Any) = ToolRecord(id, type, day, gson.toJson(payload), now, now)
        listOf(
            tool("doctor_demo1", "doctor", t.minusDays(20).toEpochDay(), mapOf("text" to "درد روز اول پریودم با مسکن کم می‌شود؛ این طبیعی است؟", "done" to true, "answer" to "دکتر گفت اگر با مسکن بهتر می‌شود معمولاً طبیعی است.")),
            tool("doctor_demo2", "doctor", t.minusDays(5).toEpochDay(), mapOf("text" to "نفخ قبل از پریود را چطور کمتر کنم؟", "done" to false)),
            tool("doctor_demo3", "doctor", t.toEpochDay(), mapOf("text" to "آزمایش آهن لازم دارم؟", "done" to false)),
            tool("appointments_demo1", "appointments", t.minusDays(40).toEpochDay(), mapOf("text" to "زنان — دکتر احمدی، کلینیک بهار", "date" to t.minusDays(40).toString(), "done" to true)),
            tool("appointments_demo2", "appointments", t.plusDays(9).toEpochDay(), mapOf("text" to "سونوگرافی دوره‌ای", "date" to t.plusDays(9).toString(), "done" to false))
        ).forEach { dao.upsert(it) }
        val recs = listOf(
            "امروز ۱۰ دقیقه پیاده‌روی آرام کن." to "در روزهای بعد از پریود انرژی‌ات معمولاً بیشتر است.",
            "یک لیوان آب بیشتر از همیشه بنوش." to "در ثبت‌هایت روزهای کم‌آبی با سردرد همراه بوده‌اند.",
            "امشب کمی زودتر بخواب." to "پیش از پریود خوابت کمتر از ۷ ساعت ثبت شده.",
            "کیسهٔ آب گرم برای درد زیر شکم کمک می‌کند." to "در دو روز اول پریود درد بیشتری ثبت کرده‌ای.",
            "یک وعدهٔ سبک با سبزیجات بخور." to "پیش از پریود نفخ در ثبت‌هایت پرتکرار است."
        )
        (1..5).forEach { back ->
            val day = t.minusDays(back.toLong()).toEpochDay()
            val (body, why) = recs[back - 1]
            dao.upsert(tool("recommendation_$day", "recommendation", day, DailyRecommendation(body = body, category = "demo_$back", reasonType = "pattern", reasonText = why, generatedAt = now, shown = true, opened = back > 2)))
        }
        app.database.reminderDao().upsert(ReminderPrefEntity(id = "daily_log", enabled = true, hour = 21, minute = 0, updatedAt = now))
        runCatching { app.toolsRepository.regenerateInsights() }
        runCatching { app.toolsRepository.ensureRecommendation() }
        // Demo account: female, premium, "logged in" with placeholder tokens (never sent anywhere).
        app.prefs.setAccount("09120000000", "demo-user")
        app.prefs.setAccessToken("demo")
        app.prefs.setRefreshToken("demo")
        app.prefs.setAccountRole("female")
        app.prefs.setServerSubscription(true, "yearly", t.plusYears(1).toString() + "T00:00:00Z", 0)
        writePartner(app, husband = false)
    }

    /** A paired husband; the "share" is built from her local data exactly like the server would return it. */
    private suspend fun writePartner(app: MahavaApplication, husband: Boolean) {
        val pair = PartnerPairDto(id = "demo-pair", status = "active", myRole = if (husband) "male" else "female",
            requestedAt = "2026-05-01T10:00:00Z", approvedAt = "2026-05-01T10:05:00Z", partner = PartnerInfoDto(if (husband) "0912***0000" else "0935***4412"))
        app.prefs.setPartnerStatusJson(gson.toJson(PartnerStatusDto(ok = true, role = if (husband) "male" else "female", pair = pair)))
        val snap = runCatching { app.partnerRepository.buildSnapshot() }.getOrNull()
        val logs = app.database.dailyLogDao().getAll().sortedByDescending { it.epochDay }.take(120).map { gson.toJsonTree(it).asJsonObject }
        val periods = app.database.periodDao().getAll().sortedByDescending { it.startEpochDay }.map { gson.toJsonTree(it).asJsonObject }
        val tools = app.database.toolRecordDao().getAll().filter { it.deletedAt == null && it.type in setOf("doctor", "appointments", "pregnancy") }
            .map { PartnerToolDto(it.id, it.type, it.epochDay, it.payloadJson) }
        app.prefs.setPartnerSnapshotJson(gson.toJson(PartnerShareDto(ok = true, snapshot = snap, logs = logs, periods = periods, tools = tools,
            updatedAt = java.time.Instant.now().toString(), version = 1)))
    }
}
