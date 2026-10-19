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

/** Full restore/upload over a slow link or VPN can take longer than a normal call. */
private const val DATA_READ_TIMEOUT_MS = 60_000

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

    suspend fun requestOtp(phone: String, purpose: String): MahOtpResponse {
        val raw = request("POST", "/api/mah/auth/otp/request", mapOf("phone" to phone, "purpose" to purpose), null)
        return gson.fromJson(raw, MahOtpResponse::class.java)
    }

    suspend fun verifyOtp(phone: String, purpose: String, otp: String): MahOtpVerifyResponse {
        val raw = request(
            "POST",
            "/api/mah/auth/otp/verify",
            mapOf("phone" to phone, "purpose" to purpose, "otp" to otp),
            null
        )
        return gson.fromJson(raw, MahOtpVerifyResponse::class.java)
    }

    suspend fun register(
        phone: String,
        password: String,
        role: String? = null,
        otp: String,
        acceptedPrivacy: Boolean,
        acceptedTerms: Boolean,
        privacyVersion: String,
        termsVersion: String
    ): MahAuthResponse =
        post(
            "/api/mah/auth/register",
            mapOf(
                "phone" to phone,
                "password" to password,
                "role" to (role ?: ""),
                "otp" to otp,
                "acceptedPrivacy" to acceptedPrivacy,
                "acceptedTerms" to acceptedTerms,
                "privacyVersion" to privacyVersion,
                "termsVersion" to termsVersion
            )
        )

    suspend fun resetPassword(phone: String, otp: String, newPassword: String): MahSimpleOkResponse {
        val raw = request(
            "POST",
            "/api/mah/auth/password/reset",
            mapOf("phone" to phone, "otp" to otp, "newPassword" to newPassword),
            null
        )
        return gson.fromJson(raw, MahSimpleOkResponse::class.java)
    }

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
            priceYearlyTomans = obj.get("priceYearlyTomans")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
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

    /** Activate yearly plan after Cafe Bazaar IAB purchase (server verifies RSA signature). */
    suspend fun claimBazaarPurchase(token: String, purchase: Map<String, Any?>): JsonObject {
        val raw = request("POST", "/api/mah/subscription/bazaar/claim", purchase, token)
        return gson.fromJson(raw, JsonObject::class.java)
    }

    suspend fun discountPending(token: String): MahDiscountPreviewDto {
        val raw = request("GET", "/api/mah/subscription/discount/pending", null, token)
        return gson.fromJson(raw, MahDiscountPreviewDto::class.java)
    }

    suspend fun attachDiscount(token: String, code: String): MahDiscountPreviewDto {
        val raw = request("POST", "/api/mah/subscription/discount/attach", mapOf("code" to code), token)
        return gson.fromJson(raw, MahDiscountPreviewDto::class.java)
    }

    suspend fun clearDiscount(token: String): MahSimpleOkResponse {
        val raw = request("POST", "/api/mah/subscription/discount/clear", emptyMap(), token)
        return gson.fromJson(raw, MahSimpleOkResponse::class.java)
    }

    suspend fun redeemDiscount(token: String, code: String): JsonObject {
        val raw = request("POST", "/api/mah/subscription/discount/redeem", mapOf("code" to code), token)
        return gson.fromJson(raw, JsonObject::class.java)
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

    suspend fun partnerGetShare(token: String): PartnerShareDto {
        val raw = request("GET", "/api/mah/partner/share", null, token)
        return parsePartnerShare(raw)
    }

    /** Lenient parse: a bad `updatedAt` / log field must not wipe the whole share. */
    internal fun parsePartnerShare(raw: String): PartnerShareDto {
        val root = try {
            gson.fromJson(raw, JsonObject::class.java)
        } catch (_: Throwable) {
            throw MahApiException(500, "پاسخ سرور قابل خواندن نیست")
        } ?: throw MahApiException(500, "پاسخ سرور خالی است")
        val snapEl = root.get("snapshot")
        val snapshot = if (snapEl != null && snapEl.isJsonObject) {
            try {
                // Gson can choke on today.log:null when the field type is JsonObject.
                val obj = snapEl.asJsonObject.deepCopy()
                obj.get("today")?.takeIf { it.isJsonObject }?.asJsonObject?.let { today ->
                    val log = today.get("log")
                    if (log == null || log.isJsonNull || !log.isJsonObject) today.remove("log")
                }
                gson.fromJson(obj, PartnerSnapshotDto::class.java)
            } catch (_: Throwable) {
                null
            }
        } else null
        fun arr(name: String): List<JsonObject>? {
            val a = root.get(name) ?: return null
            if (!a.isJsonArray) return null
            return a.asJsonArray.mapNotNull { el -> if (el != null && el.isJsonObject) el.asJsonObject else null }
        }
        val updated = root.get("updatedAt")?.let { el ->
            when {
                el.isJsonNull -> null
                el.isJsonPrimitive -> el.asJsonPrimitive.asString
                else -> null
            }
        }
        return PartnerShareDto(
            ok = root.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: true,
            snapshot = snapshot,
            logs = arr("logs"),
            periods = arr("periods"),
            tools = root.get("tools")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
                try { if (el.isJsonObject) gson.fromJson(el, PartnerToolDto::class.java) else null }
                catch (_: Throwable) { null }
            },
            permissions = root.get("permissions")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()
                ?.mapNotNull { (key, value) ->
                    try { if (value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) key to value.asBoolean else null }
                    catch (_: Throwable) { null }
                }?.toMap(),
            updatedAt = updated,
            version = root.get("version")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0
        )
    }

    suspend fun partnerChanges(token: String, sinceMs: Long): PartnerChangesDto =
        gson.fromJson(request("GET", "/api/mah/partner/changes?since=$sinceMs", null, token), PartnerChangesDto::class.java)

    // ---- Full data sync (/api/mah/data) ----

    suspend fun partnerRegisterDevice(token:String,deviceId:String,pushToken:String) {
        request("POST","/api/mah/partner/device",mapOf("deviceId" to deviceId,"token" to pushToken),token)
    }

    suspend fun dataPull(token: String, since: Long): DataPullDto =
        gson.fromJson(requestRaw("GET", "/api/mah/data?since=$since", null, token, DATA_READ_TIMEOUT_MS), DataPullDto::class.java)

    suspend fun dataPush(token: String, changes: List<com.mahava.app.sync.SyncRecord>): DataPushDto {
        // Build a real JsonObject so nested record `data` is not lost (Map+Any + Gson can drop JsonObject).
        val root = JsonObject()
        val arr = com.google.gson.JsonArray()
        changes.forEach { c ->
            arr.add(JsonObject().apply {
                addProperty("kind", c.kind)
                addProperty("key", c.key)
                addProperty("deleted", c.deleted)
                addProperty("updatedAt", c.updatedAt)
                if (c.deleted || c.data == null) add("data", com.google.gson.JsonNull.INSTANCE)
                else add("data", c.data)
            })
        }
        root.add("changes", arr)
        return gson.fromJson(requestRaw("POST", "/api/mah/data/sync", gson.toJson(root), token, DATA_READ_TIMEOUT_MS), DataPushDto::class.java)
    }

    suspend fun dataWipe(token: String) {
        request("DELETE", "/api/mah/data", null, token)
    }

    suspend fun deleteAccount(token: String, password: String) {
        request("POST", "/api/mah/auth/delete-account", mapOf("password" to password), token)
    }

    suspend fun listTickets(token: String): MahTicketsResponse =
        gson.fromJson(request("GET", "/api/mah/tickets", null, token), MahTicketsResponse::class.java)

    suspend fun createTicket(token: String, subject: String, body: String): MahTicketsResponse =
        gson.fromJson(
            request("POST", "/api/mah/tickets", mapOf("subject" to subject, "body" to body), token),
            MahTicketsResponse::class.java
        )

    suspend fun replyTicket(token: String, id: String, body: String): MahTicketsResponse =
        gson.fromJson(
            request("POST", "/api/mah/tickets/$id/reply", mapOf("body" to body), token),
            MahTicketsResponse::class.java
        )

    suspend fun reportAppLog(token: String, message: String, detail: String = "", level: String = "error") {
        request(
            "POST",
            "/api/mah/tickets/logs",
            mapOf(
                "message" to message,
                "detail" to detail,
                "level" to level,
                "source" to "android",
                "appVersion" to BuildConfig.VERSION_NAME
            ),
            token
        )
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
    ): String = requestRaw(method, path, body?.let { gson.toJson(it) }, token)

    private suspend fun requestJson(
        method: String,
        path: String,
        body: JsonObject,
        token: String?
    ): String = requestRaw(method, path, gson.toJson(body), token)

    private suspend fun requestRaw(
        method: String,
        path: String,
        bodyJson: String?,
        token: String?,
        readTimeoutMs: Int = 20000
    ): String = withContext(Dispatchers.IO) {
        // The offline demo build never talks to a server.
        if (BuildConfig.MAH_DEMO) throw java.io.IOException("demo build is offline")
        val url = URL(currentBaseUrl() + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty(com.mahava.app.update.AppUpdateState.HEADER_CODE, BuildConfig.VERSION_CODE.toString())
            setRequestProperty(com.mahava.app.update.AppUpdateState.HEADER_NAME, BuildConfig.VERSION_NAME)
            doInput = true
            if (bodyJson != null) {
                doOutput = true
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(bodyJson) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: "{}"
        if (code == 426) com.mahava.app.update.AppUpdateState.onUpgradeRequired(text)
        if (code !in 200..299) {
            val obj = try {
                gson.fromJson(text, JsonObject::class.java)
            } catch (_: Throwable) { null }
            val msg = try { obj?.get("message")?.asString } catch (_: Throwable) { null }
            val retryAfter = (try { obj?.get("retryAfterSec")?.asInt } catch (_: Throwable) { null })
                ?: conn.getHeaderField("Retry-After")?.trim()?.toIntOrNull()
            throw MahApiException(
                code,
                msg ?: "خطای سرور (کد ${com.mahava.app.util.PersianDigits.toPersian(code.toString())})",
                retryAfter?.takeIf { it > 0 }
            )
        }
        text
    }
}
