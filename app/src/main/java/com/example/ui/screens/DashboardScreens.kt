package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicResult
import com.example.data.model.Announcement
import com.example.data.model.AttendanceRecord
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Payment
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.ui.components.Base64Avatar
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyGoldContainer
import com.example.ui.theme.JyRoyalBlue
import com.example.ui.theme.JyTeal

@Composable
fun StudentDashboardScreen(
  profile: UserProfile?,
  settings: InstitutionSettings,
  enrollments: List<Enrollment>,
  payments: List<Payment>,
  attendance: List<AttendanceRecord>,
  results: List<AcademicResult>,
  certificates: List<Certificate>,
  announcements: List<Announcement>,
  programmes: List<Programme>,
  onNavigate: (String) -> Unit,
) {
  val totalPaid = enrollments.sumOf { it.amountPaidNaira }
  val totalOutstanding = enrollments.sumOf { it.outstandingNaira }
  val presentCount = attendance.count { it.status == "present" || it.status == "late" }
  val attendancePct =
    if (attendance.isEmpty()) 100 else ((presentCount * 100f) / attendance.size).toInt()

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("student_dashboard_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Hello, ${profile?.fullName?.ifBlank { "Student" } ?: "Student"}",
        subtitle =
          "${settings.institutionName} • ${profile?.studentIdNumber?.ifBlank { "Pending ID" } ?: "Welcome"}",
        badgeText = settings.activeSession,
      ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Base64Avatar(
            base64 = profile?.passportPhotoBase64.orEmpty(),
            name = profile?.fullName ?: "Student",
            size = 48.dp,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = profile?.selectedProgrammeTitle?.ifBlank { "No programme selected" } ?: "Select a course",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "Status: ${(profile?.status ?: "active").uppercase()} • Tuition Balance: ${JyCatalogDefaults.formatNaira(totalOutstanding)}",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.85f),
            )
          }
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricStatCard(
          label = "Enrolled Courses",
          value = enrollments.size.toString(),
          icon = Icons.Filled.MenuBook,
          accentColor = JyRoyalBlue,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("course_registration") },
        )
        MetricStatCard(
          label = "Attendance Rate",
          value = "$attendancePct%",
          icon = Icons.Filled.EventAvailable,
          accentColor = JyTeal,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("attendance") },
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricStatCard(
          label = "Tuition Paid",
          value = JyCatalogDefaults.formatNaira(totalPaid),
          icon = Icons.Filled.CheckCircle,
          accentColor = JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("payment_history") },
        )
        MetricStatCard(
          label = "Outstanding",
          value = JyCatalogDefaults.formatNaira(totalOutstanding),
          icon = Icons.Filled.AccountBalanceWallet,
          accentColor = if (totalOutstanding > 0) JyGold else JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("payments") },
        )
      }
    }

    item {
      SectionHeader("Quick Actions")
      val actions =
        listOf(
          Triple("Pay Tuition", Icons.Filled.Payments, "payments"),
          Triple("Courses", Icons.Filled.School, "course_catalogue"),
          Triple("Results (${results.size})", Icons.Filled.Grade, "results"),
          Triple("Certificates (${certificates.size})", Icons.Filled.WorkspacePremium, "certificates"),
          Triple("Live Chat", Icons.AutoMirrored.Filled.Chat, "student_chat"),
          Triple("Group Chat", Icons.Filled.Group, "group_chat"),
        )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(actions) { (label, icon, route) ->
          AssistChip(
            onClick = { onNavigate(route) },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) },
          )
        }
      }
    }

    item {
      SectionHeader(
        title = "Upcoming Classes & Schedule",
        actionLabel = "All Courses",
        onAction = { onNavigate("course_catalogue") },
      )
    }
    if (programmes.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Class Schedules Yet",
          message = "Active training programmes and class timetables will appear here.",
        )
      }
    } else {
      items(programmes.take(3), key = { it.id }) { prog ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(prog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                "${prog.scheduleText} • ${prog.instructorName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            StatusPill(
              text = prog.duration,
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }

    item {
      SectionHeader(
        title = "Important Announcements",
        actionLabel = "See All",
        onAction = { onNavigate("announcements") },
      )
    }
    if (announcements.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Announcements",
          message = "Academic updates, exam dates, and campus news will appear here.",
          icon = Icons.Filled.Campaign,
        )
      }
    } else {
      items(announcements.take(3), key = { it.id }) { item ->
        AnnouncementSummaryCard(item)
      }
    }
  }
}

