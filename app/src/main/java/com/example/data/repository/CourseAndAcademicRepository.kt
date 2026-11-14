package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AcademicResult
import com.example.data.model.AttendanceRecord
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.LearningMaterial
import com.example.data.model.Programme
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CourseAndAcademicRepository(private val db: FirebaseFirestore) {
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

  fun observeProgrammes(): Flow<List<Programme>> = flow {
    requireUserId()
    emitAll(
      db.collection("programmes")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Programme::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.feeNaira }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "programmes")
          throw error
        }
    )
  }

  suspend fun saveProgramme(programme: Programme): Result<String> {
    requireUserId()
    val docRef =
      if (programme.id.isBlank()) {
        db.collection("programmes").document()
      } else {
        db.collection("programmes").document(programme.id)
      }
    return try {
      val existing = if (programme.id.isNotBlank()) docRef.get().await().exists() else false
      if (!existing) {
        docRef
          .set(
            mapOf(
              "title" to programme.title.trim(),
              "description" to programme.description.trim(),
              "duration" to programme.duration.trim(),
              "feeNaira" to programme.feeNaira,
              "category" to programme.category.trim(),
              "instructorUid" to programme.instructorUid,
              "instructorName" to programme.instructorName.trim(),
              "scheduleText" to programme.scheduleText.trim(),
              "active" to programme.active,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "title" to programme.title.trim(),
              "description" to programme.description.trim(),
              "duration" to programme.duration.trim(),
              "feeNaira" to programme.feeNaira,
              "category" to programme.category.trim(),
              "instructorUid" to programme.instructorUid,
              "instructorName" to programme.instructorName.trim(),
              "scheduleText" to programme.scheduleText.trim(),
              "active" to programme.active,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteProgramme(programmeId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("programmes").document(programmeId)
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

  fun observeEnrollments(role: String, uid: String): Flow<List<Enrollment>> = flow {
    requireUserId()
    val query =
      when (role) {
        "admin" -> db.collection("enrollments")
        "instructor" -> db.collection("enrollments").whereEqualTo("instructorUid", uid)
        else -> db.collection("enrollments").whereEqualTo("userId", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            Enrollment::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "enrollments")
          throw error
        }
    )
  }

  suspend fun registerCourseEnrollment(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    session: String,
  ): Result<String> {
    requireUserId()
    val docRef = db.collection("enrollments").document()
    return try {
      docRef
        .set(
          mapOf(
            "userId" to studentUid,
            "studentName" to studentName,
            "studentIdNumber" to studentIdNumber,
            "programmeId" to programme.id,
            "programmeTitle" to programme.title,
            "instructorUid" to programme.instructorUid,
            "session" to session,
            "totalFeeNaira" to programme.feeNaira,
            "amountPaidNaira" to 0L,
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeAttendance(role: String, uid: String): Flow<List<AttendanceRecord>> = flow {
    requireUserId()
    val query =
      when (role) {
        "admin" -> db.collection("attendance")
        "instructor" -> db.collection("attendance").whereEqualTo("instructorUid", uid)
        else -> db.collection("attendance").whereEqualTo("studentUid", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            AttendanceRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "attendance")
          throw error
        }
    )
  }

  suspend fun recordAttendance(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    dateKey: String,
    status: String,
    remarks: String = "",
  ): Result<Unit> {
    val markerUid = requireUserId()
    val docId = "${programme.id}_${dateKey}_${studentUid}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    val docRef = db.collection("attendance").document(docId)
    return try {
      val existing = docRef.get().await().exists()
      if (!existing) {
        docRef
          .set(
            mapOf(
              "studentUid" to studentUid,
              "studentName" to studentName,
              "studentIdNumber" to studentIdNumber,
              "programmeId" to programme.id,
              "programmeTitle" to programme.title,
              "instructorUid" to programme.instructorUid.ifBlank { markerUid },
              "dateKey" to dateKey,
              "status" to status,
              "markedByUid" to markerUid,
              "remarks" to remarks,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "status" to status,
              "markedByUid" to markerUid,
              "remarks" to remarks,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeResults(role: String, uid: String): Flow<List<AcademicResult>> = flow {
    requireUserId()
    val query =
      when (role) {
        "admin" -> db.collection("results")
        "instructor" -> db.collection("results").whereEqualTo("instructorUid", uid)
        else ->
          db.collection("results")
            .whereEqualTo("studentUid", uid)
            .whereEqualTo("published", true)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            AcademicResult::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "results")
          throw error
        }
    )
  }

  suspend fun saveAcademicResult(
    resultId: String?,
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    session: String,
    assignmentScore: Long,
    examScore: Long,
    remarks: String,
    published: Boolean,
  ): Result<String> {
    val actorUid = requireUserId()
    val docRef =
      if (resultId.isNullOrBlank()) {
        db.collection("results").document("${programme.id}_${studentUid}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_"))
      } else {
        db.collection("results").document(resultId)
      }
    val safeAssign = assignmentScore.coerceIn(0L, 40L)
    val safeExam = examScore.coerceIn(0L, 60L)
    val total = (safeAssign + safeExam).coerceIn(0L, 100L)
    val grade = JyCatalogDefaults.calculateGrade(total)

    return try {
      val exists = docRef.get().await().exists()
      val batch = db.batch()
      if (!exists) {
        batch.set(
          docRef,
          mapOf(
            "studentUid" to studentUid,
            "studentName" to studentName,
            "studentIdNumber" to studentIdNumber,
            "programmeId" to programme.id,
            "programmeTitle" to programme.title,
            "instructorUid" to programme.instructorUid.ifBlank { actorUid },
            "session" to session,
            "assignmentScore" to safeAssign,
            "examScore" to safeExam,
            "totalScore" to total,
            "grade" to grade,
            "remarks" to remarks.trim(),
            "published" to published,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      } else {
        batch.update(
          docRef,
          mapOf(
            "assignmentScore" to safeAssign,
            "examScore" to safeExam,
            "totalScore" to total,
            "grade" to grade,
            "remarks" to remarks.trim(),
            "published" to published,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      if (published) {
        val notifRef = db.collection("notifications").document()
        batch.set(
          notifRef,
          mapOf(
            "userId" to studentUid,
            "title" to "Academic Result Published",
            "message" to "${programme.title}: Total $total/100 (Grade $grade)",
            "type" to "result",
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  fun observeCertificates(isAdmin: Boolean, uid: String): Flow<List<Certificate>> = flow {
    requireUserId()
    val query =
      if (isAdmin) {
        db.collection("certificates")
      } else {
        db.collection("certificates").whereEqualTo("studentUid", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            Certificate::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "certificates")
          throw error
        }
    )
  }

  suspend fun issueCertificate(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programmeId: String,
    programmeTitle: String,
    graduationDate: String,
    signatoryName: String,
    signatoryTitle: String,
  ): Result<String> {
    val adminUid = requireUserId()
    val certRef = db.collection("certificates").document()
    val code = "JYC-CERT-${UUID.randomUUID().toString().take(8).uppercase()}"
    return try {
      val batch = db.batch()
      batch.set(
        certRef,
        mapOf(
          "studentUid" to studentUid,
          "studentName" to studentName,
          "studentIdNumber" to studentIdNumber,
          "programmeId" to programmeId,
          "programmeTitle" to programmeTitle,
          "graduationDate" to graduationDate,
          "verificationCode" to code,
          "signatoryName" to signatoryName,
          "signatoryTitle" to signatoryTitle,
          "issuedByUid" to adminUid,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      val notifRef = db.collection("notifications").document()
      batch.set(
        notifRef,
        mapOf(
          "userId" to studentUid,
          "title" to "Certificate Ready",
          "message" to "Your graduation certificate for $programmeTitle is ready to download.",
          "type" to "certificate",
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(certRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, certRef.path)
      Result.failure(e)
    }
  }

  fun observeLearningMaterials(): Flow<List<LearningMaterial>> = flow {
    requireUserId()
    emitAll(
      db.collection("learning_materials")
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            LearningMaterial::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "learning_materials")
          throw error
        }
    )
  }

  suspend fun addLearningMaterial(
    programmeId: String,
    programmeTitle: String,
    title: String,
    description: String,
    resourceUrl: String,
    authorName: String,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("learning_materials").document()
    return try {
      docRef
        .set(
          mapOf(
            "programmeId" to programmeId,
            "programmeTitle" to programmeTitle,
            "title" to title.trim(),
            "description" to description.trim(),
            "resourceUrl" to resourceUrl.trim(),
            "authorName" to authorName,
            "authorUid" to uid,
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
