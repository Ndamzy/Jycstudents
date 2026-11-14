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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.data.model.Payment
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.OfficialOpayBankCard
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
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
  profile: UserProfile?,
  settings: InstitutionSettings,
  enrollments: List<Enrollment>,
  payments: List<Payment>,
  activeRole: String,
  isBusy: Boolean,
  onSubmitOnlinePayment: (Enrollment, Long) -> Unit,
  onSubmitOpayTransfer: (Enrollment, Long, String, String, String, String) -> Unit,
  onAdminRecordPayment: (Enrollment, Long, String, String, String, String) -> Unit,
  onAdminReviewPayment: (Payment, Boolean, String) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isAdmin = activeRole == "admin"

  var selectedTab by remember { mutableIntStateOf(if (isAdmin) 2 else 1) }
  var selectedEnrollment by remember(enrollments) { mutableStateOf(enrollments.firstOrNull()) }
  var enrDropdownExpanded by remember { mutableStateOf(false) }

  var amountInput by remember(selectedEnrollment) {
    mutableStateOf(
      (selectedEnrollment?.outstandingNaira?.takeIf { it > 0 } ?: selectedEnrollment?.totalFeeNaira ?: 25000L)
        .toString()
    )
  }
  val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
  var paymentDate by remember { mutableStateOf(todayStr) }
  var opayRef by remember { mutableStateOf("") }
  var senderName by remember(profile) { mutableStateOf(profile?.fullName ?: "") }
  var evidenceBase64 by remember { mutableStateOf("") }
  var adminSearchQuery by remember { mutableStateOf("") }
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

  if (showRecordPaymentDialog && enrollments.isNotEmpty()) {
    AdminRecordPaymentDialog(
      enrollments = enrollments,
      onDismiss = { showRecordPaymentDialog = false },
      onRecord = { enr, amt, method, ref, date, remarks ->
        onAdminRecordPayment(enr, amt, method, ref, date, remarks)
        showRecordPaymentDialog = false
      },
    )
  }

  Column(modifier = Modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = selectedTab) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = { Text("Online Pay") },
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = { Text("OPay Transfer") },
      )
      if (isAdmin) {
        val pendingCount = payments.count { it.status == "pending" }
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Verify ($pendingCount)") },
        )
        Tab(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          text = { Text("All & Cash") },
        )
      }
    }

    if (selectedTab == 2 && isAdmin) {
      val pending = remember(payments) { payments.filter { it.status == "pending" } }
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (pending.isEmpty()) {
          item {
            EmptyStateCard(
              title = "No Pending Payments",
              message = "All submitted OPay transfers and gateway references have been reviewed.",
            )
          }
        } else {
          items(pending, key = { it.id }) { pay ->
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
                  Column {
                    Text(pay.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${pay.studentIdNumber} • ${pay.programmeTitle}", style = MaterialTheme.typography.bodySmall)
                  }
                  Text(
                    JyCatalogDefaults.formatNaira(pay.amountNaira),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text("Method: ${pay.method} • Ref: ${pay.reference} • Date: ${pay.paymentDate.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                if (pay.senderName.isNotBlank()) {
                  Text("Sender: ${pay.senderName}", style = MaterialTheme.typography.bodySmall)
                }
                if (proofBitmap != null) {
                  Image(
                    bitmap = proofBitmap.asImageBitmap(),
                    contentDescription = "Uploaded OPay payment proof",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)),
                  )
                }
                OutlinedTextField(
                  value = remarkText,
                  onValueChange = { remarkText = it },
                  label = { Text("Admin Remarks (optional)") },
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
    } else if (selectedTab == 3 && isAdmin) {
      val filteredPayments =
        remember(payments, adminSearchQuery) {
          payments.filter { p ->
            adminSearchQuery.isBlank() ||
              p.studentName.contains(adminSearchQuery, ignoreCase = true) ||
              p.studentIdNumber.contains(adminSearchQuery, ignoreCase = true) ||
              p.reference.contains(adminSearchQuery, ignoreCase = true) ||
              p.programmeTitle.contains(adminSearchQuery, ignoreCase = true)
          }
        }
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Button(
            onClick = { showRecordPaymentDialog = true },
            enabled = enrollments.isNotEmpty() && !isBusy,
            modifier = Modifier.fillMaxWidth().testTag("record_cash_payment_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Record Cash / Direct OPay Payment")
          }
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = adminSearchQuery,
            onValueChange = { adminSearchQuery = it },
            label = { Text("Search by student name, Reg No, or reference") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        items(filteredPayments, key = { "adm_pay_${it.id}" }) { pay ->
          PaymentRecordCard(pay = pay, settings = settings)
        }
      }
    } else {
      Column(
        modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        OfficialOpayBankCard(settings = settings, onCopied = onNotify)

        if (enrollments.isEmpty()) {
          EmptyStateCard(
            title = "No Active Course Enrolment",
            message = "Register for a course in Course Registration first before paying tuition.",
          )
        } else {
          if (profile != null) {
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text("Student: ${profile.fullName}", fontWeight = FontWeight.SemiBold)
                Text("Registration No: ${profile.studentIdNumber}", style = MaterialTheme.typography.bodySmall)
              }
            }
          }

          ExposedDropdownMenuBox(
            expanded = enrDropdownExpanded,
            onExpandedChange = { enrDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value =
                selectedEnrollment?.let {
                  "${it.programmeTitle} (Balance: ${JyCatalogDefaults.formatNaira(it.outstandingNaira)})"
                } ?: "Select Enrolment",
              onValueChange = {},
              readOnly = true,
              label = { Text("Select Course Invoice") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enrDropdownExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = enrDropdownExpanded,
              onDismissRequest = { enrDropdownExpanded = false },
            ) {
              enrollments.forEach { enr ->
                DropdownMenuItem(
                  text = {
                    Text("${enr.programmeTitle} — Balance ${JyCatalogDefaults.formatNaira(enr.outstandingNaira)}")
                  },
                  onClick = {
                    selectedEnrollment = enr
                    amountInput =
                      (enr.outstandingNaira.takeIf { it > 0 } ?: enr.totalFeeNaira).toString()
                    enrDropdownExpanded = false
                  },
                )
              }
            }
          }

          OutlinedTextField(
            value = amountInput,
            onValueChange = { amountInput = it.filter { c -> c.isDigit() } },
            label = { Text("Amount Paid (₦) *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("payment_amount_input"),
          )

          if (selectedTab == 0) {
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
                  "Generates a unique transaction reference and verifies payment securely with the configured server endpoint.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.onlinePaymentUrl.isNotBlank()) {
                  OutlinedButton(
                    onClick = {
                      context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(settings.onlinePaymentUrl)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text("Open Gateway Checkout Portal")
                  }
                }
                Button(
                  onClick = {
                    val enr = selectedEnrollment ?: return@Button
                    val amt = amountInput.toLongOrNull() ?: 0L
                    if (amt > 0) onSubmitOnlinePayment(enr, amt)
                  },
                  enabled = !isBusy && selectedEnrollment != null && (amountInput.toLongOrNull() ?: 0L) > 0,
                  modifier = Modifier.fillMaxWidth().height(52.dp).testTag("pay_online_button"),
                ) {
                  Text("Generate Reference & Pay Online")
                }
              }
            }
          } else {
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
              label = { Text("Account Sender Name *") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("opay_sender_input"),
            )
            OutlinedTextField(
              value = opayRef,
              onValueChange = { opayRef = it },
              label = { Text("Transaction Reference / OPay Session ID *") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("opay_reference_input"),
            )

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
                onClick = {
                  evidencePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.weight(1f).testTag("upload_opay_proof_button"),
              ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (evidenceBase64.isBlank()) "Upload Evidence (≤2MB)" else "Replace Evidence")
              }
              if (evidenceBase64.isNotBlank()) {
                OutlinedButton(onClick = { evidenceBase64 = "" }) {
                  Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove evidence", tint = JyCrimson)
                }
              }
            }

            Button(
              onClick = {
                val enr = selectedEnrollment ?: return@Button
                val amt = amountInput.toLongOrNull() ?: 0L
                if (amt > 0 && opayRef.trim().length >= 4 && senderName.trim().length >= 2 && paymentDate.isNotBlank()) {
                  onSubmitOpayTransfer(enr, amt, opayRef, paymentDate, senderName, evidenceBase64)
                  opayRef = ""
                  evidenceBase64 = ""
                } else {
                  onNotify("Enter valid amount, payment date, sender name, and transaction reference.")
                }
              },
              enabled = !isBusy && selectedEnrollment != null,
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_opay_button"),
            ) {
              Text("Submit Payment Evidence for Verification")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminRecordPaymentDialog(
  enrollments: List<Enrollment>,
  onDismiss: () -> Unit,
  onRecord: (Enrollment, Long, String, String, String, String) -> Unit,
) {
  var selectedEnr by remember { mutableStateOf(enrollments.first()) }
  var enrExpanded by remember { mutableStateOf(false) }
  var amountStr by remember(selectedEnr) {
    mutableStateOf(
      (selectedEnr.outstandingNaira.takeIf { it > 0 } ?: selectedEnr.totalFeeNaira).toString()
    )
  }
  var method by remember { mutableStateOf("cash") }
  var ref by remember { mutableStateOf("JYC-OFF-${System.currentTimeMillis() % 100000}") }
  var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
  var remarks by remember { mutableStateOf("Received at JY-Computers Finance Desk") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Record Cash / OPay Payment") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        ExposedDropdownMenuBox(expanded = enrExpanded, onExpandedChange = { enrExpanded = it }) {
          OutlinedTextField(
            value = "${selectedEnr.studentName} — ${selectedEnr.programmeTitle}",
            onValueChange = {},
            readOnly = true,
            label = { Text("Student Enrolment") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = enrExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = enrExpanded, onDismissRequest = { enrExpanded = false }) {
            enrollments.forEach { enr ->
              DropdownMenuItem(
                text = { Text("${enr.studentName} (${enr.studentIdNumber}) • Bal ${JyCatalogDefaults.formatNaira(enr.outstandingNaira)}") },
                onClick = {
                  selectedEnr = enr
                  enrExpanded = false
                },
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = method == "cash", onClick = { method = "cash" }, label = { Text("Cash") })
          FilterChip(selected = method == "opay_transfer", onClick = { method = "opay_transfer" }, label = { Text("OPay Transfer") })
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
          if (amt > 0 && ref.trim().length >= 4) {
            onRecord(selectedEnr, amt, method, ref, date, remarks)
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
      "opay_transfer" -> "OPay Transfer"
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
          Text("${pay.studentName} (${pay.studentIdNumber})", style = MaterialTheme.typography.bodySmall)
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
        "$methodLabel • $dateText",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (pay.adminRemarks.isNotBlank()) {
        Text("Note: ${pay.adminRemarks}", style = MaterialTheme.typography.bodySmall)
      }
      OutlinedButton(
        onClick = {
          val pdfFile = ExportAndPdfUtil.generatePaymentReceiptPdf(context, pay, settings)
          ExportAndPdfUtil.shareFile(
            context,
            pdfFile,
            "application/pdf",
            "Print / Share Tuition Receipt",
          )
        },
        modifier = Modifier.testTag("download_receipt_${pay.id}"),
      ) {
        Icon(Icons.Filled.Download, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Print / Share PDF Receipt")
      }
    }
  }
}

@Composable
fun PaymentHistoryScreen(
  payments: List<Payment>,
  settings: InstitutionSettings,
) {
  var filterStatus by remember { mutableStateOf("all") }

  val filtered =
    remember(payments, filterStatus) {
      if (filterStatus == "all") payments else payments.filter { it.status == filterStatus }
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
    }

    if (filtered.isEmpty()) {
      item {
        EmptyStateCard(
          title = "No Payment Records",
          message = "Your tuition payment transactions and downloadable receipts will appear here.",
        )
      }
    } else {
      items(filtered, key = { it.id }) { pay ->
        PaymentRecordCard(pay = pay, settings = settings)
      }
    }
  }
}
