package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Enrollment
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.JyServiceItem
import com.example.data.model.Payment
import com.example.data.model.ServiceRequest
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
import kotlin.random.Random
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

  fun observeServices(): Flow<List<JyServiceItem>> = flow {
    requireUserId()
    emitAll(
      db.collection("services")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(JyServiceItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedWith(compareBy<JyServiceItem> { it.category }.thenBy { it.title })
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "services")
          throw error
        }
    )
  }

  suspend fun saveService(service: JyServiceItem): Result<String> {
    requireUserId()
    val docRef =
      if (service.id.isBlank()) db.collection("services").document()
      else db.collection("services").document(service.id)
    return try {
      val exists = if (service.id.isNotBlank()) docRef.get().await().exists() else false
      if (!exists) {
        docRef
          .set(
            mapOf(
              "title" to service.title.trim(),
              "category" to service.category.trim(),
              "description" to service.description.trim(),
              "defaultFeeNaira" to service.defaultFeeNaira.coerceAtLeast(0L),
              "paymentEnabled" to service.paymentEnabled,
              "allowPartialPayment" to service.allowPartialPayment,
              "isTrainingCourse" to service.isTrainingCourse,
              "linkedProgrammeId" to service.linkedProgrammeId,
              "active" to service.active,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } else {
        docRef
          .update(
            mapOf(
              "title" to service.title.trim(),
              "category" to service.category.trim(),
              "description" to service.description.trim(),
              "defaultFeeNaira" to service.defaultFeeNaira.coerceAtLeast(0L),
              "paymentEnabled" to service.paymentEnabled,
              "allowPartialPayment" to service.allowPartialPayment,
              "isTrainingCourse" to service.isTrainingCourse,
              "linkedProgrammeId" to service.linkedProgrammeId,
              "active" to service.active,
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

  suspend fun toggleServicePayment(serviceId: String, enabled: Boolean): Result<Unit> {
    requireUserId()
    val docRef = db.collection("services").document(serviceId)
    return try {
      docRef
        .update(
          mapOf(
            "paymentEnabled" to enabled,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteService(serviceId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("services").document(serviceId)
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

  fun observeServiceRequests(isAdmin: Boolean, uid: String): Flow<List<ServiceRequest>> = flow {
    requireUserId()
    val query =
      if (isAdmin) db.collection("service_requests")
      else db.collection("service_requests").whereEqualTo("userId", uid)
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(ServiceRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "service_requests")
          throw error
        }
    )
  }

  suspend fun createServiceRequest(
    customerUid: String,
    customerName: String,
    customerPhone: String,
    customerEmail: String,
    customerRegNumber: String,
    service: JyServiceItem,
    details: String,
    customFeeNaira: Long? = null,
  ): Result<ServiceRequest> {
    requireUserId()
    val docRef = db.collection("service_requests").document()
    val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val randSuffix = Random.nextInt(1000, 9999)
    val reqNo = "SRV-$year-$randSuffix"
    val invNo = "INV-$year-$randSuffix"
    val fee = (customFeeNaira ?: service.defaultFeeNaira).coerceAtLeast(0L)
    return try {
      docRef
        .set(
          mapOf(
            "userId" to customerUid,
            "customerName" to customerName.trim().ifBlank { "Customer" },
            "customerPhone" to customerPhone.trim(),
            "customerEmail" to customerEmail.trim(),
            "customerRegNumber" to customerRegNumber.trim().ifBlank { "JYC-GUEST" },
            "requestNumber" to reqNo,
            "invoiceNumber" to invNo,
            "serviceId" to service.id,
            "serviceTitle" to service.title,
            "serviceCategory" to service.category,
            "details" to details.trim(),
            "totalFeeNaira" to fee,
            "amountPaidNaira" to 0L,
            "paymentStatus" to "unpaid",
            "status" to "pending",
            "adminNotes" to "",
            "invoiceDate" to today,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(
        ServiceRequest(
          id = docRef.id,
          userId = customerUid,
          customerName = customerName.trim().ifBlank { "Customer" },
          customerPhone = customerPhone.trim(),
          customerEmail = customerEmail.trim(),
          customerRegNumber = customerRegNumber.trim().ifBlank { "JYC-GUEST" },
          requestNumber = reqNo,
          invoiceNumber = invNo,
          serviceId = service.id,
          serviceTitle = service.title,
          serviceCategory = service.category,
          details = details.trim(),
          totalFeeNaira = fee,
          amountPaidNaira = 0L,
          paymentStatus = "unpaid",
          status = "pending",
          invoiceDate = today,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateServiceRequestAdmin(
    requestId: String,
    status: String,
    totalFeeNaira: Long? = null,
    adminNotes: String = "",
    actorName: String = "Administrator",
  ): Result<Unit> {
    val actorUid = requireUserId()
    val docRef = db.collection("service_requests").document(requestId)
    val logRef = db.collection("activity_logs").document()
    return try {
      val snap = docRef.get().await()
      if (!snap.exists()) {
        return Result.failure(IllegalArgumentException("Service request not found"))
      }
      val batch = db.batch()
      val updates =
        mutableMapOf<String, Any>(
          "status" to status,
          "adminNotes" to adminNotes.trim(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (totalFeeNaira != null && totalFeeNaira >= 0L) {
        val paid = snap.getLong("amountPaidNaira") ?: 0L
        updates["totalFeeNaira"] = totalFeeNaira
        updates["paymentStatus"] =
          when {
            totalFeeNaira > 0L && paid >= totalFeeNaira -> "paid"
            paid > 0L -> "partial"
            else -> "unpaid"
          }
      }
      batch.update(docRef, updates)
      batch.set(
        logRef,
        mapOf(
          "actorUid" to actorUid,
          "actorName" to actorName.ifBlank { "Administrator" },
          "action" to "UPDATE_SERVICE_REQUEST",
          "targetId" to requestId,
          "details" to "Updated service request $requestId status to ${status.uppercase()}",
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

  private suspend fun checkReferenceAlreadyUsed(reference: String, ownerUid: String?): String? {
    val cleanRef = reference.trim()
    if (cleanRef.length < 4) return "Transaction reference must be at least 4 characters."
    val refKey = JyCatalogDefaults.normalizePaymentReferenceKey(cleanRef)
    if (refKey.length < 4) return "Transaction reference must contain at least 4 alphanumeric characters."
    val refDoc = db.collection("payment_references").document(refKey).get().await()
    if (refDoc.exists()) {
      val status = refDoc.getString("status").orEmpty()
      return if (status == "pending") {
        "Duplicate transaction reference: '$cleanRef' is already pending verification."
      } else {
        "Duplicate transaction reference: '$cleanRef' has already been verified."
      }
    }
    if (!ownerUid.isNullOrBlank()) {
      val existingMine =
        db.collection("payments")
          .whereEqualTo("userId", ownerUid)
          .whereEqualTo("referenceNormalized", refKey)
          .get()
          .await()
      if (existingMine.documents.any { it.getString("status") != "rejected" }) {
        return "You have already submitted transaction reference '$cleanRef'."
      }
    }
    return null
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
    senderBank: String = "OPay",
    serviceCategory: String = "Training Courses",
  ): Result<String> {
    return submitUniversalPayment(
      customerUid = studentUid,
      customerName = studentName,
      customerIdNumber = studentIdNumber,
      enrollment = enrollment,
      serviceRequest = null,
      amountNaira = amountNaira,
      method = method,
      reference = reference,
      paymentDate = paymentDate,
      senderName = senderName,
      senderBank = senderBank,
      evidenceBase64 = evidenceBase64,
      serviceCategory = serviceCategory,
    )
  }

  suspend fun submitUniversalPayment(
    customerUid: String,
    customerName: String,
    customerIdNumber: String,
    enrollment: Enrollment?,
    serviceRequest: ServiceRequest?,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    senderName: String,
    senderBank: String,
    evidenceBase64: String,
    serviceCategory: String? = null,
  ): Result<String> {
    requireUserId()
    require(enrollment != null || serviceRequest != null) {
      "Select a course enrolment or service request before submitting payment."
    }
    val docRef = db.collection("payments").document()
    val cleanRef = reference.trim()
    val refKey = JyCatalogDefaults.normalizePaymentReferenceKey(cleanRef)
    val refRegistryDoc = db.collection("payment_references").document(refKey)
    val itemId = enrollment?.programmeId ?: serviceRequest?.serviceId.orEmpty()
    val itemTitle = enrollment?.programmeTitle ?: serviceRequest?.serviceTitle.orEmpty()
    val category =
      serviceCategory
        ?: serviceRequest?.serviceCategory
        ?: "Training Courses"
    val invoiceNo =
      serviceRequest?.invoiceNumber?.ifBlank { "INV-TRN-${enrollment?.id?.take(6)?.uppercase()}" }
        ?: "INV-TRN-${enrollment?.id?.take(6)?.uppercase()}"

    return try {
      val dupError = checkReferenceAlreadyUsed(cleanRef, customerUid)
      if (dupError != null) {
        return Result.failure(IllegalStateException(dupError))
      }

      val batch = db.batch()
      batch.set(
        docRef,
        mapOf(
          "userId" to customerUid,
          "studentName" to customerName,
          "studentIdNumber" to customerIdNumber,
          "enrollmentId" to (enrollment?.id ?: ""),
          "serviceRequestId" to (serviceRequest?.id ?: ""),
          "requestNumber" to (serviceRequest?.requestNumber ?: ""),
          "invoiceNumber" to invoiceNo,
          "programmeId" to itemId,
          "programmeTitle" to itemTitle,
          "serviceCategory" to category,
          "amountNaira" to amountNaira,
          "method" to method,
          "reference" to cleanRef,
          "referenceNormalized" to refKey,
          "paymentDate" to paymentDate.trim(),
          "senderName" to senderName.trim(),
          "senderBank" to senderBank.trim().ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_BANK },
          "evidenceBase64" to evidenceBase64,
          "status" to "pending",
          "adminRemarks" to "",
          "verifiedByUid" to "",
          "verifiedByName" to "",
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.set(
        refRegistryDoc,
        mapOf(
          "referenceNormalized" to refKey,
          "paymentId" to docRef.id,
          "status" to "pending",
          "submittedByUid" to customerUid,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun recordAdminPayment(
    enrollment: Enrollment? = null,
    serviceRequest: ServiceRequest? = null,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String,
    remarks: String,
    actorName: String,
  ): Result<String> {
    val actorUid = requireUserId()
    require(enrollment != null || serviceRequest != null) {
      "Select an enrollment or service request to record payment."
    }
    val payRef = db.collection("payments").document()
    val cleanRef = reference.trim()
    val refKey = JyCatalogDefaults.normalizePaymentReferenceKey(cleanRef)
    val refRegistryDoc = db.collection("payment_references").document(refKey)

    val targetUid = enrollment?.userId ?: serviceRequest!!.userId
    val targetName = enrollment?.studentName ?: serviceRequest!!.customerName
    val targetIdNo = enrollment?.studentIdNumber ?: serviceRequest!!.customerRegNumber.ifBlank { serviceRequest.requestNumber }
    val itemId = enrollment?.programmeId ?: serviceRequest!!.serviceId
    val itemTitle = enrollment?.programmeTitle ?: serviceRequest!!.serviceTitle
    val category = serviceRequest?.serviceCategory ?: "Training Courses"
    val notifRef = db.collection("notifications").document()
    val logRef = db.collection("activity_logs").document()

    return try {
      if (refRegistryDoc.get().await().exists()) {
        return Result.failure(
          IllegalStateException("Duplicate transaction reference: '$cleanRef' is already verified.")
        )
      }

      val batch = db.batch()
      batch.set(
        payRef,
        mapOf(
          "userId" to targetUid,
          "studentName" to targetName,
          "studentIdNumber" to targetIdNo,
          "enrollmentId" to (enrollment?.id ?: ""),
          "serviceRequestId" to (serviceRequest?.id ?: ""),
          "requestNumber" to (serviceRequest?.requestNumber ?: ""),
          "invoiceNumber" to (serviceRequest?.invoiceNumber ?: "INV-${enrollment?.id?.take(6)?.uppercase()}"),
          "programmeId" to itemId,
          "programmeTitle" to itemTitle,
          "serviceCategory" to category,
          "amountNaira" to amountNaira,
          "method" to method,
          "reference" to cleanRef,
          "referenceNormalized" to refKey,
          "paymentDate" to paymentDate.trim(),
          "senderName" to actorName.ifBlank { "JY-Computers Admin" },
          "senderBank" to JyCatalogDefaults.OFFICIAL_OPAY_BANK,
          "evidenceBase64" to "",
          "status" to "approved",
          "adminRemarks" to remarks.trim().ifBlank { "Recorded by Administration" },
          "verifiedByUid" to actorUid,
          "verifiedByName" to actorName.ifBlank { "Administrator" },
          "verifiedAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      batch.set(
        refRegistryDoc,
        mapOf(
          "referenceNormalized" to refKey,
          "paymentId" to payRef.id,
          "status" to "approved",
          "submittedByUid" to targetUid,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (enrollment != null) {
        val enrollmentRef = db.collection("enrollments").document(enrollment.id)
        val userRef = db.collection("users").document(enrollment.userId)
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
      }

      if (serviceRequest != null) {
        val reqRef = db.collection("service_requests").document(serviceRequest.id)
        val reqSnap = reqRef.get().await()
        if (reqSnap.exists()) {
          val currentPaid = reqSnap.getLong("amountPaidNaira") ?: 0L
          val totalFee = reqSnap.getLong("totalFeeNaira") ?: 0L
          val updatedPaid = currentPaid + amountNaira
          val payStatus =
            when {
              totalFee > 0L && updatedPaid >= totalFee -> "paid"
              updatedPaid > 0L -> "partial"
              else -> "unpaid"
            }
          val reqStatus =
            if (payStatus == "paid" && reqSnap.getString("status") == "pending") "in_progress"
            else reqSnap.getString("status") ?: "pending"
          batch.update(
            reqRef,
            mapOf(
              "amountPaidNaira" to updatedPaid,
              "paymentStatus" to payStatus,
              "status" to reqStatus,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }

      batch.set(
        notifRef,
        mapOf(
          "userId" to targetUid,
          "title" to "Payment Confirmed",
          "message" to "A ${method.replace('_', ' ')} payment of ₦%,d ($cleanRef) for $itemTitle has been recorded."
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
          "details" to "Recorded ₦%,d ($method) for $targetName ($cleanRef)".format(amountNaira),
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
    val notifRef = db.collection("notifications").document()
    val logRef = db.collection("activity_logs").document()
    val refKey = JyCatalogDefaults.normalizePaymentReferenceKey(payment.reference)
    val refRegistryDoc = db.collection("payment_references").document(refKey)

    return try {
      val existingRefDoc = refRegistryDoc.get().await()
      val existingRefPaymentId = existingRefDoc.getString("paymentId").orEmpty()
      val existingRefStatus = existingRefDoc.getString("status").orEmpty()
      if (approve) {
        if (payment.status == "approved") {
          return Result.failure(IllegalStateException("Payment ${payment.reference} is already approved."))
        }
        if (
          existingRefDoc.exists() &&
            (existingRefPaymentId != payment.id || existingRefStatus != "pending")
        ) {
          return Result.failure(
            IllegalStateException(
              "Duplicate reference blocked: '${payment.reference}' was already approved or claimed for another transaction."
            )
          )
        }
        val otherApproved =
          db.collection("payments")
            .whereEqualTo("referenceNormalized", refKey)
            .whereEqualTo("status", "approved")
            .limit(1)
            .get()
            .await()
        if (!otherApproved.isEmpty) {
          return Result.failure(
            IllegalStateException(
              "Duplicate reference blocked: '${payment.reference}' already exists on an approved payment."
            )
          )
        }
      }

      val batch = db.batch()
      val newStatus = if (approve) "approved" else "rejected"
      val safeActor = actorName.ifBlank { "Administrator" }
      val safeRemarks =
        adminRemarks.trim().ifBlank {
          if (approve) "Verified & Approved" else "Rejected: Transaction reference could not be verified"
        }

      batch.update(
        payRef,
        mapOf(
          "status" to newStatus,
          "adminRemarks" to safeRemarks,
          "verifiedByUid" to actorUid,
          "verifiedByName" to safeActor,
          "verifiedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )

      if (!approve) {
        if (
          existingRefDoc.exists() &&
            existingRefPaymentId == payment.id &&
            existingRefStatus == "pending"
        ) {
          batch.delete(refRegistryDoc)
        }
      }

      if (approve && payment.status != "approved") {
        if (existingRefDoc.exists()) {
          batch.update(
            refRegistryDoc,
            mapOf("status" to "approved"),
          )
        } else {
          batch.set(
            refRegistryDoc,
            mapOf(
              "referenceNormalized" to refKey,
              "paymentId" to payment.id,
              "status" to "approved",
              "submittedByUid" to payment.userId,
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          )
        }

        if (payment.enrollmentId.isNotBlank()) {
          val enrollmentRef = db.collection("enrollments").document(payment.enrollmentId)
          val userRef = db.collection("users").document(payment.userId)
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

        if (payment.serviceRequestId.isNotBlank()) {
          val reqRef = db.collection("service_requests").document(payment.serviceRequestId)
          val reqSnap = reqRef.get().await()
          if (reqSnap.exists()) {
            val currentPaid = reqSnap.getLong("amountPaidNaira") ?: 0L
            val totalFee = reqSnap.getLong("totalFeeNaira") ?: 0L
            val updatedPaid = currentPaid + payment.amountNaira
            val payStatus =
              when {
                totalFee > 0L && updatedPaid >= totalFee -> "paid"
                updatedPaid > 0L -> "partial"
                else -> "unpaid"
              }
            val currentReqStatus = reqSnap.getString("status") ?: "pending"
            val updatedReqStatus = if (payStatus == "paid" && currentReqStatus == "pending") "in_progress" else currentReqStatus
            batch.update(
              reqRef,
              mapOf(
                "amountPaidNaira" to updatedPaid,
                "paymentStatus" to payStatus,
                "status" to updatedReqStatus,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            )
          }
        }
      }

      batch.set(
        notifRef,
        mapOf(
          "userId" to payment.userId,
          "title" to if (approve) "Payment Approved" else "Payment Rejected",
          "message" to
            if (approve) {
              "Your payment of ₦%,d (${payment.reference}) for ${payment.programmeTitle} has been confirmed. Download your receipt in Payment History."
                .format(payment.amountNaira)
            } else {
              "Payment ${payment.reference} was declined: $safeRemarks"
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
          "actorName" to safeActor,
          "action" to if (approve) "APPROVE_PAYMENT" else "REJECT_PAYMENT",
          "targetId" to payment.id,
          "details" to "${if (approve) "Approved" else "Rejected"} ₦%,d (${payment.reference}) for ${payment.studentName} — $safeRemarks"
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
