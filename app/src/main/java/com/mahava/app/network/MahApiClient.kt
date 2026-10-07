package com.mahava.app.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mahava.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTP client for /api/mah — no OkHttp dependency (offline Gradle friendly).
 */
class MahApiClient(
    private var baseUrl: String = BuildConfig.MAH_API_BASE_URL,
    private val gson: Gson = Gson()
) {
    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun currentBaseUrl(): String = baseUrl.trimEnd('/')

    suspend fun register(phone: String, password: String, name: String?, role: String? = null): MahAuthResponse =
        post("/api/mah/auth/register", mapOf(
            "phone" to phone,
            "password" to password,
            "name" to (name ?: ""),
            "role" to (role ?: "")
        ))

    suspend fun login(phone: String, password: String): MahAuthResponse =
        post("/api/mah/auth/login", mapOf("phone" to phone, "password" to password))

    suspend fun refresh(refreshToken: String): MahAuthResponse =
        post("/api/mah/auth/refresh", mapOf("refreshToken" to refreshToken))

    suspend fun me(accessToken: String): MahAuthResponse =
        get("/api/mah/auth/me", accessToken)

    suspend fun logout(accessToken: String): MahAuthResponse =
        post("/api/mah/auth/logout", emptyMap(), accessToken)

    suspend fun subscriptionStatus(accessToken: String): MahSubscriptionDto {
        val raw = request("GET", "/api/mah/subscription/status", null, accessToken)
        val obj = gson.fromJson(raw, JsonObject::class.java)
        if (obj.get("ok")?.asBoolean == false) {
            throw MahApiException(400, obj.get("message")?.asString ?: "خطا")
        }
        return MahSubscriptionDto(
            hasActiveSubscription = obj.get("hasActiveSubscription")?.asBoolean ?: false,
            endsAt = obj.get("endsAt")?.takeIf { !it.isJsonNull }?.asString,
            plan = obj.get("plan")?.asString ?: "none",
            priceYearlyTomans = obj.get("priceYearlyTomans")?.asInt ?: 585000,
            paymentGatewayReady = obj.get("paymentGatewayReady")?.asBoolean ?: false,
            ownSubscriptionActive = obj.get("ownSubscriptionActive")?.takeIf { !it.isJsonNull }?.asBoolean
                ?: (obj.get("hasActiveSubscription")?.asBoolean ?: false),
            sharedFromPartner = obj.get("sharedFromPartner")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        )
    }

    suspend fun plans(): MahPlansResponse {
        val raw = request("GET", "/api/mah/subscription/plans", null, null)
        return gson.fromJson(raw, MahPlansResponse::class.java)
    }

    suspend fun appConfig(versionCode: Int): MahAppConfigResponse {
        val raw = request("GET", "/api/mah/app-config?versionCode=$versionCode", null, null)
        return gson.fromJson(raw, MahAppConfigResponse::class.java)
    }

    suspend fun inbox(accessToken: String): MahInboxResponse {
        val raw = request("GET", "/api/mah/inbox", null, accessToken)
        return gson.fromJson(raw, MahInboxResponse::class.java)
    }

    suspend fun dismissInbox(accessToken: String, id: String) {
        request("POST", "/api/mah/inbox/$id/dismiss", emptyMap(), accessToken)
    }

    // ---- Partner pairing (/api/mah/partner) ----

    suspend fun partnerStatus(token: String): PartnerStatusDto =
        gson.fromJson(request("GET", "/api/mah/partner/status", null, token), PartnerStatusDto::class.java)

    suspend fun partnerSetRole(token: String, role: String): PartnerStatusDto =
        gson.fromJson(request("POST", "/api/mah/partner/role", mapOf("role" to role), token), PartnerStatusDto::class.java)

    suspend fun partnerCreateCode(token: String): PartnerCodeDto =
        gson.fromJson(request("POST", "/api/mah/partner/code", emptyMap(), token), PartnerCodeDto::class.java)

    suspend fun partnerRedeem(token: String, code: String): PartnerPairResponse =
        gson.fromJson(request("POST", "/api/mah/partner/redeem", mapOf("code" to code), token), PartnerPairResponse::class.java)

    suspend fun partnerRespond(token: String, pairId: String, approve: Boolean): PartnerPairResponse =
        gson.fromJson(
            request("POST", "/api/mah/partner/respond", mapOf("pairId" to pairId, "approve" to approve), token),
            PartnerPairResponse::class.java
        )

    suspend fun partnerUnpair(token: String) {
        request("POST", "/api/mah/partner/unpair", emptyMap(), token)
    }

    suspend fun partnerPutShare(token: String, snapshot: PartnerSnapshotDto) {
        request("PUT", "/api/mah/partner/share", mapOf("snapshot" to snapshot), token)
    }

    suspend fun partnerGetShare(token: String): PartnerShareDto =
        gson.fromJson(request("GET", "/api/mah/partner/share", null, token), PartnerShareDto::class.java)

    suspend fun partnerChanges(token: String, sinceMs: Long): PartnerChangesDto =
        gson.fromJson(request("GET", "/api/mah/partner/changes?since=$sinceMs", null, token), PartnerChangesDto::class.java)

    // ---- Full data sync (/api/mah/data) ----

    suspend fun dataPull(token: String, since: Long): DataPullDto =
        gson.fromJson(request("GET", "/api/mah/data?since=$since", null, token), DataPullDto::class.java)

    suspend fun dataPush(token: String, changes: List<com.mahava.app.sync.SyncRecord>): DataPushDto {
        val body = mapOf(
            "changes" to changes.map {
                mapOf("kind" to it.kind, "key" to it.key, "data" to it.data, "deleted" to it.deleted, "updatedAt" to it.updatedAt)
            }
        )
        return gson.fromJson(request("POST", "/api/mah/data/sync", body, token), DataPushDto::class.java)
    }

    suspend fun dataWipe(token: String) {
        request("DELETE", "/api/mah/data", null, token)
    }

    suspend fun deleteAccount(token: String, password: String) {
        request("POST", "/api/mah/auth/delete-account", mapOf("password" to password), token)
    }

    private suspend fun post(path: String, body: Map<String, Any?>, token: String? = null): MahAuthResponse {
        val raw = request("POST", path, body, token)
        return gson.fromJson(raw, MahAuthResponse::class.java)
    }

    private suspend fun get(path: String, token: String): MahAuthResponse {
        val raw = request("GET", path, null, token)
        return gson.fromJson(raw, MahAuthResponse::class.java)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: Map<String, Any?>?,
        token: String?
    ): String = withContext(Dispatchers.IO) {
        val url = URL(currentBaseUrl() + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            doInput = true
            if (body != null) {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(gson.toJson(body)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: "{}"
        if (code !in 200..299) {
            val msg = try {
                gson.fromJson(text, JsonObject::class.java).get("message")?.asString
            } catch (_: Throwable) { null }
            throw MahApiException(code, msg ?: "خطای شبکه ($code)")
        }
        text
    }
}
