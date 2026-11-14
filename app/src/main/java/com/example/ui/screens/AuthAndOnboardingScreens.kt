package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.remote.GoogleAuthHelper
import com.example.ui.components.Base64Avatar
import com.example.ui.theme.JyDeepNavy
import com.example.ui.theme.JyRoyalBlue
import com.example.util.ImageCompressionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: () -> Unit) {
  var visible by remember { mutableStateOf(false) }
  val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(550), label = "splash")
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  LaunchedEffect(Unit) {
    visible = true
    var settled = false
    GoogleAuthHelper.attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      scope = scope,
      onAuthSuccess = { settled = true },
      onUnauthenticated = { settled = true },
    )
    delay(1100)
    if (!settled) {
      delay(300)
    }
    onFinished()
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(Brush.verticalGradient(listOf(JyDeepNavy, JyRoyalBlue)))
        .statusBarsPadding()
        .navigationBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.alpha(alpha).padding(32.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.size(116.dp),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_institution_logo),
          contentDescription = "JY-Computers Institutional Crest",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize().padding(10.dp),
        )
      }
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "JY-COMPUTERS",
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "STUDENT MANAGEMENT SYSTEM",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.85f),
      )
      Spacer(modifier = Modifier.height(28.dp))
      CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
    }
  }
}

