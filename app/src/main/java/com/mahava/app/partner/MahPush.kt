package com.mahava.app.partner

import android.content.Context
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mahava.app.BuildConfig
import com.mahava.app.MahavaApplication
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Push is a wake-up hint only. Health text is fetched from the authenticated server after consent checks. */
object MahPush {
    fun initialize(app:MahavaApplication) {
        if(BuildConfig.MAH_FCM_APP_ID.isBlank()) return
        if(FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app,FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.MAH_FCM_APP_ID).setApiKey(BuildConfig.MAH_FCM_API_KEY)
            .setGcmSenderId(BuildConfig.MAH_FCM_SENDER_ID).setProjectId(BuildConfig.MAH_FCM_PROJECT_ID).build())
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            app.getSharedPreferences("mah_push",Context.MODE_PRIVATE).edit().putString("token",token).apply()
            schedule(app)
        }
    }
    fun schedule(context:Context,eventId:String?=null,recipientId:String?=null) {
        val data=workDataOf("eventId" to eventId,"recipientId" to recipientId)
        WorkManager.getInstance(context).enqueueUniqueWork("mah_push_${eventId ?: "register"}",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MahPushWorker>().setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    suspend fun register(app:MahavaApplication) {
        val sp=app.getSharedPreferences("mah_push",Context.MODE_PRIVATE)
        val token=sp.getString("token",null) ?: return
        if(!app.authRepository.hasSessionTokens()) return
        val owner=app.prefs.accountUserId.first() ?: return
        if(sp.getString("owner",null)==owner && sp.getString("registered_token",null)==token && app.clock.nowMillis()-sp.getLong("registered_at",0)<7*86400000L) return
        val device=sp.getString("device",null) ?: UUID.randomUUID().toString().also {sp.edit().putString("device",it).commit()}
        app.authRepository.withToken {app.apiClient.partnerRegisterDevice(it,device,token)}
        sp.edit().putString("owner",owner).putString("registered_token",token).putLong("registered_at",app.clock.nowMillis()).apply()
    }
}

class MahFirebaseMessagingService:FirebaseMessagingService() {
    override fun onNewToken(token:String) {
        getSharedPreferences("mah_push",Context.MODE_PRIVATE).edit().putString("token",token).apply()
        MahPush.schedule(this)
    }
    override fun onMessageReceived(message:RemoteMessage) {
        val id=message.data["mahEventId"] ?: return
        if(!id.matches(Regex("[a-fA-F0-9]{24}"))) return
        MahPush.schedule(this,id,message.data["recipientId"])
    }
}

class MahPushWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val app=applicationContext as MahavaApplication
        if(!app.authRepository.hasSessionTokens()) return Result.success()
        return try {
            MahPush.register(app)
            val event=inputData.getString("eventId") ?: return Result.success()
            if(app.prefs.accountUserId.first()!=inputData.getString("recipientId")) return Result.success()
            // Revoked, disconnected or wrong-user events are excluded by this API, even if FCM was queued earlier.
            val response=app.authRepository.withToken {app.apiClient.partnerChanges(it,0)}
            val allowed=response.events.orEmpty().filter {it.id==event}
            if(allowed.isNotEmpty()) {
                val status=app.partnerRepository.refreshStatus()
                PartnerSync.process(app,PartnerRepository.Poll(allowed,false,status))
            }
            Result.success()
        } catch(e:Exception) {Result.retry()}
    }
}
