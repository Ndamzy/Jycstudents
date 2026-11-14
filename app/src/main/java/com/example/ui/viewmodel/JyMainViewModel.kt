package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AcademicResult
import com.example.data.model.ActivityLog
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.InstructorRole
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.LearningMaterial
import com.example.data.model.Payment
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.data.remote.GatewayVerificationResult
import com.example.data.remote.PaymentGatewayClient
import com.example.data.remote.unwrapFirestoreMessage
import com.example.data.repository.CourseAndAcademicRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.ProfileAndSettingsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class JyMainViewModel(
  private val profileRepo: ProfileAndSettingsRepository,
  private val academicRepo: CourseAndAcademicRepository,
  private val financeRepo: FinanceRepository,
  val currentUserId: String,
  val currentUserEmail: String,
  val currentUserDisplayName: String,
) : ViewModel() {

  private val _isAdminVerified = MutableStateFlow(false)
  val isAdminVerified: StateFlow<Boolean> = _isAdminVerified.asStateFlow()

  private val _isInstructorVerified = MutableStateFlow(false)
  val isInstructorVerified: StateFlow<Boolean> = _isInstructorVerified.asStateFlow()

  private val _workspaceOverride = MutableStateFlow<String?>(null)
  val workspaceOverride: StateFlow<String?> = _workspaceOverride.asStateFlow()

  private val _actionBusy = MutableStateFlow(false)
  val actionBusy: StateFlow<Boolean> = _actionBusy.asStateFlow()

  private val _feedbackMessage = MutableStateFlow<String?>(null)
  val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

  val institutionSettings: StateFlow<InstitutionSettings> =
    profileRepo
      .observeInstitutionSettings()
      .catch { error -> handleStreamError("settings", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), InstitutionSettings())

  val userProfileState: StateFlow<UiState<UserProfile?>> =
    profileRepo
      .observeUserProfile(currentUserId)
      .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
      .catch { error ->
        val msg = handleStreamError("userProfile", error)
        emit(UiState.Error(msg))
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), UiState.Loading)

  val activeRole: StateFlow<String> =
    combine(_isAdminVerified, _isInstructorVerified, userProfileState, _workspaceOverride) {
        isAdmin,
        isInst,
        profileUi,
        override ->
        val baseRole =
          when {
            isAdmin -> "admin"
            isInst -> "instructor"
            profileUi is UiState.Success -> profileUi.data?.role ?: "student"
            else -> "student"
          }
        if (isAdmin && override != null) override else baseRole
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), "student")

  val programmes: StateFlow<List<Programme>> =
    academicRepo
      .observeProgrammes()
      .catch { error -> handleStreamError("programmes", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val enrollments: StateFlow<List<Enrollment>> =
    activeRole
      .flatMapLatest { role -> academicRepo.observeEnrollments(role, currentUserId) }
      .catch { error -> handleStreamError("enrollments", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val payments: StateFlow<List<Payment>> =
    activeRole
      .flatMapLatest { role -> financeRepo.observePayments(role == "admin", currentUserId) }
      .catch { error -> handleStreamError("payments", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val attendance: StateFlow<List<AttendanceRecord>> =
    activeRole
      .flatMapLatest { role -> academicRepo.observeAttendance(role, currentUserId) }
      .catch { error -> handleStreamError("attendance", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val results: StateFlow<List<AcademicResult>> =
    activeRole
      .flatMapLatest { role -> academicRepo.observeResults(role, currentUserId) }
      .catch { error -> handleStreamError("results", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val certificates: StateFlow<List<Certificate>> =
    activeRole
      .flatMapLatest { role -> academicRepo.observeCertificates(role == "admin", currentUserId) }
      .catch { error -> handleStreamError("certificates", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  init {
    viewModelScope.launch {
      val admin = profileRepo.isUidAdmin(currentUserId)
      _isAdminVerified.value = admin
      if (!admin) {
        _isInstructorVerified.value = profileRepo.isUidInstructor(currentUserId)
      }
      if (admin) {
        profileRepo.seedInitialInstitutionIfNeeded()
      }
      profileRepo.updateOnlinePresence(true)
    }
  }

  val allUsersAdmin: StateFlow<List<UserProfile>> =
    _isAdminVerified
      .flatMapLatest { isAdmin ->
        if (isAdmin) profileRepo.observeAllUsersAdmin() else flowOf(emptyList())
      }
      .catch { error -> handleStreamError("allUsers", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val instructorsAdmin: StateFlow<List<InstructorRole>> =
    _isAdminVerified
      .flatMapLatest { isAdmin ->
        if (isAdmin) profileRepo.observeInstructorsAdmin() else flowOf(emptyList())
      }
      .catch { error -> handleStreamError("instructors", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val activityLogsAdmin: StateFlow<List<ActivityLog>> =
    _isAdminVerified
      .flatMapLatest { isAdmin ->
        if (isAdmin) profileRepo.observeActivityLogsAdmin() else flowOf(emptyList())
      }
      .catch { error -> handleStreamError("activityLogs", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val learningMaterials: StateFlow<List<LearningMaterial>> =
    academicRepo
      .observeLearningMaterials()
      .catch { error -> handleStreamError("learningMaterials", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  fun setWorkspaceRole(role: String?) {
    if (_isAdminVerified.value) {
      _workspaceOverride.value = role
    }
  }

  fun clearFeedback() {
    _feedbackMessage.value = null
  }

  fun postFeedback(msg: String) {
    _feedbackMessage.value = msg
  }

  private fun actorDisplayName(): String =
    (userProfileState.value as? UiState.Success)?.data?.fullName?.ifBlank { currentUserDisplayName }
      ?: currentUserDisplayName

  private suspend fun generateUniqueStudentId(): String {
    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    repeat(6) {
      val candidate = "JYC-$year-${Random.nextInt(1000, 9999)}"
      if (!profileRepo.isStudentIdTaken(candidate)) return candidate
    }
    return "JYC-$year-${(System.currentTimeMillis() % 9000 + 1000)}"
  }

  fun registerStudent(
    fullName: String,
    gender: String,
    dateOfBirth: String,
    phoneNumber: String,
    email: String,
    residentialAddress: String,
    stateOfOrigin: String,
    localGovernmentArea: String,
    nextOfKin: String,
    nextOfKinPhone: String,
    passportPhotoBase64: String,
    programme: Programme,
    trainingLevel: String,
    targetUid: String? = null,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _actionBusy.value = true
      val studentId = generateUniqueStudentId()
      val regDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
      val uid = targetUid ?: currentUserId
      val role =
        if (targetUid == null && _isAdminVerified.value) "admin"
        else if (targetUid == null && _isInstructorVerified.value) "instructor"
        else "student"
      val initialApproval =
        if (_isAdminVerified.value) "approved" else "pending"

      val profile =
        UserProfile(
          userId = uid,
          role = role,
          studentIdNumber = studentId,
          fullName = fullName,
          gender = gender,
          dateOfBirth = dateOfBirth,
          phoneNumber = phoneNumber,
          email = email,
          residentialAddress = residentialAddress,
          stateOfOrigin = stateOfOrigin,
          localGovernmentArea = localGovernmentArea,
          nextOfKin = nextOfKin,
          nextOfKinPhone = nextOfKinPhone,
          passportPhotoBase64 = passportPhotoBase64,
          selectedProgrammeId = programme.id,
          selectedProgrammeTitle = programme.title,
          trainingLevel = trainingLevel,
          registrationDate = regDate,
          approvalStatus = initialApproval,
          paymentStatus = "unpaid",
          courseProgressPercent = 0,
          status = "active",
        )

      val regRes = profileRepo.registerOrCreateUserProfile(profile, targetUid = uid, roleOverride = role)
      if (regRes.isSuccess) {
        if (programme.id.isNotBlank()) {
          academicRepo.registerCourseEnrollment(
            studentUid = uid,
            studentName = fullName,
            studentIdNumber = studentId,
            programme = programme,
            session = institutionSettings.value.activeSession,
          )
        }
        if (_isAdminVerified.value) {
          profileRepo.logAdminActivity(
            actorName = actorDisplayName(),
            action = "REGISTER_STUDENT",
            targetId = uid,
            details = "Registered $fullName ($studentId) for ${programme.title}",
          )
        }
        _feedbackMessage.value = "Registration completed! Reg No: $studentId"
        onSuccess()
      } else {
        _feedbackMessage.value =
          "Registration failed: ${regRes.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
      }
      _actionBusy.value = false
    }
  }

  fun updateProfile(updated: UserProfile, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = profileRepo.updateStudentProfile(updated)
      _feedbackMessage.value =
        if (res.isSuccess) {
          onSuccess()
          "Profile updated successfully."
        } else {
          "Update failed: ${res.exceptionOrNull()?.localizedMessage ?: "Error"}"
        }
      _actionBusy.value = false
    }
  }

  fun editStudentAdmin(updated: UserProfile, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = profileRepo.adminEditStudentProfile(updated, actorName = actorDisplayName())
      _feedbackMessage.value =
        if (res.isSuccess) {
          onSuccess()
          "Updated ${updated.fullName}'s student record."
        } else {
          "Failed to update student: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun approveOrRejectStudentAdmin(targetUid: String, studentName: String, approvalStatus: String) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        profileRepo.adminUpdateApprovalStatus(
          targetUid = targetUid,
          studentName = studentName,
          approvalStatus = approvalStatus,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) "$studentName registration marked ${approvalStatus.uppercase()}."
        else "Approval update failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun updateStudentProgressAdmin(
    targetUid: String,
    studentName: String,
    progressPercent: Int,
    statusOverride: String? = null,
  ) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        profileRepo.adminUpdateStudentProgress(
          targetUid = targetUid,
          studentName = studentName,
          progressPercent = progressPercent,
          statusOverride = statusOverride,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) "Course progress for $studentName updated to ${progressPercent.coerceIn(0, 100)}%."
        else "Progress update failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun assignStudentToCourseAdmin(student: UserProfile, programme: Programme, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _actionBusy.value = true
      val enrRes =
        academicRepo.registerCourseEnrollment(
          studentUid = student.userId,
          studentName = student.fullName,
          studentIdNumber = student.studentIdNumber,
          programme = programme,
          session = institutionSettings.value.activeSession,
        )
      if (enrRes.isSuccess) {
        profileRepo.adminEditStudentProfile(
          student.copy(
            selectedProgrammeId = programme.id,
            selectedProgrammeTitle = programme.title,
          ),
          actorName = actorDisplayName(),
        )
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "ASSIGN_COURSE",
          targetId = student.userId,
          details = "Assigned ${student.fullName} to ${programme.title}",
        )
        _feedbackMessage.value = "Assigned ${student.fullName} to ${programme.title}."
        onDone()
      } else {
        _feedbackMessage.value =
          "Course assignment failed: ${enrRes.exceptionOrNull()?.localizedMessage}"
      }
      _actionBusy.value = false
    }
  }

  fun updateStudentStatusAdmin(targetUid: String, status: String, studentName: String = "") {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        profileRepo.adminUpdateStudentStatus(
          targetUid = targetUid,
          newStatus = status,
          studentName = studentName,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) "Account status updated to $status."
        else "Failed to update status: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun deleteStudentAdmin(targetUid: String, studentName: String = "") {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        profileRepo.adminDeleteStudent(
          targetUid = targetUid,
          studentName = studentName,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) "Student record deleted."
        else "Delete failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun assignUserRoleAdmin(
    targetUid: String,
    fullName: String,
    email: String,
    role: String,
    specialization: String,
    assignedCourseIds: List<String>,
  ) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        profileRepo.adminAssignRole(
          targetUid = targetUid,
          fullName = fullName,
          email = email,
          role = role,
          specialization = specialization,
          assignedCourseIds = assignedCourseIds,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) "Updated role for $fullName to ${role.uppercase()}."
        else "Role update failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun saveProgramme(programme: Programme, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = academicRepo.saveProgramme(programme)
      if (res.isSuccess && _isAdminVerified.value) {
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "SAVE_COURSE",
          targetId = res.getOrDefault(programme.id),
          details = "Saved course '${programme.title}' (${JyCatalogDefaults.formatNaira(programme.feeNaira)})",
        )
      }
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "Course '${programme.title}' saved."
        } else {
          "Failed to save course: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun toggleProgrammeActiveAdmin(programme: Programme) {
    viewModelScope.launch {
      _actionBusy.value = true
      val next = !programme.active
      val res = academicRepo.toggleProgrammeActive(programme.id, next)
      if (res.isSuccess) {
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "TOGGLE_COURSE",
          targetId = programme.id,
          details = "Set '${programme.title}' availability to ${if (next) "AVAILABLE" else "DISABLED"}",
        )
      }
      _feedbackMessage.value =
        if (res.isSuccess) "${programme.title} marked ${if (next) "Available" else "Unavailable"}."
        else "Update failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun deleteProgramme(programmeId: String) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = academicRepo.deleteProgramme(programmeId)
      if (res.isSuccess && _isAdminVerified.value) {
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "DELETE_COURSE",
          targetId = programmeId,
          details = "Deleted course $programmeId",
        )
      }
      _feedbackMessage.value =
        if (res.isSuccess) "Course removed."
        else "Failed to delete course: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun enrollInProgramme(programme: Programme, onDone: () -> Unit = {}) {
    val profile = (userProfileState.value as? UiState.Success)?.data
    if (profile == null) {
      _feedbackMessage.value = "Complete your student registration profile before registering for a course."
      return
    }
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        academicRepo.registerCourseEnrollment(
          studentUid = currentUserId,
          studentName = profile.fullName,
          studentIdNumber = profile.studentIdNumber,
          programme = programme,
          session = institutionSettings.value.activeSession,
        )
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "Enrolled in ${programme.title}. Tuition invoice created."
        } else {
          "Enrollment failed: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun updateEnrollmentStatusAdmin(enrollmentId: String, status: String) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = academicRepo.updateEnrollmentStatus(enrollmentId, status)
      _feedbackMessage.value =
        if (res.isSuccess) "Enrolment status updated to ${status.uppercase()}."
        else "Update failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun submitManualOpayPayment(
    enrollment: Enrollment,
    amountNaira: Long,
    reference: String,
    paymentDate: String,
    senderName: String,
    evidenceBase64: String,
    onDone: () -> Unit = {},
  ) {
    val profile = (userProfileState.value as? UiState.Success)?.data
    val name = profile?.fullName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName
    val studentId = profile?.studentIdNumber?.ifBlank { "JYC-PENDING" } ?: "JYC-PENDING"
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        financeRepo.submitPayment(
          studentUid = currentUserId,
          studentName = name,
          studentIdNumber = studentId,
          enrollment = enrollment,
          amountNaira = amountNaira,
          method = "opay_transfer",
          reference = reference,
          paymentDate = paymentDate,
          senderName = senderName,
          evidenceBase64 = evidenceBase64,
        )
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "OPay transfer submitted for verification ($reference)."
        } else {
          "Payment submission failed: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun submitOnlinePayment(
    enrollment: Enrollment,
    amountNaira: Long,
    onDone: (String) -> Unit = {},
  ) {
    val profile = (userProfileState.value as? UiState.Success)?.data
    val name = profile?.fullName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName
    val studentId = profile?.studentIdNumber?.ifBlank { "JYC-PENDING" } ?: "JYC-PENDING"
    val ref = PaymentGatewayClient.generateTransactionReference()
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        financeRepo.submitPayment(
          studentUid = currentUserId,
          studentName = name,
          studentIdNumber = studentId,
          enrollment = enrollment,
          amountNaira = amountNaira,
          method = "online_gateway",
          reference = ref,
          paymentDate = today,
          senderName = name,
          evidenceBase64 = "",
        )
      if (res.isSuccess) {
        val verify = PaymentGatewayClient.verifyTransactionWithBackend(ref, amountNaira, studentId)
        _feedbackMessage.value =
          when (verify) {
            is GatewayVerificationResult.Verified -> "Payment verified: ${verify.message}"
            is GatewayVerificationResult.PendingManualReview ->
              "Reference $ref generated. ${verify.reason}"
            is GatewayVerificationResult.Failed ->
              "Reference $ref saved (verification pending): ${verify.error}"
          }
        onDone(ref)
      } else {
        _feedbackMessage.value =
          "Online payment failed: ${res.exceptionOrNull()?.localizedMessage}"
      }
      _actionBusy.value = false
    }
  }

  fun recordAdminPayment(
    enrollment: Enrollment,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String,
    remarks: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        financeRepo.recordAdminPayment(
          enrollment = enrollment,
          amountNaira = amountNaira,
          method = method,
          reference = reference,
          paymentDate = paymentDate,
          remarks = remarks,
          actorName = actorDisplayName(),
        )
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "Recorded ${JyCatalogDefaults.formatNaira(amountNaira)} payment for ${enrollment.studentName}."
        } else {
          "Failed to record payment: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun reviewPaymentAdmin(payment: Payment, approve: Boolean, remarks: String) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = financeRepo.adminReviewPayment(payment, approve, remarks, actorName = actorDisplayName())
      _feedbackMessage.value =
        if (res.isSuccess) {
          if (approve) "Payment ${payment.reference} approved & student ledger updated."
          else "Payment ${payment.reference} rejected."
        } else {
          "Review failed: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun markAttendance(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    dateKey: String,
    status: String,
    remarks: String = "",
  ) {
    viewModelScope.launch {
      val res =
        academicRepo.recordAttendance(
          studentUid = studentUid,
          studentName = studentName,
          studentIdNumber = studentIdNumber,
          programme = programme,
          dateKey = dateKey,
          status = status,
          remarks = remarks,
        )
      if (res.isFailure) {
        _feedbackMessage.value =
          "Failed to mark attendance: ${res.exceptionOrNull()?.localizedMessage}"
      } else {
        _feedbackMessage.value = "Marked $studentName as ${status.uppercase()} for $dateKey."
      }
    }
  }

  fun saveResult(
    resultId: String?,
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    assignmentScore: Long,
    examScore: Long,
    remarks: String,
    published: Boolean,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        academicRepo.saveAcademicResult(
          resultId = resultId,
          studentUid = studentUid,
          studentName = studentName,
          studentIdNumber = studentIdNumber,
          programme = programme,
          session = institutionSettings.value.activeSession,
          assignmentScore = assignmentScore,
          examScore = examScore,
          remarks = remarks,
          published = published,
        )
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          if (published) "Result published for $studentName." else "Draft result saved for $studentName."
        } else {
          "Failed to save result: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun issueCertificate(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programmeId: String,
    programmeTitle: String,
    graduationDate: String,
    onDone: () -> Unit = {},
  ) {
    val settings = institutionSettings.value
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        academicRepo.issueCertificate(
          studentUid = studentUid,
          studentName = studentName,
          studentIdNumber = studentIdNumber,
          programmeId = programmeId,
          programmeTitle = programmeTitle,
          graduationDate = graduationDate,
          signatoryName = settings.signatoryName,
          signatoryTitle = settings.signatoryTitle,
        )
      if (res.isSuccess && _isAdminVerified.value) {
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "ISSUE_CERTIFICATE",
          targetId = res.getOrDefault(""),
          details = "Issued certificate for $studentName ($programmeTitle)",
        )
      }
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "Certificate issued for $studentName!"
        } else {
          "Certificate issuance failed: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun addLearningMaterial(
    programme: Programme,
    title: String,
    description: String,
    resourceUrl: String,
    onDone: () -> Unit = {},
  ) {
    val profile = (userProfileState.value as? UiState.Success)?.data
    val author = profile?.fullName?.ifBlank { currentUserDisplayName } ?: currentUserDisplayName
    viewModelScope.launch {
      _actionBusy.value = true
      val res =
        academicRepo.addLearningMaterial(
          programmeId = programme.id,
          programmeTitle = programme.title,
          title = title,
          description = description,
          resourceUrl = resourceUrl,
          authorName = author,
        )
      _feedbackMessage.value =
        if (res.isSuccess) {
          onDone()
          "Learning material shared."
        } else {
          "Failed to share material: ${res.exceptionOrNull()?.localizedMessage}"
        }
      _actionBusy.value = false
    }
  }

  fun saveInstitutionSettings(settings: InstitutionSettings) {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = profileRepo.saveInstitutionSettings(settings)
      if (res.isSuccess && _isAdminVerified.value) {
        profileRepo.logAdminActivity(
          actorName = actorDisplayName(),
          action = "SAVE_SETTINGS",
          targetId = "institution",
          details = "Updated institution & payment settings",
        )
      }
      _feedbackMessage.value =
        if (res.isSuccess) "Administrative settings saved."
        else "Failed to save settings: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  fun seedDefaultProgrammesNow() {
    viewModelScope.launch {
      _actionBusy.value = true
      val res = profileRepo.seedInitialInstitutionIfNeeded()
      _feedbackMessage.value =
        if (res.isSuccess) "Default JY-Computers programmes & settings initialized."
        else "Seed failed: ${res.exceptionOrNull()?.localizedMessage}"
      _actionBusy.value = false
    }
  }

  private fun handleStreamError(stream: String, error: Throwable): String {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val msg = unwrapFirestoreMessage(error, "Failed to load $stream")
    Log.w(TAG, "Stream error [$stream]: $msg", error)
    return msg
  }

  private companion object {
    const val TAG = "JyMainViewModel"
    const val STOP_TIMEOUT = 5000L
  }
}