@Composable
fun AdminDashboardScreen(
  settings: InstitutionSettings,
  allUsers: List<UserProfile>,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  payments: List<Payment>,
  announcements: List<Announcement>,
  onUpdateStudentStatus: (String, String) -> Unit,
  onDeleteStudent: (String) -> Unit,
  onOpenAddStudent: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  var statusFilter by remember { mutableStateOf("all") }
  var studentToDelete by remember { mutableStateOf<UserProfile?>(null) }

  val students = remember(allUsers) { allUsers.filter { it.role == "student" || it.studentIdNumber.isNotBlank() } }
  val activeCount = students.count { it.status == "active" }
  val graduatedCount = students.count { it.status == "graduated" }
  val completedPaymentsTotal = payments.filter { it.status == "approved" }.sumOf { it.amountNaira }
  val outstandingTotal = enrollments.sumOf { it.outstandingNaira }
  val pendingPaymentsCount = payments.count { it.status == "pending" }

  val filteredStudents =
    remember(students, searchQuery, statusFilter) {
      students.filter { s ->
        val matchesQuery =
          searchQuery.isBlank() ||
            s.fullName.contains(searchQuery, ignoreCase = true) ||
            s.studentIdNumber.contains(searchQuery, ignoreCase = true) ||
            s.selectedProgrammeTitle.contains(searchQuery, ignoreCase = true)
        val matchesFilter = statusFilter == "all" || s.status == statusFilter
        matchesQuery && matchesFilter
      }
    }

  if (studentToDelete != null) {
    ConfirmationDialog(
      title = "Delete Student Record",
      message = "Remove ${studentToDelete!!.fullName} (${studentToDelete!!.studentIdNumber}) from JY-Computers?",
      confirmLabel = "Delete",
      onConfirm = {
        onDeleteStudent(studentToDelete!!.userId)
        studentToDelete = null
      },
      onDismiss = { studentToDelete = null },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("admin_dashboard_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Administrator Console",
        subtitle = "${settings.institutionName} • ${settings.activeSession}",
        badgeText = "$pendingPaymentsCount Pending Pay",
      )
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricStatCard(
          label = "Total Students",
          value = students.size.toString(),
          icon = Icons.Filled.Group,
          accentColor = JyRoyalBlue,
          modifier = Modifier.weight(1f),
        )
        MetricStatCard(
          label = "Active / Graduated",
          value = "$activeCount / $graduatedCount",
          icon = Icons.Filled.School,
          accentColor = JyTeal,
          modifier = Modifier.weight(1f),
        )
        MetricStatCard(
          label = "Courses",
          value = programmes.size.toString(),
          icon = Icons.Filled.MenuBook,
          accentColor = JyGold,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("course_catalogue") },
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricStatCard(
          label = "Completed Payments",
          value = JyCatalogDefaults.formatNaira(completedPaymentsTotal),
          icon = Icons.Filled.CheckCircle,
          accentColor = JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("payments") },
        )
        MetricStatCard(
          label = "Outstanding Fees",
          value = JyCatalogDefaults.formatNaira(outstandingTotal),
          icon = Icons.Filled.AccountBalanceWallet,
          accentColor = JyCrimson,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("reports") },
        )
      }
    }

    item {
      SectionHeader("Management Modules")
      val tools =
        listOf(
          Triple("Register Student", Icons.Filled.PersonAdd, "add_student"),
          Triple("Approve Payments", Icons.Filled.Payments, "payments"),
          Triple("Attendance", Icons.Filled.EventAvailable, "attendance"),
          Triple("Results", Icons.Filled.Grade, "results"),
          Triple("Certificates", Icons.Filled.WorkspacePremium, "certificates"),
          Triple("Announcements", Icons.Filled.Campaign, "announcements"),
          Triple("Reports & Export", Icons.Filled.Analytics, "reports"),
          Triple("Settings", Icons.Filled.Settings, "admin_settings"),
        )
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tools) { (label, icon, route) ->
          AssistChip(
            onClick = { if (route == "add_student") onOpenAddStudent() else onNavigate(route) },
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp)) },
          )
        }
      }
    }

    item {
      SectionHeader(
        title = "Recent Transactions",
        actionLabel = "All Payments",
        onAction = { onNavigate("payments") },
      )
    }
    if (payments.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Transactions Recorded",
          message = "Student online and OPay tuition submissions will appear here for review.",
        )
      }
    } else {
      items(payments.take(3), key = { it.id }) { pay ->
        Card(
          onClick = { onNavigate("payments") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(pay.studentName, fontWeight = FontWeight.SemiBold)
              Text(
                "${pay.programmeTitle} • ${pay.reference}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text(
                JyCatalogDefaults.formatNaira(pay.amountNaira),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
              val (bg, fg) =
                when (pay.status) {
                  "approved" -> JyEmeraldContainer to JyEmerald
                  "rejected" -> JyCrimsonContainer to JyCrimson
                  else -> JyGoldContainer to JyGold
                }
              StatusPill(pay.status.uppercase(), bg, fg)
            }
          }
        }
      }
    }

    item {
      SectionHeader(
        title = "Student Directory & Accounts",
        actionLabel = "+ Add",
        onAction = onOpenAddStudent,
      )
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search by name, Student ID, or course") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("admin_student_search"),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all", "active", "graduated", "suspended").forEach { f ->
          FilterChip(
            selected = statusFilter == f,
            onClick = { statusFilter = f },
            label = { Text(f.replaceFirstChar { it.uppercase() }) },
          )
        }
      }
    }

    if (filteredStudents.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Matching Students",
          message = "Registered students will appear here. Tap + Add to register a student.",
        )
      }
    } else {
      items(filteredStudents, key = { it.userId }) { student ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Base64Avatar(student.passportPhotoBase64, student.fullName, size = 44.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                  "${student.studentIdNumber} • ${student.selectedProgrammeTitle}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              val (bg, fg) =
                when (student.status) {
                  "graduated" -> JyEmeraldContainer to JyEmerald
                  "suspended" -> JyCrimsonContainer to JyCrimson
                  else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }
              StatusPill(student.status.uppercase(), bg, fg)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (student.status != "active") {
                TextButton(onClick = { onUpdateStudentStatus(student.userId, "active") }) {
                  Text("Activate")
                }
              }
              if (student.status != "graduated") {
                TextButton(onClick = { onUpdateStudentStatus(student.userId, "graduated") }) {
                  Text("Graduate")
                }
              }
              if (student.status != "suspended") {
                TextButton(onClick = { onUpdateStudentStatus(student.userId, "suspended") }) {
                  Text("Suspend", color = JyGold)
                }
              }
              IconButton(onClick = { studentToDelete = student }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete student", tint = JyCrimson)
              }
            }
          }
        }
      }
    }

    if (announcements.isNotEmpty()) {
      item {
        SectionHeader("Recent Announcements", "Manage", onAction = { onNavigate("announcements") })
      }
      items(announcements.take(2), key = { "ann_${it.id}" }) { ann ->
        AnnouncementSummaryCard(ann)
      }
    }
  }
}

