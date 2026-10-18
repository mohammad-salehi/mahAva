package com.mahava.app.health

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mahava.app.MahavaApplication
import com.mahava.app.data.db.ToolRecord
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mahava.app.cycle.CycleSubWindow
import com.mahava.app.util.PersianDigits

/** All edits are durable in Room before network scheduling. Existing sync transports tool records. */
class ToolsRepository(private val app: MahavaApplication) {
    private val db get()=app.database
    private val gson=Gson()
    val records get()=db.toolRecordDao().observeAll()
    suspend fun updateDaily(day:Long, transform:(com.mahava.app.data.db.DailyLogEntity)->com.mahava.app.data.db.DailyLogEntity) {
        require(day<=app.clock.today().toEpochDay())
        db.withTransaction {
            val now=app.clock.nowMillis()
            val old=db.dailyLogDao().getByDay(day) ?: com.mahava.app.data.db.DailyLogEntity(epochDay=day,createdAt=now,updatedAt=now)
            app.repository.upsertDailyLog(transform(old))
        }
        com.mahava.app.sync.DataSync.syncNow(app)
    }
    suspend fun save(id:String, type:String, fields:Any, day:Long?=null) {
        val json=gson.toJson(fields)
        require(json.length<=4000) { "متن بیش از حد طولانی است" }
        db.withTransaction {
            val old=db.toolRecordDao().get(id)
            val now=maxOf(app.clock.nowMillis(),(old?.updatedAt ?: 0)+1)
            db.toolRecordDao().upsert(ToolRecord(id,type,day,json,old?.createdAt ?: now,now))
        }
        com.mahava.app.sync.DataSync.syncNow(app)
    }
    suspend fun deleteDaily(day:Long) {
        db.withTransaction {
            val row=db.dailyLogDao().getByDay(day) ?: return@withTransaction
            val now=maxOf(app.clock.nowMillis(),row.updatedAt+1)
            db.toolRecordDao().upsert(ToolRecord("tombstone_log_$day","sync_tombstone",payloadJson="{\"kind\":\"log\",\"key\":\"$day\"}",createdAt=now,updatedAt=now))
            db.dailyLogDao().deleteByDay(day)
        }
        com.mahava.app.sync.DataSync.syncNow(app)
    }
    suspend fun delete(id:String) {
        db.withTransaction {
            db.toolRecordDao().get(id)?.let { db.toolRecordDao().upsert(it.copy(payloadJson="{}",deletedAt=app.clock.nowMillis(),updatedAt=maxOf(app.clock.nowMillis(),it.updatedAt+1))) }
        }
        com.mahava.app.sync.DataSync.syncNow(app)
    }
    suspend fun regenerateInsights() {
        val periods=db.periodDao().getAll(); val logs=db.dailyLogDao().getAll()
        val fingerprint=ToolsAnalytics.fingerprint(periods,logs)
        val now=app.clock.nowMillis()
        val insights=ToolsAnalytics.analyze(periods,logs,app.clock.today().toEpochDay(),now)
        val desired=insights.map { "insight_${it.relatedMetrics}" }.toSet()
        db.toolRecordDao().getAll().filter { it.type=="insight" && it.id !in desired && it.deletedAt==null }.forEach { delete(it.id) }
        insights.forEach { insight ->
            val id="insight_${insight.relatedMetrics}"
            val old=db.toolRecordDao().get(id)
            val data=gson.toJsonTree(insight).asJsonObject.apply { addProperty("inputFingerprint",fingerprint) }
            if(old==null || JsonParser.parseString(old.payloadJson).asJsonObject.get("inputFingerprint")?.asString!=fingerprint) save(id,"insight",data)
        }
    }
    suspend fun ensureRecommendation(): ToolRecord = generationMutex.withLock {
        val today=app.clock.today().toEpochDay(); val id="recommendation_$today"
        db.toolRecordDao().get(id)?.takeIf { it.deletedAt==null }?.let { return@withLock it }
        val logs=db.dailyLogDao().getAll().filter { it.epochDay<=today }
        val todayLog=logs.find { it.epochDay==today }
        val yesterdayLog=logs.find { it.epochDay==today-1 }
        val profile=db.profileDao().get()
        val periods=db.periodDao().getAll()
        val preg=db.pregnancyDao().getActive()
        // Newest first, so "don't repeat yesterday's category" really compares with yesterday.
        val history=db.toolRecordDao().getAll()
            .filter { it.type=="recommendation" && it.deletedAt==null && (it.epochDay ?: 0)>today-30 && (it.epochDay ?: 0)<today }
            .sortedByDescending { it.epochDay ?: 0 }
            .mapNotNull { runCatching { gson.fromJson(it.payloadJson,DailyRecommendation::class.java) }.getOrNull() }
        val pool=mutableListOf<DailyRecommendation>()
        val now=app.clock.nowMillis()
        val personal=ToolsAnalytics.analyze(periods,logs,today,now)
        val cycle=if(preg==null && profile!=null && periods.isNotEmpty()) runCatching { app.repository.computeCycle(profile,periods) }.getOrNull() else null
        val ctx=cycle?.let { runCatching { com.mahava.app.cycle.CycleDayContextResolver.resolve(it) }.getOrNull() }

        // At 08:00 today is usually empty: sleep entered today is "last night"; otherwise use yesterday's log.
        val sleepToday=todayLog?.let { ToolsAnalytics.sleep(it) }
        val sleepYesterday=yesterdayLog?.let { ToolsAnalytics.sleep(it) }
        if(sleepToday!=null) {
            if(sleepToday<360) pool += DailyRecommendation(
                body="دیشب فقط ${RecText.hours(sleepToday)} خوابیدی؛ امروز اگه می‌تونی بعضی کارها رو به بقیه بسپار، بین کارها کمی استراحت کن و امشب زودتر بخواب.",
                category="sleep",reasonType="personal",
                reasonText="چون تو ثبت امروزت نوشتی دیشب ${RecText.hours(sleepToday)} خوابیدی (کمتر از ۶ ساعت).",
                evidenceKeys="log/$today",generatedAt=now)
        } else if(sleepYesterday!=null && sleepYesterday<360) pool += DailyRecommendation(
            body="تو ثبت دیروزت نوشتی فقط ${RecText.hours(sleepYesterday)} خوابیده بودی؛ امروز اگه می‌تونی برنامه‌ات رو سبک‌تر بچین، بعضی کارها رو به بقیه بسپار و امشب زودتر بخواب.",
            category="sleep",reasonType="personal",
            reasonText="چون تو ثبت دیروزت خوابت ${RecText.hours(sleepYesterday)} بوده (کمتر از ۶ ساعت).",
            evidenceKeys="log/${today-1}",generatedAt=now)
        val energyLog=todayLog?.takeIf { it.energy!=null || ToolsAnalytics.energy(it)!=null } ?: yesterdayLog
        if(energyLog!=null) {
            val score=ToolsAnalytics.energy(energyLog)
            if(energyLog.energy=="low" || score?.let { it<=2 }==true) {
                val whenFa=if(energyLog.epochDay==today) "امروز" else "دیروز"
                val level=score?.let { "${PersianDigits.toPersian(it)} از ۵" } ?: "کم"
                pool += DailyRecommendation(
                    body="$whenFa انرژی‌ات رو کم ثبت کردی ($level)؛ امروز کارهای مهم رو بذار برای وقتی که سرحال‌تری، برنامه رو سبک‌تر بچین و اگه شد از یکی کمک بخواه.",
                    category="energy",reasonType="personal",
                    reasonText="چون $whenFa تو ثبتت انرژی‌ات رو $level زدی.",
                    evidenceKeys="log/${energyLog.epochDay}",generatedAt=now)
            }
        }
        // Past-cycle pattern: only on the days it is about (the week before the next estimated period).
        val daysToPeriod=ctx?.daysUntilCentralPeriod
        if(preg==null && daysToPeriod!=null && daysToPeriod in 0..7) {
            personal.firstOrNull { it.relatedMetrics.startsWith("pms_") }?.let {
                val label=it.title.substringAfter(": ").trim()
                val cycles=PersianDigits.toPersian(it.cycleCount.coerceAtLeast(3))
                val left=PersianDigits.toPersian(daysToPeriod)
                pool += DailyRecommendation(
                    body="تو $cycles چرخهٔ قبلی‌ات، هفتهٔ قبل از پریود «$label» ثبت کردی و الان حدود $left روز تا پریود بعدی مونده؛ اگه امروز هم این حالت رو داری، برنامه‌ات رو سبک‌تر بچین و به خودت سخت نگیر.",
                    category="pattern",reasonType="pattern",
                    reasonText="این الگو از ثبت‌های چرخه‌های قبلی خودت درآمده: در $cycles چرخه، هفتهٔ قبل از پریود «$label» ثبت کردی و الان حدود $left روز تا پریود تخمینی بعدی مونده. این فقط یک مشاهده‌ست، نه تشخیص.",
                    evidenceKeys=it.relatedMetrics,generatedAt=now)
            }
        }
        if(preg!=null) {
            val week=PregnancyService.progressFromEntity(preg,app.clock.today()).contentWeek.coerceIn(1,42)
            val weekFa=PersianDigits.toPersian(week)
            app.scientificContent.fetalWeek(week)?.let { fetal -> pool+=DailyRecommendation(
                body="چون الان هفتهٔ $weekFa بارداری‌ات هستی: ${fetal.careFa}",category="pregnancy",reasonType="pregnancy",
                reasonText="بر اساس تاریخ بارداری‌ای که ثبت کردی، الان هفتهٔ $weekFa هستی.",
                medicalSourceIds=fetal.sourceIds.joinToString(","),generatedAt=now) }
        } else if(cycle!=null && ctx!=null) {
            val care=app.contentRepository.selectCareForToday(ctx,cycle.restriction,null)
            val why=RecText.cycleWhy(ctx)
            care.item.whatYouCanDoFa.orEmpty().lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotBlank() }.forEach { tip ->
                pool+=DailyRecommendation(
                    body=if(why!=null) "$why، $tip" else "یه نکتهٔ کلی دربارهٔ چرخه: $tip",
                    category="cycle",reasonType="cycle",
                    reasonText=if(why!=null) "بر اساس مرحلهٔ تخمینی چرخه‌ات: ${ctx.subWindow.titleFa}${RecText.daysNote(ctx)}. این از روی تاریخ پریودهای ثبت‌شده‌ات حساب شده و تشخیص نیست."
                        else "چون از روی ثبت‌هات هنوز نمی‌شه گفت الان کجای چرخه‌ای، یه نکتهٔ کلی دربارهٔ چرخه اومده.",
                    medicalSourceIds=care.item.sourceIds.orEmpty().joinToString(","),generatedAt=now)
            }
        }
        // General self-care fallbacks for everyone: no symptom, phase or personal pattern is claimed.
        RecText.GENERAL.forEachIndexed { i,tip -> pool+=DailyRecommendation(
            body="یه نکتهٔ کلی برای امروز (برای همه، نه مخصوص تو): $tip",
            category="general_${i%5}",reasonType="general",
            reasonText="این یه پیشنهاد کلی برای مراقبت از خودته و به ثبت‌ها یا چرخه‌ات ربطی نداره؛ توصیهٔ پزشکی نیست.",
            generatedAt=now) }
        val unseen=pool.filter { candidate -> history.none { it.body==candidate.body } }
        val chosen=unseen.firstOrNull { it.category!=history.firstOrNull()?.category } ?: unseen.firstOrNull() ?: pool.last()
        save(id,"recommendation",chosen,today)
        db.toolRecordDao().get(id)!!
    }
    companion object { private val generationMutex=Mutex() }
}

