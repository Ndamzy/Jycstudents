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
      ?: throw IllegalStateException("User must be signed in before accessing Firestore.")

  private fun normalizeInstitutionSettings(raw: InstitutionSettings): InstitutionSettings {
    val isLegacyAccount =
      raw.opayAccountNumber.isBlank() ||
        raw.opayAccountNumber == "8030001122" ||
        raw.opayAccountNumber == "0000000000" ||
        raw.opayAccountNumber == "1234567890" ||
        raw.opayAccountName.isBlank() ||
        raw.opayAccountName == "JY-COMPUTERS" ||
        raw.opayAccountName.contains("Training Academy", ignoreCase = true)

    val normPrimaryPhone =
      if (raw.contactPhone.isBlank() || raw.contactPhone.contains("800 592 6678")) {
        JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE
      } else {
        raw.contactPhone
      }
    val normAltPhone =
      raw.alternativePhone.ifBlank { JyCatalogDefaults.OFFICIAL_ALT_PHONE }
    val normWhatsapp =
      if (raw.whatsappNumber.isBlank() || raw.whatsappNumber.contains("800 592 6678")) {
        JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE
      } else {
        raw.whatsappNumber
      }
    val normEmail =
      if (raw.email.isBlank() || raw.email == "admissions@jycomputers.edu.ng") {
        JyCatalogDefaults.OFFICIAL_EMAIL
      } else {
        raw.email
      }
    val normWebsite = raw.website.ifBlank { JyCatalogDefaults.OFFICIAL_WEBSITE }
    val normAddress =
      if (raw.physicalAddress.isBlank() || raw.physicalAddress.contains("Plot 24, Technology Drive")) {
        JyCatalogDefaults.OFFICIAL_ADDRESS
      } else {
        raw.physicalAddress
      }
    val normSlogan = raw.slogan.ifBlank { JyCatalogDefaults.OFFICIAL_SLOGAN }

    return raw.copy(
      slogan = normSlogan,
      contactPhone = normPrimaryPhone,
      alternativePhone = normAltPhone,
      whatsappNumber = normWhatsapp,
      email = normEmail,
      website = normWebsite,
      physicalAddress = normAddress,
      opayBankName =
        if (isLegacyAccount || raw.opayBankName.isBlank()) JyCatalogDefaults.OFFICIAL_OPAY_BANK
        else raw.opayBankName,
      opayAccountName =
        if (isLegacyAccount) JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME else raw.opayAccountName,
      opayAccountNumber =
        if (isLegacyAccount) JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER
        else raw.opayAccountNumber,
      opayInstructions =
        if (isLegacyAccount || raw.opayInstructions.isBlank()) {
          JyCatalogDefaults.OFFICIAL_OPAY_INSTRUCTIONS
        } else {
          raw.opayInstructions
        },
    )
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
      val currentUser = auth.currentUser ?: return false
      if (
        uid != JyCatalogDefaults.SINGLE_ADMIN_UID ||
          currentUser.uid != JyCatalogDefaults.SINGLE_ADMIN_UID ||
          !JyCatalogDefaults.isOwnerAdminIdentity(
            uid = currentUser.uid,
            email = currentUser.email,
            emailVerified = currentUser.isEmailVerified,
          )
      ) {
        return false
      }
      val tokenResult =
        runCatching { currentUser.getIdToken(true).await() }
          .getOrElse { currentUser.getIdToken(false).await() }
      val claims = tokenResult.claims
      val hasSingleAdminClaim = claims["jySingleAdmin"] == true
      val hasAdminRoleClaim = claims["role"] == "admin"
      val signInProvider = tokenResult.signInProvider
      hasSingleAdminClaim && hasAdminRoleClaim && signInProvider == "google.com"
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
      if (dirRef.get().await().exists()) {
        batch.update(dirRef, dirUpdates)
      } else if (profile.role == "student") {
        batch.set(
          dirRef,
          mapOf(
            "userId" to uid,
            "fullName" to profile.fullName.trim(),
            "studentIdNumber" to profile.studentIdNumber.ifBlank { "JYC-USER" },
            "role" to profile.role,
            "selectedProgrammeTitle" to profile.selectedProgrammeTitle,
            "isOnline" to true,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, userRef.path)
      Result.failure(e)
    }
  }

  suspend fun ensureAdminDirectoryEntry(
    uid: String,
    fullName: String,
    email: String,
  ) {
    if (!JyCatalogDefaults.isOwnerAdminIdentity(uid, email)) return
    val dirRef = db.collection("directory").document(uid)
    try {
      if (!dirRef.get().await().exists()) {
        val safeName = fullName.trim().ifBlank { JyCatalogDefaults.SINGLE_ADMIN_ROLE_LABEL }
        dirRef
          .set(
            mapOf(
              "userId" to uid,
              "fullName" to safeName,
              "studentIdNumber" to "ADMIN",
              "role" to "admin",
              "selectedProgrammeTitle" to "Administration",
              "isOnline" to true,
              "lastSeenAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (_: Exception) {
      // Best-effort directory registration for verified admin
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
    if (role == "admin") {
      return Result.failure(
        SecurityException("Single-administrator policy: creating additional administrator accounts is disabled.")
      )
    }
    if (role !in listOf("student", "instructor")) {
      return Result.failure(IllegalArgumentException("Unsupported role: $role"))
    }
    if (JyCatalogDefaults.isOwnerAdminIdentity(targetUid, email)) {
      return Result.failure(SecurityException("Cannot reassign the owner administrator role."))
    }
    val userRef = db.collection("users").document(targetUid)
    val dirRef = db.collection("directory").document(targetUid)
    val instRef = db.collection("instructors").document(targetUid)
    return try {
      val batch = db.batch()
      batch.update(userRef, mapOf("role" to role, "updatedAt" to FieldValue.serverTimestamp()))
      val dirSnap = dirRef.get().await()
      if (dirSnap.exists()) {
        batch.update(dirRef, mapOf("role" to role, "updatedAt" to FieldValue.serverTimestamp()))
      } else {
        val userSnap = userRef.get().await()
        batch.set(
          dirRef,
          mapOf(
            "userId" to targetUid,
            "fullName" to fullName.trim().ifBlank { "JY User" },
            "studentIdNumber" to (userSnap.getString("studentIdNumber") ?: "JYC-STAFF"),
            "role" to role,
            "selectedProgrammeTitle" to (userSnap.getString("selectedProgrammeTitle") ?: ""),
            "isOnline" to false,
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val cleanCourseIds = assignedCourseIds.distinct().take(20)
      if (role == "instructor") {
        batch.set(
          instRef,
          mapOf(
            "uid" to targetUid,
            "fullName" to fullName.trim(),
            "email" to email.trim(),
            "specialization" to specialization.trim().ifBlank { "Computer Training" },
            "assignedCourseIds" to cleanCourseIds,
            "active" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        if (instRef.get().await().exists()) {
          batch.delete(instRef)
        }
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

      // Sync assigned courses and academic records so instructor queries & security rules align immediately
      val effectiveAssignedIds = if (role == "instructor") cleanCourseIds.toSet() else emptySet()
      val allProgrammes = db.collection("programmes").get().await()
      val affectedProgrammeUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
      val assignedCourseIdList = mutableListOf<String>()
      val clearedCourseIdList = mutableListOf<String>()

      allProgrammes.documents.forEach { progDoc ->
        val currentlyAssignedUid = progDoc.getString("instructorUid").orEmpty()
        if (progDoc.id in effectiveAssignedIds) {
          assignedCourseIdList.add(progDoc.id)
          if (currentlyAssignedUid != targetUid) {
            affectedProgrammeUpdates.add(
              progDoc.reference to mapOf(
                "instructorUid" to targetUid,
                "instructorName" to fullName.trim(),
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
          }
        } else if (currentlyAssignedUid == targetUid) {
          clearedCourseIdList.add(progDoc.id)
          affectedProgrammeUpdates.add(
            progDoc.reference to mapOf(
              "instructorUid" to "",
              "instructorName" to "JY-Computers Faculty",
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
        }
      }

      affectedProgrammeUpdates.chunked(400).forEach { chunk ->
        val progBatch = db.batch()
        chunk.forEach { (ref, map) -> progBatch.update(ref, map) }
        progBatch.commit().await()
      }

      val academicDocUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, String>>()
      listOf("enrollments", "attendance", "results").forEach { coll ->
        assignedCourseIdList.forEach { progId ->
          val snap = db.collection(coll).whereEqualTo("programmeId", progId).get().await()
          snap.documents.forEach { doc ->
            if (doc.getString("instructorUid").orEmpty() != targetUid) {
              academicDocUpdates.add(doc.reference to targetUid)
            }
          }
        }
        clearedCourseIdList.forEach { progId ->
          val snap = db.collection(coll).whereEqualTo("programmeId", progId).get().await()
          snap.documents.forEach { doc ->
            if (doc.getString("instructorUid").orEmpty() == targetUid) {
              academicDocUpdates.add(doc.reference to "")
            }
          }
        }
      }
      academicDocUpdates.chunked(400).forEach { chunk ->
        val acadBatch = db.batch()
        chunk.forEach { (ref, instUid) ->
          acadBatch.update(ref, mapOf("instructorUid" to instUid, "updatedAt" to FieldValue.serverTimestamp()))
        }
        acadBatch.commit().await()
      }

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
          "slogan" to settings.slogan.trim().ifBlank { JyCatalogDefaults.OFFICIAL_SLOGAN },
          "contactPhone" to settings.contactPhone.trim().ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE },
          "alternativePhone" to settings.alternativePhone.trim().ifBlank { JyCatalogDefaults.OFFICIAL_ALT_PHONE },
          "whatsappNumber" to settings.whatsappNumber.trim().ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE },
          "email" to settings.email.trim().ifBlank { JyCatalogDefaults.OFFICIAL_EMAIL },
          "website" to settings.website.trim().ifBlank { JyCatalogDefaults.OFFICIAL_WEBSITE },
          "physicalAddress" to settings.physicalAddress.trim().ifBlank { JyCatalogDefaults.OFFICIAL_ADDRESS },
          "opayBankName" to settings.opayBankName.trim().ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_BANK },
          "opayAccountName" to settings.opayAccountName.trim().ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME },
          "opayAccountNumber" to settings.opayAccountNumber.trim().ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER },
          "opayInstructions" to settings.opayInstructions.trim().ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_INSTRUCTIONS },
          "onlinePaymentUrl" to settings.onlinePaymentUrl.trim(),
          "paymentsEnabled" to settings.paymentsEnabled,
          "allowPartialPayment" to settings.allowPartialPayment,
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
      val currentPhone = settingsSnap.getString("contactPhone").orEmpty()
      val currentEmail = settingsSnap.getString("email").orEmpty()
      val currentAlt = settingsSnap.getString("alternativePhone").orEmpty()
      if (
        !settingsSnap.exists() ||
          currentAccNum.isBlank() ||
          currentAccNum == "8030001122" ||
          currentBank.isBlank() ||
          currentAccName == "JY-COMPUTERS" ||
          currentAccName.contains("Training Academy", ignoreCase = true) ||
          currentPhone.contains("800 592 6678") ||
          currentEmail == "admissions@jycomputers.edu.ng" ||
          currentAlt.isBlank()
      ) {
        val current =
          if (settingsSnap.exists()) {
            settingsSnap.toObject(
              InstitutionSettings::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            ) ?: InstitutionSettings()
          } else {
            InstitutionSettings()
          }
        saveInstitutionSettings(normalizeInstitutionSettings(current))
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
        batch.commit().await()
      }

      val loungeRef = db.collection("chat_rooms").document("general_student_lounge")
      if (!loungeRef.get().await().exists()) {
        loungeRef
          .set(
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
            )
          )
          .await()
      }

      val servicesSnap = db.collection("services").limit(1).get().await()
      if (servicesSnap.isEmpty) {
        val batch = db.batch()
        JyCatalogDefaults.DEFAULT_SERVICES.forEach { seed ->
          val srvRef = db.collection("services").document(seed.id)
          batch.set(
            srvRef,
            mapOf(
              "title" to seed.title,
              "category" to seed.category,
              "description" to seed.description,
              "defaultFeeNaira" to seed.defaultFeeNaira,
              "paymentEnabled" to seed.paymentEnabled,
              "allowPartialPayment" to seed.allowPartialPayment,
              "isTrainingCourse" to seed.isTrainingCourse,
              "linkedProgrammeId" to seed.linkedProgrammeId,
              "active" to true,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
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
