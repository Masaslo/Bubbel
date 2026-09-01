package com.example.bubbel.audio

import kotlinx.coroutines.flow.StateFlow

interface AudioSessionController : AutoCloseable {
    val state: StateFlow<AudioSessionState>
    fun start(config: AudioSessionConfig = AudioSessionConfig())
    fun stop()
    fun setFilterMode(mode: FilterMode)
    override fun close()
}
