package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AcademicResult
import com.example.data.model.AttendanceRecord
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyGoldContainer
import com.example.util.ExportAndPdfUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
  activeRole: String,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  attendance: List<AttendanceRecord>,
  onMarkAttendance: (String, String, String, Programme, String, String) -> Unit,
) {
  val isStaff = activeRole == "admin" || activeRole == "instructor"
  var selectedProgramme by remember(programmes) { mutableStateOf(programmes.firstOrNull()) }
  var progExpanded by remember { mutableStateOf(false) }
  var dateKey by remember {
    mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()))
  }

  val presentCount = attendance.count { it.status == "present" }
  val lateCount = attendance.count { it.status == "late" }
  val total = attendance.size
  val rate = if (total == 0) 100 else (((presentCount + lateCount) * 100f) / total).toInt()

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("attendance_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = if (isStaff) "Class Attendance Roll" else "My Attendance",
        subtitle =
          if (isStaff) "Mark daily attendance for enrolled students"
          else "Present: $presentCount • Late: $lateCount • Total Classes: $total",
        badgeText = "$rate% Rate",
      ) {
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
          progress = { (rate / 100f).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)),
        )
      }
    }

    if (isStaff && programmes.isNotEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ExposedDropdownMenuBox(
              expanded = progExpanded,
              onExpandedChange = { progExpanded = it },
            ) {
              OutlinedTextField(
                value = selectedProgramme?.title ?: "Select Course",
                onValueChange = {},
                readOnly = true,
                label = { Text("Course") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = progExpanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
              )
              ExposedDropdownMenu(expanded = progExpanded, onDismissRequest = { progExpanded = false }) {
                programmes.forEach { prog ->
                  DropdownMenuItem(
                    text = { Text(prog.title) },
                    onClick = {
                      selectedProgramme = prog
                      progExpanded = false
                    },
                  )
                }
              }
            }
            OutlinedTextField(
              value = dateKey,
              onValueChange = { dateKey = it },
              label = { Text("Class Date (YYYY-MM-DD)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }

      val courseEnrollments =
        enrollments.filter { selectedProgramme == null || it.programmeId == selectedProgramme?.id }

      if (courseEnrollments.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Enrolled Students in Selected Course",
            message = "Enrollments for the selected course will appear here for roll call.",
            icon = Icons.Filled.EventAvailable,
          )
        }
      } else {
        items(courseEnrollments, key = { it.id }) { enr ->
          val prog = selectedProgramme ?: programmes.first()
          val currentStatus =
            attendance.firstOrNull {
              it.studentUid == enr.userId && it.programmeId == prog.id && it.dateKey == dateKey
            }?.status
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(enr.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                "${enr.studentIdNumber} • ${enr.programmeTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("present", "late", "absent").forEach { st ->
                  FilterChip(
                    selected = currentStatus == st,
                    onClick = {
                      onMarkAttendance(enr.userId, enr.studentName, enr.studentIdNumber, prog, dateKey, st)
                    },
                    label = { Text(st.replaceFirstChar { it.uppercase() }) },
                  )
                }
              }
            }
          }
        }
      }
    }

    item {
      Text(
        "Attendance History",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    if (attendance.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Attendance Records Yet",
          message = "Daily class attendance logs will appear here.",
          icon = Icons.Filled.EventAvailable,
        )
      }
    } else {
      items(attendance.sortedByDescending { it.dateKey }, key = { it.id }) { rec ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${rec.studentName} • ${rec.programmeTitle}", fontWeight = FontWeight.SemiBold)
              Text(
                "Date: ${rec.dateKey}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            val (bg, fg) =
              when (rec.status) {
                "present" -> JyEmeraldContainer to JyEmerald
                "late" -> JyGoldContainer to JyGold
                else -> JyCrimsonContainer to JyCrimson
              }
            StatusPill(rec.status.uppercase(), bg, fg)
          }
        }
      }
    }
  }
}

