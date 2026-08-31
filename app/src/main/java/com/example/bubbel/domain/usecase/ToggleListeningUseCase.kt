package com.example.bubbel.domain.usecase

import com.example.bubbel.domain.model.ListeningState
import com.example.bubbel.domain.repository.ListeningStateRepository

class ToggleListeningUseCase(private val repository: ListeningStateRepository) {
    operator fun invoke(): ListeningState {
        val nextState = when (repository.currentState()) {
            ListeningState.Inactive -> ListeningState.Active
            ListeningState.Active -> ListeningState.Inactive
        }
        repository.update(nextState)
        return nextState
    }
}
