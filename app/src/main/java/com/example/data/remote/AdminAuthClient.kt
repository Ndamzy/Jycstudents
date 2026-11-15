package com.example.data.remote

import com.example.BuildConfig
import com.example.data.model.JyCatalogDefaults
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AdminSessionState(
  val sessionToken: String = "",
  val csrfToken: String = "",
  val expiresAtMs: Long = 0L,
  val inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS,
  val lastActivityAtMs: Long = 0L,
  val sessionVersion: Int = 0,
  val authenticated: Boolean = false,
) {
  companion object {
    const val DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000L
  }
}

sealed interface AdminAuthResult {
  data class Success(val uid: String, val expiresAtMs: Long) : AdminAuthResult
  data class LockedOut(val message: String, val retryAfterSeconds: Long) : AdminAuthResult
  data class Failed(val message: String) : AdminAuthResult
}

sealed interface AdminRecoveryResult {
  data class Success(val message: String) : AdminRecoveryResult
  data class Failed(val message: String) : AdminRecoveryResult
}

object AdminAuthClient {
  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()

  private val _sessionState = MutableStateFlow(AdminSessionState())
  val sessionState: StateFlow<AdminSessionState> = _sessionState.asStateFlow()

  @Volatile
  private var testEndpointOverride: String? = null

  fun setEndpointOverrideForTesting(url: String?) {
    testEndpointOverride = url?.trim()?.trimEnd('/')
  }

  private fun resolveBaseUrl(): String {
    val override = testEndpointOverride
    if (!override.isNullOrBlank()) return override
    return BuildConfig.ADMIN_AUTH_ENDPOINT_URL.trim().trimEnd('/')
  }

  fun isBackendConfigured(): Boolean {
    val url = resolveBaseUrl()
    if (url.isBlank() || url == "MY_ADMIN_AUTH_ENDPOINT_URL") return false
    return url.startsWith("https://") ||
      url.startsWith("http://127.0.0.1") ||
      url.startsWith("http://localhost") ||
      url.startsWith("http://10.0.2.2")
  }

  fun hasActiveSession(): Boolean {
    val current = _sessionState.value
    return current.authenticated &&
      current.sessionToken.isNotBlank() &&
      System.currentTimeMillis() < current.expiresAtMs &&
      !isSessionExpiredDueToInactivity()
  }

  fun recordUserActivity() {
    val current = _sessionState.value
    if (current.authenticated) {
      _sessionState.value = current.copy(lastActivityAtMs = System.currentTimeMillis())
    }
  }

  fun isSessionExpiredDueToInactivity(nowMs: Long = System.currentTimeMillis()): Boolean {
    val current = _sessionState.value
    if (!current.authenticated || current.lastActivityAtMs <= 0L) return false
    return (nowMs - current.lastActivityAtMs) > current.inactivityTimeoutMs
  }

