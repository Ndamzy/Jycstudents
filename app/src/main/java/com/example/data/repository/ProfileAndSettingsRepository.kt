package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ActivityLog
import com.example.data.model.BlockedUser
import com.example.data.model.DirectoryEntry
import com.example.data.model.InstitutionSettings
import com.example.data.model.InstructorRole
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProfileAndSettingsRepository(private val db: FirebaseFirestore) {
  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun normalizeInstitutionSettings(raw: InstitutionSettings): InstitutionSettings {
    val isLegacyAccount =
      raw.opayAccountNumber.isBlank() ||
        raw.opayAccountNumber == "8030001122" ||
        raw.opayAccountNumber == "0000000000" ||
        raw.opayAccountNumber == "1234567890" ||
        raw.opayAccountName.isBlank() ||
        raw.opayAccountName.contains("Training Academy", ignoreCase = true)
    return if (isLegacyAccount) {
      raw.copy(
        opayBankName = JyCatalogDefaults.OFFICIAL_OPAY_BANK,
        opayAccountName = JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME,
        opayAccountNumber = JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER,
        opayInstructions =
          raw.opayInstructions.ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_INSTRUCTIONS },
      )
    } else if (raw.opayBankName.isBlank()) {
      raw.copy(opayBankName = JyCatalogDefaults.OFFICIAL_OPAY_BANK)
    } else {
      raw
    }
  }

  fun observeInstitutionSettings(): Flow<InstitutionSettings> =
    db.collection("settings")
      .document("institution")
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          val parsed =
            snapshot.toObject(
              InstitutionSettings::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: InstitutionSettings()
          normalizeInstitutionSettings(parsed)
        } else {
          InstitutionSettings()
        }
      }
      .catch { error ->
        logFlowError(error, OperationType.GET, "settings/institution")
        throw error
      }

  fun observeUserProfile(uid: String): Flow<UserProfile?> =
    db.collection("users")
      .document(uid)
      .snapshots()
      .map { snapshot ->
        if (snapshot.exists()) {
          snapshot.toObject(
            UserProfile::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        } else {
          null
        }
      }
      .catch { error ->
        logFlowError(error, OperationType.GET, "users/$uid")
        throw error
      }

  suspend fun isUidAdmin(uid: String): Boolean {
    return try {
      val userEmail = auth.currentUser?.email
      val isVerified = auth.currentUser?.isEmailVerified == true
      if (isVerified && userEmail.equals(JyCatalogDefaults.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)) {
        return true
      }
      db.collection("admins").document(uid).get().await().exists()
    } catch (_: Exception) {
      false
    }
  }

  suspend fun isUidInstructor(uid: String): Boolean {
    return try {
      db.collection("instructors").document(uid).get().await().exists()
    } catch (_: Exception) {
      false
    }
  }

  suspend fun isStudentIdTaken(studentIdNumber: String): Boolean {
    return try {
      val snap =
        db.collection("directory")
          .whereEqualTo("studentIdNumber", studentIdNumber)
          .limit(1)
          .get()
          .await()
      !snap.isEmpty
    } catch (_: Exception) {
      false
    }
  }

  fun observeAllUsersAdmin(): Flow<List<UserProfile>> = flow {
    requireUserId()
    emitAll(
      db.collection("users")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              UserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "users")
          throw error
        }
    )
  }

  fun observeActivityLogsAdmin(): Flow<List<ActivityLog>> = flow {
    requireUserId()
    emitAll(
      db.collection("activity_logs")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              ActivityLog::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "activity_logs")
          throw error
        }
    )
  }

  fun observeDirectory(): Flow<List<DirectoryEntry>> = flow {
    requireUserId()
    emitAll(
      db.collection("directory")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            DirectoryEntry::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "directory")
          throw error
        }
    )
  }

  fun observeInstructorsAdmin(): Flow<List<InstructorRole>> = flow {
    requireUserId()
    emitAll(
      db.collection("instructors")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            InstructorRole::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "instructors")
          throw error
        }
    )
  }

  fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> = flow {
    requireUserId()
    val path = "users/$uid/blocked_users"
    emitAll(
      db.collection("users")
        .document(uid)
        .collection("blocked_users")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            BlockedUser::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, path)
          throw error
        }
    )
  }

  suspend fun registerOrCreateUserProfile(
    profile: UserProfile,
    targetUid: String? = null,
    roleOverride: String = "student",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val uid = targetUid ?: actorUid
    val userRef = db.collection("users").document(uid)
    val dirRef = db.collection("directory").document(uid)
    return try {
      val batch = db.batch()
      val level =
        if (profile.trainingLevel in JyCatalogDefaults.TRAINING_LEVELS) profile.trainingLevel
        else "Beginner"
      val approval =
        if (profile.approvalStatus in listOf("pending", "approved", "rejected")) profile.approvalStatus
        else "pending"
      val paymentSt =
        if (profile.paymentStatus in listOf("unpaid", "pending", "paid")) profile.paymentStatus
        else "unpaid"

      val userPayload =
        mapOf(
          "userId" to uid,
          "role" to roleOverride,
          "studentIdNumber" to profile.studentIdNumber,
          "fullName" to profile.fullName.trim(),
          "gender" to profile.gender,
          "dateOfBirth" to profile.dateOfBirth.trim(),
          "phoneNumber" to profile.phoneNumber.trim(),
          "email" to profile.email.trim(),
          "residentialAddress" to profile.residentialAddress.trim(),
          "stateOfOrigin" to profile.stateOfOrigin.trim(),
          "localGovernmentArea" to profile.localGovernmentArea.trim(),
          "nextOfKin" to profile.nextOfKin.trim(),
          "nextOfKinPhone" to profile.nextOfKinPhone.trim(),
          "passportPhotoBase64" to profile.passportPhotoBase64,
          "selectedProgrammeId" to profile.selectedProgrammeId,
          "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
          "trainingLevel" to level,
          "registrationDate" to profile.registrationDate,
          "approvalStatus" to approval,
          "paymentStatus" to paymentSt,
          "courseProgressPercent" to profile.courseProgressPercent.coerceIn(0, 100),
          "status" to profile.status,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      val dirPayload =
        mapOf(
          "userId" to uid,
          "fullName" to profile.fullName.trim(),
          "studentIdNumber" to profile.studentIdNumber,
          "role" to roleOverride,
          "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
          "isOnline" to true,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      batch.set(userRef, userPayload)
      batch.set(dirRef, dirPayload)
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateStudentProfile(profile: UserProfile): Result<Unit> {
    requireUserId()
    val uid = profile.userId
    val userRef = db.collection("users").document(uid)
    val dirRef = db.collection("directory").document(uid)
    return try {
      val level =
        if (profile.trainingLevel in JyCatalogDefaults.TRAINING_LEVELS) profile.trainingLevel
        else "Beginner"
      val batch = db.batch()
      val userUpdates =
        mapOf(
          "fullName" to profile.fullName.trim(),
          "gender" to profile.gender,
          "dateOfBirth" to profile.dateOfBirth.trim(),
          "phoneNumber" to profile.phoneNumber.trim(),
          "email" to profile.email.trim(),
          "residentialAddress" to profile.residentialAddress.trim(),
          "stateOfOrigin" to profile.stateOfOrigin.trim(),
          "localGovernmentArea" to profile.localGovernmentArea.trim(),
          "nextOfKin" to profile.nextOfKin.trim(),
          "nextOfKinPhone" to profile.nextOfKinPhone.trim(),
          "passportPhotoBase64" to profile.passportPhotoBase64,
          "selectedProgrammeId" to profile.selectedProgrammeId,
          "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
          "trainingLevel" to level,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      val dirUpdates =
        mapOf(
          "fullName" to profile.fullName.trim(),
          "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
          "isOnline" to true,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      batch.update(userRef, userUpdates)
      batch.update(dirRef, dirUpdates)
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminEditStudentProfile(
    profile: UserProfile,
    actorName: String,
  ): Result<Unit> {
    val actorUid = requireUserId()
    val uid = profile.userId
    val userRef = db.collection("users").document(uid)
    val dirRef = db.collection("directory").document(uid)
    return try {
      val level =
        if (profile.trainingLevel in JyCatalogDefaults.TRAINING_LEVELS) profile.trainingLevel
        else "Beginner"
      val batch = db.batch()
      batch.update(
        userRef,
        mapOf(
          "fullName" to profile.fullName.trim(),
          "gender" to profile.gender,
          "dateOfBirth" to profile.dateOfBirth.trim(),
          "phoneNumber" to profile.phoneNumber.trim(),
          "email" to profile.email.trim(),
          "residentialAddress" to profile.residentialAddress.trim(),
          "stateOfOrigin" to profile.stateOfOrigin.trim(),
          "localGovernmentArea" to profile.localGovernmentArea.trim(),
          "nextOfKin" to profile.nextOfKin.trim(),
          "nextOfKinPhone" to profile.nextOfKinPhone.trim(),
          "passportPhotoBase64" to profile.passportPhotoBase64,
          "selectedProgrammeId" to profile.selectedProgrammeId,
          "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
          "trainingLevel" to level,
          "approvalStatus" to profile.approvalStatus,
          "paymentStatus" to profile.paymentStatus,
          "courseProgressPercent" to profile.courseProgressPercent.coerceIn(0, 100),
          "status" to profile.status,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (dirRef.get().await().exists()) {
        batch.update(
          dirRef,
          mapOf(
            "fullName" to profile.fullName.trim(),
            "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      val logRef = db.collection("activity_logs").document()
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "UPDATE_STUDENT",
          "targetId" to uid,
          "details" to "Updated profile for ${profile.fullName} (${profile.studentIdNumber})",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminUpdateApprovalStatus(
    targetUid: String,
    studentName: String,
    approvalStatus: String,
    actorName: String,
  ): Result<Unit> {
    val actorUid = requireUserId()
    val docRef = db.collection("users").document(targetUid)
    val notifRef = db.collection("notifications").document()
    val logRef = db.collection("activity_logs").document()
    return try {
      val batch = db.batch()
      batch.update(
        docRef,
        mapOf(
          "approvalStatus" to approvalStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        notifRef,
        mapOf(
          "userId" to targetUid,
          "title" to if (approvalStatus == "approved") "Registration Approved" else "Registration Update",
          "message" to
            if (approvalStatus == "approved") {
              "Your JY-Computers student registration has been approved by Administration."
            } else {
              "Your registration status was updated to ${approvalStatus.uppercase()}."
            },
          "type" to "registration",
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "APPROVAL_${approvalStatus.uppercase()}",
          "targetId" to targetUid,
          "details" to "Set registration approval for $studentName to ${approvalStatus.uppercase()}",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminUpdateStudentProgress(
    targetUid: String,
    studentName: String,
    progressPercent: Int,
    statusOverride: String?,
    actorName: String,
  ): Result<Unit> {
    val actorUid = requireUserId()
    val docRef = db.collection("users").document(targetUid)
    val logRef = db.collection("activity_logs").document()
    return try {
      val clamped = progressPercent.coerceIn(0, 100)
      val batch = db.batch()
      val updates =
        mutableMapOf<String, Any>(
          "courseProgressPercent" to clamped,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!statusOverride.isNullOrBlank()) {
        updates["status"] = statusOverride
      }
      batch.update(docRef, updates)
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "UPDATE_PROGRESS",
          "targetId" to targetUid,
          "details" to "Updated course progress for $studentName to $clamped%${statusOverride?.let { " ($it)" } ?: ""}",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminUpdateStudentStatus(
    targetUid: String,
    newStatus: String,
    studentName: String = "",
    actorName: String = "Administrator",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val docRef = db.collection("users").document(targetUid)
    return try {
      val batch = db.batch()
      batch.update(
        docRef,
        mapOf(
          "status" to newStatus,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      val logRef = db.collection("activity_logs").document()
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "STUDENT_STATUS_${newStatus.uppercase()}",
          "targetId" to targetUid,
          "details" to "Updated status for ${studentName.ifBlank { targetUid }} to $newStatus",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminDeleteStudent(
    targetUid: String,
    studentName: String = "",
    actorName: String = "Administrator",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val userRef = db.collection("users").document(targetUid)
    val dirRef = db.collection("directory").document(targetUid)
    return try {
      val batch = db.batch()
      batch.delete(userRef)
      batch.delete(dirRef)
      val logRef = db.collection("activity_logs").document()
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "DELETE_STUDENT",
          "targetId" to targetUid,
          "details" to "Deleted student record ${studentName.ifBlank { targetUid }}",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminAssignRole(
    targetUid: String,
    fullName: String,
    email: String,
    role: String,
    specialization: String = "Computer Training",
    assignedCourseIds: List<String> = emptyList(),
    actorName: String = "Administrator",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val userRef = db.collection("users").document(targetUid)
    val dirRef = db.collection("directory").document(targetUid)
    return try {
      val batch = db.batch()
      batch.update(userRef, mapOf("role" to role, "updatedAt" to FieldValue.serverTimestamp()))
      if (dirRef.get().await().exists()) {
        batch.update(dirRef, mapOf("role" to role, "updatedAt" to FieldValue.serverTimestamp()))
      }
      if (role == "instructor") {
        val instRef = db.collection("instructors").document(targetUid)
        batch.set(
          instRef,
          mapOf(
            "uid" to targetUid,
            "fullName" to fullName,
            "email" to email,
            "specialization" to specialization,
            "assignedCourseIds" to assignedCourseIds,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else if (role == "admin") {
        val adminRef = db.collection("admins").document(targetUid)
        batch.set(
          adminRef,
          mapOf(
            "uid" to targetUid,
            "email" to email,
            "fullName" to fullName,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      val logRef = db.collection("activity_logs").document()
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "ASSIGN_ROLE_${role.uppercase()}",
          "targetId" to targetUid,
          "details" to "Assigned role ${role.uppercase()} to $fullName ($email)",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun logAdminActivity(
    actorName: String,
    action: String,
    targetId: String,
    details: String,
  ) {
    val actorUid = auth.currentUser?.uid ?: return
    try {
      db.collection("activity_logs")
        .document()
        .set(
          mapOf(
            "actorUid" to actorUid,
            "actorName" to actorName.ifBlank { "Administrator" },
            "action" to action,
            "targetId" to targetId,
            "details" to details,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (_: Exception) {
      // Best-effort audit logging
    }
  }

  suspend fun updateOnlinePresence(isOnline: Boolean) {
    val uid = auth.currentUser?.uid ?: return
    val dirRef = db.collection("directory").document(uid)
    try {
      val snapshot = dirRef.get().await()
      if (snapshot.exists()) {
        dirRef
          .update(
            mapOf(
              "isOnline" to isOnline,
              "lastSeenAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (_: Exception) {
      // Presence updates are best-effort
    }
  }

  suspend fun saveInstitutionSettings(settings: InstitutionSettings): Result<Unit> {
    requireUserId()
    val docRef = db.collection("settings").document("institution")
    return try {
      val payload =
        mapOf(
          "institutionName" to settings.institutionName.trim(),
          "motto" to settings.motto.trim(),
          "contactPhone" to settings.contactPhone.trim(),
          "email" to settings.email.trim(),
          "physicalAddress" to settings.physicalAddress.trim(),
          "opayBankName" to settings.opayBankName.trim(),
          "opayAccountName" to settings.opayAccountName.trim(),
          "opayAccountNumber" to settings.opayAccountNumber.trim(),
          "opayInstructions" to settings.opayInstructions.trim(),
          "onlinePaymentUrl" to settings.onlinePaymentUrl.trim(),
          "activeSession" to settings.activeSession.trim(),
          "signatoryName" to settings.signatoryName.trim(),
          "signatoryTitle" to settings.signatoryTitle.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload, SetOptions.merge()).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun blockUser(blockedUid: String, blockedName: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("blocked_users").document(blockedUid)
    return try {
      docRef
        .set(
          mapOf(
            "blockedUid" to blockedUid,
            "blockedName" to blockedName,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun unblockUser(blockedUid: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("blocked_users").document(blockedUid)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun seedInitialInstitutionIfNeeded(): Result<Unit> {
    val uid = requireUserId()
    return try {
      val settingsRef = db.collection("settings").document("institution")
      val settingsSnap = settingsRef.get().await()
      val currentAccNum = settingsSnap.getString("opayAccountNumber").orEmpty()
      val currentBank = settingsSnap.getString("opayBankName").orEmpty()
      val currentAccName = settingsSnap.getString("opayAccountName").orEmpty()
      if (
        !settingsSnap.exists() ||
          currentAccNum.isBlank() ||
          currentAccNum == "8030001122" ||
          currentBank.isBlank() ||
          currentAccName.contains("Training Academy", ignoreCase = true)
      ) {
        saveInstitutionSettings(InstitutionSettings())
      }
      val programmesSnap = db.collection("programmes").limit(1).get().await()
      if (programmesSnap.isEmpty) {
        val batch = db.batch()
        JyCatalogDefaults.DEFAULT_PROGRAMMES.forEach { seed ->
          val progRef = db.collection("programmes").document(seed.id)
          batch.set(
            progRef,
            mapOf(
              "title" to seed.title,
              "description" to seed.description,
              "duration" to seed.duration,
              "feeNaira" to seed.feeNaira,
              "category" to seed.category,
              "level" to seed.level,
              "instructorUid" to "",
              "instructorName" to "JY-Computers Faculty",
              "scheduleText" to seed.scheduleText,
              "active" to true,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        val loungeRef = db.collection("chat_rooms").document("general_student_lounge")
        batch.set(
          loungeRef,
          mapOf(
            "roomType" to "group",
            "title" to "JY-Computers Student Lounge",
            "programmeId" to "",
            "memberUids" to listOf(uid),
            "isGroupPublic" to true,
            "typingUids" to emptyList<String>(),
            "lastMessage" to "Welcome to JY-Computers Student Community!",
            "lastSenderName" to "Administration",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "seedInitialInstitution")
      Result.failure(e)
    }
  }

  private fun logFlowError(error: Throwable, op: OperationType, path: String) {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val ex = firestoreError ?: (error as? Exception) ?: return
    if (auth.currentUser != null) {
      handleFirestoreError(ex, op, path)
    }
  }
}
