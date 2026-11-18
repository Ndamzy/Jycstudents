package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.JyCatalogDefaults
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

  protected lateinit var firestore: FirebaseFirestore
  protected lateinit var auth: FirebaseAuth
  protected lateinit var databaseId: String

  @Before
  open fun setUpFirebase() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val resId =
      context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
    databaseId =
      if (resId != 0) {
        context.getString(resId)
      } else {
        "ai-studio-android-jycomput-682cf32f-41f3-47bf-a6b2-6ad57154d3a5"
      }

    val app =
      if (FirebaseApp.getApps(context).isEmpty()) {
        val newApp =
          FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
              .setApplicationId("com.aistudio.jycomputers.mrxqkp")
              .setProjectId(PROJECT_ID)
              .setApiKey("fake-api-key-for-emulator")
              .build(),
          )
        val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
        newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
        newFirestore.firestoreSettings =
          FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()

        val newAuth = FirebaseAuth.getInstance(newApp)
        newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
        newApp
      } else {
        FirebaseApp.getInstance()
      }

    firestore = FirebaseFirestore.getInstance(app, databaseId)
    auth = FirebaseAuth.getInstance(app)
  }

  @After
  open fun tearDownFirebase() {
    auth.signOut()
  }

  protected suspend fun clearAuthEmulatorAccounts() =
    withContext(Dispatchers.IO) {
      auth.signOut()
      val url =
        java.net.URL(
          "http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/accounts"
        )
      val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
        requestMethod = "DELETE"
        setRequestProperty("Authorization", "Bearer owner")
      }
      check(conn.responseCode in 200..299) {
        "Clearing Auth emulator accounts failed: HTTP ${conn.responseCode}"
      }
      conn.disconnect()
    }

  protected suspend fun signInTestUser(email: String): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val result =
          try {
            auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          } catch (_: FirebaseAuthInvalidUserException) {
            auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
          }
        checkNotNull(result.user?.uid) { "User auth failed" }
      }
    }

  protected fun createEmulatorCustomToken(
    uid: String,
    claims: Map<String, Any> = emptyMap(),
  ): String {
    val nowSec = System.currentTimeMillis() / 1000L
    val headerJson = JSONObject().put("alg", "none").put("typ", "JWT").toString()
    val claimsJson = JSONObject(claims)
    val payloadJson =
      JSONObject()
        .put("iss", "firebase-auth-emulator@example.com")
        .put("sub", "firebase-auth-emulator@example.com")
        .put(
          "aud",
          "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
        )
        .put("iat", nowSec)
        .put("exp", nowSec + 3600L)
        .put("uid", uid)
        .put("claims", claimsJson)
        .toString()

    val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
    val headerB64 = android.util.Base64.encodeToString(headerJson.toByteArray(Charsets.UTF_8), flags)
    val payloadB64 = android.util.Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), flags)
    return "$headerB64.$payloadB64."
  }

  private fun createEmulatorGoogleIdToken(
    subject: String,
    email: String,
    emailVerified: Boolean = true,
    displayName: String = "Joshua Dangana",
  ): String {
    val nowSec = System.currentTimeMillis() / 1000L
    val headerJson = JSONObject().put("alg", "none").put("typ", "JWT").toString()
    val payloadJson =
      JSONObject()
        .put("iss", "https://accounts.google.com")
        .put("sub", subject)
        .put("aud", "emulator-client-id.apps.googleusercontent.com")
        .put("iat", nowSec)
        .put("exp", nowSec + 3600L)
        .put("email", email)
        .put("email_verified", emailVerified)
        .put("name", displayName)
        .toString()

    val flags = android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
    val headerB64 = android.util.Base64.encodeToString(headerJson.toByteArray(Charsets.UTF_8), flags)
    val payloadB64 = android.util.Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), flags)
    return "$headerB64.$payloadB64."
  }

  private fun updateEmulatorAccountAttributes(
    uid: String,
    emailVerified: Boolean,
    customAttributesJson: String?,
  ) {
    val url =
      java.net.URL(
        "http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/projects/$PROJECT_ID/accounts:update"
      )
    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Authorization", "Bearer owner")
    }
    val payload =
      JSONObject().apply {
        put("localId", uid)
        put("emailVerified", emailVerified)
        if (customAttributesJson != null) {
          put("customAttributes", customAttributesJson)
        }
      }
    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
    check(conn.responseCode in 200..299) {
      "Auth emulator account update failed: HTTP ${conn.responseCode}"
    }
    conn.disconnect()
  }

  private fun createEmulatorAccountWithFixedUid(
    uid: String,
    email: String,
    emailVerified: Boolean,
    displayName: String = "Joshua Dangana",
  ) {
    val url =
      java.net.URL(
        "http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/projects/$PROJECT_ID/accounts"
      )
    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Authorization", "Bearer owner")
    }
    val payload =
      JSONObject()
        .put("localId", uid)
        .put("email", email)
        .put("emailVerified", emailVerified)
        .put("displayName", displayName)
    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
    val code = conn.responseCode
    conn.disconnect()
    check(code in 200..299 || code == 400) {
      "Auth emulator account create failed: HTTP $code"
    }
  }

  protected suspend fun signInGoogleUserInEmulator(
    email: String,
    subject: String = "google_sub_${email.lowercase()}",
    emailVerified: Boolean = true,
    customAttributesJson: String = "{}",
    fixedUid: String? = null,
  ): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        if (!fixedUid.isNullOrBlank()) {
          createEmulatorAccountWithFixedUid(
            uid = fixedUid,
            email = email,
            emailVerified = emailVerified,
          )
        }
        val fakeGoogleIdToken =
          createEmulatorGoogleIdToken(
            subject = subject,
            email = email,
            emailVerified = emailVerified,
          )
        val credential = GoogleAuthProvider.getCredential(fakeGoogleIdToken, null)
        val initialResult = auth.signInWithCredential(credential).await()
        val uid = checkNotNull(initialResult.user?.uid) { "Google Auth emulator sign-in failed" }
        if (!fixedUid.isNullOrBlank()) {
          check(uid == fixedUid) { "Expected pinned UID '$fixedUid' in Auth Emulator, got '$uid'" }
        }
        updateEmulatorAccountAttributes(
          uid = uid,
          emailVerified = emailVerified,
          customAttributesJson = customAttributesJson,
        )
        // Re-authenticate with credential after updating attributes so ID token claims reflect update
        val refreshed = auth.signInWithCredential(credential).await()
        val user = checkNotNull(refreshed.user) { "Google Auth emulator refresh failed" }
        user.getIdToken(true).await()
        user.uid
      }
    }

  protected suspend fun signInSingleOwnerAdmin(): String =
    signInGoogleUserInEmulator(
      email = JyCatalogDefaults.OWNER_ADMIN_EMAIL,
      subject = "joshua_owner_google_sub",
      emailVerified = true,
      customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
      fixedUid = JyCatalogDefaults.SINGLE_ADMIN_UID,
    )

  protected suspend fun provisionEmailUserInEmulator(
    email: String,
    password: String,
    customAttributesJson: String? = null,
    emailVerified: Boolean = true,
  ): String =
    withContext(Dispatchers.IO) {
      withTimeout(AUTH_TIMEOUT_MS) {
        val result =
          try {
            auth.createUserWithEmailAndPassword(email, password).await()
          } catch (_: Exception) {
            auth.signInWithEmailAndPassword(email, password).await()
          }
        val user = checkNotNull(result.user) { "Provisioned user missing" }
        val uid = user.uid
        updateEmulatorAccountAttributes(
          uid = uid,
          emailVerified = emailVerified,
          customAttributesJson = customAttributesJson,
        )
        auth.signOut()
        uid
      }
    }

  protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
    var current: Throwable? = this
    while (current != null) {
      if (current is FirebaseFirestoreException) return current
      current = current.cause
    }
    return null
  }

  private companion object {
    const val EMULATOR_HOST = "127.0.0.1"
    const val FIRESTORE_PORT = 8085
    const val AUTH_PORT = 9099
    const val PROJECT_ID = "demo-no-project"
    const val DEFAULT_PASSWORD = "password123"
    const val AUTH_TIMEOUT_MS = 5000L
  }
}
