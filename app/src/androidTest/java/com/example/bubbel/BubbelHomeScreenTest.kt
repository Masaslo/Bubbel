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
import com.example.bubbel.audio.AudioRoute
import com.example.bubbel.audio.AudioSessionConfig
import com.example.bubbel.audio.AudioSessionController
import com.example.bubbel.audio.AudioSessionState
import com.example.bubbel.audio.FilterMode
import com.example.bubbel.presentation.home.ListeningViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class BubbelHomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bubbleActivatesOnlyAfterTheControllerReportsRunning() {
        val controller = ScreenTestController()
        val viewModel = ListeningViewModel(controller)
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen(viewModel, viewModel::start) }
        }

        composeRule.onNodeWithTag("bubble_toggle")
            .assert(hasContentDescription("Luistermodus uit"))
            .performClick()
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus starten"))

        composeRule.runOnIdle {
            controller.state.value = AudioSessionState.Running(AudioRoute.Wired)
        }
        composeRule.onNodeWithTag("bubble_toggle")
            .assertIsSelected()
            .assert(hasContentDescription("Luistermodus aan via ${AudioRoute.Wired.label}"))
            .performClick()
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus uit"))
    }

    @Test
    fun bubbleTracksRecoveryAndFailureWithoutAnotherTap() {
        val controller = ScreenTestController().apply {
            state.value = AudioSessionState.Running(AudioRoute.Wired)
        }
        val viewModel = ListeningViewModel(controller)
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen(viewModel, viewModel::start) }
        }

        composeRule.onNodeWithTag("bubble_toggle").assertIsSelected()
        composeRule.runOnIdle {
            controller.state.value = AudioSessionState.Recovering(1)
        }
        composeRule.onNodeWithTag("bubble_toggle")
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus herstellen"))

        composeRule.runOnIdle {
            controller.state.value = AudioSessionState.Running(AudioRoute.Bluetooth)
        }
        composeRule.onNodeWithTag("bubble_toggle")
            .assertIsSelected()
            .assert(hasContentDescription("Luistermodus aan via ${AudioRoute.Bluetooth.label}"))
        composeRule.onNodeWithTag("audio_route_status")
            .assertExists()

        composeRule.runOnIdle {
            controller.state.value = AudioSessionState.Failed("stream disconnected")
        }
        composeRule.onNodeWithTag("bubble_toggle")
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus mislukt: stream disconnected"))
        composeRule.onNodeWithTag("audio_route_status").assertDoesNotExist()
    }

    @Test
    fun permissionDenialUpdatesTheBubbleWithoutAControllerStateChange() {
        val viewModel = ListeningViewModel(ScreenTestController())
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen(viewModel, viewModel::start) }
        }

        composeRule.onNodeWithTag("bubble_toggle").assertIsNotSelected()
        composeRule.runOnIdle { viewModel.onPermissionDenied() }
        composeRule.onNodeWithTag("bubble_toggle")
            .assertIsNotSelected()
            .assert(hasContentDescription("Luistermodus mislukt: microfoonmachtiging geweigerd"))
        composeRule.onNodeWithTag("audio_route_status").assertDoesNotExist()
    }

    @Test
    fun settingsControlIsAvailableForTalkBack() {
        val viewModel = ListeningViewModel(ScreenTestController())
        composeRule.setContent {
            BubbelTheme { BubbelHomeScreen(viewModel, viewModel::start) }
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

private class ScreenTestController : AudioSessionController {
    override val state = MutableStateFlow<AudioSessionState>(AudioSessionState.Idle)
    override fun start(config: AudioSessionConfig) { state.value = AudioSessionState.Starting }
    override fun stop() { state.value = AudioSessionState.Idle }
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun close() = Unit
}
