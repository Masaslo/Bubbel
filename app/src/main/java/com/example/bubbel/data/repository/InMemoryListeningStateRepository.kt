package com.example.bubbel.data.repository

import com.example.bubbel.domain.model.ListeningState
import com.example.bubbel.domain.repository.ListeningStateRepository

class InMemoryListeningStateRepository : ListeningStateRepository {
    private var state = ListeningState.Inactive

    override fun currentState() = state

    override fun update(state: ListeningState) {
        this.state = state
    }
}
