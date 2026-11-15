package com.example.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.UserProfile
import com.example.data.remote.AdminAuthClient
import com.example.ui.navigation.AdminAccessDeniedScreen
import com.example.ui.screens.AdminRolesAndLogsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.JyComputersTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SingleAdminUiSecurityTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var localServer: LocalSingleAdminServer

  @Before
  fun setUp() {
    localServer = LocalSingleAdminServer { _, _ -> "" }
    localServer.start()
    AdminAuthClient.setEndpointOverrideForTesting(localServer.baseUrl)
  }

  @After
  fun tearDown() {
    AdminAuthClient.setEndpointOverrideForTesting(null)
    localServer.stop()
  }

  @Test
  fun loginScreen_replacesGoogleAdminAuthWithUsernamePasswordForm() {
    composeTestRule.setContent {
      JyComputersTheme {
        LoginScreen(
          onBack = {},
          onOpenRecovery = {},
          onAuthSuccess = {},
        )
      }
    }
    composeTestRule.waitForIdle()

    // Student tab keeps Student Google Sign-In
    composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Student Sign In with Google").assertIsDisplayed()

    // Switch to Administrator Login tab
    composeTestRule.onNodeWithTag("tab_admin_login").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Google admin sign-in must be completely gone
    composeTestRule.onNodeWithText("Administrator Sign In with Google").assertDoesNotExist()
    composeTestRule.onNodeWithTag("google_sign_in_button").assertDoesNotExist()

    // Secure username + password form elements are present
    composeTestRule.onNodeWithTag("admin_username_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_password_input").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_toggle_password_visibility").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_login_submit_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_forgot_password_button").performScrollTo().assertIsDisplayed()

    // Enter invalid credentials and submit -> renders generic error banner
    composeTestRule.onNodeWithTag("admin_username_input").performScrollTo().performTextInput("wrong_user")
    composeTestRule.onNodeWithTag("admin_password_input").performScrollTo().performTextInput("wrong_pass")
    composeTestRule.onNodeWithTag("admin_login_submit_button").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5000L) {
      runCatching {
        composeTestRule.onNodeWithTag("admin_login_error_banner").performScrollTo().assertIsDisplayed()
        true
      }.getOrDefault(false)
    }
    composeTestRule.onNodeWithText("Invalid administrator credentials.").assertIsDisplayed()

    // Owner Recovery modal opens without crashing
    composeTestRule.onNodeWithTag("admin_forgot_password_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Owner Administrator Recovery").assertIsDisplayed()
    composeTestRule.onNodeWithTag("admin_recovery_key_input").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun adminAccessDeniedScreen_blocksNonAdminAndOffersReturnAction() {
    var returned = false
    composeTestRule.setContent {
      JyComputersTheme {
        AdminAccessDeniedScreen(onReturnHome = { returned = true })
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("admin_access_denied_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Administrator Access Denied").assertIsDisplayed()
    composeTestRule.onNodeWithText("Return to My Portal").performClick()
    assertTrue(returned)
  }

  @Test
  fun adminRolesAndLogsScreen_hasNoGrantAdminButton() {
    val sampleStudent =
      UserProfile(
        userId = "stu_ui_1",
        role = "student",
        fullName = "Bola Student",
        email = "bola@test.com",
      )
    composeTestRule.setContent {
      JyComputersTheme {
        AdminRolesAndLogsScreen(
          allUsers = listOf(sampleStudent),
          programmes = emptyList(),
          activityLogs = emptyList(),
          onAssignRole = { _, _, _, _, _, _ -> },
          currentUserId = "jy_single_owner_admin",
          instructors = emptyList(),
        )
      }
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("admin_roles_and_logs_screen").assertIsDisplayed()
    composeTestRule.onNodeWithText("Grant Admin").assertDoesNotExist()
    composeTestRule.onNodeWithText("Make Instructor").assertIsDisplayed()
  }
}
