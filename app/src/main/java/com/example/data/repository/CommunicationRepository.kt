package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Announcement
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.ChatReport
import com.example.data.model.ChatRoom
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

class CommunicationRepository(private val db: FirebaseFirestore) {
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

  fun observeAnnouncements(): Flow<List<Announcement>> = flow {
    requireUserId()
    emitAll(
      db.collection("announcements")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "announcements")
          throw error
        }
    )
  }

  suspend fun createAnnouncement(
    title: String,
    body: String,
    category: String,
    targetRole: String,
    eventDate: String,
    authorName: String,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("announcements").document()
    return try {
      docRef
        .set(
          mapOf(
            "title" to title.trim(),
            "body" to body.trim(),
            "category" to category,
            "targetRole" to targetRole,
            "eventDate" to eventDate.trim(),
            "authorName" to authorName,
            "authorUid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
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

  suspend fun deleteAnnouncement(announcementId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("announcements").document(announcementId)
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

  fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
    requireUserId()
    emitAll(
      db.collection("notifications")
        .whereEqualTo("userId", uid)
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(
              AppNotification::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "notifications")
          throw error
        }
    )
  }

  suspend fun markNotificationRead(notificationId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("notifications").document(notificationId)
    return try {
      docRef.update("isRead", true).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  fun observePublicGroupRooms(): Flow<List<ChatRoom>> = flow {
    requireUserId()
    emitAll(
      db.collection("chat_rooms")
        .whereEqualTo("isGroupPublic", true)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "chat_rooms_public")
          throw error
        }
    )
  }

  fun observeMemberRooms(uid: String): Flow<List<ChatRoom>> = flow {
    requireUserId()
    emitAll(
      db.collection("chat_rooms")
        .whereArrayContains("memberUids", uid)
        .snapshots()
        .map { snapshot ->
          snapshot.toObjects(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "chat_rooms_member")
          throw error
        }
    )
  }

  suspend fun getOrCreateDirectRoom(
    currentUid: String,
    currentName: String,
    peerUid: String,
    peerName: String,
  ): Result<ChatRoom> {
    requireUserId()
    val sorted = listOf(currentUid, peerUid).sorted()
    val roomId = "dm_${sorted[0]}_${sorted[1]}".take(120)
    val roomRef = db.collection("chat_rooms").document(roomId)
    return try {
      val snap = roomRef.get().await()
      if (snap.exists()) {
        val room =
          snap.toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        if (room != null) return Result.success(room)
      }
      val title = "$currentName & $peerName"
      roomRef
        .set(
          mapOf(
            "roomType" to "direct",
            "title" to title.take(110),
            "programmeId" to "",
            "memberUids" to sorted,
            "isGroupPublic" to false,
            "typingUids" to emptyList<String>(),
            "lastMessage" to "",
            "lastSenderName" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      val created =
        roomRef
          .get()
          .await()
          .toObject(ChatRoom::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: ChatRoom(id = roomId, roomType = "direct", title = title, memberUids = sorted)
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, roomRef.path)
      Result.failure(e)
    }
  }

  suspend fun createGroupOrCourseRoom(
    title: String,
    roomType: String,
    programmeId: String,
  ): Result<String> {
    val uid = requireUserId()
    val roomRef = db.collection("chat_rooms").document()
    return try {
      roomRef
        .set(
          mapOf(
            "roomType" to roomType,
            "title" to title.trim(),
            "programmeId" to programmeId,
            "memberUids" to listOf(uid),
            "isGroupPublic" to true,
            "typingUids" to emptyList<String>(),
            "lastMessage" to "Discussion group created",
            "lastSenderName" to "System",
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(roomRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, roomRef.path)
      Result.failure(e)
    }
  }

  suspend fun ensureGeneralLoungeExists() {
    val uid = auth.currentUser?.uid ?: return
    val loungeRef = db.collection("chat_rooms").document("general_student_lounge")
    try {
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
    } catch (_: Exception) {
      // Best-effort creation; may race with another user initializing concurrently
    }
  }

  fun observeRoomMessages(room: ChatRoom, currentUid: String): Flow<List<ChatMessage>> = flow {
    requireUserId()
    val coll = db.collection("chat_rooms").document(room.id).collection("messages")
    val query =
      if (room.isGroupPublic) {
        coll.whereEqualTo("isGroupPublic", true)
      } else {
        coll.whereArrayContains("memberUids", currentUid)
      }
    emitAll(
      query
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "chat_rooms/${room.id}/messages")
          throw error
        }
    )
  }

  suspend fun sendMessage(
    room: ChatRoom,
    senderName: String,
    text: String,
    imageBase64: String = "",
    attachmentName: String = "",
  ): Result<Unit> {
    val uid = requireUserId()
    val roomRef = db.collection("chat_rooms").document(room.id)
    val msgRef = roomRef.collection("messages").document()
    return try {
      val batch = db.batch()
      batch.set(
        msgRef,
        mapOf(
          "roomId" to room.id,
          "senderUid" to uid,
          "senderName" to senderName,
          "text" to text.trim(),
          "imageBase64" to imageBase64,
          "attachmentName" to attachmentName,
          "memberUids" to room.memberUids,
          "isGroupPublic" to room.isGroupPublic,
          "readBy" to listOf(uid),
          "reported" to false,
          "deleted" to false,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      val preview =
        when {
          text.isNotBlank() -> text.trim().take(80)
          imageBase64.isNotBlank() -> "[Image attachment]"
          else -> attachmentName
        }
      batch.update(
        roomRef,
        mapOf(
          "lastMessage" to preview,
          "lastSenderName" to senderName,
          "lastMessageAt" to FieldValue.serverTimestamp(),
          "typingUids" to FieldValue.arrayRemove(uid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, msgRef.path)
      Result.failure(e)
    }
  }

  suspend fun setTypingStatus(roomId: String, isTyping: Boolean) {
    val uid = auth.currentUser?.uid ?: return
    val roomRef = db.collection("chat_rooms").document(roomId)
    try {
      val op = if (isTyping) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
      roomRef.update(mapOf("typingUids" to op, "updatedAt" to FieldValue.serverTimestamp())).await()
    } catch (_: Exception) {
      // Best effort typing status
    }
  }

  suspend fun markMessageRead(roomId: String, messageId: String) {
    val uid = auth.currentUser?.uid ?: return
    val msgRef = db.collection("chat_rooms").document(roomId).collection("messages").document(messageId)
    try {
      msgRef.update("readBy", FieldValue.arrayUnion(uid)).await()
    } catch (_: Exception) {
      // Best effort read receipt
    }
  }

  suspend fun reportMessage(
    roomId: String,
    message: ChatMessage,
    reason: String,
  ): Result<Unit> {
    val uid = requireUserId()
    val reportRef = db.collection("chat_reports").document()
    val msgRef = db.collection("chat_rooms").document(roomId).collection("messages").document(message.id)
    return try {
      val batch = db.batch()
      batch.set(
        reportRef,
        mapOf(
          "reporterUid" to uid,
          "reportedUid" to message.senderUid,
          "roomId" to roomId,
          "messageId" to message.id,
          "reason" to reason.trim(),
          "messagePreview" to message.text.take(200),
          "status" to "open",
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(msgRef, "reported", true)
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, reportRef.path)
      Result.failure(e)
    }
  }

  fun observeChatReportsAdmin(): Flow<List<ChatReport>> = flow {
    requireUserId()
    emitAll(
      db.collection("chat_reports")
        .snapshots()
        .map { snapshot ->
          snapshot
            .toObjects(ChatReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logFlowError(error, OperationType.LIST, "chat_reports")
          throw error
        }
    )
  }

  suspend fun adminModerateReport(
    report: ChatReport,
    deleteMessage: Boolean,
    status: String,
  ): Result<Unit> {
    requireUserId()
    val reportRef = db.collection("chat_reports").document(report.id)
    return try {
      val batch = db.batch()
      batch.update(reportRef, "status", status)
      if (deleteMessage && report.roomId.isNotBlank() && report.messageId.isNotBlank()) {
        val msgRef =
          db.collection("chat_rooms")
            .document(report.roomId)
            .collection("messages")
            .document(report.messageId)
        batch.delete(msgRef)
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, reportRef.path)
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
