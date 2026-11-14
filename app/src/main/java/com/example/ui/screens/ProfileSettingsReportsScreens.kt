package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AcademicResult
import com.example.data.model.AttendanceRecord
import com.example.data.model.Certificate
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Payment
import com.example.data.model.Programme
import com.example.data.model.UserProfile
import com.example.ui.components.Base64Avatar
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.util.AppThemeMode
import com.example.util.ExportAndPdfUtil
import com.example.util.ImageCompressionUtil
import kotlinx.coroutines.launch

@Composable
fun StudentProfileScreen(
  profile: UserProfile?,
  currentEmail: String,
  themeMode: AppThemeMode,
  isBusy: Boolean,
  onUpdateProfile: (UserProfile) -> Unit,
  onThemeModeChange: (AppThemeMode) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var editing by remember { mutableStateOf(false) }
  var fullName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
  var phone by remember(profile) { mutableStateOf(profile?.phoneNumber ?: "") }
  var address by remember(profile) { mutableStateOf(profile?.residentialAddress ?: "") }
  var nextOfKin by remember(profile) { mutableStateOf(profile?.nextOfKin ?: "") }
  var nextOfKinPhone by remember(profile) { mutableStateOf(profile?.nextOfKinPhone ?: "") }
  var passportBase64 by remember(profile) { mutableStateOf(profile?.passportPhotoBase64 ?: "") }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null && profile != null) {
        scope.launch {
          val encoded = ImageCompressionUtil.compressUriToBase64(context, uri, maxDimension = 360, quality = 70)
          if (encoded != null) {
            passportBase64 = encoded
            onUpdateProfile(profile.copy(passportPhotoBase64 = encoded))
          }
        }
      }
    }

  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("profile_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    JyGradientHeaderCard(
      title = profile?.fullName?.ifBlank { "Account Profile" } ?: "Account Profile",
      subtitle = "${profile?.studentIdNumber ?: "Staff / Admin"} • $currentEmail",
      badgeText = (profile?.role ?: "USER").uppercase(),
    ) {
      Spacer(modifier = Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Base64Avatar(
          base64 = passportBase64,
          name = profile?.fullName ?: currentEmail,
          size = 64.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
          Text(
            profile?.selectedProgrammeTitle?.ifBlank { "JY-Computers Portal" } ?: "JY-Computers Portal",
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            "Tap below to update photo or personal details",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
          )
        }
      }
    }

    if (profile != null) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Personal Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { editing = !editing }) {
              Icon(Icons.Filled.Edit, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (editing) "Cancel" else "Edit")
            }
          }

          if (!editing) {
            ProfileFieldRow("Student ID", profile.studentIdNumber)
            ProfileFieldRow("Programme", profile.selectedProgrammeTitle)
            ProfileFieldRow("Gender / DOB", "${profile.gender} • ${profile.dateOfBirth}")
            ProfileFieldRow("Phone", profile.phoneNumber)
            ProfileFieldRow("Address", profile.residentialAddress)
            ProfileFieldRow("State of Origin", profile.stateOfOrigin)
            ProfileFieldRow("Next of Kin", "${profile.nextOfKin} (${profile.nextOfKinPhone})")
            ProfileFieldRow("Registration Date", profile.registrationDate)
            OutlinedButton(
              onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Update Passport Photograph")
            }
          } else {
            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nextOfKin, onValueChange = { nextOfKin = it }, label = { Text("Next of Kin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nextOfKinPhone, onValueChange = { nextOfKinPhone = it }, label = { Text("Next of Kin Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
              onClick = {
                onUpdateProfile(
                  profile.copy(
                    fullName = fullName,
                    phoneNumber = phone,
                    residentialAddress = address,
                    nextOfKin = nextOfKin,
                    nextOfKinPhone = nextOfKinPhone,
                    passportPhotoBase64 = passportBase64,
                  )
                )
                editing = false
              },
              enabled = !isBusy,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Filled.Save, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Profile Changes")
            }
          }
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Appearance & Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AppThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = themeMode == mode,
              onClick = { onThemeModeChange(mode) },
              label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
          }
        }
      }
    }

    OutlinedButton(
      onClick = onSignOut,
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button"),
    ) {
      Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Sign Out")
    }
  }
}

