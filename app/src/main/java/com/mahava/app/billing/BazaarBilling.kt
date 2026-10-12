package com.mahava.app.billing

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultRegistry
import com.mahava.app.BuildConfig
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Cafe Bazaar In-App Billing (Poolakey) for the yearly Mah subscription SKU.
 */
class BazaarBilling(context: Context) {
    private val appContext = context.applicationContext
    private val payment: Payment = Payment(
        context = appContext,
        config = PaymentConfiguration(
            localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = BuildConfig.BAZAAR_RSA_PUBLIC_KEY)
        )
    )
    private var connection: Connection? = null

    val yearlySku: String get() = BuildConfig.BAZAAR_SKU_YEARLY

    suspend fun connect(): Result<Unit> = suspendCancellableCoroutine { cont ->
        connection?.disconnect()
        connection = payment.connect {
            connectionSucceed {
                if (cont.isActive) cont.resume(Result.success(Unit))
            }
            connectionFailed { e ->
                if (cont.isActive) {
                    cont.resume(Result.failure(Exception(friendlyBazaarError(e), e)))
                }
            }
            disconnected { }
        }
        cont.invokeOnCancellation { runCatching { connection?.disconnect() } }
    }

    fun disconnect() {
        runCatching { connection?.disconnect() }
        connection = null
    }

    suspend fun purchaseYearly(registry: ActivityResultRegistry): Result<PurchaseInfo> =
        suspendCancellableCoroutine { cont ->
            payment.purchaseProduct(
                registry = registry,
                request = PurchaseRequest(productId = yearlySku)
            ) {
                purchaseSucceed { info ->
                    if (cont.isActive) cont.resume(Result.success(info))
                }
                purchaseCanceled {
                    if (cont.isActive) cont.resume(Result.failure(Exception("خرید لغو شد")))
                }
                purchaseFailed { e ->
                    if (cont.isActive) cont.resume(Result.failure(Exception(friendlyBazaarError(e), e)))
                }
                failedToBeginFlow { e ->
                    if (cont.isActive) {
                        cont.resume(Result.failure(Exception(friendlyBazaarError(e), e)))
                    }
                }
            }
        }

    suspend fun consume(purchaseToken: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        payment.consumeProduct(purchaseToken) {
            consumeSucceed {
                if (cont.isActive) cont.resume(Result.success(Unit))
            }
            consumeFailed { e ->
                if (cont.isActive) cont.resume(Result.failure(Exception(friendlyBazaarError(e), e)))
            }
        }
    }

    suspend fun queryOwnedYearly(): List<PurchaseInfo> = suspendCancellableCoroutine { cont ->
        payment.getPurchasedProducts {
            querySucceed { list ->
                if (cont.isActive) {
                    cont.resume(list.filter { it.productId == yearlySku })
                }
            }
            queryFailed {
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    companion object {
        const val BAZAAR_PACKAGE = "com.farsitel.bazaar"
        private const val BAZAAR_BILLING_ACTION = "ir.cafebazaar.pardakht.InAppBillingService.BIND"

        fun isBazaarInstalled(context: Context): Boolean {
            val pm = context.packageManager
            if (hasPackage(pm, BAZAAR_PACKAGE)) return true
            // Fallback: billing service visible (Android 11+ package visibility).
            return try {
                val intent = Intent(BAZAAR_BILLING_ACTION).setPackage(BAZAAR_PACKAGE)
                pm.resolveService(intent, 0) != null
            } catch (_: Throwable) {
                false
            }
        }

        private fun hasPackage(pm: PackageManager, packageName: String): Boolean = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }

        fun friendlyBazaarError(error: Throwable?): String {
            val raw = (error?.message ?: "").lowercase()
            return when {
                raw.contains("not installed") || raw.contains("bazaar is not") ->
                    "کافه‌بازار روی این گوشی پیدا نشد یا در دسترس نیست. اپ بازار را نصب/به‌روز کن، یک‌بار بازش کن و وارد حسابت شو، بعد دوباره از ماه خرید را بزن."
                raw.contains("disconnect") || raw.contains("connection") ->
                    "اتصال به سرویس پرداخت بازار برقرار نشد. بازار را باز کن و دوباره امتحان کن."
                raw.contains("cancel") -> "خرید لغو شد."
                raw.isBlank() -> "خرید از بازار انجام نشد."
                else -> error?.message ?: "خرید از بازار انجام نشد."
            }
        }
    }
}

fun PurchaseInfo.toClaimMap(): Map<String, Any?> = mapOf(
    "productId" to productId,
    "orderId" to orderId,
    "purchaseToken" to purchaseToken,
    "packageName" to packageName,
    "purchaseTime" to purchaseTime,
    "purchaseState" to purchaseState.toString(),
    "payload" to payload,
    "purchaseData" to originalJson,
    "dataSignature" to dataSignature
)
