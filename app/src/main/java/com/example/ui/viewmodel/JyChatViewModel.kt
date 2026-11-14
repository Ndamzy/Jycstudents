package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Announcement
import com.example.data.model.AppNotification
import com.example.data.model.BlockedUser
import com.example.data.model.ChatMessage
import com.example.data.model.ChatReport
import com.example.data.model.ChatRoom
import com.example.data.model.DirectoryEntry
import com.example.data.remote.unwrapFirestoreMessage
import com.example.data.repository.CommunicationRepository
import com.example.data.repository.ProfileAndSettingsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JyChatViewModel(
  private val commRepo: CommunicationRepository,
  private val profileRepo: ProfileAndSettingsRepository,
  val currentUserId: String,
  val currentUserName: String,
  val isAdmin: Boolean,
) : ViewModel() {

  private val _activeRoom = MutableStateFlow<ChatRoom?>(null)
  val activeRoom: StateFlow<ChatRoom?> = _activeRoom.asStateFlow()

  private val _chatFeedback = MutableStateFlow<String?>(null)
  val chatFeedback: StateFlow<String?> = _chatFeedback.asStateFlow()

  val announcements: StateFlow<List<Announcement>> =
    commRepo
      .observeAnnouncements()
      .catch { error -> handleStreamError("announcements", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val notifications: StateFlow<List<AppNotification>> =
    commRepo
      .observeNotifications(currentUserId)
      .catch { error -> handleStreamError("notifications", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val directory: StateFlow<List<DirectoryEntry>> =
    profileRepo
      .observeDirectory()
      .catch { error -> handleStreamError("directory", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val blockedUsers: StateFlow<List<BlockedUser>> =
    profileRepo
      .observeBlockedUsers(currentUserId)
      .catch { error -> handleStreamError("blockedUsers", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val publicGroupRooms: StateFlow<List<ChatRoom>> =
    commRepo
      .observePublicGroupRooms()
      .catch { error -> handleStreamError("publicRooms", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val directAndMemberRooms: StateFlow<List<ChatRoom>> =
    commRepo
      .observeMemberRooms(currentUserId)
      .catch { error -> handleStreamError("memberRooms", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val allRooms: StateFlow<List<ChatRoom>> =
    combine(publicGroupRooms, directAndMemberRooms) { pub, mem ->
        (pub + mem).distinctBy { it.id }.sortedByDescending { it.lastMessageAt?.seconds ?: 0L }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val activeMessages: StateFlow<List<ChatMessage>> =
    _activeRoom
      .flatMapLatest { room ->
        if (room == null) flowOf(emptyList())
        else commRepo.observeRoomMessages(room, currentUserId)
      }
      .catch { error -> handleStreamError("messages", error) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

  val chatReportsAdmin: StateFlow<List<ChatReport>> =
    if (isAdmin) {
      commRepo
        .observeChatReportsAdmin()
        .catch { error -> handleStreamError("chatReports", error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())
    } else {
      MutableStateFlow(emptyList())
    }

  fun clearFeedback() {
    _chatFeedback.value = null
  }

  fun openRoom(room: ChatRoom) {
    _activeRoom.value = room
  }

  fun closeRoom() {
    val prev = _activeRoom.value
    if (prev != null) {
      viewModelScope.launch { commRepo.setTypingStatus(prev.id, false) }
    }
    _activeRoom.value = null
  }

  fun startDirectChat(peer: DirectoryEntry, senderDisplayName: String, onRoomReady: (ChatRoom) -> Unit) {
    viewModelScope.launch {
      val res =
        commRepo.getOrCreateDirectRoom(
          currentUid = currentUserId,
          currentName = senderDisplayName,
          peerUid = peer.userId,
          peerName = peer.fullName,
        )
      if (res.isSuccess) {
        val room = res.getOrThrow()
        _activeRoom.value = room
        onRoomReady(room)
      } else {
        _chatFeedback.value =
          "Could not start chat: ${res.exceptionOrNull()?.localizedMessage}"
      }
    }
  }

  fun createGroupRoom(title: String, roomType: String = "group", programmeId: String = "") {
    viewModelScope.launch {
      val res = commRepo.createGroupOrCourseRoom(title, roomType, programmeId)
      _chatFeedback.value =
        if (res.isSuccess) "Group '$title' created."
        else "Could not create group: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun sendMessage(
    room: ChatRoom,
    senderName: String,
    text: String,
    imageBase64: String = "",
    attachmentName: String = "",
  ) {
    if (text.isBlank() && imageBase64.isBlank()) return
    viewModelScope.launch {
      val res = commRepo.sendMessage(room, senderName, text, imageBase64, attachmentName)
      if (res.isFailure) {
        _chatFeedback.value =
          "Message failed to send: ${res.exceptionOrNull()?.localizedMessage}"
      }
    }
  }

  fun setTyping(roomId: String, isTyping: Boolean) {
    viewModelScope.launch { commRepo.setTypingStatus(roomId, isTyping) }
  }

  fun markMessagesRead(roomId: String, messages: List<ChatMessage>) {
    viewModelScope.launch {
      messages
        .filter { it.senderUid != currentUserId && currentUserId !in it.readBy }
        .forEach { commRepo.markMessageRead(roomId, it.id) }
    }
  }

  fun reportMessage(roomId: String, message: ChatMessage, reason: String) {
    viewModelScope.launch {
      val res = commRepo.reportMessage(roomId, message, reason)
      _chatFeedback.value =
        if (res.isSuccess) "Message reported to Administrator for review."
        else "Report failed: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun blockUser(peerUid: String, peerName: String) {
    viewModelScope.launch {
      val res = profileRepo.blockUser(peerUid, peerName)
      _chatFeedback.value =
        if (res.isSuccess) "Blocked $peerName."
        else "Block failed: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun unblockUser(peerUid: String) {
    viewModelScope.launch {
      val res = profileRepo.unblockUser(peerUid)
      _chatFeedback.value =
        if (res.isSuccess) "User unblocked."
        else "Unblock failed: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  fun createAnnouncement(
    title: String,
    body: String,
    category: String,
    targetRole: String,
    eventDate: String,
    authorName: String,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch {
      val res = commRepo.createAnnouncement(title, body, category, targetRole, eventDate, authorName)
      _chatFeedback.value =
        if (res.isSuccess) {
          onDone()
          "Announcement published."
        } else {
          "Failed to publish announcement: ${res.exceptionOrNull()?.localizedMessage}"
        }
    }
  }

  fun deleteAnnouncement(id: String) {
    viewModelScope.launch {
      val res = commRepo.deleteAnnouncement(id)
      if (res.isFailure) {
        _chatFeedback.value =
          "Delete failed: ${res.exceptionOrNull()?.localizedMessage}"
      }
    }
  }

  fun markNotificationRead(id: String) {
    viewModelScope.launch { commRepo.markNotificationRead(id) }
  }

  fun moderateReportAdmin(report: ChatReport, deleteMessage: Boolean, status: String) {
    viewModelScope.launch {
      val res = commRepo.adminModerateReport(report, deleteMessage, status)
      _chatFeedback.value =
        if (res.isSuccess) "Moderation action applied."
        else "Moderation failed: ${res.exceptionOrNull()?.localizedMessage}"
    }
  }

  private fun handleStreamError(stream: String, error: Throwable) {
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val msg = unwrapFirestoreMessage(error, "Failed to load $stream")
    Log.w(TAG, "Stream error [$stream]: $msg", error)
  }

  private companion object {
    const val TAG = "JyChatViewModel"
    const val STOP_TIMEOUT = 5000L
  }
}
