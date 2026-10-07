package com.mahava.app.data.auth

import com.mahava.app.BuildConfig
import com.mahava.app.data.prefs.UserPreferences
import com.mahava.app.network.MahApiClient
import com.mahava.app.network.MahApiException
import com.mahava.app.network.MahAuthResponse
import com.mahava.app.network.MahSubscriptionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AuthRepository(
    private val api: MahApiClient,
    private val prefs: UserPreferences
) {
    val isLoggedIn: Flow<Boolean> = prefs.accessToken.combine(prefs.refreshToken) { a, r ->
        !a.isNullOrBlank() && !r.isNullOrBlank()
    }

    /**
     * Premium features require a logged-in session (access+refresh tokens) AND
     * an active server subscription. Local demo unlock only counts in DEBUG builds,
     * and still requires login tokens — never a bare local flag alone.
     */
    val isPremiumEffective: Flow<Boolean> = combine(
        isLoggedIn,
        prefs.serverHasActiveSubscription,
        prefs.isPremium
    ) { loggedIn, server, local ->
        loggedIn && (server || (BuildConfig.DEBUG && local))
    }

    suspend fun hasSessionTokens(): Boolean {
        val a = prefs.getAccessToken()
        val r = prefs.getRefreshToken()
        return !a.isNullOrBlank() && !r.isNullOrBlank()
    }

    /**
     * On premium feature use: ensure tokens exist and still work.
     * Returns false if missing/expired/invalid (caller should send user to login).
     * On 401 clears auth so gates flip immediately.
     */
    suspend fun verifySessionOrClear(): Boolean {
        if (!hasSessionTokens()) return false
        return try {
            val me = fetchMe()
            if (me != null) {
                refreshSubscription()
                return true
            }
            // fetchMe null: could be offline or 401-after-refresh-fail (clearAuth not always called).
            // Only clear when refresh also fails AND we get an auth error.
            if (refreshIfNeeded()) {
                refreshSubscription()
                true
            } else {
                // Keep tokens on network blips; premium Flow still needs tokens+sub.
                // If refresh token is dead, refreshIfNeeded returns false — drop session.
                val still = hasSessionTokens()
                if (!still) return false
                // Probe subscription; MahApiException 401 clears via refresh path inside.
                val sub = refreshSubscription()
                sub != null || prefs.serverHasActiveSubscription.first() || (BuildConfig.DEBUG && prefs.isPremium.first())
            }
        } catch (e: MahApiException) {
            if (e.status == 401) {
                prefs.clearAuth()
                false
            } else {
                // Network / server error: do not log the user out.
                hasSessionTokens()
            }
        } catch (_: Throwable) {
            hasSessionTokens()
        }
    }

    suspend fun isPremiumEffectiveNow(): Boolean {
        if (!hasSessionTokens()) return false
        val serverActive = prefs.serverHasActiveSubscription.first()
        val local = prefs.isPremium.first()
        return serverActive || (BuildConfig.DEBUG && local)
    }

    suspend fun register(phone: String, password: String, name: String?, role: String? = null): MahAuthResponse {
        val res = api.register(phone, password, name, role)
        persistSession(res)
        return res
    }

    suspend fun login(phone: String, password: String): MahAuthResponse {
        val res = api.login(phone, password)
        persistSession(res)
        return res
    }

    /** Run an authed call; on 401 refresh the session once and retry. */
    suspend fun <T> withToken(block: suspend (String) -> T): T {
        val token = prefs.getAccessToken() ?: throw MahApiException(401, "اول وارد حساب شو")
        return try {
            block(token)
        } catch (e: MahApiException) {
            if (e.status == 401 && refreshIfNeeded()) {
                block(prefs.getAccessToken() ?: throw e)
            } else throw e
        }
    }

    suspend fun refreshIfNeeded(): Boolean {
        val refresh = prefs.getRefreshToken() ?: return false
        return try {
            val res = api.refresh(refresh)
            persistSession(res)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun fetchMe(): MahAuthResponse? {
        val access = prefs.getAccessToken() ?: return null
        return try {
            val res = api.me(access)
            persistSession(res, keepTokens = true)
            res
        } catch (e: MahApiException) {
            if (e.status == 401 && refreshIfNeeded()) {
                val again = prefs.getAccessToken() ?: return null
                val res = api.me(again)
                persistSession(res, keepTokens = true)
                res
            } else null
        }
    }

    suspend fun refreshSubscription(): MahSubscriptionDto? {
        var access = prefs.getAccessToken() ?: return null
        return try {
            val s = api.subscriptionStatus(access)
            prefs.setServerSubscription(s.hasActiveSubscription, s.plan, s.endsAt, s.priceYearlyTomans)
            prefs.setSubscriptionShare(s.ownSubscriptionActive, s.sharedFromPartner)
            s
        } catch (e: MahApiException) {
            if (e.status == 401 && refreshIfNeeded()) {
                access = prefs.getAccessToken() ?: return null
                val s = api.subscriptionStatus(access)
                prefs.setServerSubscription(s.hasActiveSubscription, s.plan, s.endsAt, s.priceYearlyTomans)
                prefs.setSubscriptionShare(s.ownSubscriptionActive, s.sharedFromPartner)
                s
            } else null
        }
    }

    suspend fun logout() {
        val access = prefs.getAccessToken()
        if (!access.isNullOrBlank()) {
            try { api.logout(access) } catch (_: Throwable) { }
        }
        prefs.clearAuth()
    }

    suspend fun setApiBaseUrl(url: String) {
        prefs.setApiBaseUrl(url)
        api.setBaseUrl(url)
    }

    suspend fun loadApiBaseUrl() {
        // Production API is fixed; ignore any old custom URL from prefs.
        api.setBaseUrl(BuildConfig.MAH_API_BASE_URL)
        prefs.setApiBaseUrl(null)
    }

    private suspend fun persistSession(res: MahAuthResponse, keepTokens: Boolean = false) {
        if (!keepTokens) {
            if (!res.accessToken.isNullOrBlank()) prefs.setAccessToken(res.accessToken)
            if (!res.refreshToken.isNullOrBlank()) prefs.setRefreshToken(res.refreshToken)
        } else {
            // me/refresh may omit tokens
            if (!res.accessToken.isNullOrBlank()) prefs.setAccessToken(res.accessToken)
            if (!res.refreshToken.isNullOrBlank()) prefs.setRefreshToken(res.refreshToken)
        }
        res.user?.let {
            prefs.setAccount(it.phone, it.name, it.id)
            prefs.setAccountRole(it.role)
        }
        res.subscription?.let {
            prefs.setServerSubscription(
                it.hasActiveSubscription,
                it.plan,
                it.endsAt,
                it.priceYearlyTomans
            )
            prefs.setSubscriptionShare(it.ownSubscriptionActive || (it.hasActiveSubscription && !it.sharedFromPartner), it.sharedFromPartner)
        }
    }
}
