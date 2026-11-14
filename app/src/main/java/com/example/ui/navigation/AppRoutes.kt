package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.ui.screens.AcademicResultsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminSettingsScreen
import com.example.ui.screens.AnnouncementsScreen
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.CertificatesScreen
import com.example.ui.screens.CourseCatalogueScreen
import com.example.ui.screens.CourseRegistrationScreen
import com.example.ui.screens.GroupChatScreen
import com.example.ui.screens.InstructorDashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PaymentHistoryScreen
import com.example.ui.screens.PaymentScreen
import com.example.ui.screens.ReportsAndAnalyticsScreen
import com.example.ui.screens.StudentDashboardScreen
import com.example.ui.screens.StudentLiveChatScreen
import com.example.ui.screens.StudentProfileScreen
import com.example.ui.screens.StudentRegistrationScreen
import com.example.ui.viewmodel.JyChatViewModel
import com.example.ui.viewmodel.JyMainViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.AppThemeMode
import com.example.util.LocalNotificationHelper
import java.util.UUID

object AppRoutes {
  const val SPLASH = "splash"
  const val WELCOME = "welcome"
  const val LOGIN = "login"
  const val RECOVERY = "recovery"
  const val REGISTRATION = "registration"
  const val STUDENT_DASHBOARD = "student_dashboard"
  const val ADMIN_DASHBOARD = "admin_dashboard"
  const val INSTRUCTOR_DASHBOARD = "instructor_dashboard"
  const val COURSE_CATALOGUE = "course_catalogue"
  const val COURSE_REGISTRATION = "course_registration"
  const val PAYMENTS = "payments"
  const val PAYMENT_HISTORY = "payment_history"
  const val ATTENDANCE = "attendance"
  const val RESULTS = "results"
  const val CERTIFICATES = "certificates"
  const val STUDENT_CHAT = "student_chat"
  const val GROUP_CHAT = "group_chat"
  const val ANNOUNCEMENTS = "announcements"
  const val NOTIFICATIONS = "notifications"
  const val PROFILE = "profile"
  const val ADMIN_SETTINGS = "admin_settings"
  const val REPORTS = "reports"
}

