package com.example.security

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.data.remote.AdminAuthClient
import com.example.data.remote.AdminAuthResult
import com.example.data.repository.CourseAndAcademicRepository
import com.example.data.repository.ProfileAndSettingsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Option A security verification suite covering JY-COMPUTERS Exclusive Owner Google Sign-In
 * (`joshua.dangana@gmail.com`) + Firebase custom claims (`jySingleAdmin = true`, `role = "admin"`),
 * single-administrator enforcement, student/instructor isolation, and Firestore rules.
 */
class SingleAdminSecurityTest : FirestoreEmulatorTestBase() {

  @Before
  fun resetAuthEmulatorForTest() = runBlocking {
    clearAuthEmulatorAccounts()
  }

  @After
  fun cleanUpSession() = runBlocking {
    AdminAuthClient.logout(auth)
  }

  // TEST 1: Valid owner Google Sign-In + server custom claims grants admin access
  @Test
  fun test1_validOwnerGoogleSignInWithClaims_authenticatesSingleAdminAndGrantsAccess() = runBlocking {
    val ownerUid = signInSingleOwnerAdmin()
    val result =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
      }

    assertTrue("Expected Success, got $result", result is AdminAuthResult.Success)
    assertEquals(JyCatalogDefaults.SINGLE_ADMIN_UID, ownerUid)
    assertEquals(JyCatalogDefaults.SINGLE_ADMIN_UID, (result as AdminAuthResult.Success).uid)
    assertTrue("In-memory admin session must be active", AdminAuthClient.hasActiveSession())

