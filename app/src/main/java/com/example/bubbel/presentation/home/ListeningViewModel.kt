package com.example.bubbel.presentation.home

import androidx.lifecycle.ViewModel
import com.example.bubbel.audio.AudioRoute
import com.example.bubbel.audio.AudioSessionConfig
import com.example.bubbel.audio.AudioSessionController
import com.example.bubbel.audio.AudioSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListeningUiState(
    val isActive: Boolean,
    val route: AudioRoute? = null,
    val failureDescription: String? = null,
)

class ListeningViewModel(private val controller: AudioSessionController) : ViewModel() {
    val state: StateFlow<AudioSessionState> = controller.state
    private val mutablePermissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = mutablePermissionDenied.asStateFlow()
    val uiState: ListeningUiState
        get() = listeningUiState(state.value, permissionDenied.value)

    fun start() {
        mutablePermissionDenied.value = false
        controller.start(AudioSessionConfig())
    }

    fun stop() {
        controller.stop()
    }

    fun onPermissionDenied() {
        mutablePermissionDenied.value = true
    }

    override fun onCleared() {
        controller.close()
    }
}

internal fun listeningUiState(
    state: AudioSessionState,
    permissionDenied: Boolean,
): ListeningUiState {
    if (permissionDenied) {
        return ListeningUiState(isActive = false, failureDescription = "Microfoonmachtiging geweigerd")
    }
    return when (state) {
        is AudioSessionState.Running -> ListeningUiState(isActive = true, route = state.route)
        is AudioSessionState.Failed -> ListeningUiState(isActive = false, failureDescription = state.reason)
        AudioSessionState.Idle,
        AudioSessionState.Starting,
        is AudioSessionState.Recovering -> ListeningUiState(isActive = false)
    }
}
