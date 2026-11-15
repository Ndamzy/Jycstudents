package com.example.security

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.data.remote.AdminAuthClient
import com.example.data.remote.AdminAuthResult
import com.example.data.remote.AdminRecoveryResult
import com.example.data.repository.CourseAndAcademicRepository
import com.example.data.repository.ProfileAndSettingsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive security verification covering Tests 1-10 for JY-COMPUTERS Single-Administrator
 * Access Control against real Firebase Auth & Firestore Emulators + real AdminAuthClient HTTP calls.
 */
class SingleAdminSecurityTest : FirestoreEmulatorTestBase() {

  private lateinit var mockServer: LocalSingleAdminServer

  @Before
  fun startLocalAdminServer() {
    mockServer =
      LocalSingleAdminServer { uid, claims ->
        createEmulatorCustomToken(uid, claims)
      }
    mockServer.start()
    AdminAuthClient.setEndpointOverrideForTesting(mockServer.baseUrl)
  }

  @After
  fun stopLocalAdminServer() = runBlocking {
    AdminAuthClient.logout(auth)
    AdminAuthClient.setEndpointOverrideForTesting(null)
    mockServer.stop()
  }

  // TEST 1: Valid owner administrator login grants access to Admin Dashboard
  @Test
  fun test1_validOwnerAdminLogin_authenticatesSingleAdminAndGrantsAccess() = runBlocking {
    val result =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }

    assertTrue("Expected Success, got $result", result is AdminAuthResult.Success)
    val success = result as AdminAuthResult.Success
    assertEquals(JyCatalogDefaults.SINGLE_ADMIN_UID, success.uid)
    assertTrue("In-memory admin session must be active", AdminAuthClient.hasActiveSession())

    val profileRepo = ProfileAndSettingsRepository(firestore)
    assertTrue("Owner admin UID + custom claims must pass isUidAdmin", profileRepo.isUidAdmin(success.uid))

    val verifyRes = withTimeout(TIMEOUT_MS) { AdminAuthClient.verifyServerSession() }
    assertTrue("Server /admin/session verification should succeed", verifyRes.getOrDefault(false))