@Composable
private fun ProfileFieldRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}

@Composable
fun AdminSettingsScreen(
  settings: InstitutionSettings,
  allUsers: List<UserProfile>,
  programmes: List<Programme>,
  isBusy: Boolean,
  onSaveSettings: (InstitutionSettings) -> Unit,
  onSeedDefaults: () -> Unit,
  onAssignRole: (String, String, String, String, String, List<String>) -> Unit,
) {
  var name by remember(settings) { mutableStateOf(settings.institutionName) }
  var motto by remember(settings) { mutableStateOf(settings.motto) }
  var phone by remember(settings) { mutableStateOf(settings.contactPhone) }
  var email by remember(settings) { mutableStateOf(settings.email) }
  var address by remember(settings) { mutableStateOf(settings.physicalAddress) }
  var opayBank by remember(settings) { mutableStateOf(settings.opayBankName) }
  var opayAccName by remember(settings) { mutableStateOf(settings.opayAccountName) }
  var opayAccNum by remember(settings) { mutableStateOf(settings.opayAccountNumber) }
  var opayInstr by remember(settings) { mutableStateOf(settings.opayInstructions) }
  var onlineUrl by remember(settings) { mutableStateOf(settings.onlinePaymentUrl) }
  var session by remember(settings) { mutableStateOf(settings.activeSession) }
  var sigName by remember(settings) { mutableStateOf(settings.signatoryName) }
  var sigTitle by remember(settings) { mutableStateOf(settings.signatoryTitle) }

  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("admin_settings_screen"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    SectionHeader("Institution & Branding")
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Institution Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = motto, onValueChange = { motto = it }, label = { Text("Motto / Tagline") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Contact Phone Numbers") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Official Email") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Physical Address") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = session, onValueChange = { session = it }, label = { Text("Active Academic Session") }, modifier = Modifier.fillMaxWidth())

    SectionHeader("OPay & Online Payment Configuration")
    OutlinedTextField(value = opayBank, onValueChange = { opayBank = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = opayAccName, onValueChange = { opayAccName = it }, label = { Text("OPay Account Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = opayAccNum, onValueChange = { opayAccNum = it }, label = { Text("OPay Account Number") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = opayInstr, onValueChange = { opayInstr = it }, label = { Text("Transfer Instructions") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = onlineUrl, onValueChange = { onlineUrl = it }, label = { Text("Online Checkout URL (Optional Public Link)") }, modifier = Modifier.fillMaxWidth())

    SectionHeader("Certificate Signatory")
    OutlinedTextField(value = sigName, onValueChange = { sigName = it }, label = { Text("Authorized Signatory Name") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = sigTitle, onValueChange = { sigTitle = it }, label = { Text("Signatory Title") }, modifier = Modifier.fillMaxWidth())

    Button(
      onClick = {
        onSaveSettings(
          settings.copy(
            institutionName = name,
            motto = motto,
            contactPhone = phone,
            email = email,
            physicalAddress = address,
            opayBankName = opayBank,
            opayAccountName = opayAccName,
            opayAccountNumber = opayAccNum,
            opayInstructions = opayInstr,
            onlinePaymentUrl = onlineUrl,
            activeSession = session,
            signatoryName = sigName,
            signatoryTitle = sigTitle,
          )
        )
      },
      enabled = !isBusy,
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_admin_settings_button"),
    ) {
      Icon(Icons.Filled.Save, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Save Institutional Settings")
    }

    OutlinedButton(
      onClick = onSeedDefaults,
      enabled = !isBusy,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Icon(Icons.Filled.Settings, contentDescription = null)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Initialize Default 9 Programmes & Community Chat")
    }

    SectionHeader("User Role & Instructor Management")
    allUsers.forEach { user ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Filled.SupervisorAccount, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(user.fullName, fontWeight = FontWeight.SemiBold)
            Text("${user.email} • Role: ${user.role.uppercase()}", style = MaterialTheme.typography.bodySmall)
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
              }
            ) { Text("Make Instructor") }
          } else {
            StatusPill(
              "INSTRUCTOR",
              MaterialTheme.colorScheme.primaryContainer,
              MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
fun ReportsAndAnalyticsScreen(
  settings: InstitutionSettings,
  allUsers: List<UserProfile>,
  enrollments: List<Enrollment>,
  payments: List<Payment>,
  attendance: List<AttendanceRecord>,
  results: List<AcademicResult>,
  certificates: List<Certificate>,
) {
  val context = LocalContext.current
  val reportTypes =
    listOf(
      "Student Registration",
      "Course Enrolment",
      "Payment Report",
      "Outstanding Payments",
      "Attendance Report",
      "Academic Performance",
      "Graduation Report",
    )
  var selectedReport by remember { mutableStateOf(reportTypes.first()) }

  val (headers, rows) =
    remember(selectedReport, allUsers, enrollments, payments, attendance, results, certificates) {
      when (selectedReport) {
        "Student Registration" ->
          listOf("Student ID", "Full Name", "Programme", "Status") to
            allUsers.map { listOf(it.studentIdNumber, it.fullName, it.selectedProgrammeTitle, it.status) }
        "Course Enrolment" ->
          listOf("Student", "Course", "Session", "Status") to
            enrollments.map { listOf(it.studentName, it.programmeTitle, it.session, it.status) }
        "Payment Report" ->
          listOf("Reference", "Student", "Amount", "Status") to
            payments.map {
              listOf(it.reference, it.studentName, JyCatalogDefaults.formatNaira(it.amountNaira), it.status)
            }
        "Outstanding Payments" ->
          listOf("Student", "Course", "Fee", "Balance") to
            enrollments
              .filter { it.outstandingNaira > 0 }
              .map {
                listOf(
                  it.studentName,
                  it.programmeTitle,
                  JyCatalogDefaults.formatNaira(it.totalFeeNaira),
                  JyCatalogDefaults.formatNaira(it.outstandingNaira),
                )
              }
        "Attendance Report" ->
          listOf("Date", "Student", "Course", "Status") to
            attendance.map { listOf(it.dateKey, it.studentName, it.programmeTitle, it.status) }
        "Academic Performance" ->
          listOf("Student", "Course", "Score", "Grade") to
            results.map {
              listOf(it.studentName, it.programmeTitle, "${it.totalScore}/100", it.grade)
            }
        else ->
          listOf("Student", "Student ID", "Programme", "Verify Code") to
            certificates.map {
              listOf(it.studentName, it.studentIdNumber, it.programmeTitle, it.verificationCode)
            }
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("reports_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      JyGradientHeaderCard(
        title = "Reports & Data Export",
        subtitle = "Generate institutional PDF and Excel-compatible CSV reports",
        badgeText = "${rows.size} Rows",
      )
    }

    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(reportTypes) { rt ->
          FilterChip(
            selected = selectedReport == rt,
            onClick = { selectedReport = rt },
            label = { Text(rt) },
          )
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = {
            val file = ExportAndPdfUtil.generateReportPdf(context, selectedReport, headers, rows, settings)
            ExportAndPdfUtil.shareFile(context, file, "application/pdf", "Export $selectedReport PDF")
          },
          modifier = Modifier.weight(1f).testTag("export_pdf_button"),
        ) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Export PDF")
        }
        OutlinedButton(
          onClick = {
            val file = ExportAndPdfUtil.generateCsvReport(context, selectedReport, headers, rows)
            ExportAndPdfUtil.shareFile(context, file, "text/csv", "Export $selectedReport Excel/CSV")
          },
          modifier = Modifier.weight(1f).testTag("export_csv_button"),
        ) {
          Icon(Icons.Filled.TableChart, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Excel / CSV")
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
      ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          headers.forEach { h ->
            Text(
              h,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }

    items(rows) { row ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
          row.forEach { cell ->
            Text(
              cell.ifBlank { "-" },
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}
