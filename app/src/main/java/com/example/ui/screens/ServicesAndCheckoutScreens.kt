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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.JyServiceItem
import com.example.data.model.ServiceRequest
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.JyBusinessFooter
import com.example.ui.components.JyGradientHeaderCard
import com.example.ui.components.OfficialContactCard
import com.example.ui.components.OfficialOpayBankCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyCrimson
import com.example.ui.theme.JyCrimsonContainer
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer
import com.example.ui.theme.JyGold
import com.example.ui.theme.JyGoldContainer
import com.example.util.ExportAndPdfUtil

@Composable
fun ServicesHubScreen(
  profile: UserProfile?,
  settings: InstitutionSettings,
  services: List<JyServiceItem>,
  serviceRequests: List<ServiceRequest>,
  activeRole: String,
  isBusy: Boolean,
  onSaveServiceAdmin: (JyServiceItem) -> Unit,
  onToggleServicePaymentAdmin: (JyServiceItem) -> Unit,
  onDeleteServiceAdmin: (String) -> Unit,
  onCreateServiceRequest: (JyServiceItem, String, Long?, String, (ServiceRequest) -> Unit) -> Unit,
  onUpdateServiceRequestAdmin: (String, String, Long?, String) -> Unit,
  onPayRequestNow: (ServiceRequest) -> Unit,
  onNotify: (String) -> Unit,
) {
  val context = LocalContext.current
  val isAdmin = activeRole == "admin"

  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }
  var requestStatusFilter by remember { mutableStateOf("all") }

  var bookingService by remember { mutableStateOf<JyServiceItem?>(null) }
  var recentlyCreatedRequest by remember { mutableStateOf<ServiceRequest?>(null) }
  var editingService by remember { mutableStateOf<JyServiceItem?>(null) }
  var editingRequest by remember { mutableStateOf<ServiceRequest?>(null) }

  val effectiveServices =
    remember(services) {
      services.ifEmpty {
        JyCatalogDefaults.DEFAULT_SERVICES.map {
          JyServiceItem(
            id = it.id,
            title = it.title,
            category = it.category,
            description = it.description,
            defaultFeeNaira = it.defaultFeeNaira,
            paymentEnabled = it.paymentEnabled,
            allowPartialPayment = it.allowPartialPayment,
            isTrainingCourse = it.isTrainingCourse,
            linkedProgrammeId = it.linkedProgrammeId,
            active = true,
          )
        }
      }
    }

  val filteredServices =
    remember(effectiveServices, selectedCategory, searchQuery, isAdmin) {
      effectiveServices.filter { srv ->
        val visible = isAdmin || srv.active
        val matchesCategory = selectedCategory == "All" || srv.category == selectedCategory
        val matchesQuery =
          searchQuery.isBlank() ||
            srv.title.contains(searchQuery, ignoreCase = true) ||
            srv.description.contains(searchQuery, ignoreCase = true) ||
            srv.category.contains(searchQuery, ignoreCase = true)
        visible && matchesCategory && matchesQuery
      }
    }

  val filteredRequests =
    remember(serviceRequests, requestStatusFilter, searchQuery) {
      serviceRequests.filter { req ->
        val matchesStatus =
          when (requestStatusFilter) {
            "all" -> true
            "unpaid" -> req.paymentStatus == "unpaid" || req.paymentStatus == "partial"
            else -> req.paymentStatus == requestStatusFilter || req.status == requestStatusFilter
          }
        val matchesQuery =
          searchQuery.isBlank() ||
            req.customerName.contains(searchQuery, ignoreCase = true) ||
            req.serviceTitle.contains(searchQuery, ignoreCase = true) ||
            req.requestNumber.contains(searchQuery, ignoreCase = true) ||
            req.invoiceNumber.contains(searchQuery, ignoreCase = true)
        matchesStatus && matchesQuery
      }
    }

  if (bookingService != null) {
    CreateServiceRequestDialog(
      service = bookingService!!,
      defaultPhone = profile?.phoneNumber.orEmpty(),
      isBusy = isBusy,
      onDismiss = { bookingService = null },
      onSubmit = { details, customFee, phone ->
        val targetService = bookingService!!
        onCreateServiceRequest(targetService, details, customFee, phone) { created ->
          bookingService = null
          recentlyCreatedRequest = created
          selectedTab = 1
        }
      },
    )
  }

  if (recentlyCreatedRequest != null) {
    val req = recentlyCreatedRequest!!
    AlertDialog(
      onDismissRequest = { recentlyCreatedRequest = null },
      title = { Text("Invoice Ready: ${req.invoiceNumber}") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Service: ${req.serviceTitle}\nRequest ID: ${req.requestNumber}\nAmount Due: ${JyCatalogDefaults.formatNaira(req.outstandingNaira)}",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            "Pay directly into JY-COMPUTERS (${JyCatalogDefaults.OFFICIAL_OPAY_BANK} • ${JyCatalogDefaults.OFFICIAL_OPAY_ACCOUNT_NUMBER}) or download your invoice PDF.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            recentlyCreatedRequest = null
            onPayRequestNow(req)
          },
          enabled = settings.paymentsEnabled,
          modifier = Modifier.testTag("created_request_pay_now_button"),
        ) {
          Text("Pay Now")
        }
      },
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              val pdf =
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
              ExportAndPdfUtil.shareFile(context, pdf, "application/pdf", "Share Invoice ${req.invoiceNumber}")
            }
          ) {
            Text("Invoice PDF")
          }
          TextButton(onClick = { recentlyCreatedRequest = null }) { Text("Close") }
        }
      },
    )
  }

  if (editingService != null) {
    ServiceEditorDialog(
      initial = editingService!!,
      onDismiss = { editingService = null },
      onSave = { updated ->
        onSaveServiceAdmin(updated)
        editingService = null
      },
    )
  }

  if (editingRequest != null) {
    AdminUpdateServiceRequestDialog(
      request = editingRequest!!,
      onDismiss = { editingRequest = null },
      onSave = { status, fee, notes ->
        onUpdateServiceRequestAdmin(editingRequest!!.id, status, fee, notes)
        editingRequest = null
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      if (isAdmin && selectedTab == 0) {
        FloatingActionButton(
          onClick = {
            editingService =
              JyServiceItem(
                category = JyCatalogDefaults.SERVICE_CATEGORIES.first(),
                paymentEnabled = true,
                allowPartialPayment = true,
                active = true,
              )
          },
          modifier = Modifier.testTag("add_service_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Add Service")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Services (${filteredServices.size})") },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Requests & Invoices (${serviceRequests.size})") },
        )
      }

      if (selectedTab == 0) {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("services_catalogue_list"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            JyGradientHeaderCard(
              title = "JY-COMPUTERS Services & ICT Hub",
              subtitle = "Training, Repairs, Networking, Web Solutions, Cyber Café, NYSC, CAC & Printing",
              badgeText = "${effectiveServices.size} Services",
              showOfficialLogo = true,
            )
          }

          item {
            OfficialOpayBankCard(settings = settings, onCopied = onNotify)
          }

          item {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              label = { Text("Search services (e.g. NYSC, Website, Repairs, Printing)") },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("services_search_input"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(listOf("All") + JyCatalogDefaults.SERVICE_CATEGORIES) { category ->
                FilterChip(
                  selected = selectedCategory == category,
                  onClick = { selectedCategory = category },
                  label = { Text(category) },
                )
              }
            }
          }

          if (filteredServices.isEmpty()) {
            item {
              EmptyStateCard(
                title = "No Matching Services",
                message = "Try a different keyword or category filter, or contact us directly.",
                icon = Icons.Filled.Storefront,
              )
            }
          } else {
            items(filteredServices, key = { it.id }) { srv ->
              val isPersisted = services.any { it.id == srv.id }
              val payAllowed = settings.paymentsEnabled && srv.paymentEnabled
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      StatusPill(
                        text = srv.category,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                      )
                      StatusPill(
                        text = if (srv.allowPartialPayment && settings.allowPartialPayment) "PARTIAL OK" else "FULL PAY",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                      )
                    }
                    Text(
                      text = JyCatalogDefaults.formatNaira(srv.defaultFeeNaira),
                      style = MaterialTheme.typography.titleLarge,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }

                  Text(srv.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Text(
                    srv.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Button(
                      onClick = { bookingService = srv },
                      enabled = !isBusy && srv.active && payAllowed,
                      modifier = Modifier.testTag("request_service_${srv.id}"),
                    ) {
                      Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(if (payAllowed) "Request & Pay" else "In-Office Payment")
                    }

                    if (isAdmin) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                          checked = srv.paymentEnabled,
                          onCheckedChange = {
                            if (isPersisted) {
                              onToggleServicePaymentAdmin(srv)
                            } else {
                              onSaveServiceAdmin(srv.copy(paymentEnabled = !srv.paymentEnabled))
                            }
                          },
                        )
                        IconButton(onClick = { editingService = srv }) {
                          Icon(Icons.Filled.Edit, contentDescription = "Edit service")
                        }
                        if (isPersisted) {
                          IconButton(onClick = { onDeleteServiceAdmin(srv.id) }) {
                            Icon(
                              Icons.Filled.Delete,
                              contentDescription = "Remove service",
                              tint = MaterialTheme.colorScheme.error,
                            )
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            OfficialContactCard(settings = settings, onNotify = onNotify)
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("service_requests_list"),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              label = { Text("Search by invoice #, request #, service, or name") },
              leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("all", "unpaid", "partial", "paid", "completed").forEach { filter ->
                item {
                  FilterChip(
                    selected = requestStatusFilter == filter,
                    onClick = { requestStatusFilter = filter },
                    label = { Text(filter.replaceFirstChar { it.uppercase() }) },
                  )
                }
              }
            }
          }

          if (filteredRequests.isEmpty()) {
            item {
              EmptyStateCard(
                title = "No Service Requests Yet",
                message = "Tap 'Services' and choose 'Request & Pay' on any service to generate an invoice and pay via OPay or bank transfer.",
                icon = Icons.Filled.ReceiptLong,
              )
            }
          } else {
            items(filteredRequests, key = { it.id }) { req ->
              val (payBg, payFg) =
                when (req.paymentStatus) {
                  "paid" -> JyEmeraldContainer to JyEmerald
                  "partial" -> JyGoldContainer to JyGold
                  else -> JyCrimsonContainer to JyCrimson
                }
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
                    Column(modifier = Modifier.weight(1f)) {
                      Text(req.serviceTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                      Text(
                        "${req.invoiceNumber} • ${req.requestNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                      )
                    }
                    StatusPill(req.paymentStatus.uppercase(), payBg, payFg)
                  }

                  Text(
                    "Client: ${req.customerName}${if (req.customerPhone.isNotBlank()) " (${req.customerPhone})" else ""} • ${req.serviceCategory}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (req.details.isNotBlank()) {
                    Text("Details: ${req.details}", style = MaterialTheme.typography.bodySmall)
                  }
                  Text(
                    "Fee: ${JyCatalogDefaults.formatNaira(req.totalFeeNaira)} • Paid: ${JyCatalogDefaults.formatNaira(req.amountPaidNaira)} • Balance: ${JyCatalogDefaults.formatNaira(req.outstandingNaira)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  if (req.adminNotes.isNotBlank()) {
                    Text("Admin Note: ${req.adminNotes}", style = MaterialTheme.typography.bodySmall)
                  }

                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    OutlinedButton(
                      onClick = {
                        val pdf =
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
                        ExportAndPdfUtil.shareFile(context, pdf, "application/pdf", "Share Invoice ${req.invoiceNumber}")
                      },
                      modifier = Modifier.weight(1f).testTag("download_invoice_${req.id}"),
                    ) {
                      Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Invoice PDF")
                    }

                    if (req.outstandingNaira > 0 && settings.paymentsEnabled) {
                      Button(
                        onClick = { onPayRequestNow(req) },
                        modifier = Modifier.weight(1f).testTag("pay_service_request_${req.id}"),
                      ) {
                        Icon(Icons.Filled.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pay Now")
                      }
                    }

                    if (isAdmin) {
                      IconButton(onClick = { editingRequest = req }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Update request")
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            JyBusinessFooter(settings = settings, onNotify = onNotify)
          }
        }
      }
    }
  }
}

@Composable
private fun CreateServiceRequestDialog(
  service: JyServiceItem,
  defaultPhone: String,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (details: String, customFeeNaira: Long?, phone: String) -> Unit,
) {
  var details by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf(defaultPhone) }
  var feeStr by remember { mutableStateOf(service.defaultFeeNaira.toString()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Request: ${service.title}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          "${service.category} • Standard Fee: ${JyCatalogDefaults.formatNaira(service.defaultFeeNaira)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Contact Phone / WhatsApp") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("service_request_phone_input"),
        )
        OutlinedTextField(
          value = feeStr,
          onValueChange = { feeStr = it.filter { ch -> ch.isDigit() } },
          label = { Text("Agreed / Invoice Amount (₦)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("service_request_fee_input"),
        )
        OutlinedTextField(
          value = details,
          onValueChange = { details = it },
          label = { Text("Service Requirements / Notes *") },
          placeholder = { Text("Describe your request (e.g. specs, pages, portal details)") },
          modifier = Modifier.fillMaxWidth().testTag("service_request_details_input"),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val fee = feeStr.toLongOrNull() ?: service.defaultFeeNaira
          onSubmit(details.ifBlank { "Standard ${service.title} request" }, fee, phone)
        },
        enabled = !isBusy,
        modifier = Modifier.testTag("confirm_create_service_request_button"),
      ) {
        Text("Generate Invoice & Proceed")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceEditorDialog(
  initial: JyServiceItem,
  onDismiss: () -> Unit,
  onSave: (JyServiceItem) -> Unit,
) {
  var title by remember { mutableStateOf(initial.title) }
  var category by remember { mutableStateOf(initial.category.ifBlank { JyCatalogDefaults.SERVICE_CATEGORIES.first() }) }
  var catExpanded by remember { mutableStateOf(false) }
  var description by remember { mutableStateOf(initial.description) }
  var feeStr by remember { mutableStateOf(if (initial.defaultFeeNaira > 0L) initial.defaultFeeNaira.toString() else "5000") }
  var paymentEnabled by remember { mutableStateOf(initial.paymentEnabled) }
  var allowPartial by remember { mutableStateOf(initial.allowPartialPayment) }
  var active by remember { mutableStateOf(initial.active) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.id.isBlank()) "Add JY-Computers Service" else "Edit Service") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Service Title *") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
          OutlinedTextField(
            value = category,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = catExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
            JyCatalogDefaults.SERVICE_CATEGORIES.forEach { cat ->
              DropdownMenuItem(
                text = { Text(cat) },
                onClick = {
                  category = cat
                  catExpanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(
          value = feeStr,
          onValueChange = { feeStr = it.filter { c -> c.isDigit() } },
          label = { Text("Standard Fee (₦)") },
          singleLine = true,
        )
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Online/App Payment Enabled")
          Switch(checked = paymentEnabled, onCheckedChange = { paymentEnabled = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Allow Partial Payment")
          Switch(checked = allowPartial, onCheckedChange = { allowPartial = it })
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text("Service Active / Visible")
          Switch(checked = active, onCheckedChange = { active = it })
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (title.isNotBlank()) {
            onSave(
              initial.copy(
                title = title.trim(),
                category = category,
                description = description.trim(),
                defaultFeeNaira = feeStr.toLongOrNull() ?: 0L,
                paymentEnabled = paymentEnabled,
                allowPartialPayment = allowPartial,
                active = active,
              )
            )
          }
        }
      ) { Text("Save Service") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
private fun AdminUpdateServiceRequestDialog(
  request: ServiceRequest,
  onDismiss: () -> Unit,
  onSave: (String, Long?, String) -> Unit,
) {
  var status by remember { mutableStateOf(request.status) }
  var feeStr by remember { mutableStateOf(request.totalFeeNaira.toString()) }
  var notes by remember { mutableStateOf(request.adminNotes) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage ${request.requestNumber}") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text("${request.serviceTitle} • ${request.customerName}", style = MaterialTheme.typography.bodySmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(listOf("pending", "in_progress", "completed", "cancelled")) { st ->
            FilterChip(
              selected = status == st,
              onClick = { status = st },
              label = { Text(st.replace('_', ' ').replaceFirstChar { it.uppercase() }) },
            )
          }
        }
        OutlinedTextField(
          value = feeStr,
          onValueChange = { feeStr = it.filter { c -> c.isDigit() } },
          label = { Text("Total Invoice Fee (₦)") },
          singleLine = true,
        )
        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Admin Remarks") })
      }
    },
    confirmButton = {
      TextButton(onClick = { onSave(status, feeStr.toLongOrNull(), notes) }) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
