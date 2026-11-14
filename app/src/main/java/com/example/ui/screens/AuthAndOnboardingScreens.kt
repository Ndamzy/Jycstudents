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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.InstitutionSettings
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.Programme
import com.example.data.remote.GoogleAuthHelper
import com.example.ui.components.OfficialJyLogoBadge
import com.example.ui.components.OfficialOpayBankCard
import com.example.ui.components.PassportCameraCaptureDialog
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
  val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = tween(550),
    label = "splash",
  )
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
      OfficialJyLogoBadge(size = 144.dp, cornerRadius = 24.dp)
      Spacer(modifier = Modifier.height(24.dp))
      Text(
        text = "JY-COMPUTERS",
        style = MaterialTheme.typography.displaySmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "TRAINING INSTITUTE MANAGEMENT SYSTEM",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.88f),
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(28.dp))
      CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 3.dp,
        modifier = Modifier.size(30.dp),
      )
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
        OfficialJyLogoBadge(size = 56.dp, cornerRadius = 12.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            "JY-COMPUTERS",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
          Text(
            "Learn • Practice • Excel",
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
          "Register for certified computer training programmes, verify OPay & online tuition, track results, and download official certificates.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(20.dp))

      val highlights =
        listOf(
          Triple(Icons.Filled.School, "Certified IT Programmes", "Beginner to Advanced training levels"),
          Triple(Icons.Filled.Payments, "Official OPay & Online Fees", "JY-COMPUTERS • 6118366524 • Instant Receipts"),
          Triple(Icons.Filled.MenuBook, "Student & Admin Portals", "Role-based campus management & reports"),
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
        Text("Sign In to JY-Computers Portal", style = MaterialTheme.typography.labelLarge)
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
  var selectedPortalTab by remember { mutableIntStateOf(0) } // 0 = Student Login, 1 = Admin Login
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(if (selectedPortalTab == 1) "Administrator Login" else "Student Portal Login")
        },
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
        OfficialJyLogoBadge(size = 104.dp, cornerRadius = 20.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          if (selectedPortalTab == 1) "Administrator Sign In" else "Student Sign In",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Select your portal below and sign in securely via Google Identity Services.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedPortalTab) {
          Tab(
            selected = selectedPortalTab == 0,
            onClick = { selectedPortalTab = 0 },
            text = { Text("Student Login") },
            icon = { Icon(Icons.Filled.School, contentDescription = null) },
          )
          Tab(
            selected = selectedPortalTab == 1,
            onClick = { selectedPortalTab = 1 },
            text = { Text("Administrator Login") },
            icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                if (selectedPortalTab == 1) "Restricted Admin Access" else "Student & Enrolment Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              if (selectedPortalTab == 1) {
                "Server-verified Role-Based Access Control ensures only authorized JY-Computers Administrators can open management controls."
              } else {
                "Sign in to access your Student Dashboard, course registration, OPay tuition details, or complete new student registration."
              },
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
                Text(
                  if (selectedPortalTab == 1) "Administrator Sign In with Google" else "Student Sign In with Google",
                  style = MaterialTheme.typography.labelLarge,
                )
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
      Column(modifier = Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        OfficialJyLogoBadge(size = 84.dp)
        Spacer(modifier = Modifier.height(16.dp))
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
              "JY-Computers uses Google Identity Services for zero-password security. If you forgot your password or cannot access your account, use Google's official recovery flow below.",
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
              "Need to update your registered email or restore a suspended account? Contact the JY-Computers Admissions Desk:\n\nEmail: admissions@jycomputers.edu.ng\nPhone: +234 800 592 6678",
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
  settings: InstitutionSettings,
  programmes: List<Programme>,
  isBusy: Boolean,
  isAdminAddingStudent: Boolean = false,
  onNotify: (String) -> Unit = {},
  onSubmit: (
    fullName: String,
    gender: String,
    dateOfBirth: String,
    phoneNumber: String,
    email: String,
    residentialAddress: String,
    stateOfOrigin: String,
    localGovernmentArea: String,
    nextOfKin: String,
    nextOfKinPhone: String,
    passportPhotoBase64: String,
    programme: Programme,
    trainingLevel: String,
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
  var lga by remember { mutableStateOf("") }
  var nextOfKin by remember { mutableStateOf("") }
  var nextOfKinPhone by remember { mutableStateOf("") }
  var passportBase64 by remember { mutableStateOf("") }
  var photoError by remember { mutableStateOf<String?>(null) }
  var showCameraCapture by remember { mutableStateOf(false) }

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
            level = it.level,
            scheduleText = it.scheduleText,
          )
        }
      }
    }
  var selectedProgramme by remember(effectiveProgrammes) { mutableStateOf(effectiveProgrammes.first()) }
  var trainingLevel by remember { mutableStateOf("Beginner") }

  var genderExpanded by remember { mutableStateOf(false) }
  var stateExpanded by remember { mutableStateOf(false) }
  var progExpanded by remember { mutableStateOf(false) }
  var levelExpanded by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val regDate = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
  val previewStudentId = remember { "JYC-${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}-AUTO" }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val result = ImageCompressionUtil.validateAndCompressUri(context, uri)
          if (result.base64 != null) {
            passportBase64 = result.base64
            photoError = null
          } else {
            photoError = result.errorMessage ?: "Invalid image selection."
          }
        }
      }
    }

  if (showCameraCapture) {
    PassportCameraCaptureDialog(
      onPhotoAccepted = { base64 ->
        passportBase64 = base64
        photoError = null
        showCameraCapture = false
      },
      onDismiss = { showCameraCapture = false },
    )
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
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            OfficialJyLogoBadge(size = 34.dp, cornerRadius = 8.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(if (isAdminAddingStudent) "Register New Student" else "Student Registration")
          }
        },
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
        // Passport Photograph (Mandatory) Card — Upload OR Live Camera Capture
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              val previewBitmap = remember(passportBase64) {
                ImageCompressionUtil.decodeBase64ToBitmap(passportBase64)
              }
              Box(
                modifier =
                  Modifier.size(88.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                      1.5.dp,
                      if (passportBase64.isBlank()) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                      else MaterialTheme.colorScheme.primary,
                      RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
              ) {
                if (previewBitmap != null) {
                  Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Passport photograph preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(
                    Icons.Filled.Person,
                    contentDescription = "No passport photo uploaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                  )
                }
              }
              Spacer(modifier = Modifier.width(14.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  "Passport Photograph *",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  "Required: Upload JPG/PNG (max 2MB) or capture live passport photo.\nReg No: $previewStudentId • $regDate",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.weight(1f).testTag("upload_passport_button"),
              ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (passportBase64.isBlank()) "Upload Photo" else "Replace Photo")
              }
              Button(
                onClick = { showCameraCapture = true },
                modifier = Modifier.weight(1f).testTag("capture_passport_button"),
              ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capture Passport Photograph", maxLines = 1)
              }
            }

            if (passportBase64.isNotBlank()) {
              TextButton(
                onClick = { passportBase64 = "" },
                modifier = Modifier.align(Alignment.End).testTag("remove_passport_button"),
              ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Remove Photograph", color = MaterialTheme.colorScheme.error)
              }
            }

            if (photoError != null) {
              Text(
                photoError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          ExposedDropdownMenuBox(
            expanded = stateExpanded,
            onExpandedChange = { stateExpanded = it },
            modifier = Modifier.weight(1f),
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

          OutlinedTextField(
            value = lga,
            onValueChange = { lga = it },
            label = { Text("Local Govt Area (LGA) *") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("reg_lga_input"),
          )
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
            label = { Text("Selected Training Course *") },
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

        ExposedDropdownMenuBox(
          expanded = levelExpanded,
          onExpandedChange = { levelExpanded = it },
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = trainingLevel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Training Level *") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
          )
          ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
            JyCatalogDefaults.TRAINING_LEVELS.forEach { lvl ->
              DropdownMenuItem(
                text = { Text(lvl) },
                onClick = {
                  trainingLevel = lvl
                  levelExpanded = false
                },
              )
            }
          }
        }

        // Official JY-COMPUTERS OPay Account Details
        OfficialOpayBankCard(settings = settings, onCopied = onNotify)

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
              passportBase64.isBlank() ->
                validationError = "Passport photograph is required. Upload from device or capture with camera."
              fullName.trim().length < 2 -> validationError = "Enter your full name."
              dateOfBirth.isBlank() -> validationError = "Select or enter your date of birth."
              phoneNumber.trim().length < 7 -> validationError = "Enter a valid phone number."
              !email.contains("@") -> validationError = "Enter a valid email address."
              residentialAddress.trim().length < 4 -> validationError = "Enter your residential address."
              lga.trim().length < 2 -> validationError = "Enter your Local Government Area (LGA)."
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
                  lga,
                  nextOfKin,
                  nextOfKinPhone,
                  passportBase64,
                  selectedProgramme,
                  trainingLevel,
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
