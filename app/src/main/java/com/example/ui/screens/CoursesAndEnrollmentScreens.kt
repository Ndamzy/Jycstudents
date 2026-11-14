package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
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
import com.example.data.model.Enrollment
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.LearningMaterial
import com.example.data.model.Programme
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyEmeraldContainer

@Composable
fun CourseCatalogueScreen(
  programmes: List<Programme>,
  materials: List<LearningMaterial>,
  activeRole: String,
  onSaveProgramme: (Programme) -> Unit,
  onDeleteProgramme: (String) -> Unit,
  onAddLearningMaterial: (Programme, String, String, String) -> Unit,
  onGoToRegister: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var selectedCategory by remember { mutableStateOf("All") }
  var editingProgramme by remember { mutableStateOf<Programme?>(null) }
  var showAddMaterialDialog by remember { mutableStateOf(false) }

  val isAdmin = activeRole == "admin"
  val canShareMaterial = activeRole == "admin" || activeRole == "instructor"
  val context = LocalContext.current

  val displayProgrammes =
    remember(programmes, selectedCategory) {
      val base =
        programmes.ifEmpty {
          JyCatalogDefaults.DEFAULT_PROGRAMMES.map {
            Programme(
              id = it.id,
              title = it.title,
              description = it.description,
              duration = it.duration,
              feeNaira = it.feeNaira,
              category = it.category,
              scheduleText = it.scheduleText,
            )
          }
        }
      if (selectedCategory == "All") base else base.filter { it.category == selectedCategory }
    }

  if (editingProgramme != null) {
    ProgrammeEditorDialog(
      initial = editingProgramme!!,
      onDismiss = { editingProgramme = null },
      onSave = { updated ->
        onSaveProgramme(updated)
        editingProgramme = null
      },
    )
  }

  if (showAddMaterialDialog && programmes.isNotEmpty()) {
    AddLearningMaterialDialog(
      programmes = programmes,
      onDismiss = { showAddMaterialDialog = false },
      onSubmit = { prog, title, desc, url ->
        onAddLearningMaterial(prog, title, desc, url)
        showAddMaterialDialog = false
      },
    )
  }

  Scaffold(
    floatingActionButton = {
      if (selectedTab == 0 && isAdmin) {
        FloatingActionButton(
          onClick = { editingProgramme = Programme(active = true) },
          modifier = Modifier.testTag("add_programme_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Add Programme")
        }
      } else if (selectedTab == 1 && canShareMaterial) {
        FloatingActionButton(
          onClick = { showAddMaterialDialog = true },
          modifier = Modifier.testTag("add_material_fab"),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Share Learning Material")
        }
      }
    }
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Programmes (${displayProgrammes.size})") },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Study Materials (${materials.size})") },
        )
      }

      if (selectedTab == 0) {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(listOf("All", "Foundation", "Diploma", "Hardware", "Software")) { cat ->
                FilterChip(
                  selected = selectedCategory == cat,
                  onClick = { selectedCategory = cat },
                  label = { Text(cat) },
                )
              }
            }
          }

          items(displayProgrammes, key = { it.id }) { prog ->
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  StatusPill(
                    text = prog.category,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Text(
                    text = JyCatalogDefaults.formatNaira(prog.feeNaira),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(prog.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  prog.description,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    "Duration: ${prog.duration} • ${prog.scheduleText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  OutlinedButton(onClick = onGoToRegister) {
                    Icon(Icons.Filled.School, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Register Course")
                  }
                  if (isAdmin) {
                    Row {
                      IconButton(onClick = { editingProgramme = prog }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit programme")
                      }
                      IconButton(onClick = { onDeleteProgramme(prog.id) }) {
                        Icon(
                          Icons.Filled.Delete,
                          contentDescription = "Remove programme",
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
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (materials.isEmpty()) {
            item {
              EmptyStateCard(
                title = "No Study Materials Yet",
                message = "Instructors can tap + to share course notes, slides, and reference links.",
                icon = Icons.Filled.LibraryBooks,
              )
            }
          } else {
            items(materials, key = { it.id }) { mat ->
              Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              ) {
                Column(modifier = Modifier.padding(16.dp)) {
                  StatusPill(
                    text = mat.programmeTitle,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(mat.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(mat.description, style = MaterialTheme.typography.bodyMedium)
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    "Shared by ${mat.authorName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  if (mat.resourceUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                      onClick = {
                        val url =
                          if (mat.resourceUrl.startsWith("http")) mat.resourceUrl
                          else "https://${mat.resourceUrl}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                      }
                    ) {
                      Icon(Icons.Filled.OpenInNew, contentDescription = null)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Open Resource")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun CourseRegistrationScreen(
  programmes: List<Programme>,
  enrollments: List<Enrollment>,
  isBusy: Boolean,
  onEnroll: (Programme) -> Unit,
  onGoToPayment: () -> Unit,
) {
  val enrolledIds = remember(enrollments) { enrollments.map { it.programmeId }.toSet() }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("course_registration_list"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (enrollments.isNotEmpty()) {
      item {
        Text(
          "Your Active Enrollments",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
      }
      items(enrollments, key = { it.id }) { enr ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(enr.programmeTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              StatusPill(
                text = enr.status.uppercase(),
                containerColor = JyEmeraldContainer,
                contentColor = JyEmerald,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Fee: ${JyCatalogDefaults.formatNaira(enr.totalFeeNaira)} • Paid: ${JyCatalogDefaults.formatNaira(enr.amountPaidNaira)} • Balance: ${JyCatalogDefaults.formatNaira(enr.outstandingNaira)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (enr.outstandingNaira > 0) {
              Spacer(modifier = Modifier.height(10.dp))
              Button(onClick = onGoToPayment) { Text("Pay Outstanding Tuition") }
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        "Available Programmes to Register",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    items(programmes, key = { "reg_${it.id}" }) { prog ->
      val alreadyEnrolled = prog.id in enrolledIds
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(prog.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              "${prog.duration} • ${JyCatalogDefaults.formatNaira(prog.feeNaira)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Button(
            onClick = { onEnroll(prog) },
            enabled = !alreadyEnrolled && !isBusy,
            modifier = Modifier.testTag("enroll_${prog.id}_button"),
          ) {
            Text(if (alreadyEnrolled) "Enrolled" else "Register")
          }
        }
      }
    }
  }
}

@Composable
private fun ProgrammeEditorDialog(
  initial: Programme,
  onDismiss: () -> Unit,
  onSave: (Programme) -> Unit,
) {
  var title by remember { mutableStateOf(initial.title) }
  var description by remember { mutableStateOf(initial.description) }
  var duration by remember { mutableStateOf(initial.duration.ifBlank { "3 Months" }) }
  var feeStr by remember { mutableStateOf(if (initial.feeNaira > 0) initial.feeNaira.toString() else "50000") }
  var category by remember { mutableStateOf(initial.category.ifBlank { "Foundation" }) }
  var instructorName by remember { mutableStateOf(initial.instructorName.ifBlank { "JY-Computers Faculty" }) }
  var scheduleText by remember { mutableStateOf(initial.scheduleText.ifBlank { "Mon - Wed • 10:00 AM" }) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial.id.isBlank()) "Add Training Programme" else "Edit Programme") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Programme Title") }, singleLine = true)
        OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("Duration (e.g. 6 Months)") }, singleLine = true)
        OutlinedTextField(value = feeStr, onValueChange = { feeStr = it.filter { c -> c.isDigit() } }, label = { Text("Tuition Fee (₦)") }, singleLine = true)
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
        OutlinedTextField(value = instructorName, onValueChange = { instructorName = it }, label = { Text("Instructor Name") }, singleLine = true)
        OutlinedTextField(value = scheduleText, onValueChange = { scheduleText = it }, label = { Text("Class Schedule") }, singleLine = true)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") })
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          val fee = feeStr.toLongOrNull() ?: 0L
          if (title.isNotBlank() && duration.isNotBlank()) {
            onSave(
              initial.copy(
                title = title,
                description = description,
                duration = duration,
                feeNaira = fee,
                category = category,
                instructorName = instructorName,
                scheduleText = scheduleText,
                active = true,
              )
            )
          }
        }
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLearningMaterialDialog(
  programmes: List<Programme>,
  onDismiss: () -> Unit,
  onSubmit: (Programme, String, String, String) -> Unit,
) {
  var selected by remember { mutableStateOf(programmes.first()) }
  var expanded by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Share Learning Material") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
          OutlinedTextField(
            value = selected.title,
            onValueChange = {},
            readOnly = true,
            label = { Text("Course") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            programmes.forEach { prog ->
              DropdownMenuItem(
                text = { Text(prog.title) },
                onClick = {
                  selected = prog
                  expanded = false
                },
              )
            }
          }
        }
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Material Title") }, singleLine = true)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Summary / Notes") })
        OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Resource URL (optional)") }, singleLine = true)
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (title.isNotBlank() && description.isNotBlank()) {
            onSubmit(selected, title, description, url)
          }
        }
      ) { Text("Share") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
