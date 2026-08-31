package com.example.bubbel.domain.repository

import com.example.bubbel.domain.model.ListeningState

interface ListeningStateRepository {
    fun currentState(): ListeningState
    fun update(state: ListeningState)
}
