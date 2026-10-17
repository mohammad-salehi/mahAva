package com.mahava.app.health

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mahava.app.MahavaApplication
import com.mahava.app.data.db.ToolRecord
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        val profile=db.profileDao().get()
        val periods=db.periodDao().getAll()
        val preg=db.pregnancyDao().getActive()
        val history=db.toolRecordDao().getAll().filter { it.type=="recommendation" && it.deletedAt==null && (it.epochDay ?: 0)>today-30 }.mapNotNull { runCatching { gson.fromJson(it.payloadJson,DailyRecommendation::class.java) }.getOrNull() }
        val pool=mutableListOf<DailyRecommendation>()
        val now=app.clock.nowMillis()
        val personal=ToolsAnalytics.analyze(periods,logs,today,now)
        if(todayLog!=null) {
            if(todayLog.energy=="low" || ToolsAnalytics.energy(todayLog)?.let { it<=2 }==true) pool += DailyRecommendation(body="امروز انرژی پایینی ثبت کرده‌ای. اگر برایت ممکن است، برنامهٔ امروز را سبک‌تر کن و از یک نفر کمک بخواه.",category="energy",reasonType="personal",reasonText="بر اساس انرژی ثبت‌شدهٔ امروز",evidenceKeys="log/$today",generatedAt=now)
            if(ToolsAnalytics.sleep(todayLog)?.let { it<360 }==true) pool += DailyRecommendation(body="خواب کوتاهی ثبت کرده‌ای؛ اگر امکانش را داری برای استراحت امشب از قبل وقت کنار بگذار.",category="sleep",reasonType="personal",reasonText="بر اساس مدت خواب ثبت‌شدهٔ امروز",evidenceKeys="log/$today",generatedAt=now)
        }
        personal.firstOrNull { preg==null && it.relatedMetrics.startsWith("pms_") }?.let { pool += DailyRecommendation(body="${it.description} اگر امروز هم این حالت را داری، برنامه‌ات را با نیاز واقعی بدنت هماهنگ کن.",category="pattern",reasonType="pattern",reasonText="بر اساس ثبت‌های چرخه‌های کامل قبلی؛ علت را نشان نمی‌دهد",evidenceKeys=it.relatedMetrics,generatedAt=now) }
        if(preg!=null) {
            val week=PregnancyService.progressFromEntity(preg,app.clock.today()).contentWeek.coerceIn(1,42)
            app.scientificContent.fetalWeek(week)?.let { fetal -> pool+=DailyRecommendation(body=fetal.careFa,category="pregnancy",reasonType="pregnancy",reasonText="بر اساس هفتهٔ بارداری از تاریخ ثبت‌شده",medicalSourceIds=fetal.sourceIds.joinToString(","),generatedAt=now) }
        } else if(profile!=null && periods.isNotEmpty()) {
            val cycle=app.repository.computeCycle(profile,periods)
            val context=com.mahava.app.cycle.CycleDayContextResolver.resolve(cycle)
            val care=app.contentRepository.selectCareForToday(context,cycle.restriction,todayLog)
            care.item.whatYouCanDoFa.orEmpty().lines().filter { it.isNotBlank() }.forEach { tip -> pool+=DailyRecommendation(body=tip,category="cycle",reasonType="cycle",reasonText="بر اساس مرحلهٔ تخمینی چرخه؛ نه تشخیص وضعیت تو",medicalSourceIds=care.item.sourceIds.orEmpty().joinToString(","),generatedAt=now) }
        }
        // Non-medical fallbacks; no fabricated symptom, phase or personal pattern.
        listOf("برای استراحت زمانی کنار بگذار","کاری را که امروز می‌توانی به دیگری بسپاری مشخص کن","اگر سؤالی برای پزشک داری آن را در ابزار سؤالات پزشک بنویس","ثبت قبلی‌ات را مرور کن و اگر اشتباهی دارد اصلاحش کن","اگر دوست داری، کیفیت خواب دیشب را ثبت کن","فقط علائمی را ثبت کن که واقعاً تجربه کرده‌ای","یادداشت خصوصی‌ات را برای گفت‌وگوی بعدی با پزشک مرور کن","برای کارهای ضروری امروز اولویت تعیین کن","چند دقیقه کشش آرام برای گردن و شانه‌ها انجام بده","یک میوهٔ تازه به خوراکی امروزت اضافه کن","اگر می‌توانی چند دقیقه زیر نور روز قدم بزن","پیش از خواب چند دقیقه صفحهٔ گوشی را کنار بگذار","چند نفس عمیق و آرام بکش و شانه‌هایت را رها کن","امروز یک وعدهٔ غذایی را با آرامش و بدون عجله بخور","یک لیوان آب کنار دستت بگذار و گاهی از آن بنوش","برای خودت یک کار کوچک خوشایند در نظر بگیر","اگر نیاز به همراهی داری، دربارهٔ آن با فرد مورداعتمادت صحبت کن","بخشی از برنامهٔ امروز را برای خودت نگه دار","اگر اطلاعات روزی را به یاد نمی‌آوری آن را نامشخص بگذار","برای مراجعهٔ بعدی، پرسش مهمت را یادداشت کن","تاریخ‌های پریود ثبت‌شده را از نظر اشتباه بررسی کن","یک فعالیت آرام و دلخواه برای امروز انتخاب کن","می‌توانی پاسخ سؤال پزشک را کنار همان سؤال بنویسی","ثبت یک روز کافی نیست تا الگویی قطعی بسازد","اگر امروز ثبت نمی‌کنی، روز خالی به معنی نبود علامت نیست","پیش از اصلاح تاریخ بارداری، تاریخ مبنا را بررسی کن","در صورت تمایل، حال کلی امروزت را با یک انتخاب ثبت کن","از خودت بپرس امروز چه کمکی نیاز داری","اگر قرار ملاقاتی داری، زمانش را در ابزار مراجعه‌ها ذخیره کن","برای مرور روندها به ثبت‌های واقعی چند چرخه نگاه کن").forEachIndexed { i,tip -> pool+=DailyRecommendation(body="$tip.",category="general_${i%5}",reasonType="general",reasonText="یک پیشنهاد عمومی برای مراقبت از خودت و ثبت بهتر؛ توصیهٔ پزشکی شخصی نیست",generatedAt=now) }
        val unseen=pool.filter { candidate -> history.none { it.body==candidate.body } }
        val chosen=unseen.firstOrNull { it.category!=history.firstOrNull()?.category } ?: unseen.firstOrNull() ?: pool.last()
        save(id,"recommendation",chosen,today)
        db.toolRecordDao().get(id)!!
    }
    companion object { private val generationMutex=Mutex() }
}
