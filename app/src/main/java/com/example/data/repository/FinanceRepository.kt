package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Enrollment
import com.example.data.model.Payment
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(private val db: FirebaseFirestore) {
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

  fun observePayments(isAdmin: Boolean, uid: String): Flow<List<Payment>> = flow {
    requireUserId()
    val query =
      if (isAdmin) {
        db.collection("payments")
      } else {
        db.collection("payments").whereEqualTo("userId", uid)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Payment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "payments")
          throw error
        }
    )
  }

  suspend fun submitPayment(
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    enrollment: Enrollment,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    senderName: String,
    evidenceBase64: String,
  ): Result<String> {
    requireUserId()
    val docRef = db.collection("payments").document()
    return try {
      docRef
        .set(
          mapOf(
            "userId" to studentUid,
            "studentName" to studentName,
            "studentIdNumber" to studentIdNumber,
            "enrollmentId" to enrollment.id,
            "programmeId" to enrollment.programmeId,
            "programmeTitle" to enrollment.programmeTitle,
            "amountNaira" to amountNaira,
            "method" to method,
            "reference" to reference.trim(),
            "paymentDate" to paymentDate.trim(),
            "senderName" to senderName.trim(),
            "evidenceBase64" to evidenceBase64,
            "status" to "pending",
            "adminRemarks" to "",
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

  suspend fun recordAdminPayment(
    enrollment: Enrollment,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String,
    remarks: String,
    actorName: String,
  ): Result<String> {
    val actorUid = requireUserId()
    val payRef = db.collection("payments").document()
    val enrollmentRef = db.collection("enrollments").document(enrollment.id)
    val userRef = db.collection("users").document(enrollment.userId)
    val notifRef = db.collection("notifications").document()
    val logRef = db.collection("activity_logs").document()
    return try {
      val batch = db.batch()
      batch.set(
        payRef,
        mapOf(
          "userId" to enrollment.userId,
          "studentName" to enrollment.studentName,
          "studentIdNumber" to enrollment.studentIdNumber,
          "enrollmentId" to enrollment.id,
          "programmeId" to enrollment.programmeId,
          "programmeTitle" to enrollment.programmeTitle,
          "amountNaira" to amountNaira,
          "method" to method,
          "reference" to reference.trim(),
          "paymentDate" to paymentDate.trim(),
          "senderName" to actorName.ifBlank { "JY-Computers Admin" },
          "evidenceBase64" to "",
          "status" to "approved",
          "adminRemarks" to remarks.trim().ifBlank { "Recorded by Administration" },
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      val enrSnap = enrollmentRef.get().await()
      var newPaymentStatus = "paid"
      if (enrSnap.exists()) {
        val currentPaid = enrSnap.getLong("amountPaidNaira") ?: 0L
        val totalFee = enrSnap.getLong("totalFeeNaira") ?: 0L
        val updatedPaid = currentPaid + amountNaira
        val updatedEnrStatus = if (updatedPaid >= totalFee && totalFee > 0) "completed" else "active"
        newPaymentStatus = if (updatedPaid >= totalFee && totalFee > 0) "paid" else "pending"
        batch.update(
          enrollmentRef,
          mapOf(
            "amountPaidNaira" to updatedPaid,
            "status" to updatedEnrStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      if (userRef.get().await().exists()) {
        batch.update(
          userRef,
          mapOf(
            "paymentStatus" to newPaymentStatus,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      batch.set(
        notifRef,
        mapOf(
          "userId" to enrollment.userId,
          "title" to "Tuition Payment Recorded",
          "message" to "A ${method.replace('_', ' ')} payment of ₦%,d (${reference}) for ${enrollment.programmeTitle} has been recorded."
            .format(amountNaira),
          "type" to "payment",
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "RECORD_PAYMENT",
          "targetId" to payRef.id,
          "details" to "Recorded ₦%,d ($method) for ${enrollment.studentName} (${reference})".format(amountNaira),
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.commit().await()
      Result.success(payRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, payRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminReviewPayment(
    payment: Payment,
    approve: Boolean,
    adminRemarks: String,
    actorName: String = "Administrator",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val payRef = db.collection("payments").document(payment.id)
    val enrollmentRef = db.collection("enrollments").document(payment.enrollmentId)
    val userRef = db.collection("users").document(payment.userId)
    val notifRef = db.collection("notifications").document()
    val logRef = db.collection("activity_logs").document()
    return try {
      val batch = db.batch()
      val newStatus = if (approve) "approved" else "rejected"
      batch.update(
        payRef,
        mapOf(
          "status" to newStatus,
          "adminRemarks" to adminRemarks.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (approve && payment.status != "approved") {
        val enrSnap = enrollmentRef.get().await()
        var userPayStatus = "paid"
        if (enrSnap.exists()) {
          val currentPaid = enrSnap.getLong("amountPaidNaira") ?: 0L
          val totalFee = enrSnap.getLong("totalFeeNaira") ?: 0L
          val updatedPaid = currentPaid + payment.amountNaira
          val updatedEnrStatus = if (updatedPaid >= totalFee && totalFee > 0) "completed" else "active"
          userPayStatus = if (updatedPaid >= totalFee && totalFee > 0) "paid" else "pending"
          batch.update(
            enrollmentRef,
            mapOf(
              "amountPaidNaira" to updatedPaid,
              "status" to updatedEnrStatus,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
        if (userRef.get().await().exists()) {
          batch.update(
            userRef,
            mapOf(
              "paymentStatus" to userPayStatus,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      batch.set(
        notifRef,
        mapOf(
          "userId" to payment.userId,
          "title" to if (approve) "Payment Approved" else "Payment Rejected",
          "message" to
            if (approve) {
              "Your tuition payment of ₦%,d (${payment.reference}) for ${payment.programmeTitle} has been confirmed."
                .format(payment.amountNaira)
            } else {
              "Payment ${payment.reference} was declined: ${adminRemarks.ifBlank { "Please verify transfer details" }}"
            },
          "type" to "payment",
          "isRead" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
          "targetId" to payment.id,
          "details" to "${if (approve) "Approved" else "Rejected"} ₦%,d (${payment.reference}) for ${payment.studentName}"
            .format(payment.amountNaira),
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, payRef.path)
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
