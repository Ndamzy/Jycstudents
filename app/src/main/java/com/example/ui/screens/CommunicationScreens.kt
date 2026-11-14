package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AppNotification
import com.example.data.model.BlockedUser
import com.example.data.model.ChatMessage
import com.example.data.model.ChatReport
import com.example.data.model.ChatRoom
import com.example.data.model.DirectoryEntry
import com.example.ui.components.Base64Avatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.util.ImageCompressionUtil
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun StudentLiveChatScreen(
  currentUserId: String,
  currentUserName: String,
  directory: List<DirectoryEntry>,
  blockedUsers: List<BlockedUser>,
  activeRoom: ChatRoom?,
  messages: List<ChatMessage>,
  onStartDirectChat: (DirectoryEntry) -> Unit,
  onCloseRoom: () -> Unit,
  onSendMessage: (ChatRoom, String, String) -> Unit,
  onTypingChanged: (String, Boolean) -> Unit,
  onMarkMessagesRead: (String, List<ChatMessage>) -> Unit,
  onReportMessage: (String, ChatMessage, String) -> Unit,
  onBlockUser: (String, String) -> Unit,
  onUnblockUser: (String) -> Unit,
) {
  if (activeRoom != null) {
    ChatConversationView(
      room = activeRoom,
      currentUserId = currentUserId,
      currentUserName = currentUserName,
      messages = messages,
      blockedUids = blockedUsers.map { it.blockedUid }.toSet(),
      onBack = onCloseRoom,
      onSendMessage = { text, img -> onSendMessage(activeRoom, text, img) },
      onTypingChanged = { typing -> onTypingChanged(activeRoom.id, typing) },
      onMarkMessagesRead = { onMarkMessagesRead(activeRoom.id, messages) },
      onReportMessage = { msg, reason -> onReportMessage(activeRoom.id, msg, reason) },
      onBlockUser = onBlockUser,
    )
    return
  }

  val blockedSet = remember(blockedUsers) { blockedUsers.map { it.blockedUid }.toSet() }
  val peers = remember(directory, currentUserId) { directory.filter { it.userId != currentUserId } }
  var searchQuery by remember { mutableStateOf("") }
  val filteredPeers =
    remember(peers, searchQuery) {
      peers.filter {
        searchQuery.isBlank() ||
          it.fullName.contains(searchQuery, ignoreCase = true) ||
          it.selectedProgrammeTitle.contains(searchQuery, ignoreCase = true)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("student_chat_directory"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text("Student & Campus Live Chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search classmates or instructors") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (filteredPeers.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Peers Online Yet",
          message = "Other registered students and instructors will appear here for 1-to-1 live chat.",
        )
      }
    } else {
      items(filteredPeers, key = { it.userId }) { peer ->
        val isBlocked = peer.userId in blockedSet
        Card(
          onClick = { if (!isBlocked) onStartDirectChat(peer) },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Base64Avatar(
              base64 = "",
              name = peer.fullName,
              size = 46.dp,
              showOnlineDot = true,
              isOnline = peer.isOnline,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(peer.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                "${peer.role.replaceFirstChar { it.uppercase() }} • ${peer.selectedProgrammeTitle.ifBlank { peer.studentIdNumber }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (isBlocked) {
              TextButton(onClick = { onUnblockUser(peer.userId) }) { Text("Unblock") }
            } else {
              StatusPill(
                text = if (peer.isOnline) "ONLINE" else "OFFLINE",
                containerColor = if (peer.isOnline) JyEmeraldContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (peer.isOnline) JyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GroupChatScreen(
  currentUserId: String,
  currentUserName: String,
  activeRole: String,
  rooms: List<ChatRoom>,
  activeRoom: ChatRoom?,
  messages: List<ChatMessage>,
  blockedUsers: List<BlockedUser>,
  chatReports: List<ChatReport>,
  onOpenRoom: (ChatRoom) -> Unit,
  onCloseRoom: () -> Unit,
  onCreateGroup: (String) -> Unit,
  onSendMessage: (ChatRoom, String, String) -> Unit,
  onTypingChanged: (String, Boolean) -> Unit,
  onMarkMessagesRead: (String, List<ChatMessage>) -> Unit,
  onReportMessage: (String, ChatMessage, String) -> Unit,
  onBlockUser: (String, String) -> Unit,
  onModerateReport: (ChatReport, Boolean, String) -> Unit,
) {
  if (activeRoom != null) {
    ChatConversationView(
      room = activeRoom,
      currentUserId = currentUserId,
      currentUserName = currentUserName,
      messages = messages,
      blockedUids = blockedUsers.map { it.blockedUid }.toSet(),
      onBack = onCloseRoom,
      onSendMessage = { text, img -> onSendMessage(activeRoom, text, img) },
      onTypingChanged = { typing -> onTypingChanged(activeRoom.id, typing) },
      onMarkMessagesRead = { onMarkMessagesRead(activeRoom.id, messages) },
      onReportMessage = { msg, reason -> onReportMessage(activeRoom.id, msg, reason) },
      onBlockUser = onBlockUser,
    )
    return
  }

  var showCreateDialog by remember { mutableStateOf(false) }
  var newGroupTitle by remember { mutableStateOf("") }
  val groupRooms = remember(rooms) { rooms.filter { it.roomType != "direct" } }

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create Study / Course Group") },
      text = {
        OutlinedTextField(
          value = newGroupTitle,
          onValueChange = { newGroupTitle = it },
          label = { Text("Group Name (e.g. Coding Cohort A)") },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (newGroupTitle.trim().length >= 3) {
              onCreateGroup(newGroupTitle)
              newGroupTitle = ""
              showCreateDialog = false
            }
          }
        ) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
    )
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showCreateDialog = true },
        modifier = Modifier.testTag("create_group_fab"),
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Create Group Chat")
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("group_chat_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text("Course & Student Groups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      }

      if (groupRooms.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Discussion Groups Yet",
            message = "Tap + to start a course-based or student community discussion group.",
            icon = Icons.Filled.Group,
          )
        }
      } else {
        items(groupRooms, key = { it.id }) { room ->
          Card(
            onClick = { onOpenRoom(room) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier =
                  Modifier.size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                  room.lastMessage.ifBlank { "Tap to join conversation" },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      if (activeRole == "admin" && chatReports.isNotEmpty()) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Text("Moderation Queue (${chatReports.count { it.status == "open" }})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(chatReports, key = { it.id }) { report ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = JyCrimsonContainer.copy(alpha = 0.45f)),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("Reported: \"${report.messagePreview}\"", fontWeight = FontWeight.SemiBold)
              Text("Reason: ${report.reason} • Status: ${report.status.uppercase()}", style = MaterialTheme.typography.bodySmall)
              if (report.status == "open") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  TextButton(onClick = { onModerateReport(report, true, "resolved") }) {
                    Text("Remove Message", color = JyCrimson)
                  }
                  TextButton(onClick = { onModerateReport(report, false, "dismissed") }) {
                    Text("Dismiss")
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ChatConversationView(
  room: ChatRoom,
  currentUserId: String,
  currentUserName: String,
  messages: List<ChatMessage>,
  blockedUids: Set<String>,
  onBack: () -> Unit,
  onSendMessage: (String, String) -> Unit,
  onTypingChanged: (Boolean) -> Unit,
  onMarkMessagesRead: () -> Unit,
  onReportMessage: (ChatMessage, String) -> Unit,
  onBlockUser: (String, String) -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var inputText by remember { mutableStateOf("") }
  var pendingImageBase64 by remember { mutableStateOf("") }
  var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
  var reportReason by remember { mutableStateOf("") }

  LaunchedEffect(messages.size) {
    onMarkMessagesRead()
  }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val b64 = ImageCompressionUtil.compressUriToBase64(context, uri, maxDimension = 520, quality = 70)
          if (b64 != null) pendingImageBase64 = b64
        }
      }
    }

  if (reportTarget != null) {
    AlertDialog(
      onDismissRequest = { reportTarget = null },
      title = { Text("Report or Block User") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Report message from ${reportTarget!!.senderName} to Administrator?")
          OutlinedTextField(
            value = reportReason,
            onValueChange = { reportReason = it },
            label = { Text("Reason") },
            singleLine = true,
          )
          TextButton(
            onClick = {
              onBlockUser(reportTarget!!.senderUid, reportTarget!!.senderName)
              reportTarget = null
            }
          ) { Text("Block ${reportTarget!!.senderName}", color = JyCrimson) }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onReportMessage(reportTarget!!, reportReason.ifBlank { "Inappropriate content" })
            reportReason = ""
            reportTarget = null
          }
        ) { Text("Submit Report") }
      },
      dismissButton = { TextButton(onClick = { reportTarget = null }) { Text("Cancel") } },
    )
  }

  val visibleMessages = remember(messages, blockedUids) {
    messages.filter { !it.deleted && it.senderUid !in blockedUids }
  }
  val typingOthers = remember(room.typingUids, currentUserId) {
    room.typingUids.filter { it != currentUserId }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to rooms")
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(room.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          if (typingOthers.isNotEmpty()) {
            Text("Someone is typing...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
          } else {
            Text(
              if (room.isGroupPublic) "Course / Community Group" else "Direct Student Chat",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(visibleMessages, key = { it.id }) { msg ->
        val isMine = msg.senderUid == currentUserId
        val timeText =
          msg.createdAt?.toDate()?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
          } ?: "..."
        val bitmap = remember(msg.imageBase64) { ImageCompressionUtil.decodeBase64ToBitmap(msg.imageBase64) }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
          Surface(
            color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp),
          ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              if (!isMine) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                  Text(msg.senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                  IconButton(onClick = { reportTarget = msg }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Filled.Flag, contentDescription = "Report message", modifier = Modifier.size(14.dp))
                  }
                }
              }
              if (bitmap != null) {
                Image(
                  bitmap = bitmap.asImageBitmap(),
                  contentDescription = "Shared chat image",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp)),
                )
              }
              if (msg.text.isNotBlank()) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  timeText,
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isMine && msg.readBy.size > 1) {
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Read",
                    modifier = Modifier.size(14.dp),
                  )
                }
              }
            }
          }
        }
      }
    }

    if (pendingImageBase64.isNotBlank()) {
      Text(
        "Image ready to send",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
    }

    Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          }
        ) {
          Icon(Icons.Filled.Image, contentDescription = "Attach image")
        }
        OutlinedTextField(
          value = inputText,
          onValueChange = {
            inputText = it
            onTypingChanged(it.isNotBlank())
          },
          placeholder = { Text("Message as $currentUserName...") },
          modifier = Modifier.weight(1f).testTag("chat_message_input"),
          maxLines = 3,
        )
        Spacer(modifier = Modifier.width(6.dp))
        IconButton(
          onClick = {
            if (inputText.isNotBlank() || pendingImageBase64.isNotBlank()) {
              onSendMessage(inputText, pendingImageBase64)
              inputText = ""
              pendingImageBase64 = ""
              onTypingChanged(false)
            }
          },
          modifier = Modifier.testTag("chat_send_button"),
        ) {
          Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

@Composable
fun AnnouncementsScreen(
  activeRole: String,
  announcements: List<Announcement>,
  onCreateAnnouncement: (String, String, String, String, String) -> Unit,
  onDeleteAnnouncement: (String) -> Unit,
) {
  val isAdmin = activeRole == "admin"
  var showDialog by remember { mutableStateOf(false) }

  if (showDialog) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var eventDate by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showDialog = false },
      title = { Text("Publish Announcement") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
          OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message Body") })
          OutlinedTextField(value = eventDate, onValueChange = { eventDate = it }, label = { Text("Schedule / Exam Date (optional)") }, singleLine = true)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("general", "schedule", "exam", "urgent").forEach { cat ->
              TextButton(onClick = { category = cat }) {
                Text(
                  cat.uppercase(),
                  fontWeight = if (category == cat) FontWeight.Bold else FontWeight.Normal,
                )
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (title.trim().length >= 2 && body.trim().length >= 2) {
              onCreateAnnouncement(title, body, category, "all", eventDate)
              showDialog = false
            }
          }
        ) { Text("Publish") }
      },
      dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
    )
  }

  Scaffold(
    floatingActionButton = {
      if (isAdmin) {
        FloatingActionButton(
          onClick = { showDialog = true },
          modifier = Modifier.testTag("add_announcement_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Create Announcement")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("announcements_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (announcements.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Announcements Posted",
            message = "Institutional news, class schedules, and exam timetables will appear here.",
            icon = Icons.Filled.Campaign,
          )
        }
      } else {
        items(announcements, key = { it.id }) { ann ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                StatusPill(
                  text = ann.category.uppercase(),
                  containerColor =
                    if (ann.category == "urgent") JyCrimsonContainer else MaterialTheme.colorScheme.primaryContainer,
                  contentColor =
                    if (ann.category == "urgent") JyCrimson else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (isAdmin) {
                  IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete announcement", tint = JyCrimson)
                  }
                }
              }
              Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(ann.body, style = MaterialTheme.typography.bodyMedium)
              if (ann.eventDate.isNotBlank()) {
                Text("Event Date: ${ann.eventDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              }
              Text("Posted by ${ann.authorName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }
  }
}

@Composable
fun NotificationsScreen(
  notifications: List<AppNotification>,
  onMarkRead: (String) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("notifications_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (notifications.isEmpty()) {
      item {
        EmptyStateCard(
          title = "You're All Caught Up",
          message = "Payment confirmations, published results, certificate releases, and alerts appear here.",
          icon = Icons.Filled.Notifications,
        )
      }
    } else {
      items(notifications, key = { it.id }) { notif ->
        Card(
          onClick = { if (!notif.isRead) onMarkRead(notif.id) },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors =
            CardDefaults.cardColors(
              containerColor =
                if (notif.isRead) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
        ) {
          Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              if (notif.isRead) Icons.Filled.MarkEmailRead else Icons.Filled.Notifications,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(notif.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(notif.message, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      }
    }
  }
}
