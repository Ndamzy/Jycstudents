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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.model.InstitutionSettings
import com.example.ui.components.OfficialJyLogoBadge
import com.example.ui.components.StatusPill
import com.example.ui.screens.AcademicResultsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminRolesAndLogsScreen
import com.example.ui.screens.AdminSettingsScreen
import com.example.ui.screens.AdminStudentsScreen
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
import kotlinx.coroutines.launch

object AppRoutes {
  const val SPLASH = "splash"
  const val WELCOME = "welcome"
  const val LOGIN = "login"
  const val RECOVERY = "recovery"
  const val REGISTRATION = "registration"
  const val STUDENT_DASHBOARD = "student_dashboard"
  const val ADMIN_DASHBOARD = "admin_dashboard"
  const val ADMIN_STUDENTS = "admin_students"
  const val ADMIN_ROLES = "admin_roles"
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
  val scope = rememberCoroutineScope()

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
  val activityLogsAdmin by mainVm.activityLogsAdmin.collectAsStateWithLifecycle()
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
          OfficialJyLogoBadge(size = 96.dp, cornerRadius = 20.dp)
          Spacer(modifier = Modifier.height(16.dp))
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            "Loading JY-COMPUTERS Portal...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "EMPOWERING PEOPLE THROUGH TECHNOLOGY",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
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
            OfficialJyLogoBadge(size = 64.dp, cornerRadius = 14.dp)
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
          settings = settings,
          programmes = programmes,
          isBusy = actionBusy,
          isAdminAddingStudent = false,
          onNotify = { msg -> mainVm.postFeedback(msg) },
          onSubmit = {
            fullName,
            gender,
            dob,
            phone,
            email,
            addr,
            stateOrigin,
            lga,
            nok,
            nokPhone,
            photo,
            prog,
            level ->
            mainVm.registerStudent(
              fullName = fullName,
              gender = gender,
              dateOfBirth = dob,
              phoneNumber = phone,
              email = email,
              residentialAddress = addr,
              stateOfOrigin = stateOrigin,
              localGovernmentArea = lga,
              nextOfKin = nok,
              nextOfKinPhone = nokPhone,
              passportPhotoBase64 = photo,
              programme = prog,
              trainingLevel = level,
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
      val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

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
              NavDestination(AppRoutes.ADMIN_DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.ADMIN_STUDENTS, "Students", Icons.Filled.Group),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.PAYMENTS, "Payments", Icons.Filled.Payments),
              NavDestination(AppRoutes.REPORTS, "Reports", Icons.Filled.Analytics),
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

      val primarySidebarItems =
        when (activeRole) {
          "admin" ->
            listOf(
              NavDestination(AppRoutes.ADMIN_DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.ADMIN_STUDENTS, "Students", Icons.Filled.Group),
              NavDestination(AppRoutes.REGISTRATION, "Student Registration", Icons.Filled.PersonAdd),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.COURSE_REGISTRATION, "Enrolment", Icons.Filled.School),
              NavDestination(AppRoutes.PAYMENTS, "Payments", Icons.Filled.Payments),
              NavDestination(AppRoutes.REPORTS, "Reports", Icons.Filled.Analytics),
              NavDestination(AppRoutes.NOTIFICATIONS, "Notifications", Icons.Filled.Notifications),
              NavDestination(AppRoutes.ADMIN_ROLES, "Administrators", Icons.Filled.AdminPanelSettings),
              NavDestination(AppRoutes.ADMIN_SETTINGS, "Settings", Icons.Filled.Settings),
            )
          "instructor" ->
            listOf(
              NavDestination(AppRoutes.INSTRUCTOR_DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Assigned Courses", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.ATTENDANCE, "Attendance", Icons.Filled.EventAvailable),
              NavDestination(AppRoutes.RESULTS, "Academic Results", Icons.Filled.Grade),
              NavDestination(AppRoutes.STUDENT_CHAT, "Student Chat", Icons.AutoMirrored.Filled.Chat),
              NavDestination(AppRoutes.GROUP_CHAT, "Group Chat", Icons.Filled.Group),
              NavDestination(AppRoutes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign),
              NavDestination(AppRoutes.NOTIFICATIONS, "Notifications", Icons.Filled.Notifications),
              NavDestination(AppRoutes.PROFILE, "My Profile", Icons.Filled.Person),
            )
          else ->
            listOf(
              NavDestination(AppRoutes.STUDENT_DASHBOARD, "Dashboard", Icons.Filled.Dashboard),
              NavDestination(AppRoutes.COURSE_CATALOGUE, "Courses & Materials", Icons.Filled.MenuBook),
              NavDestination(AppRoutes.COURSE_REGISTRATION, "Course Enrolment", Icons.Filled.School),
              NavDestination(AppRoutes.PAYMENTS, "Tuition Payments", Icons.Filled.Payments),
              NavDestination(AppRoutes.PAYMENT_HISTORY, "Payment History", Icons.Filled.History),
              NavDestination(AppRoutes.ATTENDANCE, "Attendance", Icons.Filled.EventAvailable),
              NavDestination(AppRoutes.RESULTS, "Academic Results", Icons.Filled.Grade),
              NavDestination(AppRoutes.CERTIFICATES, "Certificates", Icons.Filled.WorkspacePremium),
              NavDestination(AppRoutes.STUDENT_CHAT, "Live Student Chat", Icons.AutoMirrored.Filled.Chat),
              NavDestination(AppRoutes.GROUP_CHAT, "Group Chat", Icons.Filled.Group),
              NavDestination(AppRoutes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign),
              NavDestination(AppRoutes.NOTIFICATIONS, "Notifications", Icons.Filled.Notifications),
              NavDestination(AppRoutes.PROFILE, "My Profile", Icons.Filled.Person),
            )
        }

      val secondaryAdminAcademicItems =
        listOf(
          NavDestination(AppRoutes.ATTENDANCE, "Attendance", Icons.Filled.EventAvailable),
          NavDestination(AppRoutes.RESULTS, "Academic Results", Icons.Filled.Grade),
          NavDestination(AppRoutes.CERTIFICATES, "Certificates", Icons.Filled.WorkspacePremium),
          NavDestination(AppRoutes.PAYMENT_HISTORY, "Payment Ledger", Icons.Filled.History),
          NavDestination(AppRoutes.ANNOUNCEMENTS, "Announcements", Icons.Filled.Campaign),
          NavDestination(AppRoutes.GROUP_CHAT, "Group Chat & Moderation", Icons.Filled.Group),
          NavDestination(AppRoutes.STUDENT_CHAT, "Direct Messages", Icons.AutoMirrored.Filled.Chat),
          NavDestination(AppRoutes.PROFILE, "My Profile", Icons.Filled.Person),
        )

      val unreadNotifs = notifications.count { !it.isRead }
      val displayName = profile?.fullName?.ifBlank { mainVm.currentUserDisplayName } ?: mainVm.currentUserDisplayName

      if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
          val moduleShortcuts = buildList {
            add(Triple(homeRoute, "Dashboard", Icons.Filled.Dashboard))
            if (isAdminVerified) {
              add(Triple(AppRoutes.ADMIN_STUDENTS, "Students", Icons.Filled.Group))
              add(Triple(AppRoutes.REGISTRATION, "Student Registration", Icons.Filled.PersonAdd))
            }
            add(Triple(AppRoutes.COURSE_CATALOGUE, "Courses & Materials", Icons.Filled.MenuBook))
            add(Triple(AppRoutes.COURSE_REGISTRATION, "Enrolment", Icons.Filled.School))
            add(Triple(AppRoutes.PAYMENTS, "Payments", Icons.Filled.AccountBalanceWallet))
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
              add(Triple(AppRoutes.REPORTS, "Reports & Export", Icons.Filled.Analytics))
              add(Triple(AppRoutes.ADMIN_ROLES, "Administrators", Icons.Filled.AdminPanelSettings))
              add(Triple(AppRoutes.ADMIN_SETTINGS, "Settings", Icons.Filled.Settings))
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
        val usePermanentDrawer = maxWidth >= 840.dp
        val useRail = maxWidth >= 600.dp && !usePermanentDrawer

        val drawerSheetContent: @Composable () -> Unit = {
          SidebarDrawerContent(
            settings = settings,
            displayName = displayName,
            activeRole = activeRole,
            unreadNotifs = unreadNotifs,
            currentRoute = currentRoute,
            primaryItems = primarySidebarItems,
            secondaryItems = if (activeRole == "admin") secondaryAdminAcademicItems else emptyList(),
            onSelectRoute = { route ->
              chatVm.closeRoom()
              currentRoute = route
              if (!usePermanentDrawer) {
                scope.launch { drawerState.close() }
              }
            },
            onSignOut = {
              if (!usePermanentDrawer) {
                scope.launch { drawerState.close() }
              }
              onSignOut()
            },
          )
        }

        val mainScaffoldContent: @Composable () -> Unit = {
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
                  navigationIcon = {
                    if (!usePermanentDrawer) {
                      IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier.testTag("open_sidebar_drawer_button"),
                      ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open navigation menu")
                      }
                    }
                  },
                  title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      OfficialJyLogoBadge(size = 36.dp, cornerRadius = 8.dp)
                      Spacer(modifier = Modifier.width(10.dp))
                      Column {
                        Text(
                          text = screenTitleForRoute(currentRoute),
                          style = MaterialTheme.typography.titleMedium,
                          fontWeight = FontWeight.Bold,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = "${settings.institutionName} • ${activeRole.uppercase()}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
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
                if (!useRail && !usePermanentDrawer) {
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
                        label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                      onNotify = { msg -> mainVm.postFeedback(msg) },
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
                      activityLogs = activityLogsAdmin,
                      onApproveStudent = { uid, name, approval ->
                        mainVm.approveOrRejectStudentAdmin(uid, name, approval)
                      },
                      onOpenAddStudent = { currentRoute = AppRoutes.REGISTRATION },
                      onNavigate = { route -> currentRoute = route },
                    )

                  AppRoutes.ADMIN_STUDENTS ->
                    AdminStudentsScreen(
                      settings = settings,
                      allUsers = allUsersAdmin,
                      programmes = programmes,
                      enrollments = enrollments,
                      isBusy = actionBusy,
                      onOpenAddStudent = { currentRoute = AppRoutes.REGISTRATION },
                      onApproveOrReject = { uid, name, approval ->
                        mainVm.approveOrRejectStudentAdmin(uid, name, approval)
                      },
                      onUpdateStatus = { uid, status, name ->
                        mainVm.updateStudentStatusAdmin(uid, status, name)
                      },
                      onEditStudent = { updated -> mainVm.editStudentAdmin(updated) },
                      onUpdateProgress = { uid, name, pct, statusOverride ->
                        mainVm.updateStudentProgressAdmin(uid, name, pct, statusOverride)
                      },
                      onAssignCourse = { student, prog ->
                        mainVm.assignStudentToCourseAdmin(student, prog)
                      },
                      onDeleteStudent = { uid, name -> mainVm.deleteStudentAdmin(uid, name) },
                      onNotify = { msg -> mainVm.postFeedback(msg) },
                    )

                  AppRoutes.ADMIN_ROLES ->
                    AdminRolesAndLogsScreen(
                      allUsers = allUsersAdmin,
                      programmes = programmes,
                      activityLogs = activityLogsAdmin,
                      onAssignRole = { uid, fullName, email, role, spec, courseIds ->
                        mainVm.assignUserRoleAdmin(uid, fullName, email, role, spec, courseIds)
                      },
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
                      enrollments = enrollments,
                      materials = learningMaterials,
                      activeRole = activeRole,
                      onSaveProgramme = { prog -> mainVm.saveProgramme(prog) },
                      onToggleProgrammeActive = { prog -> mainVm.toggleProgrammeActiveAdmin(prog) },
                      onDeleteProgramme = { id -> mainVm.deleteProgramme(id) },
                      onAddLearningMaterial = { prog, title, desc, url ->
                        mainVm.addLearningMaterial(prog, title, desc, url)
                      },
                      onGoToRegister = { currentRoute = AppRoutes.COURSE_REGISTRATION },
                    )

                  AppRoutes.COURSE_REGISTRATION ->
                    CourseRegistrationScreen(
                      settings = settings,
                      activeRole = activeRole,
                      allUsers = allUsersAdmin,
                      programmes = programmes,
                      enrollments = enrollments,
                      isBusy = actionBusy,
                      onEnroll = { prog -> mainVm.enrollInProgramme(prog) },
                      onAssignStudentToCourse = { student, prog ->
                        mainVm.assignStudentToCourseAdmin(student, prog)
                      },
                      onUpdateEnrollmentStatus = { id, status ->
                        mainVm.updateEnrollmentStatusAdmin(id, status)
                      },
                      onNotify = { msg -> mainVm.postFeedback(msg) },
                      onGoToPayment = { currentRoute = AppRoutes.PAYMENTS },
                    )

                  AppRoutes.PAYMENTS ->
                    PaymentScreen(
                      profile = profile,
                      settings = settings,
                      enrollments = enrollments,
                      payments = payments,
                      activeRole = activeRole,
                      isBusy = actionBusy,
                      onSubmitOnlinePayment = { enr, amt -> mainVm.submitOnlinePayment(enr, amt) },
                      onSubmitOpayTransfer = { enr, amt, ref, payDate, sender, img ->
                        mainVm.submitManualOpayPayment(enr, amt, ref, payDate, sender, img)
                      },
                      onAdminRecordPayment = { enr, amt, method, ref, payDate, remarks ->
                        mainVm.recordAdminPayment(enr, amt, method, ref, payDate, remarks)
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
                      settings = settings,
                      currentEmail = mainVm.currentUserEmail,
                      themeMode = themeMode,
                      isBusy = actionBusy,
                      onUpdateProfile = { updated -> mainVm.updateProfile(updated) },
                      onThemeModeChange = onThemeModeChange,
                      onNotify = { msg -> mainVm.postFeedback(msg) },
                      onSignOut = onSignOut,
                    )

                  AppRoutes.ADMIN_SETTINGS ->
                    AdminSettingsScreen(
                      settings = settings,
                      isBusy = actionBusy,
                      onSaveSettings = { updated -> mainVm.saveInstitutionSettings(updated) },
                      onSeedDefaults = { mainVm.seedDefaultProgrammesNow() },
                      onNotify = { msg -> mainVm.postFeedback(msg) },
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
                      settings = settings,
                      programmes = programmes,
                      isBusy = actionBusy,
                      isAdminAddingStudent = true,
                      onNotify = { msg -> mainVm.postFeedback(msg) },
                      onSubmit = {
                        fullName,
                        gender,
                        dob,
                        phone,
                        email,
                        addr,
                        stateOrigin,
                        lga,
                        nok,
                        nokPhone,
                        photo,
                        prog,
                        level ->
                        val syntheticUid = "stu_${UUID.randomUUID().toString().replace("-", "").take(12)}"
                        mainVm.registerStudent(
                          fullName = fullName,
                          gender = gender,
                          dateOfBirth = dob,
                          phoneNumber = phone,
                          email = email,
                          residentialAddress = addr,
                          stateOfOrigin = stateOrigin,
                          localGovernmentArea = lga,
                          nextOfKin = nok,
                          nextOfKinPhone = nokPhone,
                          passportPhotoBase64 = photo,
                          programme = prog,
                          trainingLevel = level,
                          targetUid = syntheticUid,
                          onSuccess = {
                            currentRoute = if (isAdminVerified) AppRoutes.ADMIN_STUDENTS else homeRoute
                          },
                        )
                      },
                      onSignOutOrCancel = { currentRoute = homeRoute },
                    )
                }
              }
            }
          }
        }