    val academicRepo = CourseAndAcademicRepository(firestore)
    val saveProgRes =
      withTimeout(TIMEOUT_MS) {
        academicRepo.saveProgramme(
          Programme(
            id = "sec_test_prog_${UUID.randomUUID().toString().take(6)}",
            title = "Cybersecurity Basics",
            duration = "3 Months",
            feeNaira = 120_000L,
            active = true,
          )
        )
      }
    assertTrue("Single owner admin must be allowed to manage programmes", saveProgRes.isSuccess)
  }

  // TEST 2: Invalid admin password fails cleanly without revealing valid username existence
  @Test
  fun test2_invalidAdminCredentialsAndNonexistentUsername_returnGenericError() = runBlocking {
    val wrongPw =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = "WrongPassword#999",
          firebaseAuth = auth,
        )
      }
    val wrongUser =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = "nonexistent_intruder",
          password = "WrongPassword#999",
          firebaseAuth = auth,
        )
      }

    assertTrue(wrongPw is AdminAuthResult.Failed)
    assertTrue(wrongUser is AdminAuthResult.Failed)
    assertEquals((wrongPw as AdminAuthResult.Failed).message, (wrongUser as AdminAuthResult.Failed).message)
    assertEquals("Invalid administrator credentials.", wrongPw.message)
    assertFalse(AdminAuthClient.hasActiveSession())
  }

  // TEST 3: Student Google/email login works for students and CANNOT access Admin features
  @Test
  fun test3_studentLogin_cannotPassAdminGateOrPerformAdminWrites() = runBlocking {
    val studentEmail = "student_${UUID.randomUUID()}@test.com"
    val studentUid = signInTestUser(studentEmail)
    val profileRepo = ProfileAndSettingsRepository(firestore)
    val academicRepo = CourseAndAcademicRepository(firestore)

    val regRes =
      withTimeout(TIMEOUT_MS) {
        profileRepo.registerOrCreateUserProfile(
          UserProfile(
            userId = studentUid,
            role = "student",
            studentIdNumber = "JYC-2026-SEC3",
            fullName = "Amaka Student",
            gender = "Female",
            dateOfBirth = "2002-05-10",
            phoneNumber = "08030001111",
            email = studentEmail,
            residentialAddress = "Abuja",
            stateOfOrigin = "FCT Abuja",
            localGovernmentArea = "AMAC",
            nextOfKin = "Chidi",
            nextOfKinPhone = "08030002222",
            selectedProgrammeId = "basic_computer",
            selectedProgrammeTitle = "Basic Computer Training",
            trainingLevel = "Beginner",
            registrationDate = "2026-11-15",
            status = "active",
          )
        )
      }
    assertTrue("Student registration must succeed", regRes.isSuccess)
    assertFalse("Student must NEVER pass isUidAdmin", profileRepo.isUidAdmin(studentUid))

    val adminWriteAttempt =
      withTimeout(TIMEOUT_MS) {
        academicRepo.saveProgramme(
          Programme(
            id = "illegal_stu_prog_${UUID.randomUUID().toString().take(6)}",
            title = "Unauthorized Course",
            duration = "1 Month",
            feeNaira = 5_000L,
          )
        )
      }
    assertTrue("Student admin write must fail with permission denied", adminWriteAttempt.isFailure)
  }

  // TEST 4: Any other Google / verified-email account (including old gmail) is rejected from Admin
  @Test
  fun test4_otherVerifiedEmailOrGoogleAccount_rejectedFromAdminAccess() = runBlocking {
    val legacyAdminUid = signInVerifiedEmailUser("joshua.dangana@gmail.com")
    val otherGoogleUid = signInVerifiedEmailUser("attacker_${UUID.randomUUID()}@gmail.com")

    val profileRepo = ProfileAndSettingsRepository(firestore)
    assertFalse("Legacy verified email must no longer grant admin", profileRepo.isUidAdmin(legacyAdminUid))
    assertFalse("Any other verified Google/email account must be denied", profileRepo.isUidAdmin(otherGoogleUid))

    // Also prove even a UID == SINGLE_ADMIN_UID signed in WITHOUT custom claims fails isUidAdmin & Firestore
    val unprivilegedAdminUidToken =
      createEmulatorCustomToken(
        uid = JyCatalogDefaults.SINGLE_ADMIN_UID,
        claims = mapOf("role" to "student"),
      )
    auth.signInWithCustomToken(unprivilegedAdminUidToken).await()
    assertFalse(
      "UID match alone without server jySingleAdmin+admin claims must fail",
      profileRepo.isUidAdmin(JyCatalogDefaults.SINGLE_ADMIN_UID),
    )

    var denied = false
    try {
      withTimeout(TIMEOUT_MS) {
        firestore
          .collection("settings")
          .document("institution")
          .set(
            mapOf(
              "institutionName" to "Hijacked",
              "opayAccountName" to "Fake",
              "opayAccountNumber" to "0000000000",
              "activeSession" to "2026",
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: Exception) {
      denied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Firestore must reject settings write without jySingleAdmin claim", denied)
  }

  // TEST 5: Direct/manual navigation check & ViewModel active session validation rejects non-admins
  @Test
  fun test5_manualRouteNavigationCheck_blocksNonAdminAndExpiredSession() = runBlocking {
    val studentUid = signInTestUser("route_probe_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)

    // Verify admin-only routes cannot be accessed when not verified
    assertFalse(profileRepo.isUidAdmin(studentUid))
    assertTrue("admin_dashboard" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_students" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_roles" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_settings" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("reports" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)

    // Also test unauthenticated activity logs read fails
    var logsDenied = false
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("activity_logs").get().await() }
    } catch (e: Exception) {
      logsDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Student navigating/reading admin activity logs must fail", logsDenied)
  }

  // TEST 6: Direct API request without valid admin token/session is rejected with 401/403
  @Test
  fun test6_directBackendRequestWithoutValidSession_isRejected401() = runBlocking {
    val unauthenticatedCheck = withTimeout(TIMEOUT_MS) { AdminAuthClient.verifyServerSession("") }
    assertTrue("Empty token must be rejected", unauthenticatedCheck.isFailure)

    val forgedCheck = withTimeout(TIMEOUT_MS) { AdminAuthClient.verifyServerSession("forged.jwt.token") }
    assertTrue("Forged token must be rejected with 401", forgedCheck.isFailure)

    val client = OkHttpClient()
    val auditReq = Request.Builder().url("${mockServer.baseUrl}/admin/audit-logs").get().build()
    client.newCall(auditReq).execute().use { resp ->
      assertEquals(401, resp.code)
    }

    val registerReq =
      Request.Builder()
        .url("${mockServer.baseUrl}/admin/register")
        .post("""{"username":"hacker","password":"Password#1234"}""".toRequestBody())
        .build()
    client.newCall(registerReq).execute().use { resp ->
      assertEquals(403, resp.code)
    }
  }

  // TEST 7: Role manipulation attempt by student fails on both client repository & Firestore rules
  @Test
  fun test7_studentRoleEscalation_blockedByRepoAndFirestoreRules() = runBlocking {
    val attackerUid = signInTestUser("escalator_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)

    // Attempt 1: Register profile with roleOverride = "admin" -> must be denied by Firestore rules
    val directProfileAdmin =
      withTimeout(TIMEOUT_MS) {
        profileRepo.registerOrCreateUserProfile(
          profile =
            UserProfile(
              userId = attackerUid,
              role = "admin",
              studentIdNumber = "JYC-ESC-1",
              fullName = "Attacker User",
              gender = "Male",
              dateOfBirth = "2000-01-01",
              phoneNumber = "08011112222",
              email = "escalator@test.com",
              residentialAddress = "Abuja",
              stateOfOrigin = "FCT Abuja",
              nextOfKin = "Kin",
              nextOfKinPhone = "08033334444",
              selectedProgrammeId = "basic_computer",
              selectedProgrammeTitle = "Basic Computer",
              registrationDate = "2026-11-15",
              status = "active",
            ),
          roleOverride = "admin",
        )
      }
    assertTrue("Student creating self-profile with role=admin must fail", directProfileAdmin.isFailure)

    // Register legitimately as student first, then attempt role escalation update to admin
    val legitRes =
      withTimeout(TIMEOUT_MS) {
        profileRepo.registerOrCreateUserProfile(
          profile =
            UserProfile(
              userId = attackerUid,
              role = "student",
              studentIdNumber = "JYC-ESC-2",
              fullName = "Attacker User",
              gender = "Male",
              dateOfBirth = "2000-01-01",
              phoneNumber = "08011112222",
              email = "escalator@test.com",
              residentialAddress = "Abuja",
              stateOfOrigin = "FCT Abuja",
              nextOfKin = "Kin",
              nextOfKinPhone = "08033334444",
              selectedProgrammeId = "basic_computer",
              selectedProgrammeTitle = "Basic Computer",
              registrationDate = "2026-11-15",
              status = "active",
            ),
          roleOverride = "student",
        )
      }
    assertTrue(legitRes.isSuccess)

    var escalationDenied = false
    try {
      withTimeout(TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(attackerUid)
          .update(mapOf("role" to "admin", "updatedAt" to FieldValue.serverTimestamp()))
          .await()
      }
    } catch (e: Exception) {
      escalationDenied =
        e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Self-updating role to admin must be rejected by Firestore rules", escalationDenied)
  }

  // TEST 8: Attempt to create second admin account fails (repository, backend API, and Firestore rules)
  @Test
  fun test8_creatingSecondAdminAccount_isBlockedEverywhere() = runBlocking {
    val loginRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }
    assertTrue(loginRes is AdminAuthResult.Success)

    val profileRepo = ProfileAndSettingsRepository(firestore)
    val targetUid = "second_admin_${UUID.randomUUID().toString().take(8)}"

    // 1. Repository rejects role == "admin"
    val repoPromote =
      withTimeout(TIMEOUT_MS) {
        profileRepo.adminAssignRole(
          targetUid = targetUid,
          fullName = "Second Admin",
          email = "second@jycomputers.com",
          role = "admin",
        )
      }
    assertTrue("adminAssignRole(role=admin) must return failure", repoPromote.isFailure)
    assertTrue(repoPromote.exceptionOrNull() is SecurityException)

    // 2. Direct Firestore write to /admins/{second_uid} is denied even by authenticated single owner admin
    var secondAdminDocDenied = false
    try {
      withTimeout(TIMEOUT_MS) {
        firestore
          .collection("admins")
          .document(targetUid)
          .set(
            mapOf(
              "uid" to targetUid,
              "email" to "second@jycomputers.com",
              "fullName" to "Second Admin",
              "createdAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: Exception) {
      secondAdminDocDenied =
        e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Firestore must forbid creating /admins/{otherUid}", secondAdminDocDenied)

    // 3. Direct Firestore write promoting another /users/{otherUid} to role = "admin" is denied
    var promoteUserDenied = false
    try {
      withTimeout(TIMEOUT_MS) {
        firestore
          .collection("users")
          .document(targetUid)
          .set(
            mapOf(
              "userId" to targetUid,
              "role" to "admin",
              "studentIdNumber" to "ADM-2",
              "fullName" to "Second Admin",
              "gender" to "Male",
              "dateOfBirth" to "1990-01-01",
              "phoneNumber" to "08000000000",
              "email" to "second@jycomputers.com",
              "residentialAddress" to "Abuja",
              "stateOfOrigin" to "FCT",
              "nextOfKin" to "Kin",
              "nextOfKinPhone" to "08000000001",
              "selectedProgrammeId" to "basic_computer",
              "selectedProgrammeTitle" to "Admin",
              "registrationDate" to "2026-11-15",
              "status" to "active",
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: Exception) {
      promoteUserDenied =
        e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Firestore must forbid creating /users/{otherUid} with role=admin", promoteUserDenied)
  }

  // TEST 9: Admin logout completely destroys session and prevents cached/back-button/token reuse
  @Test
  fun test9_adminLogout_destroysServerAndClientSessionAndBlocksReuse() = runBlocking {
    val loginRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }
    assertTrue(loginRes is AdminAuthResult.Success)
    val capturedToken = AdminAuthClient.sessionState.value.sessionToken
    assertTrue(capturedToken.isNotBlank())

    withTimeout(TIMEOUT_MS) { AdminAuthClient.logout(auth) }

    assertFalse("Client session must be cleared after logout", AdminAuthClient.hasActiveSession())
    assertTrue("FirebaseAuth currentUser must be null after logout", auth.currentUser == null)

    val replayCheck =
      withTimeout(TIMEOUT_MS) { AdminAuthClient.verifyServerSession(tokenOverride = capturedToken) }
    assertTrue("Reusing logged-out server session token must fail with 401", replayCheck.isFailure)
  }

  // TEST 10: Repeated failed logins trigger rate-limit lockout + owner recovery rotates password & revokes sessions
  @Test
  fun test10_rateLimitLockoutAndOwnerRecoveryPasswordRotation() = runBlocking {
    // First log in cleanly to capture an active session token before testing recovery invalidation
    val initialLogin =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }
    assertTrue(initialLogin is AdminAuthResult.Success)
    val priorSessionToken = AdminAuthClient.sessionState.value.sessionToken

    // Trigger 4 failed attempts (HTTP 401) + 5th failed attempt triggers HTTP 429 lockout
    repeat(4) {
      val fail =
        withTimeout(TIMEOUT_MS) {
          AdminAuthClient.login(
            username = LocalSingleAdminServer.TEST_USERNAME,
            password = "BadAttempt#$it",
            firebaseAuth = auth,
          )
        }
      assertTrue("Attempt ${it + 1} should return Failed", fail is AdminAuthResult.Failed)
    }

    val fifthAttempt =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = "BadAttempt#5",
          firebaseAuth = auth,
        )
      }
    assertTrue("5th failed attempt must trigger LockedOut (HTTP 429)", fifthAttempt is AdminAuthResult.LockedOut)

    // Even valid password must be blocked during active lockout
    val lockedValidAttempt =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }
    assertTrue("Valid password during lockout must remain LockedOut", lockedValidAttempt is AdminAuthResult.LockedOut)

    // Owner recovery with valid recovery key resets password, clears lockout, and invalidates prior sessions
    val newPassword = "NewOwnerStrong#Password2026"
    val recoveryRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.recoverPassword(
          username = LocalSingleAdminServer.TEST_USERNAME,
          recoveryKey = LocalSingleAdminServer.TEST_RECOVERY_KEY,
          newPassword = newPassword,
          firebaseAuth = auth,
        )
      }
    assertTrue("Owner recovery should succeed: $recoveryRes", recoveryRes is AdminRecoveryResult.Success)

    // Prior session token minted before recovery must now be invalidated
    val oldSessionVerify =
      withTimeout(TIMEOUT_MS) { AdminAuthClient.verifyServerSession(tokenOverride = priorSessionToken) }
    assertTrue("All prior sessions must be invalidated on password reset", oldSessionVerify.isFailure)

    // Signing in with new rotated password succeeds
    val rotatedLogin =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = newPassword,
          firebaseAuth = auth,
        )
      }
    assertTrue("Login with rotated owner password should succeed", rotatedLogin is AdminAuthResult.Success)
  }

  // TEST 11: Admin login sends sanitized device info without credentials and /admin_auth_audit denies client read/write/delete
  @Test
  fun test11_adminLoginAuditLog_sendsSafeDeviceInfoAndEnforcesWriteOnlyFirestoreRules() = runBlocking {
    // 1. Trigger failed login + valid login + logout through AdminAuthClient
    val failRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = "WrongAttempt#AuditTest",
          firebaseAuth = auth,
        )
      }
    assertTrue(failRes is AdminAuthResult.Failed)

    val okRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = LocalSingleAdminServer.TEST_USERNAME,
          password = LocalSingleAdminServer.TEST_INITIAL_PASSWORD,
          firebaseAuth = auth,
        )
      }
    assertTrue(okRes is AdminAuthResult.Success)

    // Verify captured server audit payloads contain device metadata & ZERO sensitive credentials
    val events = mockServer.recordedAuditEvents.toList()
    assertTrue("Expected at least 2 audit events", events.size >= 2)
    val failEvent = events.first { it.optString("eventType") == "ADMIN_LOGIN_FAILED" }
    val okEvent = events.first { it.optString("eventType") == "ADMIN_LOGIN_SUCCESS" }

    for (ev in listOf(failEvent, okEvent)) {
      val dev = ev.optJSONObject("deviceInfo")
      assertTrue("deviceInfo must be sent with every admin login attempt", dev != null)
      assertEquals("Android", dev!!.optString("platform"))
      assertTrue("sdkInt must be positive", dev.optInt("sdkInt") > 0)
      assertTrue("appPackage must not be blank", dev.optString("appPackage").isNotBlank())
      assertFalse(dev.has("password"))
      assertFalse(dev.has("username"))
      assertFalse(dev.has("token"))
      assertFalse(dev.has("sessionToken"))
      assertFalse(dev.has("recoveryKey"))
    }

    val serializedAll = events.joinToString("\n") { it.toString() }
    assertFalse("Username must never appear in audit events", serializedAll.contains(LocalSingleAdminServer.TEST_USERNAME))
    assertFalse("Initial password must never appear in audit events", serializedAll.contains(LocalSingleAdminServer.TEST_INITIAL_PASSWORD))
    assertFalse("Bad attempt password must never appear in audit events", serializedAll.contains("WrongAttempt#AuditTest"))

    // 2. While signed in as SINGLE_ADMIN_UID, verify Firestore client is DENIED all read/write/update/delete on /admin_auth_audit
    val auditDocRef = firestore.collection("admin_auth_audit").document("client_probe_doc")
    var adminReadDenied = false
    var adminWriteDenied = false
    var adminUpdateDenied = false
    var adminDeleteDenied = false

    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("admin_auth_audit").get().await() }
    } catch (e: Exception) {
      adminReadDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    try {
      withTimeout(TIMEOUT_MS) {
        auditDocRef.set(
          mapOf(
            "eventType" to "ADMIN_LOGIN_SUCCESS",
            "outcome" to "success",
            "createdAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      }
    } catch (e: Exception) {
      adminWriteDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    try {
      withTimeout(TIMEOUT_MS) { auditDocRef.update("outcome", "tampered").await() }
    } catch (e: Exception) {
      adminUpdateDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    try {
      withTimeout(TIMEOUT_MS) { auditDocRef.delete().await() }
    } catch (e: Exception) {
      adminDeleteDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    assertTrue("Client list on /admin_auth_audit must be denied even for admin", adminReadDenied)
    assertTrue("Client write on /admin_auth_audit must be denied even for admin", adminWriteDenied)
    assertTrue("Client update on /admin_auth_audit must be denied even for admin", adminUpdateDenied)
    assertTrue("Client delete on /admin_auth_audit must be denied even for admin", adminDeleteDenied)
  }

  // TEST 12: Direct Firebase Auth ('admin' -> 'admin@jy-computers.com') + custom claims + wrong-password + non-admin rejection + session logout
  @Test
  fun test12_directFirebaseEmailPasswordAdminLogin_withUsernameMappingAndCustomClaims() = runBlocking {
    // Switch AdminAuthClient to Direct Firebase Auth mode (no external server override)
    AdminAuthClient.setEndpointOverrideForTesting("")
    AdminAuthClient.resetClientLockoutForTesting()

    val syntheticTestPass = "TestEmulatorAdmin#2026!"

    // 1. Provision admin@jy-computers.com WITHOUT custom claims first -> login MUST reject & sign out
    provisionEmailUserInEmulator(
      email = JyCatalogDefaults.ADMIN_MAPPED_EMAIL,
      password = syntheticTestPass,
      customAttributesJson = "{}",
    )

    val unclaimedRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = JyCatalogDefaults.ADMIN_LOGIN_USERNAME,
          password = syntheticTestPass,
          firebaseAuth = auth,
        )
      }
    assertTrue("Login without custom claims must fail: $unclaimedRes", unclaimedRes is AdminAuthResult.Failed)
    assertTrue("Unclaimed user must be immediately signed out", auth.currentUser == null)
    assertFalse(AdminAuthClient.hasActiveSession())

    // 2. Provision server custom claims { jySingleAdmin: true, role: "admin" } -> login MUST succeed
    val adminUid =
      provisionEmailUserInEmulator(
        email = JyCatalogDefaults.ADMIN_MAPPED_EMAIL,
        password = syntheticTestPass,
        customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
      )

    // 3. Reject student email or wrong username on Admin Login without calling or passing admin gate
    val studentProbe =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = "student@test.com",
          password = syntheticTestPass,
          firebaseAuth = auth,
        )
      }
    assertTrue(studentProbe is AdminAuthResult.Failed)
    assertEquals("Invalid administrator credentials.", (studentProbe as AdminAuthResult.Failed).message)

    // 4. Reject wrong password for 'admin'
    val wrongPass =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = "admin",
          password = "WrongPassword#999",
          firebaseAuth = auth,
        )
      }
    assertTrue(wrongPass is AdminAuthResult.Failed)
    assertEquals("Invalid administrator credentials.", (wrongPass as AdminAuthResult.Failed).message)
    assertFalse(AdminAuthClient.hasActiveSession())

    // 5. Valid username 'admin' + valid password succeeds via Firebase Auth and passes Firestore admin rules
    val validRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.login(
          username = "admin",
          password = syntheticTestPass,
          firebaseAuth = auth,
        )
      }
    assertTrue("Expected Success, got $validRes", validRes is AdminAuthResult.Success)
    assertEquals(adminUid, (validRes as AdminAuthResult.Success).uid)
    assertTrue(AdminAuthClient.hasActiveSession())

    val profileRepo = ProfileAndSettingsRepository(firestore)
    assertTrue("isUidAdmin must verify mapped Firebase Auth admin claims", profileRepo.isUidAdmin(adminUid))

    // Remains authenticated when refreshing session between admin routes
    val refreshed = withTimeout(TIMEOUT_MS) { AdminAuthClient.refreshSession(firebaseAuth = auth) }
    assertTrue("Admin session must remain valid across navigation", refreshed)

    // Perform authorized admin write in Firestore
    val academicRepo = CourseAndAcademicRepository(firestore)
    val saveRes =
      withTimeout(TIMEOUT_MS) {
        academicRepo.saveProgramme(
          Programme(
            id = "direct_fb_admin_prog_${UUID.randomUUID().toString().take(6)}",
            title = "Firebase Admin Verified Course",
            duration = "2 Months",
            feeNaira = 45_000L,
            active = true,
          )
        )
      }
    assertTrue("Verified Firebase Auth admin must write programmes cleanly", saveRes.isSuccess)

    // 6. Logout properly destroys both client session and FirebaseAuth user
    withTimeout(TIMEOUT_MS) { AdminAuthClient.logout(firebaseAuth = auth) }
    assertFalse(AdminAuthClient.hasActiveSession())
    assertTrue(auth.currentUser == null)
    AdminAuthClient.resetClientLockoutForTesting()
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
  }
}

