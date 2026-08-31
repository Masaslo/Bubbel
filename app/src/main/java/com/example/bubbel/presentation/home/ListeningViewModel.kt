package com.example.bubbel.presentation.home

import androidx.lifecycle.ViewModel
import com.example.bubbel.domain.model.ListeningState
import com.example.bubbel.domain.repository.ListeningStateRepository
import com.example.bubbel.domain.usecase.ToggleListeningUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListeningViewModel(repository: ListeningStateRepository) : ViewModel() {
    private val toggleListening = ToggleListeningUseCase(repository)
    private val mutableState = MutableStateFlow(repository.currentState())

    val state: StateFlow<ListeningState> = mutableState.asStateFlow()

    fun toggle() {
        mutableState.value = toggleListening()
    }
}
