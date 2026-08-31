package com.example.bubbel

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.bubbel.ui.theme.BubbelTheme
import org.junit.Rule
import org.junit.Test

class BubbelHomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingTheBubbleChangesListeningModeToActive() {
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen() }
        }

        composeRule.onNodeWithTag("bubble_toggle")
            .assert(hasContentDescription("Luistermodus uit"))
            .performClick()
            .assertIsSelected()
            .assert(hasContentDescription("Luistermodus aan"))
            .performClick()
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus uit"))
    }

    @Test
    fun settingsControlIsAvailableForTalkBack() {
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen() }
        }

        composeRule.onNodeWithContentDescription("Instellingen")
            .performClick()

        composeRule.onNodeWithContentDescription("Geluiden dempen")
            .assertHasClickAction()
            .assertIsOn()
        composeRule.onNodeWithContentDescription("Trillingen")
            .assertHasClickAction()
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }
}
