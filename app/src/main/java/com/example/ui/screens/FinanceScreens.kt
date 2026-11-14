package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.Enrollment
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.JyServiceItem
import com.example.data.model.Payment
import com.example.data.model.ServiceRequest
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyBusinessFooter
import com.example.ui.components.MetricStatCard
import com.example.ui.components.OfficialOpayBankCard
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
import com.example.util.ImageCompressionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
  profile: UserProfile?,
  settings: InstitutionSettings,
  enrollments: List<Enrollment>,
  serviceRequests: List<ServiceRequest>,
  services: List<JyServiceItem>,
  payments: List<Payment>,
  activeRole: String,
  isBusy: Boolean,
  preselectedEnrollmentId: String? = null,
  preselectedServiceRequestId: String? = null,
  onClearPreselection: () -> Unit = {},
  onSubmitOnlinePayment: (Enrollment?, ServiceRequest?, Long) -> Unit,
  onSubmitTransferPayment: (
    enrollment: Enrollment?,
    serviceRequest: ServiceRequest?,
    amountNaira: Long,
    method: String,
    reference: String,
    paymentDate: String,
    senderName: String,
    senderBank: String,
    evidenceBase64: String,
  ) -> Unit,
  onAdminRecordPayment: (Enrollment?, ServiceRequest?, Long, String, String, String, String) -> Unit,
  onAdminReviewPayment: (Payment, Boolean, String) -> Unit,
  onNavigateToServices: () -> Unit = {},
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isAdmin = activeRole == "admin"

  // Tabs: 0 = Pay to Business Account (OPay), 1 = Bank Transfer (Nigerian Banks), 2 = Online Gateway, [3 = Admin Verify, 4 = Ledger & Cash]
  var selectedTab by remember {
    mutableIntStateOf(
      if (preselectedEnrollmentId != null || preselectedServiceRequestId != null) 0
      else if (isAdmin) 3
      else 0
    )
  }

  var targetMode by remember {
    mutableStateOf(
      when {
        preselectedServiceRequestId != null -> "service"
        preselectedEnrollmentId != null -> "course"
        enrollments.isNotEmpty() -> "course"
        serviceRequests.isNotEmpty() -> "service"
        else -> "course"
      }
    )
  }

  var selectedEnrollment by remember(enrollments) {
    mutableStateOf(
      enrollments.firstOrNull { it.id == preselectedEnrollmentId } ?: enrollments.firstOrNull()
    )
  }
  var selectedRequest by remember(serviceRequests) {
    mutableStateOf(
      serviceRequests.firstOrNull { it.id == preselectedServiceRequestId } ?: serviceRequests.firstOrNull()
    )
  }

  LaunchedEffect(preselectedEnrollmentId, preselectedServiceRequestId, enrollments, serviceRequests) {
    if (preselectedServiceRequestId != null) {
      val match = serviceRequests.firstOrNull { it.id == preselectedServiceRequestId }
      if (match != null) {
        targetMode = "service"
        selectedRequest = match
        if (selectedTab >= 3) selectedTab = 0
        onClearPreselection()
      }
    } else if (preselectedEnrollmentId != null) {
      val match = enrollments.firstOrNull { it.id == preselectedEnrollmentId }
      if (match != null) {
        targetMode = "course"
        selectedEnrollment = match
        if (selectedTab >= 3) selectedTab = 0
        onClearPreselection()
      }
    }
  }

  var targetDropdownExpanded by remember { mutableStateOf(false) }

  val activeServiceItem =
    remember(targetMode, selectedRequest, services) {
      if (targetMode == "service" && selectedRequest != null) {
        services.firstOrNull { it.id == selectedRequest!!.serviceId }
      } else null
    }
  val servicePaymentEnabled = activeServiceItem?.paymentEnabled ?: true
  val allowPartialForSelection =
    settings.allowPartialPayment && (activeServiceItem?.allowPartialPayment ?: true)
  val paymentsOpenForSelection = settings.paymentsEnabled && servicePaymentEnabled

  val activeTotalFee =
    if (targetMode == "service") selectedRequest?.totalFeeNaira ?: 0L
    else selectedEnrollment?.totalFeeNaira ?: 0L
  val activePaid =
    if (targetMode == "service") selectedRequest?.amountPaidNaira ?: 0L
    else selectedEnrollment?.amountPaidNaira ?: 0L
  val activeOutstanding =
    if (targetMode == "service") selectedRequest?.outstandingNaira ?: 0L
    else selectedEnrollment?.outstandingNaira ?: 0L
  val defaultPayable = activeOutstanding.takeIf { it > 0L } ?: activeTotalFee

  var amountInput by remember(targetMode, selectedEnrollment, selectedRequest) {
    mutableStateOf(defaultPayable.takeIf { it > 0L }?.toString() ?: "")
  }
  val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
  var paymentDate by remember { mutableStateOf(todayStr) }
  var transferRef by remember { mutableStateOf("") }
  var senderName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
  var senderBank by remember { mutableStateOf(JyCatalogDefaults.NIGERIAN_BANKS.first()) }
  var bankDropdownExpanded by remember { mutableStateOf(false) }
  var evidenceBase64 by remember { mutableStateOf("") }

  var adminSearchQuery by remember { mutableStateOf("") }
  var adminStatusFilter by remember { mutableStateOf("all") }
  var adminCategoryFilter by remember { mutableStateOf("All") }
  var showRecordPaymentDialog by remember { mutableStateOf(false) }

  val evidencePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val res = ImageCompressionUtil.validateAndCompressUri(context, uri, maxDimension = 540, quality = 70)
          if (res.base64 != null) {
            evidenceBase64 = res.base64
            onNotify("Payment evidence attached.")
          } else {
            onNotify(res.errorMessage ?: "Invalid receipt file.")
          }
        }
      }
    }

  if (showRecordPaymentDialog && (enrollments.isNotEmpty() || serviceRequests.isNotEmpty())) {
    AdminRecordPaymentDialog(
      enrollments = enrollments,
      serviceRequests = serviceRequests,
      onDismiss = { showRecordPaymentDialog = false },
      onRecord = { enr, req, amt, method, ref, date, remarks ->
        onAdminRecordPayment(enr, req, amt, method, ref, date, remarks)
        showRecordPaymentDialog = false
      },
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Direct OPay") },
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("Bank Transfer") },
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = { Text("Online Pay") },
      )
      if (isAdmin) {
        val pendingCount = payments.count { it.status == "pending" }
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("Verify ($pendingCount)") },
        )
        Tab(
          selected = selectedTab == 4,
          onClick = { selectedTab = 4 },
          text = { Text("Ledger & Cash") },
        )
      }
    }

    if (selectedTab == 3 && isAdmin) {
      val confirmedSum = remember(payments) { payments.filter { it.status == "approved" }.sumOf { it.amountNaira } }
      val approvedCount = remember(payments) { payments.count { it.status == "approved" } }
      val pendingPayments = remember(payments) { payments.filter { it.status == "pending" } }
      val rejectedCount = remember(payments) { payments.count { it.status == "rejected" } }
      val outstandingSum =
        remember(enrollments, serviceRequests) {
          enrollments.sumOf { it.outstandingNaira } + serviceRequests.sumOf { it.outstandingNaira }
        }
      val categoryBreakdown =
        remember(payments) {
          val palette = listOf(JyRoyalBlue, JyTeal, JyEmerald, JyBrightBlue, JyGold)
          JyCatalogDefaults.SERVICE_CATEGORIES.mapIndexed { idx, cat ->
            val sum =
              payments
                .filter { it.status == "approved" && it.serviceCategory.equals(cat, ignoreCase = true) }
                .sumOf { it.amountNaira }
                .toInt()
            Triple(cat, sum, palette[idx % palette.size])
          }.filter { it.second > 0 }
        }

      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_payment_verify_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatCard(
              label = "Confirmed Revenue",
              value = JyCatalogDefaults.formatNaira(confirmedSum),
              icon = Icons.Filled.CheckCircle,
              accentColor = JyEmerald,
              modifier = Modifier.weight(1f),
            )
            MetricStatCard(
              label = "Total Outstanding",
              value = JyCatalogDefaults.formatNaira(outstandingSum),
              icon = Icons.Filled.AccountBalanceWallet,
              accentColor = JyCrimson,
              modifier = Modifier.weight(1f),
            )
          }
        }

        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricStatCard(
              label = "Confirmed",
              value = approvedCount.toString(),
              icon = Icons.Filled.Payments,
              accentColor = JyRoyalBlue,
              modifier = Modifier.weight(1f),
            )
            MetricStatCard(
              label = "Pending",
              value = pendingPayments.size.toString(),
              icon = Icons.Filled.PendingActions,
              accentColor = JyGold,
              modifier = Modifier.weight(1f),
            )
            MetricStatCard(
              label = "Rejected",
              value = rejectedCount.toString(),
              icon = Icons.Filled.Close,
              accentColor = JyCrimson,
              modifier = Modifier.weight(1f),
            )
          }
        }

        if (categoryBreakdown.isNotEmpty()) {
          item {
            SimpleDistributionChartCard(
              title = "Confirmed Revenue by Service Category (₦)",
              items = categoryBreakdown,
            )
          }
        }

        if (pendingPayments.isEmpty()) {
          item {
            EmptyStateCard(
              title = "No Pending Payments",
              message = "All submitted OPay transfers, bank transfers, and gateway references have been reviewed.",
            )
          }
        } else {
          items(pendingPayments, key = { it.id }) { pay ->
            var remarkText by remember { mutableStateOf("") }
            val proofBitmap = remember(pay.evidenceBase64) { ImageCompressionUtil.decodeBase64ToBitmap(pay.evidenceBase64) }
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(pay.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${pay.studentIdNumber} • ${pay.programmeTitle}", style = MaterialTheme.typography.bodySmall)
                    if (pay.invoiceNumber.isNotBlank()) {
                      Text(
                        "Invoice: ${pay.invoiceNumber} • ${pay.serviceCategory}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                  }
                  Text(
                    JyCatalogDefaults.formatNaira(pay.amountNaira),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text(
                  "Method: ${pay.method} • Bank: ${pay.senderBank.ifBlank { "OPay" }} • Ref: ${pay.reference} • Date: ${pay.paymentDate.ifBlank { "-" }}",
                  style = MaterialTheme.typography.bodySmall,
                )
                if (pay.senderName.isNotBlank()) {
                  Text("Sender: ${pay.senderName}", style = MaterialTheme.typography.bodySmall)
                }
                if (proofBitmap != null) {
                  Image(
                    bitmap = proofBitmap.asImageBitmap(),
                    contentDescription = "Uploaded payment proof",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)),
                  )
                }
                OutlinedTextField(
                  value = remarkText,
                  onValueChange = { remarkText = it },
                  label = { Text("Verification Notes (optional for approve, recommended for reject)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Button(
                    onClick = { onAdminReviewPayment(pay, true, remarkText.ifBlank { "Verified & Approved" }) },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = JyEmerald),
                    modifier = Modifier.weight(1f).testTag("approve_payment_${pay.id}"),
                  ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Approve")
                  }
                  OutlinedButton(
                    onClick = { onAdminReviewPayment(pay, false, remarkText.ifBlank { "Reference not confirmed" }) },
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).testTag("reject_payment_${pay.id}"),
                  ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = JyCrimson)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reject", color = JyCrimson)
                  }
                }
              }
            }
          }
        }
      }
    } else if (selectedTab == 4 && isAdmin) {
      val enrollmentPartialSet = remember(enrollments) { enrollments.filter { it.amountPaidNaira > 0 && it.outstandingNaira > 0 }.map { it.id }.toSet() }
      val requestPartialSet = remember(serviceRequests) { serviceRequests.filter { it.paymentStatus == "partial" }.map { it.id }.toSet() }

      val filteredPayments =
        remember(payments, adminSearchQuery, adminStatusFilter, adminCategoryFilter, enrollmentPartialSet, requestPartialSet) {
          payments.filter { p ->
            val matchesSearch =
              adminSearchQuery.isBlank() ||
                p.studentName.contains(adminSearchQuery, ignoreCase = true) ||
                p.studentIdNumber.contains(adminSearchQuery, ignoreCase = true) ||
                p.reference.contains(adminSearchQuery, ignoreCase = true) ||
                p.invoiceNumber.contains(adminSearchQuery, ignoreCase = true) ||
                p.programmeTitle.contains(adminSearchQuery, ignoreCase = true)
            val matchesStatus =
              when (adminStatusFilter) {
                "all" -> true
                "partial" -> (p.enrollmentId in enrollmentPartialSet) || (p.serviceRequestId in requestPartialSet)
                else -> p.status == adminStatusFilter
              }
            val matchesCategory =
              adminCategoryFilter == "All" ||
                p.serviceCategory.equals(adminCategoryFilter, ignoreCase = true)
            matchesSearch && matchesStatus && matchesCategory
          }
        }
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("admin_payment_ledger_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { showRecordPaymentDialog = true },
              enabled = (enrollments.isNotEmpty() || serviceRequests.isNotEmpty()) && !isBusy,
              modifier = Modifier.weight(1f).testTag("record_cash_payment_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Record Cash / OPay")
            }
            OutlinedButton(
              onClick = {
                val headers = listOf("Reference", "Invoice", "Customer", "Category", "Service/Course", "Amount", "Bank/Method", "Status", "Date")
                val rows =
                  filteredPayments.map {
                    listOf(
                      it.reference,
                      it.invoiceNumber,
                      it.studentName,
                      it.serviceCategory,
                      it.programmeTitle,
                      JyCatalogDefaults.formatNaira(it.amountNaira),
                      "${it.senderBank.ifBlank { "OPay" }} (${it.method})",
                      it.status,
                      it.paymentDate,
                    )
                  }
                val csv = ExportAndPdfUtil.generateCsvReport(context, "JY_Payments_Ledger", headers, rows)
                ExportAndPdfUtil.shareFile(context, csv, "text/csv", "Export Payments Ledger CSV")
              },
              modifier = Modifier.testTag("export_payments_csv_button"),
            ) {
              Icon(Icons.Filled.TableChart, contentDescription = null)
              Spacer(modifier = Modifier.width(4.dp))
              Text("CSV")
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = adminSearchQuery,
            onValueChange = { adminSearchQuery = it },
            label = { Text("Search name, ID, invoice #, or reference") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "approved", "pending", "rejected", "partial").forEach { st ->
              item {
                FilterChip(
                  selected = adminStatusFilter == st,
                  onClick = { adminStatusFilter = st },
                  label = { Text(st.replaceFirstChar { it.uppercase() }) },
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("All") + JyCatalogDefaults.SERVICE_CATEGORIES) { cat ->
              FilterChip(
                selected = adminCategoryFilter == cat,
                onClick = { adminCategoryFilter = cat },
                label = { Text(cat) },
              )
            }
          }
        }
        if (filteredPayments.isEmpty()) {
          item {
            EmptyStateCard(
              title = "No Matching Transactions",
              message = "Adjust the search query, status, or category filter to view matching payments.",
            )
          }
        } else {
          items(filteredPayments, key = { "adm_pay_${it.id}" }) { pay ->
            PaymentRecordCard(pay = pay, settings = settings)
          }
        }
      }
    } else {
      val parsedAmount = amountInput.toLongOrNull() ?: 0L
      Column(
        modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (enrollments.isEmpty() && serviceRequests.isEmpty()) {
          OfficialOpayBankCard(settings = settings, onCopied = onNotify, showLaunchBankButton = true)
          EmptyStateCard(
            title = "No Active Course or Service Invoice",
            message = "Register for a training course or request any JY-Computers service to generate an invoice and submit payment.",
          )
          Button(
            onClick = onNavigateToServices,
            modifier = Modifier.fillMaxWidth().testTag("go_to_services_from_payments"),
          ) {
            Text("Browse Services & Request Invoice")
          }
        } else {
          if (profile != null) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("Payer: ${profile.fullName}", fontWeight = FontWeight.SemiBold)
                Text(
                  "Reg No: ${profile.studentIdNumber.ifBlank { "JYC-CLIENT" }} • ${profile.phoneNumber.ifBlank { profile.email }}",
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = targetMode == "course",
              onClick = {
                targetMode = "course"
                if (selectedEnrollment == null) selectedEnrollment = enrollments.firstOrNull()
              },
              enabled = enrollments.isNotEmpty(),
              label = { Text("Training Courses (${enrollments.size})") },
              modifier = Modifier.weight(1f).testTag("checkout_mode_course"),
            )
            FilterChip(
              selected = targetMode == "service",
              onClick = {
                targetMode = "service"
                if (selectedRequest == null) selectedRequest = serviceRequests.firstOrNull()
              },
              enabled = serviceRequests.isNotEmpty(),
              label = { Text("Services (${serviceRequests.size})") },
              modifier = Modifier.weight(1f).testTag("checkout_mode_service"),
            )
          }

          val targetLabel =
            if (targetMode == "service") {
              selectedRequest?.let {
                "${it.serviceTitle} (${it.invoiceNumber} • Bal: ${JyCatalogDefaults.formatNaira(it.outstandingNaira)})"
              } ?: "Select Service Invoice"
            } else {
              selectedEnrollment?.let {
                "${it.programmeTitle} (Bal: ${JyCatalogDefaults.formatNaira(it.outstandingNaira)})"
              } ?: "Select Course Invoice"
            }

          ExposedDropdownMenuBox(
            expanded = targetDropdownExpanded,
            onExpandedChange = { targetDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = targetLabel,
              onValueChange = {},
              readOnly = true,
              label = { Text(if (targetMode == "service") "Select Service Invoice" else "Select Course Invoice") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetDropdownExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = targetDropdownExpanded,
              onDismissRequest = { targetDropdownExpanded = false },
            ) {
              if (targetMode == "service") {
                serviceRequests.forEach { req ->
                  DropdownMenuItem(
                    text = {
                      Text("${req.serviceTitle} (${req.invoiceNumber}) — Bal ${JyCatalogDefaults.formatNaira(req.outstandingNaira)}")
                    },
                    onClick = {
                      selectedRequest = req
                      amountInput = (req.outstandingNaira.takeIf { it > 0 } ?: req.totalFeeNaira).toString()
                      targetDropdownExpanded = false
                    },
                  )
                }
              } else {
                enrollments.forEach { enr ->
                  DropdownMenuItem(
                    text = {
                      Text("${enr.programmeTitle} — Bal ${JyCatalogDefaults.formatNaira(enr.outstandingNaira)}")
                    },
                    onClick = {
                      selectedEnrollment = enr
                      amountInput = (enr.outstandingNaira.takeIf { it > 0 } ?: enr.totalFeeNaira).toString()
                      targetDropdownExpanded = false
                    },
                  )
                }
              }
            }
          }

          if (activeTotalFee > 0L || activePaid > 0L) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    "Fee: ${JyCatalogDefaults.formatNaira(activeTotalFee)} • Paid: ${JyCatalogDefaults.formatNaira(activePaid)}",
                    style = MaterialTheme.typography.bodySmall,
                  )
                  Text(
                    "Balance Due: ${JyCatalogDefaults.formatNaira(activeOutstanding)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                OutlinedButton(
                  onClick = {
                    val file =
                      if (targetMode == "service" && selectedRequest != null) {
                        val req = selectedRequest!!
                        ExportAndPdfUtil.generateInvoicePdf(
                          context = context,
                          customerName = req.customerName,
                          customerPhone = req.customerPhone,
                          invoiceNumber = req.invoiceNumber,
                          serviceTitle = req.serviceTitle,
                          serviceCategory = req.serviceCategory,
                          requestNumber = req.requestNumber,
                          totalFeeNaira = req.totalFeeNaira,
                          amountPaidNaira = req.amountPaidNaira,
                          invoiceDate = req.invoiceDate,
                          settings = settings,
                        )
                      } else if (selectedEnrollment != null) {
                        val enr = selectedEnrollment!!
                        ExportAndPdfUtil.generateInvoicePdf(
                          context = context,
                          customerName = enr.studentName,
                          customerPhone = profile?.phoneNumber.orEmpty(),
                          invoiceNumber = "INV-TRN-${enr.id.take(6).uppercase()}",
                          serviceTitle = enr.programmeTitle,
                          serviceCategory = "Training Courses",
                          requestNumber = enr.studentIdNumber,
                          totalFeeNaira = enr.totalFeeNaira,
                          amountPaidNaira = enr.amountPaidNaira,
                          invoiceDate = todayStr,
                          settings = settings,
                        )
                      } else null
                    if (file != null) {
                      ExportAndPdfUtil.shareFile(context, file, "application/pdf", "Share Official Invoice PDF")
                    }
                  },
                  modifier = Modifier.testTag("checkout_download_invoice_button"),
                ) {
                  Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Invoice PDF")
                }
              }
            }
          }

          if (!paymentsOpenForSelection) {
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                if (!settings.paymentsEnabled) {
                  "Payments are currently paused by JY-Computers Administration. Contact ${settings.contactPhone.ifBlank { JyCatalogDefaults.OFFICIAL_PRIMARY_PHONE }}."
                } else {
                  "Electronic payment is disabled for this service. Please pay at the JY-Computers office."
                },
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          OutlinedTextField(
            value = amountInput,
            onValueChange = { if (allowPartialForSelection) amountInput = it.filter { c -> c.isDigit() } },
            readOnly = !allowPartialForSelection,
            label = {
              Text(
                if (allowPartialForSelection) "Amount to Pay (₦) — Partial Payment Allowed *"
                else "Full Amount Payable (₦) *"
              )
            },
            supportingText =
              if (!allowPartialForSelection) {
                { Text("Full payment is required for this service.") }
              } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
          )

          if (allowPartialForSelection && activeOutstanding > 0L) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = parsedAmount == activeOutstanding,
                onClick = { amountInput = activeOutstanding.toString() },
                label = { Text("Full (${JyCatalogDefaults.formatNaira(activeOutstanding)})") },
              )
              if (activeOutstanding >= 2000L) {
                val half = (activeOutstanding / 2L).coerceAtLeast(1000L)
                FilterChip(
                  selected = parsedAmount == half,
                  onClick = { amountInput = half.toString() },
                  label = { Text("50% (${JyCatalogDefaults.formatNaira(half)})") },
                )
              }
            }
          }

          OfficialOpayBankCard(
            settings = settings,
            onCopied = onNotify,
            amountPayableNaira = parsedAmount.takeIf { it > 0L } ?: activeOutstanding,
            showLaunchBankButton = selectedTab == 0,
          )

          val selectedEnrOrNull = if (targetMode == "course") selectedEnrollment else null
          val selectedReqOrNull = if (targetMode == "service") selectedRequest else null
          val hasTarget = selectedEnrOrNull != null || selectedReqOrNull != null

          when (selectedTab) {
            0 -> {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    "Option 1: Confirm Direct Payment to Business Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    "After sending to OPay (${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER} • ${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME}), enter your transfer details below. Receipts unlock once Administrator verifies the transaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Payment Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("opay_date_input"),
                  )
                  OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Sender Account Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("opay_sender_input"),
                  )
                  OutlinedTextField(
                    value = transferRef,
                    onValueChange = { transferRef = it },
                    label = { Text("OPay / Transfer Session Reference *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("opay_reference_input"),
                  )
                  EvidenceUploadRow(
                    evidenceBase64 = evidenceBase64,
                    onLaunchPicker = {
                      evidencePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    onClear = { evidenceBase64 = "" },
                  )
                  Button(
                    onClick = {
                      val amt = amountInput.toLongOrNull() ?: 0L
                      when {
                        !paymentsOpenForSelection -> onNotify("Payments are disabled for this item.")
                        !allowPartialForSelection && activeOutstanding > 0L && amt != activeOutstanding ->
                          onNotify("Full balance of ${JyCatalogDefaults.formatNaira(activeOutstanding)} is required.")
                        amt <= 0L || transferRef.trim().length < 4 || senderName.trim().length < 2 || paymentDate.isBlank() ->
                          onNotify("Enter valid amount, payment date, sender name, and transaction reference.")
                        else -> {
                          onSubmitTransferPayment(
                            selectedEnrOrNull,
                            selectedReqOrNull,
                            amt,
                            "opay_transfer",
                            transferRef,
                            paymentDate,
                            senderName,
                            settings.opayBankName.ifBlank { JyCatalogDefaults.OFFICIAL_OPAY_BANK },
                            evidenceBase64,
                          )
                          transferRef = ""
                          evidenceBase64 = ""
                        }
                      }
                    },
                    enabled = !isBusy && hasTarget && paymentsOpenForSelection,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_opay_button"),
                  ) {
                    Text("Confirm & Submit OPay Payment")
                  }
                }
              }
            }

            1 -> {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text(
                    "Option 2: Bank Transfer (Any Nigerian Bank)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                  )
                  Text(
                    "Transfer from any supported Nigerian bank to OPay • ${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NAME} (${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER}), then submit your transfer proof for admin review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  ExposedDropdownMenuBox(
                    expanded = bankDropdownExpanded,
                    onExpandedChange = { bankDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    OutlinedTextField(
                      value = senderBank,
                      onValueChange = {},
                      readOnly = true,
                      label = { Text("Sender Bank *") },
                      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bankDropdownExpanded) },
                      modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().testTag("bank_transfer_bank_dropdown"),
                    )
                    ExposedDropdownMenu(
                      expanded = bankDropdownExpanded,
                      onDismissRequest = { bankDropdownExpanded = false },
                    ) {
                      JyCatalogDefaults.NIGERIAN_BANKS.forEach { bank ->
                        DropdownMenuItem(
                          text = { Text(bank) },
                          onClick = {
                            senderBank = bank
                            bankDropdownExpanded = false
                          },
                        )
                      }
                    }
                  }
                  OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it },
                    label = { Text("Sender Account Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bank_transfer_sender_input"),
                  )
                  OutlinedTextField(
                    value = transferRef,
                    onValueChange = { transferRef = it },
                    label = { Text("Bank Transfer Reference / NIP Session ID *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bank_transfer_reference_input"),
                  )
                  OutlinedTextField(
                    value = paymentDate,
                    onValueChange = { paymentDate = it },
                    label = { Text("Transfer Date (YYYY-MM-DD) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("bank_transfer_date_input"),
                  )
                  EvidenceUploadRow(
                    evidenceBase64 = evidenceBase64,
                    onLaunchPicker = {
                      evidencePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                      )
                    },
                    onClear = { evidenceBase64 = "" },
                  )
                  Button(
                    onClick = {
                      val amt = amountInput.toLongOrNull() ?: 0L
                      when {
                        !paymentsOpenForSelection -> onNotify("Payments are disabled for this item.")
                        !allowPartialForSelection && activeOutstanding > 0L && amt != activeOutstanding ->
                          onNotify("Full balance of ${JyCatalogDefaults.formatNaira(activeOutstanding)} is required.")
                        amt <= 0L || transferRef.trim().length < 4 || senderName.trim().length < 2 || paymentDate.isBlank() ->
                          onNotify("Enter valid amount, bank, sender name, date, and transaction reference.")
                        else -> {
                          onSubmitTransferPayment(
                            selectedEnrOrNull,
                            selectedReqOrNull,
                            amt,
                            "bank_transfer",
                            transferRef,
                            paymentDate,
                            senderName,
                            senderBank,
                            evidenceBase64,
                          )
                          transferRef = ""
                          evidenceBase64 = ""
                        }
                      }
                    },
                    enabled = !isBusy && hasTarget && paymentsOpenForSelection,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_bank_transfer_button"),
                  ) {
                    Text("Submit Bank Transfer for Verification")
                  }
                }
              }
            }

            else -> {
              Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Online Payment Gateway", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  }
                  Text(
                    "Generates a unique transaction reference and submits to the JY-Computers server verification flow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (settings.onlinePaymentUrl.isNotBlank()) {
                    OutlinedButton(
                      onClick = {
                        try {
                          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.onlinePaymentUrl)))
                        } catch (_: Exception) {
                          onNotify("Unable to open gateway link.")
                        }
                      },
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text("Open Gateway Checkout Portal")
                    }
                  }
                  Button(
                    onClick = {
                      val amt = amountInput.toLongOrNull() ?: 0L
                      if (amt > 0 && hasTarget && paymentsOpenForSelection) {
                        onSubmitOnlinePayment(selectedEnrOrNull, selectedReqOrNull, amt)
                      }
                    },
                    enabled = !isBusy && hasTarget && paymentsOpenForSelection && (amountInput.toLongOrNull() ?: 0L) > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pay_online_button"),
                  ) {
                    Text("Generate Reference & Pay Online")
                  }
                }
              }
            }
          }
        }

        JyBusinessFooter(settings = settings, onNotify = onNotify)
      }
    }
  }
}

