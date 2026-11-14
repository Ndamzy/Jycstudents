package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
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
import com.example.ui.components.EmptyStateCard
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
fun PaymentScreen(
  settings: InstitutionSettings,
  enrollments: List<Enrollment>,
  payments: List<Payment>,
  activeRole: String,
  isBusy: Boolean,
  onSubmitOnlinePayment: (Enrollment, Long) -> Unit,
  onSubmitOpayTransfer: (Enrollment, Long, String, String, String) -> Unit,
  onAdminReviewPayment: (Payment, Boolean, String) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isAdmin = activeRole == "admin"

  var selectedTab by remember { mutableIntStateOf(if (isAdmin) 2 else 0) }
  var selectedEnrollment by remember(enrollments) { mutableStateOf(enrollments.firstOrNull()) }
  var enrDropdownExpanded by remember { mutableStateOf(false) }

  var amountInput by remember(selectedEnrollment) {
    mutableStateOf(
      (selectedEnrollment?.outstandingNaira?.takeIf { it > 0 } ?: selectedEnrollment?.totalFeeNaira ?: 25000L)
        .toString()
    )
  }
  var opayRef by remember { mutableStateOf("") }
  var senderName by remember { mutableStateOf("") }
  var evidenceBase64 by remember { mutableStateOf("") }

  val evidencePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = ImageCompressionUtil.compressUriToBase64(context, uri, maxDimension = 540, quality = 70)
          if (encoded != null) {
            evidenceBase64 = encoded
            onNotify("Payment receipt attached.")
          }
        }
      }
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
          text = { Text("Review ($pendingCount)") },
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
                Text("Method: ${pay.method} • Ref: ${pay.reference}", style = MaterialTheme.typography.bodySmall)
                if (pay.senderName.isNotBlank()) {
                  Text("Sender: ${pay.senderName}", style = MaterialTheme.typography.bodySmall)
                }
                if (proofBitmap != null) {
                  Image(
                    bitmap = proofBitmap.asImageBitmap(),
                    contentDescription = "Uploaded OPay payment proof",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(12.dp)),
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
    } else {
      Column(
        modifier =
          Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        if (enrollments.isEmpty()) {
          EmptyStateCard(
            title = "No Active Course Enrollment",
            message = "Register for a programme in Course Registration first before paying tuition.",
          )
        } else {
          ExposedDropdownMenuBox(
            expanded = enrDropdownExpanded,
            onExpandedChange = { enrDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value =
                selectedEnrollment?.let {
                  "${it.programmeTitle} (Balance: ${JyCatalogDefaults.formatNaira(it.outstandingNaira)})"
                } ?: "Select Enrollment",
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
            label = { Text("Amount to Pay (₦)") },
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
                  "Generates a secure transaction reference and verifies payment through the configured server endpoint (gateway secrets are never stored in this app).",
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
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("OPay Bank Transfer Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Bank: ${settings.opayBankName}", style = MaterialTheme.typography.bodyMedium)
                Text("Account Name: ${settings.opayAccountName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    "Account No: ${settings.opayAccountNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                  )
                  IconButton(
                    onClick = {
                      val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      cm.setPrimaryClip(ClipData.newPlainText("OPay Account", settings.opayAccountNumber))
                      onNotify("OPay account number copied!")
                    }
                  ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy OPay account number")
                  }
                }
                Text(settings.opayInstructions, style = MaterialTheme.typography.bodySmall)
              }
            }

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
              label = { Text("OPay Transaction Reference / Session ID *") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("opay_reference_input"),
            )

            OutlinedButton(
              onClick = {
                evidencePicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              modifier = Modifier.fillMaxWidth().testTag("upload_opay_proof_button"),
            ) {
              Icon(Icons.Filled.UploadFile, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (evidenceBase64.isBlank()) "Upload Payment Receipt Screenshot" else "Receipt Attached (Tap to Replace)")
            }

            Button(
              onClick = {
                val enr = selectedEnrollment ?: return@Button
                val amt = amountInput.toLongOrNull() ?: 0L
                if (amt > 0 && opayRef.trim().length >= 4 && senderName.trim().length >= 2) {
                  onSubmitOpayTransfer(enr, amt, opayRef, senderName, evidenceBase64)
                  opayRef = ""
                  evidenceBase64 = ""
                } else {
                  onNotify("Enter valid amount, sender name, and OPay transaction reference.")
                }
              },
              enabled = !isBusy && selectedEnrollment != null,
              modifier = Modifier.fillMaxWidth().height(52.dp).testTag("submit_opay_button"),
            ) {
              Text("Submit OPay Transfer for Verification")
            }
          }
        }
      }
    }
  }
}

@Composable
fun PaymentHistoryScreen(
  payments: List<Payment>,
  settings: InstitutionSettings,
) {
  val context = LocalContext.current
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
        val dateText =
          pay.createdAt?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(it)
          } ?: "Just now"
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
              Text(pay.programmeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
              "${if (pay.method == "opay_transfer") "OPay Transfer" else "Online Gateway"} • $dateText",
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
                  "Share Tuition Receipt",
                )
              },
              modifier = Modifier.testTag("download_receipt_${pay.id}"),
            ) {
              Icon(Icons.Filled.Download, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Download / Share PDF Receipt")
            }
          }
        }
      }
    }
  }
}