private data class NavDestination(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedAppShell(
  mainVm: JyMainViewModel,
  chatVm: JyChatViewModel,
  themeMode: AppThemeMode,
  onThemeModeChange: (AppThemeMode) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current

  val isAdminVerified by mainVm.isAdminVerified.collectAsStateWithLifecycle()
  val activeRole by mainVm.activeRole.collectAsStateWithLifecycle()
  val userProfileState by mainVm.userProfileState.collectAsStateWithLifecycle()
  val settings by mainVm.institutionSettings.collectAsStateWithLifecycle()
  val programmes by mainVm.programmes.collectAsStateWithLifecycle()
  val enrollments by mainVm.enrollments.collectAsStateWithLifecycle()
  val payments by mainVm.payments.collectAsStateWithLifecycle()
  val attendance by mainVm.attendance.collectAsStateWithLifecycle()
  val results by mainVm.results.collectAsStateWithLifecycle()
  val certificates by mainVm.certificates.collectAsStateWithLifecycle()
  val allUsersAdmin by mainVm.allUsersAdmin.collectAsStateWithLifecycle()
  val learningMaterials by mainVm.learningMaterials.collectAsStateWithLifecycle()
  val actionBusy by mainVm.actionBusy.collectAsStateWithLifecycle()
  val mainFeedback by mainVm.feedbackMessage.collectAsStateWithLifecycle()

  val announcements by chatVm.announcements.collectAsStateWithLifecycle()
  val notifications by chatVm.notifications.collectAsStateWithLifecycle()
  val directory by chatVm.directory.collectAsStateWithLifecycle()
  val blockedUsers by chatVm.blockedUsers.collectAsStateWithLifecycle()
  val allRooms by chatVm.allRooms.collectAsStateWithLifecycle()
  val activeRoom by chatVm.activeRoom.collectAsStateWithLifecycle()
  val activeMessages by chatVm.activeMessages.collectAsStateWithLifecycle()
  val chatReports by chatVm.chatReportsAdmin.collectAsStateWithLifecycle()
  val chatFeedback by chatVm.chatFeedback.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val notifiedIds = remember { mutableSetOf<String>() }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

  LaunchedEffect(Unit) {
    LocalNotificationHelper.ensureChannel(context)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(notifications) {
    val firstUnread = notifications.firstOrNull { !it.isRead && it.id !in notifiedIds }
    if (firstUnread != null) {
      notifiedIds.add(firstUnread.id)
      LocalNotificationHelper.showNotification(
        context = context,
        id = firstUnread.id.hashCode(),
        title = firstUnread.title,
        body = firstUnread.message,
      )
    }
  }

  LaunchedEffect(mainFeedback) {
    val msg = mainFeedback
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      mainVm.clearFeedback()
    }
  }

  LaunchedEffect(chatFeedback) {
    val msg = chatFeedback
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      chatVm.clearFeedback()
    }
  }

  when (val state = userProfileState) {
    is UiState.Loading -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(12.dp))
          Text("Loading JY-Computers Portal...", style = MaterialTheme.typography.bodyMedium)
        }
      }
      return
    }
    is UiState.Error -> {
      Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(20.dp)) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Unable to Load Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onSignOut) { Text("Sign Out") }
          }
        }
      }
      return
    }
    is UiState.Success -> {
      val profile = state.data
      if (profile == null && !isAdminVerified) {
        StudentRegistrationScreen(
          defaultEmail = mainVm.currentUserEmail,
          defaultName = mainVm.currentUserDisplayName,
          programmes = programmes,
          isBusy = actionBusy,
          isAdminAddingStudent = false,
          onSubmit = { fullName, gender, dob, phone, email, addr, stateOrigin, nok, nokPhone, photo, prog ->
            mainVm.registerStudent(
              fullName = fullName,
              gender = gender,
              dateOfBirth = dob,
              phoneNumber = phone,
              email = email,
              residentialAddress = addr,
              stateOfOrigin = stateOrigin,
              nextOfKin = nok,
              nextOfKinPhone = nokPhone,
              passportPhotoBase64 = photo,
              programme = prog,
            )
          },
          onSignOutOrCancel = onSignOut,
        )
        return
      }

      val homeRoute =
        when (activeRole) {
          "admin" -> AppRoutes.ADMIN_DASHBOARD
          "instructor" -> AppRoutes.INSTRUCTOR_DASHBOARD
          else -> AppRoutes.STUDENT_DASHBOARD
        }

      var currentRoute by rememberSaveable(homeRoute) { mutableStateOf(homeRoute) }
      var showMoreSheet by remember { mutableStateOf(false) }
      var showWorkspaceMenu by remember { mutableStateOf(false) }

      if (currentRoute != homeRoute) {
        BackHandler {
          chatVm.closeRoom()
          currentRoute = homeRoute
        }
      }

      val primaryNavItems =
        when (activeRole) {
          "admin" ->
            listOf(
              NavDestination(AppRoutes.ADMIN_DASHBOARD, "Home", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.PAYMENTS, "Payments", Icons.Filled.Payments),
              NavDestination(AppRoutes.REPORTS, "Reports", Icons.Filled.Analytics),
              NavDestination(AppRoutes.PROFILE, "Profile", Icons.Filled.Person),
            )
          "instructor" ->
            listOf(
              NavDestination(AppRoutes.INSTRUCTOR_DASHBOARD, "Home", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.ATTENDANCE, "Attendance", Icons.Filled.EventAvailable),
              NavDestination(AppRoutes.RESULTS, "Results", Icons.Filled.Grade),
              NavDestination(AppRoutes.STUDENT_CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
            )
          else ->
            listOf(
              NavDestination(AppRoutes.STUDENT_DASHBOARD, "Home", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.PAYMENTS, "Pay", Icons.Filled.Payments),
              NavDestination(AppRoutes.STUDENT_CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
              NavDestination(AppRoutes.PROFILE, "Profile", Icons.Filled.Person),
            )
        }

      val unreadNotifs = notifications.count { !it.isRead }
      val displayName = profile?.fullName?.ifBlank { mainVm.currentUserDisplayName } ?: mainVm.currentUserDisplayName

      if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
          val moduleShortcuts = buildList {
            add(Triple(homeRoute, "Dashboard", Icons.Filled.Dashboard))
            add(Triple(AppRoutes.COURSE_CATALOGUE, "Courses & Materials", Icons.Filled.MenuBook))
            add(Triple(AppRoutes.COURSE_REGISTRATION, "Course Registration", Icons.Filled.School))
            add(Triple(AppRoutes.PAYMENTS, "Pay Tuition", Icons.Filled.AccountBalanceWallet))
            add(Triple(AppRoutes.PAYMENT_HISTORY, "Payment History", Icons.Filled.History))
            add(Triple(AppRoutes.ATTENDANCE, "Attendance", Icons.Filled.EventAvailable))
            add(Triple(AppRoutes.RESULTS, "Academic Results", Icons.Filled.Grade))
            add(Triple(AppRoutes.CERTIFICATES, "Certificates", Icons.Filled.WorkspacePremium))
            add(Triple(AppRoutes.STUDENT_CHAT, "1-on-1 Live Chat", Icons.AutoMirrored.Filled.Chat))
            add(Triple(AppRoutes.GROUP_CHAT, "Group Chat", Icons.Filled.Group))
            add(Triple(AppRoutes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign))
            add(Triple(AppRoutes.NOTIFICATIONS, "Notifications", Icons.Filled.Notifications))
            add(Triple(AppRoutes.PROFILE, "My Profile", Icons.Filled.Person))
            if (isAdminVerified) {
              add(Triple(AppRoutes.REGISTRATION, "Add Student", Icons.Filled.PersonAdd))
              add(Triple(AppRoutes.REPORTS, "Reports & Export", Icons.Filled.Analytics))
              add(Triple(AppRoutes.ADMIN_SETTINGS, "Admin Settings", Icons.Filled.Settings))
            }
          }
          Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
              "All Campus Modules",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyVerticalGrid(
              columns = GridCells.Adaptive(minSize = 148.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth().height(400.dp),
            ) {
              items(moduleShortcuts) { (route, label, icon) ->
                Card(
                  onClick = {
                    chatVm.closeRoom()
                    currentRoute = route
                    showMoreSheet = false
                  },
                  colors =
                    CardDefaults.cardColors(
                      containerColor =
                        if (currentRoute == route) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      label,
                      style = MaterialTheme.typography.labelLarge,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
          if (useRail) {
            NavigationRail(
              containerColor = MaterialTheme.colorScheme.surface,
            ) {
              Spacer(modifier = Modifier.height(12.dp))
              primaryNavItems.forEach { dest ->
                NavigationRailItem(
                  selected = currentRoute == dest.route,
                  onClick = {
                    chatVm.closeRoom()
                    currentRoute = dest.route
                  },
                  icon = { Icon(dest.icon, contentDescription = dest.label) },
                  label = { Text(dest.label) },
                )
              }
              NavigationRailItem(
                selected = false,
                onClick = { showMoreSheet = true },
                icon = { Icon(Icons.Filled.Apps, contentDescription = "More modules") },
                label = { Text("Modules") },
              )
            }
          }

          Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
              TopAppBar(
                colors =
                  TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                  ),
                title = {
                  Column {
                    Text(
                      text = screenTitleForRoute(currentRoute),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = "${settings.institutionName} • ${activeRole.uppercase()}",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                },
                actions = {
                  if (isAdminVerified) {
                    Box {
                      IconButton(
                        onClick = { showWorkspaceMenu = true },
                        modifier = Modifier.testTag("workspace_switcher_button"),
                      ) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Switch workspace role")
                      }
                      DropdownMenu(
                        expanded = showWorkspaceMenu,
                        onDismissRequest = { showWorkspaceMenu = false },
                      ) {
                        listOf("admin" to "Admin View", "instructor" to "Instructor View", "student" to "Student View")
                          .forEach { (roleKey, title) ->
                            DropdownMenuItem(
                              text = { Text(title) },
                              onClick = {
                                showWorkspaceMenu = false
                                mainVm.setWorkspaceRole(roleKey)
                                currentRoute =
                                  when (roleKey) {
                                    "admin" -> AppRoutes.ADMIN_DASHBOARD
                                    "instructor" -> AppRoutes.INSTRUCTOR_DASHBOARD
                                    else -> AppRoutes.STUDENT_DASHBOARD
                                  }
                              },
                            )
                          }
                      }
                    }
                  }
                  IconButton(
                    onClick = {
                      chatVm.closeRoom()
                      currentRoute = AppRoutes.NOTIFICATIONS
                    },
                    modifier = Modifier.testTag("topbar_notifications_button"),
                  ) {
                    BadgedBox(
                      badge = {
                        if (unreadNotifs > 0) {
                          Badge { Text(unreadNotifs.toString()) }
                        }
                      }
                    ) {
                      Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                  }
                  IconButton(
                    onClick = { showMoreSheet = true },
                    modifier = Modifier.testTag("topbar_modules_button"),
                  ) {
                    Icon(Icons.Filled.Apps, contentDescription = "All Campus Modules")
                  }
                },
              )
            },
            bottomBar = {
              if (!useRail) {
                NavigationBar(
                  containerColor = MaterialTheme.colorScheme.surface,
                  windowInsets = WindowInsets.navigationBars,
                ) {
                  primaryNavItems.forEach { dest ->
                    NavigationBarItem(
                      selected = currentRoute == dest.route,
                      onClick = {
                        chatVm.closeRoom()
                        currentRoute = dest.route
                      },
                      icon = { Icon(dest.icon, contentDescription = dest.label) },
                      label = { Text(dest.label, maxLines = 1) },
                    )
                  }
                }
              }
            },
          ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
              when (currentRoute) {
                AppRoutes.STUDENT_DASHBOARD ->
                  StudentDashboardScreen(
                    profile = profile,
                    settings = settings,
                    enrollments = enrollments,
                    payments = payments,
                    attendance = attendance,
                    results = results,
                    certificates = certificates,
                    announcements = announcements,
                    programmes = programmes,
                    onNavigate = { route -> currentRoute = route },
                  )

                AppRoutes.ADMIN_DASHBOARD ->
                  AdminDashboardScreen(
                    settings = settings,
                    allUsers = allUsersAdmin,
                    programmes = programmes,
                    enrollments = enrollments,
                    payments = payments,
                    announcements = announcements,
                    onUpdateStudentStatus = { uid, status -> mainVm.updateStudentStatusAdmin(uid, status) },
                    onDeleteStudent = { uid -> mainVm.deleteStudentAdmin(uid) },
                    onOpenAddStudent = { currentRoute = AppRoutes.REGISTRATION },
                    onNavigate = { route -> currentRoute = route },
                  )

                AppRoutes.INSTRUCTOR_DASHBOARD ->
                  InstructorDashboardScreen(
                    profile = profile,
                    currentUserId = mainVm.currentUserId,
                    programmes = programmes,
                    enrollments = enrollments,
                    announcements = announcements,
                    onNavigate = { route -> currentRoute = route },
                  )

                AppRoutes.COURSE_CATALOGUE ->
                  CourseCatalogueScreen(
                    programmes = programmes,
                    materials = learningMaterials,
                    activeRole = activeRole,
                    onSaveProgramme = { prog -> mainVm.saveProgramme(prog) },
                    onDeleteProgramme = { id -> mainVm.deleteProgramme(id) },
                    onAddLearningMaterial = { prog, title, desc, url ->
                      mainVm.addLearningMaterial(prog, title, desc, url)
                    },
                    onGoToRegister = { currentRoute = AppRoutes.COURSE_REGISTRATION },
                  )

                AppRoutes.COURSE_REGISTRATION ->
                  CourseRegistrationScreen(
                    programmes = programmes,
                    enrollments = enrollments,
                    isBusy = actionBusy,
                    onEnroll = { prog -> mainVm.enrollInProgramme(prog) },
                    onGoToPayment = { currentRoute = AppRoutes.PAYMENTS },
                  )

                AppRoutes.PAYMENTS ->
                  PaymentScreen(
                    settings = settings,
                    enrollments = enrollments,
                    payments = payments,
                    activeRole = activeRole,
                    isBusy = actionBusy,
                    onSubmitOnlinePayment = { enr, amt -> mainVm.submitOnlinePayment(enr, amt) },
                    onSubmitOpayTransfer = { enr, amt, ref, sender, img ->
                      mainVm.submitManualOpayPayment(enr, amt, ref, sender, img)
                    },
                    onAdminReviewPayment = { pay, approve, remarks ->
                      mainVm.reviewPaymentAdmin(pay, approve, remarks)
                    },
                    onNotify = { msg -> mainVm.postFeedback(msg) },
                  )

                AppRoutes.PAYMENT_HISTORY ->
                  PaymentHistoryScreen(
                    payments = payments,
                    settings = settings,
                  )

                AppRoutes.ATTENDANCE ->
                  AttendanceScreen(
                    activeRole = activeRole,
                    programmes = programmes,
                    enrollments = enrollments,
                    attendance = attendance,
                    onMarkAttendance = { uid, name, sid, prog, dateKey, status ->
                      mainVm.markAttendance(uid, name, sid, prog, dateKey, status)
                    },
                  )

                AppRoutes.RESULTS ->
                  AcademicResultsScreen(
                    activeRole = activeRole,
                    programmes = programmes,
                    enrollments = enrollments,
                    results = results,
                    isBusy = actionBusy,
                    onSaveResult = { id, uid, name, sid, prog, assign, exam, remarks, pub ->
                      mainVm.saveResult(id, uid, name, sid, prog, assign, exam, remarks, pub)
                    },
                  )

                AppRoutes.CERTIFICATES ->
                  CertificatesScreen(
                    activeRole = activeRole,
                    settings = settings,
                    certificates = certificates,
                    allUsers = allUsersAdmin,
                    programmes = programmes,
                    isBusy = actionBusy,
                    onIssueCertificate = { uid, name, sid, pid, pTitle, gradDate ->
                      mainVm.issueCertificate(uid, name, sid, pid, pTitle, gradDate)
                    },
                  )

                AppRoutes.STUDENT_CHAT ->
                  StudentLiveChatScreen(
                    currentUserId = mainVm.currentUserId,
                    currentUserName = displayName,
                    directory = directory,
                    blockedUsers = blockedUsers,
                    activeRoom = activeRoom,
                    messages = activeMessages,
                    onStartDirectChat = { peer ->
                      chatVm.startDirectChat(peer, displayName) {}
                    },
                    onCloseRoom = { chatVm.closeRoom() },
                    onSendMessage = { room, text, img ->
                      chatVm.sendMessage(room, displayName, text, img)
                    },
                    onTypingChanged = { roomId, typing -> chatVm.setTyping(roomId, typing) },
                    onMarkMessagesRead = { roomId, msgs -> chatVm.markMessagesRead(roomId, msgs) },
                    onReportMessage = { roomId, msg, reason -> chatVm.reportMessage(roomId, msg, reason) },
                    onBlockUser = { peerUid, peerName -> chatVm.blockUser(peerUid, peerName) },
                    onUnblockUser = { peerUid -> chatVm.unblockUser(peerUid) },
                  )

                AppRoutes.GROUP_CHAT ->
                  GroupChatScreen(
                    currentUserId = mainVm.currentUserId,
                    currentUserName = displayName,
                    activeRole = activeRole,
                    rooms = allRooms,
                    activeRoom = activeRoom,
                    messages = activeMessages,
                    blockedUsers = blockedUsers,
                    chatReports = chatReports,
                    onOpenRoom = { room -> chatVm.openRoom(room) },
                    onCloseRoom = { chatVm.closeRoom() },
                    onCreateGroup = { title -> chatVm.createGroupRoom(title) },
                    onSendMessage = { room, text, img ->
                      chatVm.sendMessage(room, displayName, text, img)
                    },
                    onTypingChanged = { roomId, typing -> chatVm.setTyping(roomId, typing) },
                    onMarkMessagesRead = { roomId, msgs -> chatVm.markMessagesRead(roomId, msgs) },
                    onReportMessage = { roomId, msg, reason -> chatVm.reportMessage(roomId, msg, reason) },
                    onBlockUser = { peerUid, peerName -> chatVm.blockUser(peerUid, peerName) },
                    onModerateReport = { report, deleteMsg, status ->
                      chatVm.moderateReportAdmin(report, deleteMsg, status)
                    },
                  )

                AppRoutes.ANNOUNCEMENTS ->
                  AnnouncementsScreen(
                    activeRole = activeRole,
                    announcements = announcements,
                    onCreateAnnouncement = { title, body, category, targetRole, eventDate ->
                      chatVm.createAnnouncement(title, body, category, targetRole, eventDate, displayName)
                    },
                    onDeleteAnnouncement = { id -> chatVm.deleteAnnouncement(id) },
                  )

                AppRoutes.NOTIFICATIONS ->
                  NotificationsScreen(
                    notifications = notifications,
                    onMarkRead = { id -> chatVm.markNotificationRead(id) },
                  )

                AppRoutes.PROFILE ->
                  StudentProfileScreen(
                    profile = profile,
                    currentEmail = mainVm.currentUserEmail,
                    themeMode = themeMode,
                    isBusy = actionBusy,
                    onUpdateProfile = { updated -> mainVm.updateProfile(updated) },
                    onThemeModeChange = onThemeModeChange,
                    onSignOut = onSignOut,
                  )

                AppRoutes.ADMIN_SETTINGS ->
                  AdminSettingsScreen(
                    settings = settings,
                    allUsers = allUsersAdmin,
                    programmes = programmes,
                    isBusy = actionBusy,
                    onSaveSettings = { updated -> mainVm.saveInstitutionSettings(updated) },
                    onSeedDefaults = { mainVm.seedDefaultProgrammesNow() },
                    onAssignRole = { uid, fullName, email, role, spec, courseIds ->
                      mainVm.assignUserRoleAdmin(uid, fullName, email, role, spec, courseIds)
                    },
                  )

                AppRoutes.REPORTS ->
                  ReportsAndAnalyticsScreen(
                    settings = settings,
                    allUsers = allUsersAdmin,
                    enrollments = enrollments,
                    payments = payments,
                    attendance = attendance,
                    results = results,
                    certificates = certificates,
                  )

                AppRoutes.REGISTRATION ->
                  StudentRegistrationScreen(
                    defaultEmail = "",
                    defaultName = "",
                    programmes = programmes,
                    isBusy = actionBusy,
                    isAdminAddingStudent = true,
                    onSubmit = { fullName, gender, dob, phone, email, addr, stateOrigin, nok, nokPhone, photo, prog ->
                      val syntheticUid = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                      mainVm.registerStudent(
                        fullName = fullName,
                        gender = gender,
                        dateOfBirth = dob,
                        phoneNumber = phone,
                        email = email,
                        residentialAddress = addr,
                        stateOfOrigin = stateOrigin,
                        nextOfKin = nok,
                        nextOfKinPhone = nokPhone,
                        passportPhotoBase64 = photo,
                        programme = prog,
                        targetUid = syntheticUid,
                        onSuccess = { currentRoute = homeRoute },
                      )
                    },
                    onSignOutOrCancel = { currentRoute = homeRoute },
                  )
              }
            }
          }
        }
      }
    }
  }
}

private fun screenTitleForRoute(route: String): String =
  when (route) {
    AppRoutes.STUDENT_DASHBOARD -> "Student Dashboard"
    AppRoutes.ADMIN_DASHBOARD -> "Admin Dashboard"
    AppRoutes.INSTRUCTOR_DASHBOARD -> "Instructor Dashboard"
    AppRoutes.COURSE_CATALOGUE -> "Courses & Materials"
    AppRoutes.COURSE_REGISTRATION -> "Course Registration"
    AppRoutes.PAYMENTS -> "Tuition Payments"
    AppRoutes.PAYMENT_HISTORY -> "Payment History"
    AppRoutes.ATTENDANCE -> "Attendance"
    AppRoutes.RESULTS -> "Academic Results"
    AppRoutes.CERTIFICATES -> "Certificates"
    AppRoutes.STUDENT_CHAT -> "Live Student Chat"
    AppRoutes.GROUP_CHAT -> "Group Discussions"
    AppRoutes.ANNOUNCEMENTS -> "Announcements"
    AppRoutes.NOTIFICATIONS -> "Notifications"
    AppRoutes.PROFILE -> "Account Profile"
    AppRoutes.ADMIN_SETTINGS -> "Admin Settings"
    AppRoutes.REPORTS -> "Reports & Export"
    AppRoutes.REGISTRATION -> "Register Student"
    else -> "JY-Computers"
  }