        if (usePermanentDrawer) {
          PermanentNavigationDrawer(
            drawerContent = {
              PermanentDrawerSheet(modifier = Modifier.width(296.dp)) {
                drawerSheetContent()
              }
            },
            content = mainScaffoldContent,
          )
        } else {
          ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
              ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                drawerSheetContent()
              }
            },
            content = mainScaffoldContent,
          )
        }
      }
    }
  }
}

@Composable
private fun SidebarDrawerContent(
  settings: InstitutionSettings,
  displayName: String,
  activeRole: String,
  unreadNotifs: Int,
  currentRoute: String,
  primaryItems: List<NavDestination>,
  secondaryItems: List<NavDestination>,
  onSelectRoute: (String) -> Unit,
  onSignOut: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OfficialJyLogoBadge(size = 56.dp, cornerRadius = 12.dp)
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          settings.institutionName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          displayName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        StatusPill(
          text = activeRole.uppercase(),
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    Text(
      text = if (activeRole == "admin") "Administration Navigation" else "Portal Navigation",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
    )

    primaryItems.forEach { dest ->
      NavigationDrawerItem(
        label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(dest.icon, contentDescription = null) },
        badge = {
          if (dest.route == AppRoutes.NOTIFICATIONS && unreadNotifs > 0) {
            Badge { Text(unreadNotifs.toString()) }
          }
        },
        selected = currentRoute == dest.route,
        onClick = { onSelectRoute(dest.route) },
        modifier =
          Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            .testTag("drawer_nav_${dest.route}"),
      )
    }

    if (secondaryItems.isNotEmpty()) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      Text(
        text = "Academics & Communication",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
      )
      secondaryItems.forEach { dest ->
        NavigationDrawerItem(
          label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          icon = { Icon(dest.icon, contentDescription = null) },
          selected = currentRoute == dest.route,
          onClick = { onSelectRoute(dest.route) },
          modifier =
            Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
              .testTag("drawer_nav_${dest.route}"),
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    NavigationDrawerItem(
      label = { Text("Logout") },
      icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
      selected = false,
      onClick = onSignOut,
      modifier =
        Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).testTag("drawer_nav_logout"),
    )
    Spacer(modifier = Modifier.height(16.dp))
  }
}

private fun screenTitleForRoute(route: String): String =
  when (route) {
    AppRoutes.STUDENT_DASHBOARD -> "Student Dashboard"
    AppRoutes.ADMIN_DASHBOARD -> "Admin Dashboard"
    AppRoutes.ADMIN_STUDENTS -> "Student Management"
    AppRoutes.ADMIN_ROLES -> "Administrators & Logs"
    AppRoutes.INSTRUCTOR_DASHBOARD -> "Instructor Dashboard"
    AppRoutes.COURSE_CATALOGUE -> "Courses & Materials"
    AppRoutes.COURSE_REGISTRATION -> "Course Enrolment"
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
    AppRoutes.REGISTRATION -> "Student Registration"
    else -> "JY-COMPUTERS"
  }