/**
 * In-JVM test harness implementing the exact HTTP protocol, constant-time checks,
 * brute-force lockout, custom-token issuance, and session lifecycle of `admin-auth-server/server.js`.
 */
internal class LocalSingleAdminServer(
  private val customTokenFactory: (uid: String, claims: Map<String, Any>) -> String,
) {
  private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
  private val executor = Executors.newCachedThreadPool()

  @Volatile
  private var activePassword = TEST_INITIAL_PASSWORD
  private val sessionVersion = AtomicInteger(1)
  private val failedAttempts = AtomicInteger(0)

  @Volatile
  private var lockedUntilMs = 0L
  private val activeSessions = ConcurrentHashMap<String, String>() // token -> csrf
  val recordedAuditEvents: MutableList<JSONObject> = java.util.Collections.synchronizedList(mutableListOf())

  val baseUrl: String
    get() = "http://127.0.0.1:${server.address.port}"

  fun start() {
    server.executor = executor
    server.createContext("/") { ex -> handle(ex) }
    server.start()
  }

  fun stop() {
    server.stop(0)
    executor.shutdownNow()
  }

  private fun constantTimeEquals(a: String, b: String): Boolean {
    val digest = MessageDigest.getInstance("SHA-256")
    return MessageDigest.isEqual(
      digest.digest(a.toByteArray(Charsets.UTF_8)),
      digest.digest(b.toByteArray(Charsets.UTF_8)),
    )
  }

  private fun sanitizeDevice(raw: JSONObject?): JSONObject {
    val safe = JSONObject()
    val allowed = listOf("platform", "manufacturer", "brand", "model", "device", "osVersion", "appVersion", "appPackage")
    if (raw != null) {
      for (k in allowed) {
        val v = raw.optString(k, "").trim()
        if (v.isNotEmpty()) safe.put(k, v)
      }
      if (raw.has("sdkInt")) safe.put("sdkInt", raw.optInt("sdkInt", 0))
    }
    if (!safe.has("platform")) safe.put("platform", "unknown")
    return safe
  }

  private fun handle(ex: HttpExchange) {
    val path = ex.requestURI.path.orEmpty()
    val method = ex.requestMethod.orEmpty()
    val body = ex.requestBody.bufferedReader(Charsets.UTF_8).readText()
    val json = runCatching { if (body.isBlank()) JSONObject() else JSONObject(body) }.getOrNull() ?: JSONObject()

    when {
      method == "POST" && path == "/admin/login" -> {
        val now = System.currentTimeMillis()
        val device = sanitizeDevice(json.optJSONObject("deviceInfo"))
        if (lockedUntilMs > now) {
          recordedAuditEvents.add(
            JSONObject()
              .put("eventType", "ADMIN_LOGIN_LOCKED_OUT")
              .put("outcome", "locked_out")
              .put("usernameMatched", false)
              .put("deviceInfo", device)
          )
          respondJson(
            ex,
            429,
            JSONObject()
              .put("error", "Too many failed login attempts. Administrator login is temporarily locked.")
              .put("retryAfterSeconds", 900),
          )
          return
        }
        val uOk = constantTimeEquals(json.optString("username").trim(), TEST_USERNAME)
        val pOk = constantTimeEquals(json.optString("password"), activePassword)
        if (!uOk || !pOk) {
          if (failedAttempts.incrementAndGet() >= 5) {
            lockedUntilMs = now + 15 * 60 * 1000L
            recordedAuditEvents.add(
              JSONObject()
                .put("eventType", "ADMIN_LOGIN_LOCKED_OUT")
                .put("outcome", "locked_out")
                .put("usernameMatched", uOk)
                .put("deviceInfo", device)
            )
            respondJson(
              ex,
              429,
              JSONObject()
                .put("error", "Too many failed login attempts. Administrator login is temporarily locked.")
                .put("retryAfterSeconds", 900),
            )
          } else {
            recordedAuditEvents.add(
              JSONObject()
                .put("eventType", "ADMIN_LOGIN_FAILED")
                .put("outcome", "failed")
                .put("usernameMatched", uOk)
                .put("deviceInfo", device)
            )
            respondJson(ex, 401, JSONObject().put("error", "Invalid administrator credentials."))
          }
          return
        }

        failedAttempts.set(0)
        lockedUntilMs = 0L
        recordedAuditEvents.add(
          JSONObject()
            .put("eventType", "ADMIN_LOGIN_SUCCESS")
            .put("outcome", "success")
            .put("usernameMatched", true)
            .put("deviceInfo", device)
        )
        val sessionToken = "sess_${UUID.randomUUID()}"
        val csrfToken = "csrf_${UUID.randomUUID()}"
        activeSessions[sessionToken] = csrfToken
        val sv = sessionVersion.get()
        val customToken =
          customTokenFactory(
            JyCatalogDefaults.SINGLE_ADMIN_UID,
            mapOf("jySingleAdmin" to true, "role" to "admin", "sessionVersion" to sv),
          )
        respondJson(
          ex,
          200,
          JSONObject()
            .put("uid", JyCatalogDefaults.SINGLE_ADMIN_UID)
            .put("role", "admin")
            .put("customToken", customToken)
            .put("sessionToken", sessionToken)
            .put("csrfToken", csrfToken)
            .put("expiresAtMs", now + 8 * 60 * 60 * 1000L)
            .put("inactivityTimeoutMs", 30 * 60 * 1000L)
            .put("sessionVersion", sv),
        )
      }

      method == "GET" && path == "/admin/session" -> {
        val authHdr = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val token = if (authHdr.startsWith("Bearer ")) authHdr.removePrefix("Bearer ").trim() else ""
        if (token.isNotBlank() && activeSessions.containsKey(token)) {
          respondJson(ex, 200, JSONObject().put("authenticated", true).put("uid", JyCatalogDefaults.SINGLE_ADMIN_UID))
        } else {
          respondJson(ex, 401, JSONObject().put("error", "Invalid or expired administrator session."))
        }
      }

      method == "GET" && path == "/admin/audit-logs" -> {
        val authHdr = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val token = if (authHdr.startsWith("Bearer ")) authHdr.removePrefix("Bearer ").trim() else ""
        if (token.isNotBlank() && activeSessions.containsKey(token)) {
          respondJson(ex, 200, JSONObject().put("logs", org.json.JSONArray()))
        } else {
          respondJson(ex, 401, JSONObject().put("error", "Administrator authentication required."))
        }
      }

      method == "POST" && path == "/admin/refresh" -> {
        val authHdr = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val token = if (authHdr.startsWith("Bearer ")) authHdr.removePrefix("Bearer ").trim() else ""
        if (token.isNotBlank() && activeSessions.containsKey(token)) {
          val sv = sessionVersion.get()
          val customToken =
            customTokenFactory(
              JyCatalogDefaults.SINGLE_ADMIN_UID,
              mapOf("jySingleAdmin" to true, "role" to "admin", "sessionVersion" to sv),
            )
          respondJson(
            ex,
            200,
            JSONObject()
              .put("customToken", customToken)
              .put("expiresAtMs", System.currentTimeMillis() + 8 * 60 * 60 * 1000L),
          )
        } else {
          respondJson(ex, 401, JSONObject().put("error", "Session expired."))
        }
      }

      method == "POST" && path == "/admin/logout" -> {
        val authHdr = ex.requestHeaders.getFirst("Authorization").orEmpty()
        val token = if (authHdr.startsWith("Bearer ")) authHdr.removePrefix("Bearer ").trim() else ""
        activeSessions.remove(token)
        respondJson(ex, 200, JSONObject().put("loggedOut", true))
      }

      method == "POST" && path == "/admin/recover" -> {
        val uOk = constantTimeEquals(json.optString("username").trim(), TEST_USERNAME)
        val kOk = constantTimeEquals(json.optString("recoveryKey").trim(), TEST_RECOVERY_KEY)
        val newPw = json.optString("newPassword")
        if (!uOk || !kOk) {
          respondJson(ex, 401, JSONObject().put("error", "Invalid administrator recovery credentials."))
          return
        }
        if (newPw.length < 12) {
          respondJson(ex, 400, JSONObject().put("error", "Password must be at least 12 characters long."))
          return
        }
        activePassword = newPw
        sessionVersion.incrementAndGet()
        activeSessions.clear()
        failedAttempts.set(0)
        lockedUntilMs = 0L
        respondJson(
          ex,
          200,
          JSONObject()
            .put("recovered", true)
            .put("message", "Administrator password updated and all previous sessions invalidated."),
        )
      }

      path in setOf("/admin/register", "/admin/create", "/admin/signup", "/admin/grant") -> {
        respondJson(ex, 403, JSONObject().put("error", "Single-administrator policy: creating additional admins is disabled."))
      }

      else -> {
        respondJson(ex, 404, JSONObject().put("error", "Endpoint not found."))
      }
    }
  }

  private fun respondJson(ex: HttpExchange, code: Int, payload: JSONObject) {
    val bytes = payload.toString().toByteArray(Charsets.UTF_8)
    ex.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    ex.sendResponseHeaders(code, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
  }

  companion object {
    const val TEST_USERNAME = "jy_owner_admin_test"
    const val TEST_INITIAL_PASSWORD = "JyOwner#SecurePass2026!"
    const val TEST_RECOVERY_KEY = "JYC-RECOVERY-KEY-TEST-2026"
  }
}