@Composable
fun WelcomeScreen(
  onContinueToLogin: () -> Unit,
  onOpenRecovery: () -> Unit,
) {
  Column(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .verticalScroll(rememberScrollState())
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(modifier = Modifier.widthIn(max = 560.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_institution_logo),
          contentDescription = "JY-Computers Logo",
          modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            "JY-COMPUTERS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            "Student & Academic Portal",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_welcome_hero),
          contentDescription = "JY-Computers students collaborating in computer lab",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxWidth().height(210.dp),
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "Your Complete Digital Campus",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text =
          "Register for certified computer training programmes, pay tuition securely, track attendance & results, download certificates, and connect in real time.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      val highlights =
        listOf(
          Triple(Icons.Filled.School, "9 Certified Programmes", "From Basic Computing to Android App Development"),
          Triple(Icons.Filled.Payments, "Online & OPay Tuition", "Instant receipts and verified fee ledgers"),
          Triple(Icons.Filled.MenuBook, "Live Results & Certificates", "Download official PDF certificates anytime"),
        )
      highlights.forEach { (icon, title, desc) ->
        Card(
          modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.size(42.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
      Button(
        onClick = onContinueToLogin,
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("welcome_continue_button"),
        shape = RoundedCornerShape(16.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sign In to Portal", style = MaterialTheme.typography.labelLarge)
      }
      Spacer(modifier = Modifier.height(8.dp))
      TextButton(
        onClick = onOpenRecovery,
        modifier = Modifier.testTag("welcome_recovery_button"),
      ) {
        Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Need help accessing your account?")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
  onBack: () -> Unit,
  onOpenRecovery: () -> Unit,
  onAuthSuccess: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Portal Sign In") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("login_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 480.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_institution_logo),
          contentDescription = "JY-Computers Emblem",
          modifier = Modifier.size(88.dp).clip(CircleShape),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          "Welcome Back",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Sign in securely with your Google Account to access Student, Instructor, or Administrator dashboards.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Filled.Security,
                contentDescription = "Protected authentication",
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                "Verified Role-Based Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "New students will complete their institutional registration right after signing in.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
              onClick = {
                errorMsg = null
                isLoading = true
                GoogleAuthHelper.onGoogleSignInClicked(
                  context = context,
                  credentialManager = credentialManager,
                  scope = scope,
                  onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                  },
                  onAuthError = { msg ->
                    isLoading = false
                    errorMsg = msg
                  },
                  onAuthCancelled = {
                    isLoading = false
                    errorMsg = "Sign-in was cancelled. Please select a Google account to continue."
                  },
                )
              },
              enabled = !isLoading,
              modifier = Modifier.fillMaxWidth().height(54.dp).testTag("google_sign_in_button"),
              shape = RoundedCornerShape(14.dp),
            ) {
              if (isLoading) {
                CircularProgressIndicator(
                  modifier = Modifier.size(22.dp),
                  color = MaterialTheme.colorScheme.onPrimary,
                  strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Signing in...")
              } else {
                Text("Sign in with Google", style = MaterialTheme.typography.labelLarge)
              }
            }

            if (errorMsg != null) {
              Spacer(modifier = Modifier.height(14.dp))
              Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = errorMsg!!,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
          onClick = onOpenRecovery,
          modifier = Modifier.testTag("login_recovery_link"),
        ) {
          Text("Password & Account Recovery Help")
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountRecoveryScreen(onBack: () -> Unit) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Account & Password Recovery") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("recovery_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(modifier = Modifier.widthIn(max = 520.dp)) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              "Google Sign-In Password Recovery",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "JY-Computers uses Google Identity Services for zero-password protection. If you forgot your Google password or cannot access your email, use Google's official recovery flow below.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = {
                val intent =
                  Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://accounts.google.com/signin/recovery"),
                  )
                context.startActivity(intent)
              },
              modifier = Modifier.fillMaxWidth().height(50.dp).testTag("open_google_recovery_button"),
            ) {
              Text("Open Google Account Recovery")
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text(
              "Institutional Desk Support",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Need to update your registered email or unlock a suspended student account? Contact the JY-Computers Admissions Office:\n\nEmail: admissions@jycomputers.edu.ng\nPhone: +234 800 592 6678",
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentRegistrationScreen(
  defaultEmail: String,
  defaultName: String,
  programmes: List<Programme>,
  isBusy: Boolean,
  isAdminAddingStudent: Boolean = false,
  onSubmit: (
    fullName: String,
    gender: String,
    dateOfBirth: String,
    phoneNumber: String,
    email: String,
    residentialAddress: String,
    stateOfOrigin: String,
    nextOfKin: String,
    nextOfKinPhone: String,
    passportPhotoBase64: String,
    programme: Programme,
  ) -> Unit,
  onSignOutOrCancel: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var fullName by remember { mutableStateOf(defaultName) }
  var gender by remember { mutableStateOf("Male") }
  var dateOfBirth by remember { mutableStateOf("") }
  var phoneNumber by remember { mutableStateOf("") }
  var email by remember { mutableStateOf(defaultEmail) }
  var residentialAddress by remember { mutableStateOf("") }
  var stateOfOrigin by remember { mutableStateOf("FCT Abuja") }
  var nextOfKin by remember { mutableStateOf("") }
  var nextOfKinPhone by remember { mutableStateOf("") }
  var passportBase64 by remember { mutableStateOf("") }

  val effectiveProgrammes =
    remember(programmes) {
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
    }
  var selectedProgramme by remember(effectiveProgrammes) { mutableStateOf(effectiveProgrammes.first()) }

  var genderExpanded by remember { mutableStateOf(false) }
  var stateExpanded by remember { mutableStateOf(false) }
  var progExpanded by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val regDate = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
  val previewStudentId = remember { "JYC-${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}-AUTO" }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val encoded = ImageCompressionUtil.compressUriToBase64(context, uri, maxDimension = 360, quality = 70)
          if (encoded != null) {
            passportBase64 = encoded
          }
        }
      }
    }

  if (showDatePicker) {
    val pickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            pickerState.selectedDateMillis?.let { millis ->
              dateOfBirth = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
            }
            showDatePicker = false
          }
        ) { Text("Select") }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
    ) {
      DatePicker(state = pickerState)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (isAdminAddingStudent) "Register New Student" else "Student Registration") },
        actions = {
          TextButton(onClick = onSignOutOrCancel) {
            Text(if (isAdminAddingStudent) "Cancel" else "Sign Out")
          }
        },
      )
    }
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(modifier = Modifier.widthIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier.clickable {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                }
            ) {
              Base64Avatar(base64 = passportBase64, name = fullName.ifBlank { "Student" }, size = 72.dp)
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomEnd).size(26.dp),
              ) {
                Icon(
                  Icons.Filled.AddAPhoto,
                  contentDescription = "Upload passport photograph",
                  tint = Color.White,
                  modifier = Modifier.padding(5.dp),
                )
              }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                "Passport Photograph",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                "Tap photo to select from gallery • ID: $previewStudentId\nReg Date: $regDate",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }

        OutlinedTextField(
          value = fullName,
          onValueChange = { fullName = it },
          label = { Text("Full Name *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("reg_full_name_input"),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ExposedDropdownMenuBox(
            expanded = genderExpanded,
            onExpandedChange = { genderExpanded = it },
            modifier = Modifier.weight(1f),
          ) {
            OutlinedTextField(
              value = gender,
              onValueChange = {},
              readOnly = true,
              label = { Text("Gender *") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
              modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
              listOf("Male", "Female", "Other").forEach { option ->
                DropdownMenuItem(
                  text = { Text(option) },
                  onClick = {
                    gender = option
                    genderExpanded = false
                  },
                )
              }
            }
          }

          OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it },
            label = { Text("Date of Birth *") },
            placeholder = { Text("YYYY-MM-DD") },
            trailingIcon = {
              IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date of birth")
              }
            },
            singleLine = true,
            modifier = Modifier.weight(1.2f).testTag("reg_dob_input"),
          )
        }

        OutlinedTextField(
          value = phoneNumber,
          onValueChange = { phoneNumber = it },
          label = { Text("Phone Number *") },
          placeholder = { Text("080XXXXXXXX") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("reg_phone_input"),
        )

        OutlinedTextField(
          value = email,
          onValueChange = { email = it },
          label = { Text("Email Address *") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("reg_email_input"),
        )

        OutlinedTextField(
          value = residentialAddress,
          onValueChange = { residentialAddress = it },
          label = { Text("Residential Address *") },
          modifier = Modifier.fillMaxWidth().testTag("reg_address_input"),
        )

        ExposedDropdownMenuBox(
          expanded = stateExpanded,
          onExpandedChange = { stateExpanded = it },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = stateOfOrigin,
            onValueChange = {},
            readOnly = true,
            label = { Text("State of Origin *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stateExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = stateExpanded, onDismissRequest = { stateExpanded = false }) {
            JyCatalogDefaults.NIGERIAN_STATES.forEach { state ->
              DropdownMenuItem(
                text = { Text(state) },
                onClick = {
                  stateOfOrigin = state
                  stateExpanded = false
                },
              )
            }
          }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = nextOfKin,
            onValueChange = { nextOfKin = it },
            label = { Text("Next of Kin *") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("reg_nok_input"),
          )
          OutlinedTextField(
            value = nextOfKinPhone,
            onValueChange = { nextOfKinPhone = it },
            label = { Text("Next of Kin Phone *") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("reg_nok_phone_input"),
          )
        }

        ExposedDropdownMenuBox(
          expanded = progExpanded,
          onExpandedChange = { progExpanded = it },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = "${selectedProgramme.title} (${selectedProgramme.duration} • ${JyCatalogDefaults.formatNaira(selectedProgramme.feeNaira)})",
            onValueChange = {},
            readOnly = true,
            label = { Text("Selected Training Programme *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = progExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = progExpanded, onDismissRequest = { progExpanded = false }) {
            effectiveProgrammes.forEach { prog ->
              DropdownMenuItem(
                text = {
                  Text("${prog.title} — ${prog.duration} (${JyCatalogDefaults.formatNaira(prog.feeNaira)})")
                },
                onClick = {
                  selectedProgramme = prog
                  progExpanded = false
                },
              )
            }
          }
        }

        if (validationError != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              validationError!!,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            when {
              fullName.trim().length < 2 -> validationError = "Enter your full name."
              dateOfBirth.isBlank() -> validationError = "Select or enter your date of birth."
              phoneNumber.trim().length < 7 -> validationError = "Enter a valid phone number."
              !email.contains("@") -> validationError = "Enter a valid email address."
              residentialAddress.trim().length < 4 -> validationError = "Enter your residential address."
              nextOfKin.trim().length < 2 -> validationError = "Enter next of kin full name."
              nextOfKinPhone.trim().length < 7 -> validationError = "Enter next of kin phone number."
              else -> {
                validationError = null
                onSubmit(
                  fullName,
                  gender,
                  dateOfBirth,
                  phoneNumber,
                  email,
                  residentialAddress,
                  stateOfOrigin,
                  nextOfKin,
                  nextOfKinPhone,
                  passportBase64,
                  selectedProgramme,
                )
              }
            }
          },
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth().height(54.dp).testTag("submit_registration_button"),
          shape = RoundedCornerShape(14.dp),
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Saving Registration...")
          } else {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Complete Registration")
          }
        }
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}
