package com.example.data.remote

import android.os.Build
import com.example.BuildConfig
import com.example.data.model.JyCatalogDefaults
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
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

  private val clientLock = Any()
  private var clientFailedAttempts = 0
  private var clientLockedUntilMs = 0L

  fun setEndpointOverrideForTesting(url: String?) {
    testEndpointOverride = url?.trim()?.trimEnd('/')
  }

  internal fun resetClientLockoutForTesting() {
    synchronized(clientLock) {
      clientFailedAttempts = 0
      clientLockedUntilMs = 0L
    }
  }

  private fun resolveBaseUrl(): String {
    val override = testEndpointOverride
    if (override != null) return override
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
      val baseUrl = resolveBaseUrl()
      if (!isBackendConfigured()) {
        return@withContext loginViaFirebaseAuth(cleanUser, password, firebaseAuth)
      }

      try {
        val payload =
          JSONObject().apply {
            put("username", cleanUser)
            put("password", password)
            put("deviceInfo", collectSafeDeviceInfo())
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
          if (signedInUser == null || !JyCatalogDefaults.isOwnerAdminIdentity(signedInUser.uid, signedInUser.email)) {
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

  private suspend fun loginViaFirebaseAuth(
    cleanUser: String,
    password: String,
    firebaseAuth: FirebaseAuth?,
  ): AdminAuthResult {
    val now = System.currentTimeMillis()
    val remainingLockSeconds =
      synchronized(clientLock) {
        if (clientLockedUntilMs > now) ((clientLockedUntilMs - now + 999L) / 1000L) else 0L
      }
    if (remainingLockSeconds > 0L) {
      val mins = ((remainingLockSeconds + 59L) / 60L).coerceAtLeast(1L)
      return AdminAuthResult.LockedOut(
        message = "Too many failed administrator login attempts. Try again in $mins minute(s).",
        retryAfterSeconds = remainingLockSeconds,
      )
    }

    val mappedEmail = JyCatalogDefaults.resolveAdminAuthEmail(cleanUser)
    if (mappedEmail == null) {
      val lockedNow = recordClientFailure(now)
      return if (lockedNow) {
        AdminAuthResult.LockedOut(
          message = "Too many failed login attempts. Administrator login is temporarily locked.",
          retryAfterSeconds = CLIENT_LOCKOUT_MS / 1000L,
        )
      } else {
        AdminAuthResult.Failed("Invalid administrator credentials.")
      }
    }

    val authInstance = firebaseAuth ?: Firebase.auth
    return try {
      val authResult = authInstance.signInWithEmailAndPassword(mappedEmail, password).await()
      val signedInUser = authResult.user
      if (signedInUser == null || !JyCatalogDefaults.isOwnerAdminIdentity(signedInUser.uid, signedInUser.email)) {
        authInstance.signOut()
        return AdminAuthResult.Failed("Administrator identity verification failed.")
      }

      val tokenResult = signedInUser.getIdToken(true).await()
      val hasAdminClaim = tokenResult.claims["jySingleAdmin"] == true
      val roleClaim = tokenResult.claims["role"] as? String
      if (!hasAdminClaim || roleClaim != "admin" || tokenResult.signInProvider == "google.com") {
        authInstance.signOut()
        return AdminAuthResult.Failed(
          "Administrator role claims are not provisioned for this account. Assign custom claims (role='admin', jySingleAdmin=true) in Firebase."
        )
      }

      synchronized(clientLock) {
        clientFailedAttempts = 0
        clientLockedUntilMs = 0L
      }

      val expiresAtMs = now + DIRECT_SESSION_TTL_MS
      _sessionState.value =
        AdminSessionState(
          sessionToken = "firebase_auth:${signedInUser.uid}",
          csrfToken = "",
          expiresAtMs = expiresAtMs,
          inactivityTimeoutMs = AdminSessionState.DEFAULT_INACTIVITY_TIMEOUT_MS,
          lastActivityAtMs = now,
          sessionVersion = 1,
          authenticated = true,
        )
      AdminAuthResult.Success(uid = signedInUser.uid, expiresAtMs = expiresAtMs)
    } catch (e: FirebaseAuthException) {
      when (e.errorCode) {
        "ERROR_TOO_MANY_REQUESTS", "TOO_MANY_ATTEMPTS_TRY_LATER" ->
          AdminAuthResult.LockedOut(
            message = "Too many failed login attempts. Administrator login is temporarily locked by Firebase Authentication.",
            retryAfterSeconds = CLIENT_LOCKOUT_MS / 1000L,
          )
        "ERROR_USER_DISABLED" ->
          AdminAuthResult.Failed("This administrator account is disabled in Firebase Authentication.")
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" -> {
          val lockedNow = recordClientFailure(now)
          if (lockedNow) {
            AdminAuthResult.LockedOut(
              message = "Too many failed login attempts. Administrator login is temporarily locked.",
              retryAfterSeconds = CLIENT_LOCKOUT_MS / 1000L,
            )
          } else {
            AdminAuthResult.Failed("Invalid administrator credentials.")
          }
        }
        else ->
          AdminAuthResult.Failed(e.localizedMessage ?: "Administrator authentication failed.")
      }
    } catch (e: Exception) {
      val msg = e.localizedMessage.orEmpty()
      if (msg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true)) {
        AdminAuthResult.LockedOut(
          message = "Too many failed login attempts. Administrator login is temporarily locked.",
          retryAfterSeconds = CLIENT_LOCKOUT_MS / 1000L,
        )
      } else if (
        msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
          msg.contains("INVALID_PASSWORD", ignoreCase = true) ||
          msg.contains("EMAIL_NOT_FOUND", ignoreCase = true) ||
          msg.contains("INVALID_CREDENTIAL", ignoreCase = true)
      ) {
        val lockedNow = recordClientFailure(now)
        if (lockedNow) {
          AdminAuthResult.LockedOut(
            message = "Too many failed login attempts. Administrator login is temporarily locked.",
            retryAfterSeconds = CLIENT_LOCKOUT_MS / 1000L,
          )
        } else {
          AdminAuthResult.Failed("Invalid administrator credentials.")
        }
      } else {
        AdminAuthResult.Failed(msg.ifBlank { "Unable to authenticate administrator." })
      }
    }
  }

  private fun recordClientFailure(nowMs: Long): Boolean =
    synchronized(clientLock) {
      clientFailedAttempts += 1
      if (clientFailedAttempts >= MAX_CLIENT_FAILED_ATTEMPTS) {
        clientLockedUntilMs = nowMs + CLIENT_LOCKOUT_MS
        true
      } else {
        false
      }
    }

  private const val MAX_CLIENT_FAILED_ATTEMPTS = 5
  private const val CLIENT_LOCKOUT_MS = 15 * 60 * 1000L
  private const val DIRECT_SESSION_TTL_MS = 8 * 60 * 60 * 1000L

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
      if (!current.authenticated || current.sessionToken.isBlank()) {
        return@withContext false
      }
      if (isSessionExpiredDueToInactivity() || System.currentTimeMillis() >= current.expiresAtMs) {
        _sessionState.value = AdminSessionState()
        return@withContext false
      }
      if (!isBackendConfigured()) {
        return@withContext try {
          val authInstance = firebaseAuth ?: Firebase.auth
          val signedInUser = authInstance.currentUser ?: return@withContext false
          val tokenResult = signedInUser.getIdToken(false).await()
          val valid =
            JyCatalogDefaults.isOwnerAdminIdentity(signedInUser.uid, signedInUser.email) &&
              tokenResult.claims["jySingleAdmin"] == true &&
              tokenResult.claims["role"] == "admin" &&
              tokenResult.signInProvider != "google.com"
          if (!valid) {
            _sessionState.value = AdminSessionState()
          } else {
            _sessionState.value = current.copy(lastActivityAtMs = System.currentTimeMillis())
          }
          valid
        } catch (_: Exception) {
          false
        }
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
          val logoutBody = JSONObject().put("deviceInfo", collectSafeDeviceInfo()).toString()
          val req =
            Request.Builder()
              .url("$baseUrl/admin/logout")
              .header("Authorization", "Bearer ${current.sessionToken}")
              .header("X-CSRF-Token", current.csrfToken)
              .post(logoutBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
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
          "Owner password reset must be performed via Firebase Console (Authentication -> Users) or the offline provisioning script when admin-auth-server is not deployed."
        )
      }
      val baseUrl = resolveBaseUrl()
      try {
        val payload =
          JSONObject().apply {
            put("username", username.trim())
            put("recoveryKey", recoveryKey.trim())
            put("newPassword", newPassword)
            put("deviceInfo", collectSafeDeviceInfo())
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

  /**
   * Collects strictly non-sensitive hardware/OS/app telemetry for security audit logging.
   * Never includes usernames, passwords, tokens, recovery keys, device serials, or user PII.
   */
  fun collectSafeDeviceInfo(): JSONObject {
    fun safeField(raw: String?, fallback: String = "unknown", maxLen: Int = 80): String =
      raw?.replace(Regex("[\\r\\n\\t\\u0000-\\u001f]+"), " ")?.trim()?.take(maxLen)
        ?.ifBlank { fallback } ?: fallback

    return JSONObject().apply {
      put("platform", "Android")
      put("manufacturer", safeField(Build.MANUFACTURER))
      put("brand", safeField(Build.BRAND))
      put("model", safeField(Build.MODEL))
      put("device", safeField(Build.DEVICE))
      put("osVersion", safeField(Build.VERSION.RELEASE, fallback = "Android"))
      put("sdkInt", Build.VERSION.SDK_INT.coerceAtLeast(1))
      put("appVersion", safeField(BuildConfig.VERSION_NAME, fallback = "1.0"))
      put("appPackage", safeField(BuildConfig.APPLICATION_ID, maxLen = 120))
    }
  }
}
