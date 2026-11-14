package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.remote.GoogleAuthHelper
import com.example.data.repository.CommunicationRepository
import com.example.data.repository.CourseAndAcademicRepository
import com.example.data.repository.FinanceRepository
import com.example.data.repository.ProfileAndSettingsRepository
import com.example.ui.navigation.AppRoutes
import com.example.ui.navigation.AuthenticatedAppShell
import com.example.ui.screens.AccountRecoveryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.JyComputersTheme
import com.example.ui.viewmodel.JyChatViewModel
import com.example.ui.viewmodel.JyMainViewModel
import com.example.util.AppThemeMode
import com.example.util.PreferencesManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val preferencesManager = PreferencesManager(applicationContext)

    setContent {
      val themeMode by
        preferencesManager.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
      val darkTheme =
        when (themeMode) {
          AppThemeMode.DARK -> true
          AppThemeMode.LIGHT -> false
          AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
      val scope = rememberCoroutineScope()

      JyComputersTheme(darkTheme = darkTheme) {
        JyComputersRootContent(
          themeMode = themeMode,
          onThemeModeChange = { mode -> scope.launch { preferencesManager.setThemeMode(mode) } },
        )
      }
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun JyComputersRootContent(
  themeMode: AppThemeMode,
  onThemeModeChange: (AppThemeMode) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }

  val authStateFlow = remember { Firebase.auth.authStateFlow() }
  val currentUser by authStateFlow.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  var preAuthRoute by rememberSaveable { mutableStateOf(AppRoutes.SPLASH) }

  val user = currentUser
  if (user == null) {
    when (preAuthRoute) {
      AppRoutes.SPLASH ->
        SplashScreen(
          onFinished = {
            preAuthRoute =
              if (Firebase.auth.currentUser != null) AppRoutes.WELCOME else AppRoutes.WELCOME
          }
        )
      AppRoutes.WELCOME ->
        WelcomeScreen(
          onContinueToLogin = { preAuthRoute = AppRoutes.LOGIN },
          onOpenRecovery = { preAuthRoute = AppRoutes.RECOVERY },
        )
      AppRoutes.LOGIN ->
        LoginScreen(
          onBack = { preAuthRoute = AppRoutes.WELCOME },
          onOpenRecovery = { preAuthRoute = AppRoutes.RECOVERY },
          onAuthSuccess = {},
        )
      AppRoutes.RECOVERY ->
        AccountRecoveryScreen(
          onBack = { preAuthRoute = AppRoutes.LOGIN },
        )
      else ->
        WelcomeScreen(
          onContinueToLogin = { preAuthRoute = AppRoutes.LOGIN },
          onOpenRecovery = { preAuthRoute = AppRoutes.RECOVERY },
        )
    }
  } else {
    key(user.uid) {
      val mainVm: JyMainViewModel =
        viewModel(
          key = "main_${user.uid}",
          factory =
            viewModelFactory {
              initializer {
                val appContext = context.applicationContext
                val db =
                  FirebaseFirestore.getInstance(
                    appContext.getString(R.string.firestore_database_id)
                  )
                JyMainViewModel(
                  profileRepo = ProfileAndSettingsRepository(db),
                  academicRepo = CourseAndAcademicRepository(db),
                  financeRepo = FinanceRepository(db),
                  currentUserId = user.uid,
                  currentUserEmail = user.email.orEmpty(),
                  currentUserDisplayName = user.displayName ?: user.email ?: "JY Student",
                )
              }
            },
        )

      val isAdminVerified by mainVm.isAdminVerified.collectAsStateWithLifecycle()

      val chatVm: JyChatViewModel =
        viewModel(
          key = "chat_${user.uid}_$isAdminVerified",
          factory =
            viewModelFactory {
              initializer {
                val appContext = context.applicationContext
                val db =
                  FirebaseFirestore.getInstance(
                    appContext.getString(R.string.firestore_database_id)
                  )
                JyChatViewModel(
                  commRepo = CommunicationRepository(db),
                  profileRepo = ProfileAndSettingsRepository(db),
                  currentUserId = user.uid,
                  currentUserName = user.displayName ?: user.email ?: "JY Student",
                  isAdmin = isAdminVerified,
                )
              }
            },
        )

      AuthenticatedAppShell(
        mainVm = mainVm,
        chatVm = chatVm,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onSignOut = {
          preAuthRoute = AppRoutes.WELCOME
          GoogleAuthHelper.signOut(credentialManager = credentialManager, scope = scope)
        },
      )
    }
  }
}
