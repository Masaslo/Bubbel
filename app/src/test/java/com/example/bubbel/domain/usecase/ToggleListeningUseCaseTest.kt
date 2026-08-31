package com.example.bubbel.domain.usecase

import com.example.bubbel.domain.model.ListeningState
import com.example.bubbel.domain.repository.ListeningStateRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleListeningUseCaseTest {
    @Test
    fun invokeSwitchesFromInactiveToActive() {
        val repository = FakeListeningStateRepository(ListeningState.Inactive)

        val result = ToggleListeningUseCase(repository)()

        assertEquals(ListeningState.Active, result)
        assertEquals(ListeningState.Active, repository.currentState())
    }

    @Test
    fun invokeSwitchesFromActiveToInactive() {
        val repository = FakeListeningStateRepository(ListeningState.Active)

        val result = ToggleListeningUseCase(repository)()

        assertEquals(ListeningState.Inactive, result)
        assertEquals(ListeningState.Inactive, repository.currentState())
    }
}

private class FakeListeningStateRepository(initialState: ListeningState) : ListeningStateRepository {
    private var state = initialState

    override fun currentState() = state

    override fun update(state: ListeningState) {
        this.state = state
    }
}
