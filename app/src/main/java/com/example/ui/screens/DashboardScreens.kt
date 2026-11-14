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
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicResult
import com.example.data.model.ActivityLog
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
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.OfficialOpayBankCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.SimpleDistributionChartCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyBrightBlue
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyGoldContainer
import com.example.ui.theme.JyRoyalBlue
import com.example.ui.theme.JyTeal
import com.example.util.ExportAndPdfUtil
import java.text.SimpleDateFormat
import java.util.Locale

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
  onNotify: (String) -> Unit,
  onNavigate: (String) -> Unit,
) {
  val context = LocalContext.current
  val totalPaid = enrollments.sumOf { it.amountPaidNaira }
  val totalFee = enrollments.sumOf { it.totalFeeNaira }
  val totalOutstanding = enrollments.sumOf { it.outstandingNaira }
  val presentCount = attendance.count { it.status == "present" || it.status == "late" }
  val attendancePct =
    if (attendance.isEmpty()) 100 else ((presentCount * 100f) / attendance.size).toInt()
  val matchedProg = programmes.firstOrNull { it.id == profile?.selectedProgrammeId }
  val effectivePayStatus =
    when {
      totalFee > 0 && totalPaid >= totalFee -> "PAID"
      payments.any { it.status == "pending" } -> "PENDING VERIFICATION"
      totalPaid > 0 -> "PARTIAL"
      else -> (profile?.paymentStatus ?: "unpaid").uppercase()
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("student_dashboard_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Hello, ${profile?.fullName?.ifBlank { "Student" } ?: "Student"}",
        subtitle =
          "${settings.institutionName} • Reg No: ${profile?.studentIdNumber?.ifBlank { "Pending" } ?: "Welcome"}",
        badgeText = settings.activeSession,
        showOfficialLogo = true,
      ) {
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Base64Avatar(
            base64 = profile?.passportPhotoBase64.orEmpty(),
            name = profile?.fullName ?: "Student",
            size = 54.dp,
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = profile?.selectedProgrammeTitle?.ifBlank { "No course selected" } ?: "Select a course",
              style = MaterialTheme.typography.titleMedium,
              color = Color.White,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text =
                "Level: ${profile?.trainingLevel ?: "Beginner"} • Duration: ${matchedProg?.duration ?: "Active Term"}",
              style = MaterialTheme.typography.bodySmall,
              color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              val approval = (profile?.approvalStatus ?: "pending").uppercase()
              StatusPill(
                text = "REG: $approval",
                containerColor = if (approval == "APPROVED") JyEmeraldContainer else JyGoldContainer,
                contentColor = if (approval == "APPROVED") JyEmerald else JyGold,
              )
              StatusPill(
                text = "PAY: $effectivePayStatus",
                containerColor = if (effectivePayStatus == "PAID") JyEmeraldContainer else Color.White.copy(alpha = 0.2f),
                contentColor = if (effectivePayStatus == "PAID") JyEmerald else Color.White,
              )
            }
          }
        }
      }
    }

    if (profile != null) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Training Course Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text(
                "${profile.courseProgressPercent}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            LinearProgressIndicator(
              progress = { (profile.courseProgressPercent / 100f).coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(999.dp)),
            )
            Text(
              "Phone: ${profile.phoneNumber} • State/LGA: ${profile.stateOfOrigin}${if (profile.localGovernmentArea.isNotBlank()) " / ${profile.localGovernmentArea}" else ""}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
              onClick = {
                val pdf = ExportAndPdfUtil.generateStudentProfilePdf(context, profile, enrollments, settings)
                ExportAndPdfUtil.shareFile(context, pdf, "application/pdf", "Share Registration Sheet")
              },
              modifier = Modifier.fillMaxWidth().testTag("download_student_reg_pdf"),
            ) {
              Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Download Registration Information (PDF)")
            }
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
      OfficialOpayBankCard(settings = settings, onCopied = onNotify)
    }

    item {
      SectionHeader("Quick Actions")
      val actions =
        listOf(
          Triple("Pay Tuition", Icons.Filled.Payments, "payments"),
          Triple("Payment History", Icons.Filled.History, "payment_history"),
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
  activityLogs: List<ActivityLog>,
  onApproveStudent: (String, String, String) -> Unit,
  onOpenAddStudent: () -> Unit,
  onNavigate: (String) -> Unit,
) {
  val students = remember(allUsers) { allUsers.filter { it.role == "student" || it.studentIdNumber.isNotBlank() } }
  val activeCount = students.count { it.status == "active" }
  val graduatedCount = students.count { it.status == "graduated" }
  val pendingRegCount = students.count { it.approvalStatus == "pending" }
  val approvedRegCount = students.count { it.approvalStatus == "approved" }
  val completedPaymentsTotal = payments.filter { it.status == "approved" }.sumOf { it.amountNaira }
  val pendingPaymentsCount = payments.count { it.status == "pending" }
  val outstandingTotal = enrollments.sumOf { it.outstandingNaira }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("admin_dashboard_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Administrator Dashboard",
        subtitle = "${settings.institutionName} • ${settings.activeSession}",
        badgeText = "$pendingRegCount Pending Reg",
        showOfficialLogo = true,
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
          onClick = { onNavigate("admin_students") },
        )
        MetricStatCard(
          label = "Active Students",
          value = activeCount.toString(),
          icon = Icons.Filled.VerifiedUser,
          accentColor = JyTeal,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("admin_students") },
        )
        MetricStatCard(
          label = "Graduated",
          value = graduatedCount.toString(),
          icon = Icons.Filled.School,
          accentColor = JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("reports") },
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricStatCard(
          label = "Pending Regs",
          value = pendingRegCount.toString(),
          icon = Icons.Filled.PendingActions,
          accentColor = JyGold,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("admin_students") },
        )
        MetricStatCard(
          label = "Approved Regs",
          value = approvedRegCount.toString(),
          icon = Icons.Filled.CheckCircle,
          accentColor = JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("admin_students") },
        )
        MetricStatCard(
          label = "Total Enrolments",
          value = enrollments.size.toString(),
          icon = Icons.Filled.MenuBook,
          accentColor = JyBrightBlue,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("course_registration") },
        )
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricStatCard(
          label = "Payments Received",
          value = JyCatalogDefaults.formatNaira(completedPaymentsTotal),
          icon = Icons.Filled.Payments,
          accentColor = JyEmerald,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("payments") },
        )
        MetricStatCard(
          label = "Pending Pay ($pendingPaymentsCount)",
          value = "$pendingPaymentsCount Items",
          icon = Icons.Filled.PendingActions,
          accentColor = JyGold,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("payments") },
        )
        MetricStatCard(
          label = "Outstanding",
          value = JyCatalogDefaults.formatNaira(outstandingTotal),
          icon = Icons.Filled.AccountBalanceWallet,
          accentColor = JyCrimson,
          modifier = Modifier.weight(1f),
          onClick = { onNavigate("reports") },
        )
      }
    }

    item {
      SimpleDistributionChartCard(
        title = "Student & Enrolment Analytics",
        items =
          listOf(
            Triple("Active Students", activeCount, JyRoyalBlue),
            Triple("Approved Registrations", approvedRegCount, JyEmerald),
            Triple("Pending Approvals", pendingRegCount, JyGold),
            Triple("Graduated Students", graduatedCount, JyTeal),
            Triple("Course Enrolments", enrollments.size, JyBrightBlue),
            Triple("Available Courses", programmes.count { it.active }, JyRoyalBlue),
          ),
      )
    }

    item {
      SectionHeader("Quick Admin Actions")
      val tools =
        listOf(
          Triple("Manage Students", Icons.Filled.Group, "admin_students"),
          Triple("Register Student", Icons.Filled.PersonAdd, "add_student"),
          Triple("Courses", Icons.Filled.MenuBook, "course_catalogue"),
          Triple("Payments", Icons.Filled.Payments, "payments"),
          Triple("Reports & Export", Icons.Filled.Analytics, "reports"),
          Triple("Admins & Logs", Icons.Filled.VerifiedUser, "admin_roles"),
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
        title = "Recent Student Registrations",
        actionLabel = "All Students",
        onAction = { onNavigate("admin_students") },
      )
    }
    if (students.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Student Registrations Yet",
          message = "Newly registered students will appear here for approval.",
        )
      }
    } else {
      items(students.take(4), key = { it.userId }) { student ->
        Card(
          onClick = { onNavigate("admin_students") },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Base64Avatar(student.passportPhotoBase64, student.fullName, size = 44.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(student.fullName, fontWeight = FontWeight.SemiBold)
              Text(
                "${student.studentIdNumber} • ${student.selectedProgrammeTitle} (${student.trainingLevel})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (student.approvalStatus == "pending") {
              Button(
                onClick = { onApproveStudent(student.userId, student.fullName, "approved") },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
              ) {
                Text("Approve", style = MaterialTheme.typography.labelSmall)
              }
            } else {
              StatusPill(
                text = student.approvalStatus.uppercase(),
                containerColor = JyEmeraldContainer,
                contentColor = JyEmerald,
              )
            }
          }
        }
      }
    }

    item {
      SectionHeader(
        title = "Recent Administrative Activities",
        actionLabel = "View Audit Log",
        onAction = { onNavigate("admin_roles") },
      )
    }
    if (activityLogs.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Activity Logs Yet",
          message = "Approvals, payment verifications, course edits, and student changes will be recorded here.",
        )
      }
    } else {
      items(activityLogs.take(4), key = { it.id }) { log ->
        val timeText =
          log.createdAt?.toDate()?.let {
            SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(it)
          } ?: "Just now"
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(log.details, style = MaterialTheme.typography.bodySmall)
            Text("By ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        showOfficialLogo = true,
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