@Composable
fun AcademicResultsScreen(
  activeRole: String,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  results: List<AcademicResult>,
  isBusy: Boolean,
  onSaveResult: (
    resultId: String?,
    studentUid: String,
    studentName: String,
    studentIdNumber: String,
    programme: Programme,
    assignmentScore: Long,
    examScore: Long,
    remarks: String,
    published: Boolean,
  ) -> Unit,
) {
  val isStaff = activeRole == "admin" || activeRole == "instructor"
  var showScoreDialog by remember { mutableStateOf(false) }
  var editingResult by remember { mutableStateOf<AcademicResult?>(null) }

  if (showScoreDialog && programmes.isNotEmpty()) {
    ResultEntryDialog(
      initial = editingResult,
      programmes = programmes,
      enrollments = enrollments,
      isBusy = isBusy,
      onDismiss = {
        showScoreDialog = false
        editingResult = null
      },
      onSave = { resId, uid, name, sid, prog, assign, exam, remarks, pub ->
        onSaveResult(resId, uid, name, sid, prog, assign, exam, remarks, pub)
        showScoreDialog = false
        editingResult = null
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      if (isStaff && programmes.isNotEmpty()) {
        FloatingActionButton(
          onClick = {
            editingResult = null
            showScoreDialog = true
          },
          modifier = Modifier.testTag("add_result_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Enter Student Result")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("results_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        val avg = if (results.isEmpty()) 0 else (results.sumOf { it.totalScore } / results.size)
        JyGradientHeaderCard(
          title = if (isStaff) "Academic Results Manager" else "My Published Results",
          subtitle =
            if (isStaff) "Enter assignment & exam scores, calculate grades, and publish"
            else "Average Score: $avg/100 across ${results.size} published course(s)",
          badgeText = "${results.size} Records",
        )
      }

      if (results.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Published Results Yet",
            message =
              if (isStaff) "Tap + to enter assignment and exam scores for enrolled students."
              else "Results published by your instructors will appear here.",
            icon = Icons.Filled.Grade,
          )
        }
      } else {
        items(results, key = { it.id }) { res ->
          Card(
            onClick = {
              if (isStaff) {
                editingResult = res
                showScoreDialog = true
              }
            },
            enabled = isStaff,
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
                Column(modifier = Modifier.weight(1f)) {
                  Text(res.programmeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text("${res.studentName} (${res.studentIdNumber})", style = MaterialTheme.typography.bodySmall)
                }
                StatusPill(
                  text = "GRADE ${res.grade}",
                  containerColor =
                    if (res.grade == "F") JyCrimsonContainer else JyEmeraldContainer,
                  contentColor = if (res.grade == "F") JyCrimson else JyEmerald,
                )
              }
              Text(
                "Assignment: ${res.assignmentScore}/40  •  Exam: ${res.examScore}/60  •  Total: ${res.totalScore}/100",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
              )
              if (res.remarks.isNotBlank()) {
                Text("Remarks: ${res.remarks}", style = MaterialTheme.typography.bodySmall)
              }
              if (isStaff) {
                StatusPill(
                  text = if (res.published) "PUBLISHED" else "DRAFT (HIDDEN FROM STUDENT)",
                  containerColor = if (res.published) JyEmeraldContainer else JyGoldContainer,
                  contentColor = if (res.published) JyEmerald else JyGold,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun CertificatesScreen(
  activeRole: String,
  settings: InstitutionSettings,
  certificates: List<Certificate>,
  allUsers: List<UserProfile>,
  programmes: List<Programme>,
  isBusy: Boolean,
  onIssueCertificate: (String, String, String, String, String, String) -> Unit,
) {
  val context = LocalContext.current
  val isAdmin = activeRole == "admin"
  var showIssueDialog by remember { mutableStateOf(false) }

  if (showIssueDialog && allUsers.isNotEmpty() && programmes.isNotEmpty()) {
    IssueCertificateDialog(
      students = allUsers,
      programmes = programmes,
      isBusy = isBusy,
      onDismiss = { showIssueDialog = false },
      onIssue = { uid, name, sid, pid, pTitle, gradDate ->
        onIssueCertificate(uid, name, sid, pid, pTitle, gradDate)
        showIssueDialog = false
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      if (isAdmin && allUsers.isNotEmpty() && programmes.isNotEmpty()) {
        FloatingActionButton(
          onClick = { showIssueDialog = true },
          modifier = Modifier.testTag("issue_certificate_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Issue Certificate")
        }
      }
    }
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).testTag("certificates_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        JyGradientHeaderCard(
          title = "Official Certificates",
          subtitle =
            if (isAdmin) "Generate verified PDF graduation certificates for qualified students"
            else "Download & share your verified JY-Computers certificates",
          badgeText = "${certificates.size} Issued",
        )
      }

      if (certificates.isEmpty()) {
        item {
          EmptyStateCard(
            title = "No Certificates Issued Yet",
            message =
              if (isAdmin) "Tap + to issue an official graduation certificate for a student."
              else "Completed programme certificates issued by Administration will appear here.",
            icon = Icons.Filled.WorkspacePremium,
          )
        }
      } else {
        items(certificates, key = { it.id }) { cert ->
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                  painter = painterResource(id = R.drawable.img_certificate_seal),
                  contentDescription = "Official Gold Seal",
                  modifier = Modifier.size(56.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(cert.programmeTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                  Text("${cert.studentName} • ${cert.studentIdNumber}", style = MaterialTheme.typography.bodyMedium)
                }
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = JyEmerald, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  "Verification No: ${cert.verificationCode}",
                  style = MaterialTheme.typography.labelLarge,
                  color = JyEmerald,
                )
              }
              Text(
                "Graduated: ${cert.graduationDate} • Signed by ${cert.signatoryName} (${cert.signatoryTitle})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(
                onClick = {
                  val pdf = ExportAndPdfUtil.generateCertificatePdf(context, cert, settings)
                  ExportAndPdfUtil.shareFile(context, pdf, "application/pdf", "Download / Share Certificate PDF")
                },
                modifier = Modifier.fillMaxWidth().testTag("download_cert_${cert.id}"),
              ) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download & Share PDF Certificate")
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultEntryDialog(
  initial: AcademicResult?,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String?, String, String, String, Programme, Long, Long, String, Boolean) -> Unit,
) {
  var selectedEnrollment by remember {
    mutableStateOf(
      enrollments.firstOrNull { it.userId == initial?.studentUid && it.programmeId == initial?.programmeId }
        ?: enrollments.firstOrNull()
    )
  }
  var enrExpanded by remember { mutableStateOf(false) }
  var assignStr by remember { mutableStateOf((initial?.assignmentScore ?: 30L).toString()) }
  var examStr by remember { mutableStateOf((initial?.examScore ?: 50L).toString()) }
  var remarks by remember { mutableStateOf(initial?.remarks ?: "Good performance") }
  var published by remember { mutableStateOf(initial?.published ?: true) }

  val assignVal = (assignStr.toLongOrNull() ?: 0L).coerceIn(0L, 40L)
  val examVal = (examStr.toLongOrNull() ?: 0L).coerceIn(0L, 60L)
  val totalVal = assignVal + examVal
  val gradeVal = JyCatalogDefaults.calculateGrade(totalVal)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "Enter Academic Result" else "Edit Result") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (initial == null && enrollments.isNotEmpty()) {
          ExposedDropdownMenuBox(expanded = enrExpanded, onExpandedChange = { enrExpanded = it }) {
            OutlinedTextField(
              value = selectedEnrollment?.let { "${it.studentName} (${it.programmeTitle})" } ?: "Select Student",
              onValueChange = {},
              readOnly = true,
              label = { Text("Student & Course") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enrExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = enrExpanded, onDismissRequest = { enrExpanded = false }) {
              enrollments.forEach { enr ->
                DropdownMenuItem(
                  text = { Text("${enr.studentName} • ${enr.programmeTitle}") },
                  onClick = {
                    selectedEnrollment = enr
                    enrExpanded = false
                  },
                )
              }
            }
          }
        }
        OutlinedTextField(
          value = assignStr,
          onValueChange = { assignStr = it.filter { c -> c.isDigit() } },
          label = { Text("Assignment Score (0 - 40)") },
          singleLine = true,
        )
        OutlinedTextField(
          value = examStr,
          onValueChange = { examStr = it.filter { c -> c.isDigit() } },
          label = { Text("Examination Score (0 - 60)") },
          singleLine = true,
        )
        Text(
          "Total: $totalVal / 100 • Calculated Grade: $gradeVal",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
          value = remarks,
          onValueChange = { remarks = it },
          label = { Text("Instructor Remarks") },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
          Text("Publish Result to Student")
          Switch(checked = published, onCheckedChange = { published = it })
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = !isBusy && (initial != null || selectedEnrollment != null),
        onClick = {
          val enr = selectedEnrollment
          val uid = initial?.studentUid ?: enr?.userId ?: return@TextButton
          val name = initial?.studentName ?: enr?.studentName ?: return@TextButton
          val sid = initial?.studentIdNumber ?: enr?.studentIdNumber ?: return@TextButton
          val pid = initial?.programmeId ?: enr?.programmeId ?: return@TextButton
          val prog = programmes.firstOrNull { it.id == pid } ?: programmes.first()
          onSave(initial?.id, uid, name, sid, prog, assignVal, examVal, remarks, published)
        },
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueCertificateDialog(
  students: List<UserProfile>,
  programmes: List<Programme>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onIssue: (String, String, String, String, String, String) -> Unit,
) {
  var selectedStudent by remember { mutableStateOf(students.first()) }
  var selectedProg by remember {
    mutableStateOf(programmes.firstOrNull { it.id == selectedStudent.selectedProgrammeId } ?: programmes.first())
  }
  var stExpanded by remember { mutableStateOf(false) }
  var progExpanded by remember { mutableStateOf(false) }
  var gradDate by remember {
    mutableStateOf(SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date()))
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Issue Graduation Certificate") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = stExpanded, onExpandedChange = { stExpanded = it }) {
          OutlinedTextField(
            value = "${selectedStudent.fullName} (${selectedStudent.studentIdNumber})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Student") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = stExpanded, onDismissRequest = { stExpanded = false }) {
            students.forEach { st ->
              DropdownMenuItem(
                text = { Text("${st.fullName} (${st.studentIdNumber})") },
                onClick = {
                  selectedStudent = st
                  stExpanded = false
                },
              )
            }
          }
        }
        ExposedDropdownMenuBox(expanded = progExpanded, onExpandedChange = { progExpanded = it }) {
          OutlinedTextField(
            value = selectedProg.title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Completed Programme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = progExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = progExpanded, onDismissRequest = { progExpanded = false }) {
            programmes.forEach { p ->
              DropdownMenuItem(
                text = { Text(p.title) },
                onClick = {
                  selectedProg = p
                  progExpanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(
          value = gradDate,
          onValueChange = { gradDate = it },
          label = { Text("Graduation Date") },
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = !isBusy && gradDate.isNotBlank(),
        onClick = {
          onIssue(
            selectedStudent.userId,
            selectedStudent.fullName,
            selectedStudent.studentIdNumber,
            selectedProg.id,
            selectedProg.title,
            gradDate,
          )
        },
      ) { Text("Issue") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
