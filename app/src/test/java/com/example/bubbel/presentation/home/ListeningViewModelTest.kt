package com.example.bubbel.presentation.home

import com.example.bubbel.audio.AudioRoute
import com.example.bubbel.audio.AudioSessionConfig
import com.example.bubbel.audio.AudioSessionController
import com.example.bubbel.audio.AudioSessionState
import com.example.bubbel.audio.FilterMode
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
}

private class FakeAudioSessionController(initialState: AudioSessionState) : AudioSessionController {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<AudioSessionState> = mutableState

    override fun start(config: AudioSessionConfig) = Unit
    override fun stop() = Unit
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun close() = Unit

    fun emit(state: AudioSessionState) {
        mutableState.value = state
    }
}
