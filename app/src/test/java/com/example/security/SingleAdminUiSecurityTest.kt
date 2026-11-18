package com.example.security

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.JyCatalogDefaults
import com.example.data.model.UserProfile
import com.example.ui.navigation.AdminAccessDeniedScreen
import com.example.ui.screens.AdminRolesAndLogsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.theme.JyComputersTheme
import org.junit.Assert.assertTrue
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

  @Test
  fun loginScreen_rendersOptionAOwnerGoogleSignInOnAdminTab() {
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

    // Student Google button is replaced by Exclusive Owner Google Sign-In button
    composeTestRule.onNodeWithTag("google_sign_in_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("admin_username_input").assertDoesNotExist()
    composeTestRule.onNodeWithTag("admin_password_input").assertDoesNotExist()

    composeTestRule.onNodeWithTag("admin_google_sign_in_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Administrator Sign In with Google (Owner Only)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Owner Administrator Login").assertIsDisplayed()
    composeTestRule
      .onNodeWithText(
        "Exclusive JY-COMPUTERS owner sign-in restricted to ${JyCatalogDefaults.OWNER_ADMIN_EMAIL} with Firebase custom claims."
      )
      .assertIsDisplayed()
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
          currentUserId = "joshua_owner_uid",
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
