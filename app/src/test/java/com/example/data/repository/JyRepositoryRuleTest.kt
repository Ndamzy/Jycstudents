package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Enrollment
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.JyServiceItem
import com.example.data.model.Payment
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JyRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun registerStudentAndEnrollment_authenticatedOwner_succeedsAndStreams() = runBlocking {
    val uid = signInTestUser("student_alice_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)
    val academicRepo = CourseAndAcademicRepository(firestore)

    val profile =
      UserProfile(
        userId = uid,
        role = "student",
        studentIdNumber = "JYC-2026-9901",
        fullName = "Alice Student",
        gender = "Female",
        dateOfBirth = "2003-04-12",
        phoneNumber = "08011223344",
        email = "alice@test.com",
        residentialAddress = "10 Tech Road, Abuja",
        stateOfOrigin = "FCT Abuja",
        localGovernmentArea = "AMAC",
        nextOfKin = "Grace Student",
        nextOfKinPhone = "08099887766",
        selectedProgrammeId = "coding_programme",
        selectedProgrammeTitle = "Coding Programme",
        trainingLevel = "Intermediate",
        registrationDate = "2026-11-13",
        approvalStatus = "pending",
        paymentStatus = "unpaid",
        courseProgressPercent = 0,
        status = "active",
      )

    val regResult = withTimeout(DEFAULT_TIMEOUT_MS) { profileRepo.registerOrCreateUserProfile(profile) }
    assertTrue(regResult.isSuccess)

    val loadedProfile = withTimeout(FLOW_TIMEOUT_MS) { profileRepo.observeUserProfile(uid).first { it != null } }
    assertEquals("Alice Student", loadedProfile?.fullName)
    assertEquals("AMAC", loadedProfile?.localGovernmentArea)
    assertEquals("Intermediate", loadedProfile?.trainingLevel)

    val updateResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        profileRepo.updateStudentProfile(profile.copy(localGovernmentArea = "Bwari"))
      }
    assertTrue(updateResult.isSuccess)

    val programme =
      Programme(
        id = "coding_programme",
        title = "Coding Programme",
        duration = "6 Months",
        feeNaira = 450_000L,
      )
    val enrollResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        academicRepo.registerCourseEnrollment(
          studentUid = uid,
          studentName = "Alice Student",
          studentIdNumber = "JYC-2026-9901",
          programme = programme,
          session = "2026/2027 Session",
        )
      }
    assertTrue(enrollResult.isSuccess)
    val enrollmentId = enrollResult.getOrThrow()

    val financeRepo = FinanceRepository(firestore)
    val ref1 = "OPAY-${UUID.randomUUID().toString().take(8)}"
    val payResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.submitPayment(
          studentUid = uid,
          studentName = "Alice Student",
          studentIdNumber = "JYC-2026-9901",
          enrollment =
            Enrollment(
              id = enrollmentId,
              programmeId = programme.id,
              programmeTitle = programme.title,
            ),
          amountNaira = 200_000L,
          method = "opay_transfer",
          reference = ref1,
          paymentDate = "2026-11-13",
          senderName = "Alice Student",
          evidenceBase64 = "",
        )
      }
    assertTrue(payResult.isSuccess)

    // Submitting the same reference again must be blocked
    val dupResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.submitPayment(
          studentUid = uid,
          studentName = "Alice Student",
          studentIdNumber = "JYC-2026-9901",
          enrollment =
            Enrollment(
              id = enrollmentId,
              programmeId = programme.id,
              programmeTitle = programme.title,
            ),
          amountNaira = 200_000L,
          method = "opay_transfer",
          reference = ref1.lowercase(),
          paymentDate = "2026-11-13",
          senderName = "Alice Student",
          evidenceBase64 = "",
        )
      }
    assertTrue("Duplicate reference should fail on client check", dupResult.isFailure)

    var cashDenied = false
    try {
      val cashRes =
        withTimeout(DEFAULT_TIMEOUT_MS) {
          financeRepo.submitPayment(
            studentUid = uid,
            studentName = "Alice Student",
            studentIdNumber = "JYC-2026-9901",
            enrollment = Enrollment(id = enrollmentId, programmeId = programme.id, programmeTitle = programme.title),
            amountNaira = 50_000L,
            method = "cash",
            reference = "CASH-${UUID.randomUUID().toString().take(8)}",
            paymentDate = "2026-11-13",
            senderName = "Alice Student",
            evidenceBase64 = "",
          )
        }
      cashDenied = cashRes.isFailure
    } catch (e: Exception) {
      cashDenied = e.unwrapFirestoreException()?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Students must not create cash payments directly", cashDenied)
  }

  @Test
  fun universalServiceRequestAndAdminApproval_preventsDuplicateRefsAndUpdatesRequest() = runBlocking {
    val studentUid = signInTestUser("service_customer_${UUID.randomUUID()}@test.com")
    val financeRepo = FinanceRepository(firestore)

    val nyscSeed = JyCatalogDefaults.DEFAULT_SERVICES.first { it.id == "srv_nysc_registration_assistance" }
    val nyscService =
      JyServiceItem(
        id = nyscSeed.id,
        title = nyscSeed.title,
        category = nyscSeed.category,
        description = nyscSeed.description,
        defaultFeeNaira = nyscSeed.defaultFeeNaira,
        paymentEnabled = nyscSeed.paymentEnabled,
        allowPartialPayment = nyscSeed.allowPartialPayment,
      )

    // Non-admin cannot save service catalog entries
    val nonAdminSave = withTimeout(DEFAULT_TIMEOUT_MS) { financeRepo.saveService(nyscService) }
    assertTrue("Non-admin saving service must be denied", nonAdminSave.isFailure)

    // Customer creates a service request
    val reqRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.createServiceRequest(
          customerUid = studentUid,
          customerName = "Chinedu Okafor",
          customerPhone = JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE,
          customerEmail = "chinedu@test.com",
          customerRegNumber = "JYC-2026-8811",
          service = nyscService,
          details = "NYSC Green Card & Mobilization assistance",
        )
      }
    assertTrue(reqRes.isSuccess)
    val serviceRequest = reqRes.getOrThrow()
    assertEquals("unpaid", serviceRequest.paymentStatus)
    assertTrue(serviceRequest.invoiceNumber.startsWith("INV-"))

    // Customer submits bank_transfer payment for the service request (status must stay pending)
    val refCode = "ACCESS-${UUID.randomUUID().toString().take(8)}"
    val transferRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.submitUniversalPayment(
          customerUid = studentUid,
          customerName = "Chinedu Okafor",
          customerIdNumber = "JYC-2026-8811",
          enrollment = null,
          serviceRequest = serviceRequest,
          amountNaira = nyscService.defaultFeeNaira,
          method = "bank_transfer",
          reference = refCode,
          paymentDate = "2026-11-14",
          senderName = "Chinedu Okafor",
          senderBank = "Access Bank",
          evidenceBase64 = "",
        )
      }
    assertTrue(transferRes.isSuccess)
    val paymentId = transferRes.getOrThrow()

    // Admin reviews & approves the payment
    val adminUid = signInVerifiedAdminUser()
    val adminServiceSave = withTimeout(DEFAULT_TIMEOUT_MS) { financeRepo.saveService(nyscService) }
    assertTrue("Admin saving service failed: ${adminServiceSave.exceptionOrNull()}", adminServiceSave.isSuccess)

    val paymentSnap = withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("payments").document(paymentId).get().await() }
    val loadedPayment = paymentSnap.toObject(Payment::class.java)!!
    assertEquals("pending", loadedPayment.status)

    val approveRes =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.adminReviewPayment(
          payment = loadedPayment,
          approve = true,
          adminRemarks = "Verified on OPay business ledger",
          actorName = "Dr. Joshua Dangana",
        )
      }
    assertTrue("Admin approval failed: ${approveRes.exceptionOrNull()}", approveRes.isSuccess)

    // Verify service_request updated atomically to paid + in_progress
    val updatedReqSnap =
      withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("service_requests").document(serviceRequest.id).get().await() }
    assertEquals("paid", updatedReqSnap.getString("paymentStatus"))
    assertEquals("in_progress", updatedReqSnap.getString("status"))
    assertEquals(nyscService.defaultFeeNaira, updatedReqSnap.getLong("amountPaidNaira"))

    // Verify payment_references registry blocks second payment with identical reference
    val dupAdminRecord =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        financeRepo.recordAdminPayment(
          serviceRequest = serviceRequest,
          amountNaira = 2000L,
          method = "opay_transfer",
          reference = refCode.lowercase(),
          paymentDate = "2026-11-14",
          remarks = "Duplicate attempt",
          actorName = "Admin ($adminUid)",
        )
      }
    assertFalse("Second admin payment with duplicate reference must fail", dupAdminRecord.isSuccess)
  }

  @Test
  fun crossUserProfileAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
    val profileRepo = ProfileAndSettingsRepository(firestore)
    val profile =
      UserProfile(
        userId = aliceUid,
        role = "student",
        studentIdNumber = "JYC-2026-9902",
        fullName = "Alice Private",
        gender = "Female",
        dateOfBirth = "2003-04-12",
        phoneNumber = "08011223344",
        email = "alice@test.com",
        residentialAddress = "10 Tech Road",
        stateOfOrigin = "FCT Abuja",
        localGovernmentArea = "AMAC",
        nextOfKin = "Grace",
        nextOfKinPhone = "08099887766",
        selectedProgrammeId = "diploma_1",
        selectedProgrammeTitle = "Diploma 1",
        trainingLevel = "Beginner",
        registrationDate = "2026-11-13",
        status = "active",
      )
    withTimeout(DEFAULT_TIMEOUT_MS) { profileRepo.registerOrCreateUserProfile(profile) }

    signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
    var denied = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("users").document(aliceUid).get().await() }
    } catch (e: FirebaseFirestoreException) {
      denied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Expected PERMISSION_DENIED when Bob reads Alice PII", denied)

    var logsDenied = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("activity_logs").get().await() }
    } catch (e: FirebaseFirestoreException) {
      logsDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Expected PERMISSION_DENIED when non-admin reads activity_logs", logsDenied)
  }

  @Test
  fun observeProgrammes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val academicRepo = CourseAndAcademicRepository(firestore)
    var repoGuarded = false
    try {
      withTimeout(FLOW_TIMEOUT_MS) { academicRepo.observeProgrammes().first() }
    } catch (_: IllegalStateException) {
      repoGuarded = true
    }
    assertTrue("Expected repository auth guard when unauthenticated", repoGuarded)

    var denied = false
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) { firestore.collection("programmes").get().await() }
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      denied = firestoreEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }
    assertTrue("Expected PERMISSION_DENIED when unauthenticated user reads programmes", denied)
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
