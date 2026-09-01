package com.example.bubbel.presentation.home

import com.example.bubbel.audio.AudioRoute
import com.example.bubbel.audio.AudioSessionConfig
import com.example.bubbel.audio.AudioSessionController
import com.example.bubbel.audio.AudioSessionState
import com.example.bubbel.audio.FilterMode
import com.example.bubbel.audio.InputPreference
import com.example.bubbel.shouldStopForPermissionRevocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningViewModelTest {
    @Test
    fun onlyRunningStateMakesTheBubbleActiveAndExposesItsRoute() {
        val controller = FakeAudioSessionController(AudioSessionState.Idle)
        val viewModel = ListeningViewModel(controller)

        assertFalse(viewModel.uiState.isActive)
        controller.emit(AudioSessionState.Starting)
        assertFalse(viewModel.uiState.isActive)
        controller.emit(AudioSessionState.Recovering(1))
        assertFalse(viewModel.uiState.isActive)
        controller.emit(AudioSessionState.Failed("native error"))
        assertFalse(viewModel.uiState.isActive)
        controller.emit(AudioSessionState.Running(AudioRoute.Bluetooth))

        assertTrue(viewModel.uiState.isActive)
        assertEquals(AudioRoute.Bluetooth, viewModel.uiState.route)
    }

    @Test
    fun startUsesBalancedAutomaticUnityGainAndRetriesClearPermissionDenial() {
        val controller = FakeAudioSessionController(AudioSessionState.Failed("permission was denied"))
        val viewModel = ListeningViewModel(controller)

        viewModel.onPermissionDenied()
        assertTrue(viewModel.permissionDenied.value)
        assertEquals(0, controller.startCount)

        viewModel.start()

        assertFalse(viewModel.permissionDenied.value)
        assertEquals(1, controller.startCount)
        assertEquals(FilterMode.Balanced, controller.lastStartConfig?.filterMode)
        assertEquals(InputPreference.Automatic, controller.lastStartConfig?.inputPreference)
        assertEquals(1.0f, controller.lastStartConfig?.outputGain)
    }

    @Test
    fun permissionRevocationStopsAnInFlightSessionAndShowsInactiveFailure() {
        val controller = FakeAudioSessionController(AudioSessionState.Running(AudioRoute.Wired))
        val viewModel = ListeningViewModel(controller)

        viewModel.onMicrophonePermissionRevoked()

        assertEquals(1, controller.stopCount)
        assertTrue(viewModel.permissionDenied.value)
        assertFalse(viewModel.uiState.isActive)
    }

    @Test
    fun permissionRevocationStopsOnlyInFlightAudioStates() {
        assertTrue(shouldStopForPermissionRevocation(AudioSessionState.Starting))
        assertTrue(shouldStopForPermissionRevocation(AudioSessionState.Running(AudioRoute.Wired)))
        assertTrue(shouldStopForPermissionRevocation(AudioSessionState.Recovering(1)))
        assertFalse(shouldStopForPermissionRevocation(AudioSessionState.Idle))
        assertFalse(shouldStopForPermissionRevocation(AudioSessionState.Failed("native error")))
    }

    @Test
    fun stopDelegatesToTheController() {
        val controller = FakeAudioSessionController(AudioSessionState.Recovering(1))
        val viewModel = ListeningViewModel(controller)

        viewModel.stop()

        assertEquals(1, controller.stopCount)
    }

    @Test
    fun viewModelCleanupClosesItsController() {
        val controller = FakeAudioSessionController(AudioSessionState.Idle)
        val viewModel = TestListeningViewModel(controller)

        viewModel.clearForTest()

        assertEquals(1, controller.closeCount)
    }
}

private class FakeAudioSessionController(initialState: AudioSessionState) : AudioSessionController {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<AudioSessionState> = mutableState

    var startCount = 0
    var stopCount = 0
    var closeCount = 0
    var lastStartConfig: AudioSessionConfig? = null

    override fun start(config: AudioSessionConfig) {
        startCount++
        lastStartConfig = config
    }
    override fun stop() { stopCount++ }
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun close() { closeCount++ }

    fun emit(state: AudioSessionState) {
        mutableState.value = state
    }
}

private class TestListeningViewModel(controller: AudioSessionController) : ListeningViewModel(controller) {
    fun clearForTest() = onCleared()
}