    val profileRepo = ProfileAndSettingsRepository(firestore)
    assertTrue("Owner admin UID + custom claims must pass isUidAdmin", profileRepo.isUidAdmin(ownerUid))

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
    assertTrue("Owner admin must be allowed to manage programmes", saveProgRes.isSuccess)
  }

  // TEST 2: Owner Google Sign-In WITHOUT custom claims fails cleanly and signs out
  @Test
  fun test2_ownerGoogleAccountWithoutClaims_isRejectedAndSignedOut() = runBlocking {
    val uidWithoutClaims =
      signInGoogleUserInEmulator(
        email = JyCatalogDefaults.OWNER_ADMIN_EMAIL,
        subject = "owner_no_claims_sub",
        emailVerified = true,
        customAttributesJson = "{}",
      )

    val profileRepo = ProfileAndSettingsRepository(firestore)
    assertFalse("Owner without claims must fail isUidAdmin", profileRepo.isUidAdmin(uidWithoutClaims))

    val verifyRes =
      withTimeout(TIMEOUT_MS) {
        AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
      }
    assertTrue("Expected Failed without custom claims, got $verifyRes", verifyRes is AdminAuthResult.Failed)
    assertFalse("Client admin session must stay inactive", AdminAuthClient.hasActiveSession())
    assertTrue("Unauthorized user must be signed out immediately", auth.currentUser == null)
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

  // TEST 4: Non-owner Google accounts (even with spoofed claims), unverified owner email, and non-Google providers fail
  @Test
  fun test4_nonOwnerGoogleOrUnverifiedOrWrongProvider_rejectedFromAdminAccess() = runBlocking {
    val profileRepo = ProfileAndSettingsRepository(firestore)

    // 4a. Other Google user EVEN WITH admin custom claims is rejected by identity & firestore.rules
    val intruderUid =
      signInGoogleUserInEmulator(
        email = "intruder_${UUID.randomUUID().toString().take(6)}@gmail.com",
        subject = "intruder_spoof_sub",
        emailVerified = true,
        customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
      )
    assertFalse("Non-owner Google account with claims must fail isUidAdmin", profileRepo.isUidAdmin(intruderUid))

    var intruderDenied = false
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
      intruderDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Firestore must reject other email even with admin claims", intruderDenied)

    val intruderClientCheck = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue(intruderClientCheck is AdminAuthResult.Failed)
    assertTrue("Intruder must be signed out", auth.currentUser == null)

    // 4b. Unverified owner Google email with claims must fail (isolated emulator account)
    clearAuthEmulatorAccounts()
    val unverifiedOwnerUid =
      signInGoogleUserInEmulator(
        email = JyCatalogDefaults.OWNER_ADMIN_EMAIL,
        subject = "owner_unverified_sub",
        emailVerified = false,
        customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
      )
    assertFalse("Unverified owner email must fail isUidAdmin", profileRepo.isUidAdmin(unverifiedOwnerUid))
    val unverifiedCheck = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue(unverifiedCheck is AdminAuthResult.Failed)

    // 4c. Password provider with owner email + claims must fail Option A google.com check (isolated emulator account)
    clearAuthEmulatorAccounts()
    val passwordOwnerEmail = JyCatalogDefaults.OWNER_ADMIN_EMAIL
    val passwordOwnerPass = "PassProviderProbe#2026"
    val passwordUid =
      provisionEmailUserInEmulator(
        email = passwordOwnerEmail,
        password = passwordOwnerPass,
        customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
      )
    @Suppress("DEPRECATION")
    auth.signInWithEmailAndPassword(passwordOwnerEmail, passwordOwnerPass).await()
    assertFalse("Non-Google sign-in provider must fail Option A isUidAdmin", profileRepo.isUidAdmin(passwordUid))
    val wrongProviderRes = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue(wrongProviderRes is AdminAuthResult.Failed)

    // 4d. Matching owner Google email + verified + claims + google.com provider with WRONG UID must fail
    clearAuthEmulatorAccounts()
    val wrongUidForOwnerEmail =
      signInGoogleUserInEmulator(
        email = JyCatalogDefaults.OWNER_ADMIN_EMAIL,
        subject = "owner_wrong_uid_sub",
        emailVerified = true,
        customAttributesJson = """{"jySingleAdmin":true,"role":"admin"}""",
        fixedUid = "wrong_uid_not_pinned_owner",
      )
    assertFalse("Wrong UID must fail isUidAdmin even with owner email & claims", profileRepo.isUidAdmin(wrongUidForOwnerEmail))
    var wrongUidFirestoreDenied = false
    try {
      withTimeout(TIMEOUT_MS) {
        firestore
          .collection("settings")
          .document("institution")
          .set(
            mapOf(
              "institutionName" to "Wrong UID Attempt",
              "opayAccountName" to "Fake",
              "opayAccountNumber" to "0000000000",
              "activeSession" to "2026",
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: Exception) {
      wrongUidFirestoreDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Firestore must reject owner email with wrong UID", wrongUidFirestoreDenied)
    val wrongUidClientRes = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue("Wrong UID must fail verifyOwnerGoogleSignIn", wrongUidClientRes is AdminAuthResult.Failed)
    assertTrue("Wrong UID must be signed out immediately", auth.currentUser == null)
  }

  // TEST 5: Direct/manual route navigation guard & activity logs restriction
  @Test
  fun test5_manualRouteNavigationCheck_blocksNonAdminAndRestrictedRoutes() = runBlocking {
    val studentUid = signInTestUser("route_probe_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)

    assertFalse(profileRepo.isUidAdmin(studentUid))
    assertTrue("admin_dashboard" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_students" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_roles" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("admin_settings" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("reports" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)
    assertTrue("registration" in com.example.ui.navigation.AppRoutes.ADMIN_ONLY_ROUTES)

    var logsDenied = false
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("activity_logs").get().await() }
    } catch (e: Exception) {
      logsDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Student reading admin activity logs must fail", logsDenied)
  }

  // TEST 6: Unauthenticated Firestore access to protected collections fails
  @Test
  fun test6_unauthenticatedFirestoreRequests_areDenied() = runBlocking {
    auth.signOut()
    var deniedSettings = false
    var deniedUsers = false
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("settings").document("institution").get().await() }
    } catch (e: Exception) {
      deniedSettings = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    try {
      withTimeout(TIMEOUT_MS) { firestore.collection("users").get().await() }
    } catch (e: Exception) {
      deniedUsers = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Unauthenticated read of settings must fail", deniedSettings)
    assertTrue("Unauthenticated read of users must fail", deniedUsers)
  }

  // TEST 7: Role manipulation attempt by student fails on both client repository & Firestore rules
  @Test
  fun test7_studentRoleEscalation_blockedByRepoAndFirestoreRules() = runBlocking {
    val attackerUid = signInTestUser("escalator_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)

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

  // TEST 8: Attempt to create second admin account fails (repository & Firestore rules)
  @Test
  fun test8_creatingSecondAdminAccount_isBlockedEverywhere() = runBlocking {
    val ownerUid = signInSingleOwnerAdmin()
    val loginRes = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue(loginRes is AdminAuthResult.Success)

    val profileRepo = ProfileAndSettingsRepository(firestore)
    withTimeout(TIMEOUT_MS) {
      profileRepo.ensureAdminDirectoryEntry(
        uid = ownerUid,
        fullName = "Joshua Dangana",
        email = JyCatalogDefaults.OWNER_ADMIN_EMAIL,
      )
    }
    val ownDirectoryDoc =
      withTimeout(TIMEOUT_MS) { firestore.collection("directory").document(ownerUid).get().await() }
    assertTrue("Owner admin directory entry should exist", ownDirectoryDoc.exists())

    val targetUid = "second_admin_${UUID.randomUUID().toString().take(8)}"

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

  // TEST 9: Admin logout destroys client & Firebase sessions and refreshSession checks active user
  @Test
  fun test9_adminSessionLifecycle_refreshAndLogoutDestroySessionAndPreventReuse() = runBlocking {
    val ownerUid = signInSingleOwnerAdmin()
    val res = AdminAuthClient.verifyOwnerGoogleSignIn(firebaseAuth = auth)
    assertTrue(res is AdminAuthResult.Success)
    assertTrue(AdminAuthClient.hasActiveSession())

    assertTrue(
      "Active verified owner session must remain valid across navigation",
      AdminAuthClient.refreshSession(firebaseAuth = auth),
    )

    withTimeout(TIMEOUT_MS) { AdminAuthClient.logout(firebaseAuth = auth) }
    assertFalse("Client session must be cleared after logout", AdminAuthClient.hasActiveSession())
    assertTrue("FirebaseAuth currentUser must be null after logout", auth.currentUser == null)
    assertFalse(
      "refreshSession after logout must return false",
      AdminAuthClient.refreshSession(firebaseAuth = auth),
    )

    // Cold-start session sync helper still works when user re-authenticates
    signInSingleOwnerAdmin()
    AdminAuthClient.syncDirectFirebaseSession(ownerUid)
    assertTrue(AdminAuthClient.hasActiveSession())
  }

  // TEST 10: Write-only /admin_auth_audit & device info sanitization
  @Test
  fun test10_safeDeviceTelemetry_andWriteOnlyAuditRulesEnforcedEvenForOwnerAdmin() = runBlocking {
    val device = AdminAuthClient.collectSafeDeviceInfo()
    assertEquals("Android", device.optString("platform"))
    assertTrue(device.optInt("sdkInt") > 0)
    assertTrue(device.optString("appPackage").isNotBlank())
    assertFalse(device.has("password"))
    assertFalse(device.has("token"))
    assertFalse(device.has("sessionToken"))

    signInSingleOwnerAdmin()
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

    assertTrue("Client list on /admin_auth_audit must be denied even for owner admin", adminReadDenied)
    assertTrue("Client write on /admin_auth_audit must be denied even for owner admin", adminWriteDenied)
    assertTrue("Client update on /admin_auth_audit must be denied even for owner admin", adminUpdateDenied)
    assertTrue("Client delete on /admin_auth_audit must be denied even for owner admin", adminDeleteDenied)
  }

  private companion object {
    const val TIMEOUT_MS = 8000L
  }
}
