package com.example.data.remote

import android.os.Build
import com.example.BuildConfig
import com.example.data.model.JyCatalogDefaults
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
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

object AdminAuthClient {
  private val _sessionState = MutableStateFlow(AdminSessionState())
  val sessionState: StateFlow<AdminSessionState> = _sessionState.asStateFlow()

  private val _loginInProgress = MutableStateFlow(false)
  val loginInProgress: StateFlow<Boolean> = _loginInProgress.asStateFlow()

  fun setLoginInProgress(inProgress: Boolean) {
    _loginInProgress.value = inProgress
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

  fun syncDirectFirebaseSession(uid: String) {
    if (hasActiveSession() || uid.trim() != JyCatalogDefaults.SINGLE_ADMIN_UID) return
    val now = System.currentTimeMillis()
    _sessionState.value =
      AdminSessionState(
        sessionToken = "firebase_google:${JyCatalogDefaults.SINGLE_ADMIN_UID}",
        csrfToken = "",
        expiresAtMs = now + DIRECT_SESSION_TTL_MS,
        inactivityTimeoutMs = AdminSessionState.DEFAULT_INACTIVITY_TIMEOUT_MS,
        lastActivityAtMs = now,
        sessionVersion = 1,
        authenticated = true,
      )
  }

  /**
   * Verifies that the currently signed-in Firebase user is the exclusive owner Google account
   * (`joshua.dangana@gmail.com`, UID `msUb7EcbUye0HyBqCNMe7FCIKvA3`) authenticated via `google.com`
   * with verified email and server-assigned Firebase custom claims (`jySingleAdmin == true`, `role == "admin"`).
   *
   * Any unauthorized, wrong-UID, unverified, non-Google, or unclaimed account is immediately signed out.
   */
  suspend fun verifyOwnerGoogleSignIn(
    firebaseAuth: FirebaseAuth? = null,
  ): AdminAuthResult =
    withContext(Dispatchers.IO) {
      _loginInProgress.value = true
      val authInstance = firebaseAuth ?: Firebase.auth
      try {
        val signedInUser = authInstance.currentUser
        if (signedInUser == null) {
          _sessionState.value = AdminSessionState()
          return@withContext AdminAuthResult.Failed(
            "Google Sign-In did not return a Firebase user session."
          )
        }

        if (
          signedInUser.uid != JyCatalogDefaults.SINGLE_ADMIN_UID ||
            !JyCatalogDefaults.isOwnerAdminIdentity(
              uid = signedInUser.uid,
              email = signedInUser.email,
              emailVerified = signedInUser.isEmailVerified,
            )
        ) {
          runCatching { authInstance.signOut() }
          _sessionState.value = AdminSessionState()
          return@withContext AdminAuthResult.Failed(
            "Access denied. Only the verified owner Google account (${JyCatalogDefaults.OWNER_ADMIN_EMAIL}) is authorized for Administrator Login."
          )
        }

        val tokenResult = signedInUser.getIdToken(true).await()
        if (tokenResult.signInProvider != "google.com") {
          runCatching { authInstance.signOut() }
          _sessionState.value = AdminSessionState()
          return@withContext AdminAuthResult.Failed(
            "Administrator access requires Google Sign-In with ${JyCatalogDefaults.OWNER_ADMIN_EMAIL}."
          )
        }

        val hasSingleAdminClaim = tokenResult.claims["jySingleAdmin"] == true
        val roleClaim = tokenResult.claims["role"] as? String
        if (!hasSingleAdminClaim || roleClaim != "admin") {
          runCatching { authInstance.signOut() }
          _sessionState.value = AdminSessionState()
          return@withContext AdminAuthResult.Failed(
            "Administrator custom claims (jySingleAdmin=true, role='admin') are not provisioned for ${JyCatalogDefaults.OWNER_ADMIN_EMAIL} in Firebase Authentication."
          )
        }

        val now = System.currentTimeMillis()
        val expiresAtMs = now + DIRECT_SESSION_TTL_MS
        _sessionState.value =
          AdminSessionState(
            sessionToken = "firebase_google:${signedInUser.uid}",
            csrfToken = "",
            expiresAtMs = expiresAtMs,
            inactivityTimeoutMs = AdminSessionState.DEFAULT_INACTIVITY_TIMEOUT_MS,
            lastActivityAtMs = now,
            sessionVersion = 1,
            authenticated = true,
          )
        AdminAuthResult.Success(uid = signedInUser.uid, expiresAtMs = expiresAtMs)
      } catch (e: Exception) {
        runCatching { authInstance.signOut() }
        _sessionState.value = AdminSessionState()
        AdminAuthResult.Failed(
          e.localizedMessage ?: "Unable to verify owner Google Sign-In claims."
        )
      } finally {
        _loginInProgress.value = false
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
      return@withContext try {
        val authInstance = firebaseAuth ?: Firebase.auth
        val signedInUser = authInstance.currentUser ?: return@withContext false
        val tokenResult = signedInUser.getIdToken(false).await()
        val valid =
          signedInUser.uid == JyCatalogDefaults.SINGLE_ADMIN_UID &&
            JyCatalogDefaults.isOwnerAdminIdentity(
              uid = signedInUser.uid,
              email = signedInUser.email,
              emailVerified = signedInUser.isEmailVerified,
            ) &&
            tokenResult.claims["jySingleAdmin"] == true &&
            tokenResult.claims["role"] == "admin" &&
            tokenResult.signInProvider == "google.com"
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

  suspend fun logout(
    firebaseAuth: FirebaseAuth? = null,
  ) {
    withContext(NonCancellable + Dispatchers.IO) {
      _sessionState.value = AdminSessionState()
      _loginInProgress.value = false
      runCatching { (firebaseAuth ?: Firebase.auth).signOut() }
    }
  }

  /**
   * Collects strictly non-sensitive hardware/OS/app telemetry for security diagnostics.
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

  private const val DIRECT_SESSION_TTL_MS = 8 * 60 * 60 * 1000L
}
