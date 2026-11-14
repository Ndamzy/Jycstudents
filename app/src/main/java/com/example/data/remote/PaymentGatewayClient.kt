package com.example.data.remote

import com.example.BuildConfig
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed interface GatewayVerificationResult {
  data class Verified(val gatewayReference: String, val message: String) : GatewayVerificationResult
  data class PendingManualReview(val gatewayReference: String, val reason: String) : GatewayVerificationResult
  data class Failed(val error: String) : GatewayVerificationResult
}

object PaymentGatewayClient {
  private val httpClient = OkHttpClient()

  fun generateTransactionReference(prefix: String = "JYC-PAY"): String {
    val suffix = UUID.randomUUID().toString().take(8).uppercase()
    return "$prefix-$suffix"
  }

  fun isBackendVerificationConfigured(): Boolean {
    val endpoint = BuildConfig.PAYMENT_VERIFY_ENDPOINT_URL.trim()
    return endpoint.isNotBlank() &&
      endpoint != "MY_PAYMENT_VERIFY_ENDPOINT_URL" &&
      (endpoint.startsWith("https://") || endpoint.startsWith("http://"))
  }

  /**
   * Calls the administrator-configured backend verification webhook (never storing gateway
   * private/secret keys inside the Android APK). If no backend verification URL is configured,
   * returns PendingManualReview so the transaction reference is securely recorded in Firestore
   * for admin/webhook confirmation.
   */
  suspend fun verifyTransactionWithBackend(
    reference: String,
    amountNaira: Long,
    studentIdNumber: String,
  ): GatewayVerificationResult =
    withContext(Dispatchers.IO) {
      val endpoint = BuildConfig.PAYMENT_VERIFY_ENDPOINT_URL.trim()
      if (!isBackendVerificationConfigured()) {
        return@withContext GatewayVerificationResult.PendingManualReview(
          gatewayReference = reference,
          reason =
            "Transaction reference recorded. Configure PAYMENT_VERIFY_ENDPOINT_URL in Secrets / .env for automated server-side Paystack/Flutterwave verification, or have an Administrator approve this reference.",
        )
      }

      try {
        val payload =
          JSONObject().apply {
            put("reference", reference)
            put("amountNaira", amountNaira)
            put("studentIdNumber", studentIdNumber)
          }
        val request =
          Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
          val bodyText = response.body?.string().orEmpty()
          if (!response.isSuccessful) {
            return@withContext GatewayVerificationResult.Failed(
              "Verification server returned HTTP ${response.code}"
            )
          }
          val json = JSONObject(bodyText)
          val verified = json.optBoolean("verified", false)
          val message = json.optString("message", "Verified by payment backend")
          if (verified) {
            GatewayVerificationResult.Verified(reference, message)
          } else {
            GatewayVerificationResult.PendingManualReview(reference, message)
          }
        }
      } catch (e: Exception) {
        GatewayVerificationResult.Failed(e.localizedMessage ?: "Network verification error")
      }
    }
}