  suspend fun login(
    username: String,
    password: String,
    firebaseAuth: FirebaseAuth? = null,
  ): AdminAuthResult =
    withContext(Dispatchers.IO) {
      val cleanUser = username.trim()
      if (cleanUser.isBlank() || password.isBlank()) {
        return@withContext AdminAuthResult.Failed("Enter your administrator username and password.")
      }
      if (!isBackendConfigured()) {
        return@withContext AdminAuthResult.Failed(
          "Administrator authentication server is not configured. Deploy admin-auth-server and set ADMIN_AUTH_ENDPOINT_URL in AI Studio Secrets."
        )
      }

      val baseUrl = resolveBaseUrl()
      try {
        val payload =
          JSONObject().apply {
            put("username", cleanUser)
            put("password", password)
          }
        val req =
          Request.Builder()
            .url("$baseUrl/admin/login")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()

          if (response.code == 429) {
            val retryAfter =
              response.header("Retry-After")?.toLongOrNull()
                ?: json.optLong("retryAfterSeconds", 900L)
            val msg =
              json.optString(
                "error",
                "Too many failed login attempts. Administrator login is temporarily locked.",
              )
            return@withContext AdminAuthResult.LockedOut(msg, retryAfter)
          }

          if (!response.isSuccessful) {
            val msg = json.optString("error", "Invalid administrator credentials.")
            return@withContext AdminAuthResult.Failed(msg)
          }

          val customToken = json.optString("customToken", "")
          val sessionToken = json.optString("sessionToken", "")
          val csrfToken = json.optString("csrfToken", "")
          val expiresAtMs = json.optLong("expiresAtMs", 0L)
          val inactivityTimeoutMs =
            json.optLong("inactivityTimeoutMs", AdminSessionState.DEFAULT_INACTIVITY_TIMEOUT_MS)
          val sessionVersion = json.optInt("sessionVersion", 1)

          if (customToken.isBlank() || sessionToken.isBlank()) {
            return@withContext AdminAuthResult.Failed("Incomplete response from authentication server.")
          }

          val authInstance = firebaseAuth ?: Firebase.auth
          val authResult = authInstance.signInWithCustomToken(customToken).await()
          val signedInUser = authResult.user
          if (signedInUser == null || signedInUser.uid != JyCatalogDefaults.SINGLE_ADMIN_UID) {
            authInstance.signOut()
            return@withContext AdminAuthResult.Failed("Administrator identity verification failed.")
          }

          val tokenResult = signedInUser.getIdToken(true).await()
          val hasAdminClaim = tokenResult.claims["jySingleAdmin"] == true
          val roleClaim = tokenResult.claims["role"] as? String
          if (!hasAdminClaim || roleClaim != "admin" || tokenResult.signInProvider == "google.com") {
            authInstance.signOut()
            return@withContext AdminAuthResult.Failed("Server administrator claims verification failed.")
          }

          _sessionState.value =
            AdminSessionState(
              sessionToken = sessionToken,
              csrfToken = csrfToken,
              expiresAtMs = expiresAtMs,
              inactivityTimeoutMs = inactivityTimeoutMs,
              lastActivityAtMs = System.currentTimeMillis(),
              sessionVersion = sessionVersion,
              authenticated = true,
            )

          AdminAuthResult.Success(uid = signedInUser.uid, expiresAtMs = expiresAtMs)
        }
      } catch (e: Exception) {
        AdminAuthResult.Failed(
          e.localizedMessage ?: "Unable to reach the administrator authentication server."
        )
      }
    }

  suspend fun verifyServerSession(
    tokenOverride: String? = null,
  ): Result<Boolean> =
    withContext(Dispatchers.IO) {
      if (!isBackendConfigured()) {
        return@withContext Result.failure(IllegalStateException("Admin auth server not configured"))
      }
      val token = tokenOverride ?: _sessionState.value.sessionToken
      if (token.isBlank()) {
        return@withContext Result.failure(SecurityException("Missing administrator session token"))
      }
      val baseUrl = resolveBaseUrl()
      try {
        val req =
          Request.Builder()
            .url("$baseUrl/admin/session")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        httpClient.newCall(req).execute().use { response ->
          if (response.isSuccessful) {
            Result.success(true)
          } else {
            val raw = response.body?.string().orEmpty()
            val msg =
              runCatching { JSONObject(raw).optString("error") }.getOrNull().orEmpty()
                .ifBlank { "Unauthorized HTTP ${response.code}" }
            Result.failure(SecurityException(msg))
          }
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun refreshSession(
    firebaseAuth: FirebaseAuth? = null,
  ): Boolean =
    withContext(Dispatchers.IO) {
      val current = _sessionState.value
      if (!current.authenticated || current.sessionToken.isBlank() || !isBackendConfigured()) {
        return@withContext false
      }
      if (isSessionExpiredDueToInactivity() || System.currentTimeMillis() >= current.expiresAtMs) {
        _sessionState.value = AdminSessionState()
        return@withContext false
      }
      val baseUrl = resolveBaseUrl()
      try {
        val req =
          Request.Builder()
            .url("$baseUrl/admin/refresh")
            .header("Authorization", "Bearer ${current.sessionToken}")
            .header("X-CSRF-Token", current.csrfToken)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { response ->
          if (!response.isSuccessful) {
            _sessionState.value = AdminSessionState()
            return@withContext false
          }
          val json = JSONObject(response.body?.string().orEmpty())
          val customToken = json.optString("customToken", "")
          if (customToken.isNotBlank()) {
            (firebaseAuth ?: Firebase.auth).signInWithCustomToken(customToken).await()
          }
          _sessionState.value =
            current.copy(
              expiresAtMs = json.optLong("expiresAtMs", current.expiresAtMs),
              lastActivityAtMs = System.currentTimeMillis(),
            )
          true
        }
      } catch (_: Exception) {
        false
      }
    }

  suspend fun logout(
    firebaseAuth: FirebaseAuth? = null,
  ) {
    withContext(NonCancellable + Dispatchers.IO) {
      val current = _sessionState.value
      val baseUrl = resolveBaseUrl()
      try {
        if (current.sessionToken.isNotBlank() && isBackendConfigured()) {
          val req =
            Request.Builder()
              .url("$baseUrl/admin/logout")
              .header("Authorization", "Bearer ${current.sessionToken}")
              .header("X-CSRF-Token", current.csrfToken)
              .post("{}".toRequestBody("application/json".toMediaType()))
              .build()
          httpClient.newCall(req).execute().close()
        }
      } catch (_: Exception) {
        // Always revoke client-side session state even if network fails
      } finally {
        _sessionState.value = AdminSessionState()
        runCatching { (firebaseAuth ?: Firebase.auth).signOut() }
      }
    }
  }

  suspend fun recoverPassword(
    username: String,
    recoveryKey: String,
    newPassword: String,
    firebaseAuth: FirebaseAuth? = null,
  ): AdminRecoveryResult =
    withContext(Dispatchers.IO) {
      if (username.isBlank() || recoveryKey.isBlank() || newPassword.isBlank()) {
        return@withContext AdminRecoveryResult.Failed(
          "Username, owner recovery key, and new password are required."
        )
      }
      if (!isBackendConfigured()) {
        return@withContext AdminRecoveryResult.Failed(
          "Administrator recovery server is not configured. Configure ADMIN_AUTH_ENDPOINT_URL first."
        )
      }
      val baseUrl = resolveBaseUrl()
      try {
        val payload =
          JSONObject().apply {
            put("username", username.trim())
            put("recoveryKey", recoveryKey.trim())
            put("newPassword", newPassword)
          }
        val req =
          Request.Builder()
            .url("$baseUrl/admin/recover")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { response ->
          val raw = response.body?.string().orEmpty()
          val json = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
          if (response.isSuccessful) {
            _sessionState.value = AdminSessionState()
            runCatching { (firebaseAuth ?: Firebase.auth).signOut() }
            AdminRecoveryResult.Success(
              json.optString(
                "message",
                "Administrator password updated and previous sessions invalidated.",
              )
            )
          } else {
            AdminRecoveryResult.Failed(
              json.optString("error", "Administrator recovery request failed.")
            )
          }
        }
      } catch (e: Exception) {
        AdminRecoveryResult.Failed(
          e.localizedMessage ?: "Unable to reach administrator recovery server."
        )
      }
    }
}
