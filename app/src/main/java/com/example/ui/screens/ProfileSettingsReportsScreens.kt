package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import com.example.data.model.UserProfile
import com.example.ui.components.Base64Avatar
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.OfficialJyLogoBadge
import com.example.ui.components.OfficialOpayBankCard
import com.example.ui.components.PassportCameraCaptureDialog
import com.example.ui.components.SectionHeader
import com.example.util.AppThemeMode
import com.example.util.ExportAndPdfUtil
import com.example.util.ImageCompressionUtil
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun StudentProfileScreen(
  profile: UserProfile?,
  settings: InstitutionSettings,
  currentEmail: String,
  themeMode: AppThemeMode,
  isBusy: Boolean,
  onUpdateProfile: (UserProfile) -> Unit,
  onThemeModeChange: (AppThemeMode) -> Unit,
  onNotify: (String) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var editing by remember { mutableStateOf(false) }
  var showCamera by remember { mutableStateOf(false) }
  var fullName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
  var phone by remember(profile) { mutableStateOf(profile?.phoneNumber ?: "") }
  var address by remember(profile) { mutableStateOf(profile?.residentialAddress ?: "") }
  var lga by remember(profile) { mutableStateOf(profile?.localGovernmentArea ?: "") }
  var nextOfKin by remember(profile) { mutableStateOf(profile?.nextOfKin ?: "") }
  var nextOfKinPhone by remember(profile) { mutableStateOf(profile?.nextOfKinPhone ?: "") }
  var passportBase64 by remember(profile) { mutableStateOf(profile?.passportPhotoBase64 ?: "") }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null && profile != null) {
        scope.launch {
          val res = ImageCompressionUtil.validateAndCompressUri(context, uri, maxDimension = 380, quality = 70)
          if (res.base64 != null) {
            passportBase64 = res.base64
            onUpdateProfile(profile.copy(passportPhotoBase64 = res.base64))
          } else {
            onNotify(res.errorMessage ?: "Invalid photograph")
          }
        }
      }
    }

  if (showCamera && profile != null) {
    PassportCameraCaptureDialog(
      onPhotoAccepted = { base64 ->
        passportBase64 = base64
        onUpdateProfile(profile.copy(passportPhotoBase64 = base64))
        showCamera = false
      },
      onDismiss = { showCamera = false },
    )
  }

  Column(
    modifier =
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("profile_screen"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    JyGradientHeaderCard(
      title = profile?.fullName?.ifBlank { "Account Profile" } ?: "Account Profile",
      subtitle = "${profile?.studentIdNumber ?: "Administrator"} • $currentEmail",
      badgeText = (profile?.role ?: "USER").uppercase(),
      showOfficialLogo = true,
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
            "Approval: ${(profile?.approvalStatus ?: "approved").uppercase()} • Progress: ${profile?.courseProgressPercent ?: 0}%",
            style = MaterialTheme.typography.bodySmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
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
            ProfileFieldRow("Registration Number", profile.studentIdNumber)
            ProfileFieldRow("Selected Course", "${profile.selectedProgrammeTitle} (${profile.trainingLevel})")
            ProfileFieldRow("Approval / Account Status", "${profile.approvalStatus.uppercase()} / ${profile.status.uppercase()}")
            ProfileFieldRow("Gender / DOB", "${profile.gender} • ${profile.dateOfBirth}")
            ProfileFieldRow("Phone", profile.phoneNumber)
            ProfileFieldRow("Address", profile.residentialAddress)
            ProfileFieldRow("State / LGA", "${profile.stateOfOrigin} / ${profile.localGovernmentArea}")
            ProfileFieldRow("Next of Kin", "${profile.nextOfKin} (${profile.nextOfKinPhone})")
            ProfileFieldRow("Registration Date", profile.registrationDate)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = {
                  photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Upload Photo")
              }
              OutlinedButton(
                onClick = { showCamera = true },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Camera")
              }
            }
          } else {
            OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Residential Address") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = lga, onValueChange = { lga = it }, label = { Text("Local Government Area") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nextOfKin, onValueChange = { nextOfKin = it }, label = { Text("Next of Kin") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = nextOfKinPhone, onValueChange = { nextOfKinPhone = it }, label = { Text("Next of Kin Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
              onClick = {
                onUpdateProfile(
                  profile.copy(
                    fullName = fullName,
                    phoneNumber = phone,
                    residentialAddress = address,
                    localGovernmentArea = lga,
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
              Text("Save Permitted Profile Changes")
            }
          }
        }
      }
    }

    AboutAndContactCard(settings = settings, onNotify = onNotify)

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
fun AboutAndContactCard(
  settings: InstitutionSettings,
  onNotify: (String) -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        OfficialJyLogoBadge(size = 64.dp, cornerRadius = 12.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text("About & Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(settings.institutionName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
          Text(settings.motto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(settings.physicalAddress, style = MaterialTheme.typography.bodySmall)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(settings.contactPhone, style = MaterialTheme.typography.bodySmall)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(settings.email, style = MaterialTheme.typography.bodySmall)
      }
      OfficialOpayBankCard(settings = settings, onCopied = onNotify)
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
  isBusy: Boolean,
  onSaveSettings: (InstitutionSettings) -> Unit,
  onSeedDefaults: () -> Unit,
  onNotify: (String) -> Unit,
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
    AboutAndContactCard(settings = settings, onNotify = onNotify)

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
      "Active Students",
      "Graduated Students",
      "Course Enrolment",
      "Payment Records",
      "Outstanding Balances",
      "Attendance Report",
      "Academic Performance",
    )
  val periodFilters = listOf("All Time", "Daily", "Weekly", "Monthly", "Annual")
  var selectedReport by remember { mutableStateOf(reportTypes.first()) }
  var selectedPeriod by remember { mutableStateOf("All Time") }

  val nowMs = remember { System.currentTimeMillis() }
  fun inSelectedPeriod(tsSeconds: Long?): Boolean {
    if (selectedPeriod == "All Time" || tsSeconds == null) return true
    val ageMs = nowMs - (tsSeconds * 1000L)
    return when (selectedPeriod) {
      "Daily" -> ageMs <= 24L * 3600L * 1000L
      "Weekly" -> ageMs <= 7L * 24L * 3600L * 1000L
      "Monthly" -> ageMs <= 30L * 24L * 3600L * 1000L
      "Annual" -> ageMs <= 365L * 24L * 3600L * 1000L
      else -> true
    }
  }

  val students = remember(allUsers) { allUsers.filter { it.role == "student" || it.studentIdNumber.isNotBlank() } }

  val (headers, rows) =
    remember(selectedReport, selectedPeriod, students, enrollments, payments, attendance, results, certificates) {
      when (selectedReport) {
        "Student Registration" ->
          listOf("Reg No", "Full Name", "Course", "Level", "Approval") to
            students.filter { inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.studentIdNumber, it.fullName, it.selectedProgrammeTitle, it.trainingLevel, it.approvalStatus)
            }
        "Active Students" ->
          listOf("Reg No", "Full Name", "Course", "Progress", "Phone") to
            students.filter { it.status == "active" && inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.studentIdNumber, it.fullName, it.selectedProgrammeTitle, "${it.courseProgressPercent}%", it.phoneNumber)
            }
        "Graduated Students" ->
          listOf("Reg No", "Full Name", "Course", "Reg Date", "Status") to
            students.filter { it.status == "graduated" && inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.studentIdNumber, it.fullName, it.selectedProgrammeTitle, it.registrationDate, it.status)
            }
        "Course Enrolment" ->
          listOf("Student", "Course", "Session", "Status") to
            enrollments.filter { inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.studentName, it.programmeTitle, it.session, it.status)
            }
        "Payment Records" ->
          listOf("Reference", "Student", "Amount", "Method", "Status") to
            payments.filter { inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.reference, it.studentName, JyCatalogDefaults.formatNaira(it.amountNaira), it.method, it.status)
            }
        "Outstanding Balances" ->
          listOf("Student", "Course", "Fee", "Balance") to
            enrollments
              .filter { it.outstandingNaira > 0 && inSelectedPeriod(it.createdAt?.seconds) }
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
            attendance.filter { inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.dateKey, it.studentName, it.programmeTitle, it.status)
            }
        else ->
          listOf("Student", "Course", "Score", "Grade") to
            results.filter { inSelectedPeriod(it.createdAt?.seconds) }.map {
              listOf(it.studentName, it.programmeTitle, "${it.totalScore}/100", it.grade)
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
        subtitle = "$selectedReport • $selectedPeriod Filter",
        badgeText = "${rows.size} Rows",
        showOfficialLogo = true,
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
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(periodFilters) { p ->
          FilterChip(
            selected = selectedPeriod == p,
            onClick = { selectedPeriod = p },
            label = { Text(p) },
          )
        }
      }
    }

    item {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = {
            val file = ExportAndPdfUtil.generateReportPdf(context, "$selectedReport ($selectedPeriod)", headers, rows, settings)
            ExportAndPdfUtil.shareFile(context, file, "application/pdf", "Print / Share $selectedReport PDF")
          },
          modifier = Modifier.weight(1f).testTag("export_pdf_button"),
        ) {
          Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Print / PDF")
        }
        OutlinedButton(
          onClick = {
            val file = ExportAndPdfUtil.generateCsvReport(context, "${selectedReport}_$selectedPeriod", headers, rows)
            ExportAndPdfUtil.shareFile(context, file, "text/csv", "Download $selectedReport Excel/CSV")
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