@Composable
private fun EvidenceUploadRow(
  evidenceBase64: String,
  onLaunchPicker: () -> Unit,
  onClear: () -> Unit,
) {
  val previewProof = remember(evidenceBase64) { ImageCompressionUtil.decodeBase64ToBitmap(evidenceBase64) }
  if (previewProof != null) {
    Image(
      bitmap = previewProof.asImageBitmap(),
      contentDescription = "Selected payment evidence preview",
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)),
    )
  }

  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(
      onClick = onLaunchPicker,
      modifier = Modifier.weight(1f).testTag("upload_opay_proof_button"),
    ) {
      Icon(Icons.Filled.UploadFile, contentDescription = null)
      Spacer(modifier = Modifier.width(6.dp))
      Text(if (evidenceBase64.isBlank()) "Upload Evidence (≤2MB)" else "Replace Evidence")
    }
    if (evidenceBase64.isNotBlank()) {
      OutlinedButton(onClick = onClear) {
        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove evidence", tint = JyCrimson)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminRecordPaymentDialog(
  enrollments: List<Enrollment>,
  serviceRequests: List<ServiceRequest>,
  onDismiss: () -> Unit,
  onRecord: (Enrollment?, ServiceRequest?, Long, String, String, String, String) -> Unit,
) {
  var targetMode by remember { mutableStateOf(if (enrollments.isNotEmpty()) "course" else "service") }
  var selectedEnr by remember { mutableStateOf(enrollments.firstOrNull()) }
  var selectedReq by remember { mutableStateOf(serviceRequests.firstOrNull()) }
  var targetExpanded by remember { mutableStateOf(false) }

  val initialAmount =
    if (targetMode == "service") {
      (selectedReq?.outstandingNaira?.takeIf { it > 0 } ?: selectedReq?.totalFeeNaira ?: 0L).toString()
    } else {
      (selectedEnr?.outstandingNaira?.takeIf { it > 0 } ?: selectedEnr?.totalFeeNaira ?: 0L).toString()
    }

  var amountStr by remember(targetMode, selectedEnr, selectedReq) { mutableStateOf(initialAmount) }
  var method by remember { mutableStateOf("cash") }
  var ref by remember { mutableStateOf("JYC-OFF-${System.currentTimeMillis() % 100000}") }
  var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
  var remarks by remember { mutableStateOf("Received at JY-Computers Desk") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Cash / Direct Payment") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = targetMode == "course",
            onClick = { targetMode = "course" },
            enabled = enrollments.isNotEmpty(),
            label = { Text("Course") },
          )
          FilterChip(
            selected = targetMode == "service",
            onClick = { targetMode = "service" },
            enabled = serviceRequests.isNotEmpty(),
            label = { Text("Service") },
          )
        }
        ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = it }) {
          OutlinedTextField(
            value =
              if (targetMode == "service") {
                selectedReq?.let { "${it.customerName} — ${it.serviceTitle} (${it.invoiceNumber})" } ?: "Select Service"
              } else {
                selectedEnr?.let { "${it.studentName} — ${it.programmeTitle}" } ?: "Select Enrolment"
              },
            onValueChange = {},
            readOnly = true,
            label = { Text(if (targetMode == "service") "Service Request" else "Student Enrolment") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
            if (targetMode == "service") {
              serviceRequests.forEach { req ->
                DropdownMenuItem(
                  text = { Text("${req.customerName} • ${req.serviceTitle} • Bal ${JyCatalogDefaults.formatNaira(req.outstandingNaira)}") },
                  onClick = {
                    selectedReq = req
                    targetExpanded = false
                  },
                )
              }
            } else {
              enrollments.forEach { enr ->
                DropdownMenuItem(
                  text = { Text("${enr.studentName} (${enr.studentIdNumber}) • Bal ${JyCatalogDefaults.formatNaira(enr.outstandingNaira)}") },
                  onClick = {
                    selectedEnr = enr
                    targetExpanded = false
                  },
                )
              }
            }
          }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item { FilterChip(selected = method == "cash", onClick = { method = "cash" }, label = { Text("Cash") }) }
          item { FilterChip(selected = method == "opay_transfer", onClick = { method = "opay_transfer" }, label = { Text("OPay") }) }
          item { FilterChip(selected = method == "bank_transfer", onClick = { method = "bank_transfer" }, label = { Text("Bank Transfer") }) }
        }
        OutlinedTextField(value = amountStr, onValueChange = { amountStr = it.filter { c -> c.isDigit() } }, label = { Text("Amount (₦)") }, singleLine = true)
        OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text("Receipt / Reference") }, singleLine = true)
        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Payment Date (YYYY-MM-DD)") }, singleLine = true)
        OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val amt = amountStr.toLongOrNull() ?: 0L
          val enr = if (targetMode == "course") selectedEnr else null
          val req = if (targetMode == "service") selectedReq else null
          if (amt > 0 && ref.trim().length >= 4 && (enr != null || req != null)) {
            onRecord(enr, req, amt, method, ref, date, remarks)
          }
        }
      ) { Text("Record Payment") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun PaymentRecordCard(
  pay: Payment,
  settings: InstitutionSettings,
) {
  val context = LocalContext.current
  val dateText =
    pay.paymentDate.ifBlank {
      pay.createdAt?.toDate()?.let {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
      } ?: "Just now"
    }
  val methodLabel =
    when (pay.method) {
      "opay_transfer" -> "OPay Direct Transfer"
      "bank_transfer" -> "Bank Transfer (${pay.senderBank.ifBlank { "Bank" }})"
      "cash" -> "Cash Payment"
      else -> "Online Gateway"
    }
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
        Column(modifier = Modifier.weight(1f)) {
          Text(pay.programmeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "${pay.studentName} (${pay.studentIdNumber}) • ${pay.serviceCategory.ifBlank { "Training Courses" }}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        val (bg, fg) =
          when (pay.status) {
            "approved" -> JyEmeraldContainer to JyEmerald
            "rejected" -> JyCrimsonContainer to JyCrimson
            else -> JyGoldContainer to JyGold
          }
        StatusPill(pay.status.uppercase(), bg, fg)
      }
      Text(
        "${JyCatalogDefaults.formatNaira(pay.amountNaira)} • Ref: ${pay.reference}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        "$methodLabel • $dateText${if (pay.invoiceNumber.isNotBlank()) " • ${pay.invoiceNumber}" else ""}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (pay.verifiedByName.isNotBlank() && pay.status == "approved") {
        Text("Confirmed by: ${pay.verifiedByName}", style = MaterialTheme.typography.labelSmall, color = JyEmerald)
      }
      if (pay.adminRemarks.isNotBlank()) {
        Text("Note: ${pay.adminRemarks}", style = MaterialTheme.typography.bodySmall)
      }
      if (pay.status == "approved") {
        OutlinedButton(
          onClick = {
            val pdfFile = ExportAndPdfUtil.generatePaymentReceiptPdf(context, pay, settings)
            ExportAndPdfUtil.shareFile(
              context,
              pdfFile,
              "application/pdf",
              "Print / Share Official Receipt",
            )
          },
          modifier = Modifier.testTag("download_receipt_${pay.id}"),
        ) {
          Icon(Icons.Filled.Download, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Download Official Receipt (PDF)")
        }
      } else if (pay.status == "pending") {
        Text(
          "Awaiting administrator confirmation. Official PDF receipt unlocks once approved.",
          style = MaterialTheme.typography.labelSmall,
          color = JyGold,
        )
      }
    }
  }
}

@Composable
fun PaymentHistoryScreen(
  payments: List<Payment>,
  settings: InstitutionSettings,
  onNotify: (String) -> Unit = {},
) {
  var filterStatus by remember { mutableStateOf("all") }
  var filterCategory by remember { mutableStateOf("All") }

  val filtered =
    remember(payments, filterStatus, filterCategory) {
      payments.filter { pay ->
        val matchesStatus = filterStatus == "all" || pay.status == filterStatus
        val matchesCategory =
          filterCategory == "All" || pay.serviceCategory.equals(filterCategory, ignoreCase = true)
        matchesStatus && matchesCategory
      }
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("payment_history_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all", "approved", "pending", "rejected").forEach { st ->
          FilterChip(
            selected = filterStatus == st,
            onClick = { filterStatus = st },
            label = { Text(st.replaceFirstChar { it.uppercase() }) },
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(listOf("All") + JyCatalogDefaults.SERVICE_CATEGORIES) { cat ->
          FilterChip(
            selected = filterCategory == cat,
            onClick = { filterCategory = cat },
            label = { Text(cat) },
          )
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Payment Records",
          message = "Your course & service payment transactions and official receipts will appear here.",
        )
      }
    } else {
      items(filtered, key = { it.id }) { pay ->
        PaymentRecordCard(pay = pay, settings = settings)
      }
    }

    item {
      JyBusinessFooter(settings = settings, onNotify = onNotify)
    }
  }
}
