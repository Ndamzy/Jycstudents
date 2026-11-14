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

  suspend fun adminReviewPayment(
    payment: Payment,
    approve: Boolean,
    adminRemarks: String,
  ): Result<Unit> {
    requireUserId()
    val payRef = db.collection("payments").document(payment.id)
    val enrollmentRef = db.collection("enrollments").document(payment.enrollmentId)
    val notifRef = db.collection("notifications").document()
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
        if (enrSnap.exists()) {
          val currentPaid = enrSnap.getLong("amountPaidNaira") ?: 0L
          val totalFee = enrSnap.getLong("totalFeeNaira") ?: 0L
          val updatedPaid = currentPaid + payment.amountNaira
          val updatedEnrStatus = if (updatedPaid >= totalFee && totalFee > 0) "completed" else "active"
          batch.update(
            enrollmentRef,
            mapOf(
              "amountPaidNaira" to updatedPaid,
              "status" to updatedEnrStatus,
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