@Composable
fun InstructorDashboardScreen(
  profile: UserProfile?,
  currentUserId: String,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  announcements: List<Announcement>,
  onNavigate: (String) -> Unit,
) {
  val assignedCourses =
    remember(programmes, currentUserId) {
      val specific = programmes.filter { it.instructorUid == currentUserId }
      specific.ifEmpty { programmes }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("instructor_dashboard_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Instructor Workspace",
        subtitle = "Welcome, ${profile?.fullName?.ifBlank { "Instructor" } ?: "Instructor"}",
        badgeText = "${assignedCourses.size} Courses",
      )
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricStatCard(
          label = "Assigned Courses",
          value = assignedCourses.size.toString(),
          icon = Icons.Filled.MenuBook,
          accentColor = JyRoyalBlue,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("course_catalogue") },
        )
        MetricStatCard(
          label = "Assigned Students",
          value = enrollments.size.toString(),
          icon = Icons.Filled.Group,
          accentColor = JyTeal,
          modifier = Modifier.weight(1f),
        )
      }
    }

    item {
      SectionHeader("Teaching Tools")
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
          onClick = { onNavigate("attendance") },
          label = { Text("Take Attendance") },
          leadingIcon = { Icon(Icons.Filled.EventAvailable, contentDescription = null) },
        )
        AssistChip(
          onClick = { onNavigate("results") },
          label = { Text("Upload Results") },
          leadingIcon = { Icon(Icons.Filled.Grade, contentDescription = null) },
        )
        AssistChip(
          onClick = { onNavigate("student_chat") },
          label = { Text("Student Chat") },
          leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
        )
      }
    }

    item {
      SectionHeader("Assigned Courses", "Materials", onAction = { onNavigate("course_catalogue") })
    }
    items(assignedCourses, key = { it.id }) { prog ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(prog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "${prog.duration} • ${prog.scheduleText}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    item { SectionHeader("Assigned Students") }
    if (enrollments.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Enrolled Students Yet",
          message = "Students enrolled in your assigned courses will appear here.",
        )
      }
    } else {
      items(enrollments, key = { it.id }) { enr ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(enr.studentName, fontWeight = FontWeight.SemiBold)
              Text(
                "${enr.studentIdNumber} • ${enr.programmeTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            StatusPill(
              text = enr.status.uppercase(),
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }

    if (announcements.isNotEmpty()) {
      item { SectionHeader("Campus Notices") }
      items(announcements.take(2), key = { "inst_ann_${it.id}" }) { ann ->
        AnnouncementSummaryCard(ann)
      }
    }
  }
}

@Composable
fun AnnouncementSummaryCard(ann: Announcement) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          ann.title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
        )
        StatusPill(
          text = ann.category.uppercase(),
          containerColor =
            if (ann.category == "urgent") JyCrimsonContainer else MaterialTheme.colorScheme.primaryContainer,
          contentColor =
            if (ann.category == "urgent") JyCrimson else MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        ann.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
      )
      if (ann.eventDate.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "Date: ${ann.eventDate}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}