/** Wording for the daily recommendation: every text says why it was given, with real numbers. */
internal object RecText {
    private fun fa(n: Int) = PersianDigits.toPersian(n)
    fun hours(minutes: Int): String {
        val h=minutes/60; val m=minutes%60
        return when {
            h==0 -> "${fa(m)} دقیقه"
            m==0 -> "${fa(h)} ساعت"
            m==30 -> "${fa(h)} ساعت و نیم"
            else -> "${fa(h)} ساعت و ${fa(m)} دقیقه"
        }
    }
    fun daysNote(ctx: com.mahava.app.cycle.CycleDayContext): String {
        val d=ctx.daysUntilCentralPeriod
        val cd=ctx.cycleDay
        return when {
            d!=null && d>0 && cd!=null -> " (روز ${fa(cd)} چرخه، حدود ${fa(d)} روز تا پریود بعدی)"
            d!=null && d>0 -> " (حدود ${fa(d)} روز تا پریود بعدی)"
            cd!=null -> " (روز ${fa(cd)} چرخه)"
            else -> ""
        }
    }
    /** "Because …" opener for a cycle tip, or null when the phase is unknown. */
    fun cycleWhy(ctx: com.mahava.app.cycle.CycleDayContext): String? {
        val d=ctx.daysUntilCentralPeriod
        val cd=ctx.cycleDay?.let { " (روز ${fa(it)} چرخه)" } ?: ""
        val left=d?.takeIf { it>0 }?.let { " (حدود ${fa(it)} روز تا پریود بعدی)" } ?: ""
        return when(ctx.subWindow) {
            CycleSubWindow.MENSTRUATION_EARLY -> "چون روزهای اول پریودت هستی$cd"
            CycleSubWindow.MENSTRUATION_LATE -> "چون روزهای آخر پریودت هستی$cd"
            CycleSubWindow.FOLLICULAR_EARLY -> "چون احتمالاً تازه پریودت تموم شده$cd"
            CycleSubWindow.FOLLICULAR_LATE -> "چون احتمالاً داری به تخمک‌گذاری (آزاد شدن تخمک از تخمدان) نزدیک می‌شی$cd"
            CycleSubWindow.PERI_OVULATORY -> "چون احتمالاً حوالی تخمک‌گذاری (آزاد شدن تخمک از تخمدان) هستی$cd"
            CycleSubWindow.LUTEAL_EARLY -> "چون احتمالاً تازه از تخمک‌گذاری گذشتی و نیمهٔ دوم چرخه‌ات شروع شده$left"
            CycleSubWindow.LUTEAL_MID -> "چون احتمالاً وسط نیمهٔ دوم چرخه‌ای$left"
            CycleSubWindow.LUTEAL_LATE_PREMENSTRUAL -> if (d==0) "چون تو روزهای قبل از پریودی (پریودت احتمالاً همین روزها شروع می‌شه)" else "چون تو روزهای قبل از پریودی$left"
            CycleSubWindow.LATE_PERIOD -> "چون پریودت${d?.takeIf { it<0 }?.let { " حدود ${fa(-it)} روز" } ?: ""} دیر کرده"
            CycleSubWindow.UNKNOWN -> null
        }
    }
    val GENERAL = listOf(
        "چند دقیقه کار رو بذار کنار و چند تا نفس عمیق و آروم بکش.",
        "یه لیوان آب بذار دم دستت که یادت بمونه بنوشی.",
        "اگه می‌تونی چند دقیقه زیر نور روز قدم بزن.",
        "یه میوهٔ تازه به خوراکی‌های امروزت اضافه کن.",
        "چند دقیقه گردن و شونه‌هات رو آروم کش بده.",
        "امشب یه کم زودتر گوشی رو بذار کنار تا راحت‌تر خوابت ببره.",
        "یه وعده از غذای امروزت رو با حوصله و بدون عجله بخور.",
        "یه کار کوچیک که حالت رو خوب می‌کنه برای امروز در نظر بگیر.",
        "کارهای امروزت رو اولویت‌بندی کن؛ لازم نیست همه‌شون امروز انجام بشن.",
        "اگه کاری هست که بقیه هم می‌تونن انجامش بدن، ازشون کمک بخواه.",
        "با یکی که دوستش داری چند دقیقه حرف بزن یا بهش پیام بده.",
        "اگه زیاد نشستی، هر یه ساعت یه بار پاشو و چند قدم راه برو.",
        "یه وقت کوتاه فقط برای خودت کنار بذار، حتی ده دقیقه.",
        "یه چیز خوب که امروز برات پیش اومد رو یادداشت کن.",
        "اگه خسته‌ای، به خودت اجازه بده چند دقیقه استراحت کنی.",
        "یه آهنگ آروم که دوستش داری گوش بده.",
        "امروز کنار غذات یه کم سبزی یا سالاد بذار.",
        "اگه می‌تونی امشب سر یه ساعت ثابت برو تو رختخواب.",
        "یکی از نوشیدنی‌های شیرین امروزت رو با آب یا دمنوش (چای گیاهی) عوض کن.",
        "چند دقیقه پنجره رو باز کن و هوای تازه بخور.",
        "یه دوش آب گرم می‌تونه کمکت کنه آروم‌تر بشی.",
        "شونه‌ها و فکت رو شل کن؛ خیلی وقتا بی‌اینکه بفهمیم سفتشون می‌کنیم.",
        "اگه با گوشی یا کامپیوتر کار می‌کنی، هر از گاهی چشمت رو از صفحه بگیر و به دور نگاه کن.",
        "امروز یه کم بیشتر تکون بخور؛ مثلاً اگه می‌تونی به‌جای آسانسور از پله برو.",
        "یه میان‌وعدهٔ سالم مثل یه مشت آجیل یا یه کاسه ماست دم دستت بذار.",
        "به خودت سخت نگیر؛ امروز هر چقدر تونستی، کافیه.",
        "اگه چیزی فکرت رو مشغول کرده، بنویسش تا ذهنت سبک‌تر بشه.",
        "یه کار عقب‌افتادهٔ کوچیک رو امروز تموم کن و به خودت آفرین بگو.",
        "قبل از خواب چند دقیقه نور اتاق رو کم کن تا بدنت برای خواب آماده بشه.",
        "امروز یه چیزی بخور که هم سالمه هم دوستش داری."
    )
}
