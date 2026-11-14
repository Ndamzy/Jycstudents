package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.ui.components.Base64Avatar
import com.example.ui.components.ConfirmationDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PassportCameraCaptureDialog
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyGoldContainer
import com.example.util.ExportAndPdfUtil
import com.example.util.ImageCompressionUtil
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStudentsScreen(
  settings: InstitutionSettings,
  allUsers: List<UserProfile>,
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  isBusy: Boolean,
  onOpenAddStudent: () -> Unit,
  onApproveOrReject: (String, String, String) -> Unit,
  onUpdateStatus: (String, String, String) -> Unit,
  onEditStudent: (UserProfile) -> Unit,
  onUpdateProgress: (String, String, Int, String?) -> Unit,
  onAssignCourse: (UserProfile, Programme) -> Unit,
  onDeleteStudent: (String, String) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var searchQuery by remember { mutableStateOf("") }
  var approvalFilter by remember { mutableStateOf("all") }
  var statusFilter by remember { mutableStateOf("all") }
  var courseFilter by remember { mutableStateOf("all") }
  var dateFilter by remember { mutableStateOf("") }

  var selectedStudent by remember { mutableStateOf<UserProfile?>(null) }
  var editingStudent by remember { mutableStateOf<UserProfile?>(null) }
  var studentToDelete by remember { mutableStateOf<UserProfile?>(null) }
  var showCameraForUid by remember { mutableStateOf<UserProfile?>(null) }

  val students =
    remember(allUsers) {
      allUsers.filter { it.role == "student" || it.studentIdNumber.isNotBlank() }
    }

  val filteredStudents =
    remember(students, searchQuery, approvalFilter, statusFilter, courseFilter, dateFilter) {
      students.filter { s ->
        val matchesQuery =
          searchQuery.isBlank() ||
            s.fullName.contains(searchQuery, ignoreCase = true) ||
            s.studentIdNumber.contains(searchQuery, ignoreCase = true) ||
            s.email.contains(searchQuery, ignoreCase = true) ||
            s.phoneNumber.contains(searchQuery, ignoreCase = true) ||
            s.selectedProgrammeTitle.contains(searchQuery, ignoreCase = true)
        val matchesApproval = approvalFilter == "all" || s.approvalStatus == approvalFilter
        val matchesStatus = statusFilter == "all" || s.status == statusFilter
        val matchesCourse = courseFilter == "all" || s.selectedProgrammeId == courseFilter
        val matchesDate = dateFilter.isBlank() || s.registrationDate.contains(dateFilter.trim(), ignoreCase = true)
        matchesQuery && matchesApproval && matchesStatus && matchesCourse && matchesDate
      }
    }

  val photoReplacePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      val target = selectedStudent
      if (uri != null && target != null) {
        scope.launch {
          val res = ImageCompressionUtil.validateAndCompressUri(context, uri)
          if (res.base64 != null) {
            val updated = target.copy(passportPhotoBase64 = res.base64)
            onEditStudent(updated)
            selectedStudent = updated
          } else {
            onNotify(res.errorMessage ?: "Invalid photo")
          }
        }
      }
    }

  if (showCameraForUid != null) {
    PassportCameraCaptureDialog(
      onPhotoAccepted = { base64 ->
        val target = showCameraForUid!!
        val updated = target.copy(passportPhotoBase64 = base64)
        onEditStudent(updated)
        if (selectedStudent?.userId == target.userId) {
          selectedStudent = updated
        }
        showCameraForUid = null
      },
      onDismiss = { showCameraForUid = null },
    )
  }

  if (studentToDelete != null) {
    ConfirmationDialog(
      title = "Delete Student Record",
      message = "Permanently remove ${studentToDelete!!.fullName} (${studentToDelete!!.studentIdNumber})?",
      confirmLabel = "Delete",
      onConfirm = {
        onDeleteStudent(studentToDelete!!.userId, studentToDelete!!.fullName)
        if (selectedStudent?.userId == studentToDelete!!.userId) selectedStudent = null
        studentToDelete = null
      },
      onDismiss = { studentToDelete = null },
    )
  }

  if (editingStudent != null) {
    EditStudentDialog(
      initial = editingStudent!!,
      programmes = programmes,
      onDismiss = { editingStudent = null },
      onSave = { updated ->
        onEditStudent(updated)
        if (selectedStudent?.userId == updated.userId) selectedStudent = updated
        editingStudent = null
      },
    )
  }

  if (selectedStudent != null) {
    val st = students.firstOrNull { it.userId == selectedStudent!!.userId } ?: selectedStudent!!
    val stEnrollments = enrollments.filter { it.userId == st.userId }
    StudentDetailDialog(
      student = st,
      enrollments = stEnrollments,
      programmes = programmes,
      settings = settings,
      isBusy = isBusy,
      onDismiss = { selectedStudent = null },
      onDownloadPhoto = {
        val file =
          ImageCompressionUtil.saveBase64ImageToCacheFile(
            context,
            st.passportPhotoBase64,
            st.studentIdNumber.ifBlank { st.fullName },
          )
        if (file != null) {
          ExportAndPdfUtil.shareFile(context, file, "image/jpeg", "Download / Share Passport Photograph")
        } else {
          onNotify("No passport photograph available to download.")
        }
      },
      onPickReplacementPhoto = {
        photoReplacePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
      },
      onCaptureReplacementPhoto = { showCameraForUid = st },
      onDownloadPdf = {
        val file = ExportAndPdfUtil.generateStudentProfilePdf(context, st, stEnrollments, settings)
        ExportAndPdfUtil.shareFile(context, file, "application/pdf", "Print / Share Student Registration PDF")
      },
      onEditInfo = { editingStudent = st },
      onApproveOrReject = { status -> onApproveOrReject(st.userId, st.fullName, status) },
      onUpdateAccountStatus = { status -> onUpdateStatus(st.userId, status, st.fullName) },
      onUpdateProgress = { pct, statusOverride ->
        onUpdateProgress(st.userId, st.fullName, pct, statusOverride)
      },
      onAssignCourse = { prog -> onAssignCourse(st, prog) },
      onDelete = { studentToDelete = st },
    )
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("admin_students_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "Students (${filteredStudents.size})",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Button(onClick = onOpenAddStudent, modifier = Modifier.testTag("admin_add_student_button")) {
          Icon(Icons.Filled.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Register Student")
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            val headers = listOf("Reg No", "Full Name", "Course", "Level", "Approval", "Status")
            val rows =
              filteredStudents.map {
                listOf(
                  it.studentIdNumber,
                  it.fullName,
                  it.selectedProgrammeTitle,
                  it.trainingLevel,
                  it.approvalStatus,
                  it.status,
                )
              }
            val pdf = ExportAndPdfUtil.generateReportPdf(context, "Registered Students", headers, rows, settings)
            ExportAndPdfUtil.shareFile(context, pdf, "application/pdf", "Export Students PDF")
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Export PDF")
        }
        OutlinedButton(
          onClick = {
            val headers = listOf("Reg No", "Full Name", "Gender", "Phone", "Email", "State", "LGA", "Course", "Level", "Approval", "Status", "Reg Date")
            val rows =
              filteredStudents.map {
                listOf(
                  it.studentIdNumber,
                  it.fullName,
                  it.gender,
                  it.phoneNumber,
                  it.email,
                  it.stateOfOrigin,
                  it.localGovernmentArea,
                  it.selectedProgrammeTitle,
                  it.trainingLevel,
                  it.approvalStatus,
                  it.status,
                  it.registrationDate,
                )
              }
            val csv = ExportAndPdfUtil.generateCsvReport(context, "Registered Students", headers, rows)
            ExportAndPdfUtil.shareFile(context, csv, "text/csv", "Export Students Excel CSV")
          },
          modifier = Modifier.weight(1f),
        ) {
          Icon(Icons.Filled.TableChart, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Export Excel/CSV")
        }
      }
    }

    item {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search by Name, Reg No, Email, Phone, or Course") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("students_search_field"),
      )
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedTextField(
        value = dateFilter,
        onValueChange = { dateFilter = it },
        label = { Text("Filter by Registration Date (YYYY-MM-DD, optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("all", "pending", "approved", "rejected")) { ap ->
          FilterChip(
            selected = approvalFilter == ap,
            onClick = { approvalFilter = ap },
            label = { Text("Reg: ${ap.replaceFirstChar { it.uppercase() }}") },
          )
        }
        items(listOf("active", "suspended", "graduated")) { st ->
          FilterChip(
            selected = statusFilter == st,
            onClick = { statusFilter = if (statusFilter == st) "all" else st },
            label = { Text(st.replaceFirstChar { it.uppercase() }) },
          )
        }
      }
    }

    if (programmes.isNotEmpty()) {
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            FilterChip(
              selected = courseFilter == "all",
              onClick = { courseFilter = "all" },
              label = { Text("All Courses") },
            )
          }
          items(programmes, key = { "cf_${it.id}" }) { prog ->
            FilterChip(
              selected = courseFilter == prog.id,
              onClick = { courseFilter = prog.id },
              label = { Text(prog.title) },
            )
          }
        }
      }
    }

    if (filteredStudents.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Matching Students",
          message = "Adjust your search or filters, or tap Register Student to enroll a new learner.",
        )
      }
    } else {
      items(filteredStudents, key = { it.userId }) { st ->
        Card(
          onClick = { selectedStudent = st },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Base64Avatar(st.passportPhotoBase64, st.fullName, size = 50.dp)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(st.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                  "${st.studentIdNumber} • ${st.selectedProgrammeTitle} (${st.trainingLevel})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  "${st.phoneNumber} • Reg: ${st.registrationDate}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val (appBg, appFg) =
                  when (st.approvalStatus) {
                    "approved" -> JyEmeraldContainer to JyEmerald
                    "rejected" -> JyCrimsonContainer to JyCrimson
                    else -> JyGoldContainer to JyGold
                  }
                StatusPill(st.approvalStatus.uppercase(), appBg, appFg)
                StatusPill(
                  st.status.uppercase(),
                  MaterialTheme.colorScheme.primaryContainer,
                  MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
            }

            LinearProgressIndicator(
              progress = { (st.courseProgressPercent / 100f).coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Progress: ${st.courseProgressPercent}%", style = MaterialTheme.typography.labelSmall)
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (st.approvalStatus != "approved") {
                  TextButton(onClick = { onApproveOrReject(st.userId, st.fullName, "approved") }) {
                    Text("Approve")
                  }
                }
                TextButton(onClick = { selectedStudent = st }) {
                  Text("Manage Profile")
                }
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
private fun StudentDetailDialog(
  student: UserProfile,
  enrollments: List<Enrollment>,
  programmes: List<Programme>,
  settings: InstitutionSettings,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onDownloadPhoto: () -> Unit,
  onPickReplacementPhoto: () -> Unit,
  onCaptureReplacementPhoto: () -> Unit,
  onDownloadPdf: () -> Unit,
  onEditInfo: () -> Unit,
  onApproveOrReject: (String) -> Unit,
  onUpdateAccountStatus: (String) -> Unit,
  onUpdateProgress: (Int, String?) -> Unit,
  onAssignCourse: (Programme) -> Unit,
  onDelete: () -> Unit,
) {
  var progressSlider by remember(student.courseProgressPercent) {
    mutableFloatStateOf(student.courseProgressPercent.toFloat())
  }
  var assignExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${student.fullName} (${student.studentIdNumber})") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        val photoBmp = remember(student.passportPhotoBase64) {
          ImageCompressionUtil.decodeBase64ToBitmap(student.passportPhotoBase64)
        }
        if (photoBmp != null) {
          Image(
            bitmap = photoBmp.asImageBitmap(),
            contentDescription = "Student passport photograph",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)).align(Alignment.CenterHorizontally),
          )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(onClick = onDownloadPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save Photo", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(onClick = onPickReplacementPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Replace", style = MaterialTheme.typography.labelSmall)
          }
          OutlinedButton(onClick = onCaptureReplacementPhoto, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Camera", style = MaterialTheme.typography.labelSmall)
          }
        }

        Text("Email: ${student.email}", style = MaterialTheme.typography.bodySmall)
        Text("Phone: ${student.phoneNumber}", style = MaterialTheme.typography.bodySmall)
        Text("Gender / DOB: ${student.gender} • ${student.dateOfBirth}", style = MaterialTheme.typography.bodySmall)
        Text("Address: ${student.residentialAddress}", style = MaterialTheme.typography.bodySmall)
        Text("State / LGA: ${student.stateOfOrigin} / ${student.localGovernmentArea}", style = MaterialTheme.typography.bodySmall)
        Text("Next of Kin: ${student.nextOfKin} (${student.nextOfKinPhone})", style = MaterialTheme.typography.bodySmall)
        Text("Course: ${student.selectedProgrammeTitle} (${student.trainingLevel})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text("Approval: ${student.approvalStatus.uppercase()} • Account: ${student.status.uppercase()}", style = MaterialTheme.typography.bodySmall)
        if (enrollments.isNotEmpty()) {
          val totalPaid = enrollments.sumOf { it.amountPaidNaira }
          val totalBal = enrollments.sumOf { it.outstandingNaira }
          Text("Paid: ${JyCatalogDefaults.formatNaira(totalPaid)} • Balance: ${JyCatalogDefaults.formatNaira(totalBal)}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text("Course Progress: ${progressSlider.toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Slider(
          value = progressSlider,
          onValueChange = { progressSlider = it },
          valueRange = 0f..100f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onUpdateProgress(progressSlider.toInt(), null) },
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
          ) { Text("Save Progress") }
          Button(
            onClick = { onUpdateProgress(100, "graduated") },
            enabled = !isBusy,
            modifier = Modifier.weight(1f),
          ) { Text("Mark Completed") }
        }

        if (programmes.isNotEmpty()) {
          ExposedDropdownMenuBox(expanded = assignExpanded, onExpandedChange = { assignExpanded = it }) {
            OutlinedTextField(
              value = "Assign to Another Course...",
              onValueChange = {},
              readOnly = true,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = assignExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = assignExpanded, onDismissRequest = { assignExpanded = false }) {
              programmes.forEach { prog ->
                DropdownMenuItem(
                  text = { Text("${prog.title} (${JyCatalogDefaults.formatNaira(prog.feeNaira)})") },
                  onClick = {
                    assignExpanded = false
                    onAssignCourse(prog)
                  },
                )
              }
            }
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Button(
            onClick = { onApproveOrReject("approved") },
            colors = ButtonDefaults.buttonColors(containerColor = JyEmerald),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Approve")
          }
          OutlinedButton(
            onClick = { onApproveOrReject("rejected") },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = JyCrimson, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reject", color = JyCrimson)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          OutlinedButton(
            onClick = {
              val next = if (student.status == "suspended") "active" else "suspended"
              onUpdateAccountStatus(next)
            },
            modifier = Modifier.weight(1f),
          ) {
            Text(if (student.status == "suspended") "Activate" else "Suspend")
          }
          OutlinedButton(onClick = onEditInfo, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit Info")
          }
        }

        Button(onClick = onDownloadPdf, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Download / Print Registration PDF")
        }

        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
          Icon(Icons.Filled.Delete, contentDescription = null, tint = JyCrimson)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Delete Student Record", color = JyCrimson)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditStudentDialog(
  initial: UserProfile,
  programmes: List<Programme>,
  onDismiss: () -> Unit,
  onSave: (UserProfile) -> Unit,
) {
  var fullName by remember { mutableStateOf(initial.fullName) }
  var phone by remember { mutableStateOf(initial.phoneNumber) }
  var email by remember { mutableStateOf(initial.email) }
  var address by remember { mutableStateOf(initial.residentialAddress) }
  var state by remember { mutableStateOf(initial.stateOfOrigin) }
  var lga by remember { mutableStateOf(initial.localGovernmentArea) }
  var level by remember { mutableStateOf(initial.trainingLevel) }
  var levelExpanded by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Edit Student Information") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true)
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true)
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address") })
        OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State of Origin") }, singleLine = true)
        OutlinedTextField(value = lga, onValueChange = { lga = it }, label = { Text("Local Government Area") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = levelExpanded, onExpandedChange = { levelExpanded = it }) {
          OutlinedTextField(
            value = level,
            onValueChange = {},
            readOnly = true,
            label = { Text("Training Level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
            JyCatalogDefaults.TRAINING_LEVELS.forEach { lvl ->
              DropdownMenuItem(
                text = { Text(lvl) },
                onClick = {
                  level = lvl
                  levelExpanded = false
                },
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (fullName.isNotBlank()) {
            onSave(
              initial.copy(
                fullName = fullName,
                phoneNumber = phone,
                email = email,
                residentialAddress = address,
                stateOfOrigin = state,
                localGovernmentArea = lga,
                trainingLevel = level,
              )
            )
          }
        }
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun AdminRolesAndLogsScreen(
  allUsers: List<UserProfile>,
  programmes: List<Programme>,
  activityLogs: List<ActivityLog>,
  onAssignRole: (String, String, String, String, String, List<String>) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }
  val filteredUsers =
    remember(allUsers, searchQuery) {
      allUsers.filter {
        searchQuery.isBlank() ||
          it.fullName.contains(searchQuery, ignoreCase = true) ||
          it.email.contains(searchQuery, ignoreCase = true)
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("admin_roles_and_logs_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      SectionHeader("Administrator & Instructor Roles")
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        label = { Text("Search user by name or email") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (filteredUsers.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Users Found",
          message = "Registered accounts will appear here for role management.",
        )
      }
    } else {
      items(filteredUsers, key = { "role_${it.userId}" }) { user ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                if (user.role == "admin") Icons.Filled.AdminPanelSettings else Icons.Filled.SupervisorAccount,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(user.fullName, fontWeight = FontWeight.SemiBold)
                Text("${user.email} • ${user.role.uppercase()}", style = MaterialTheme.typography.bodySmall)
              }
              StatusPill(
                user.role.uppercase(),
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (user.role != "admin") {
                OutlinedButton(
                  onClick = {
                    onAssignRole(
                      user.userId,
                      user.fullName,
                      user.email,
                      "admin",
                      "System Administrator",
                      emptyList(),
                    )
                  },
                  modifier = Modifier.weight(1f),
                ) { Text("Grant Admin") }
              }
              if (user.role != "instructor") {
                OutlinedButton(
                  onClick = {
                    onAssignRole(
                      user.userId,
                      user.fullName,
                      user.email,
                      "instructor",
                      "Faculty Instructor",
                      programmes.map { it.id },
                    )
                  },
                  modifier = Modifier.weight(1f),
                ) { Text("Make Instructor") }
              }
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(6.dp))
      SectionHeader("Recent Administrative Audit Log (${activityLogs.size})")
    }

    if (activityLogs.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Activity Recorded",
          message = "Administrative operations will appear in this immutable audit trail.",
        )
      }
    } else {
      items(activityLogs, key = { it.id }) { log ->
        val dateText =
          log.createdAt?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
          } ?: "Just now"
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
              Text(dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(log.details, style = MaterialTheme.typography.bodySmall)
            Text("Actor: ${log.actorName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}
