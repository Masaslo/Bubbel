package com.example.bubbel.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultAudioSessionControllerTest {
    @Test
    fun mapsNativeEventsAndMakesStartStopIdempotent() {
        val engine = FakeNativeAudioEngine(
            mutableListOf(
                "Starting:0",
                "Running:48000",
                "Recovering:2",
                "Failed:audio recovery exhausted",
                "Stopped:0",
            ),
        )
        val route = AudioRoute.Wired
        val controller = DefaultAudioSessionController(engine, FakeRouteMonitor(route), pollNativeEvents = false)

        controller.start()
        controller.start()
        controller.pollNextNativeEvent()
        assertEquals(AudioSessionState.Starting, controller.state.value)
        controller.pollNextNativeEvent()
        assertEquals(AudioSessionState.Running(route), controller.state.value)
        controller.pollNextNativeEvent()
        assertEquals(AudioSessionState.Recovering(2), controller.state.value)
        controller.pollNextNativeEvent()
        assertEquals(AudioSessionState.Failed("audio recovery exhausted"), controller.state.value)
        controller.pollNextNativeEvent()
        assertEquals(AudioSessionState.Idle, controller.state.value)

        controller.stop()
        controller.stop()

        assertEquals(1, engine.startCount)
        assertEquals(1, engine.stopCount)
    }
}

private class FakeNativeAudioEngine(
    private val events: MutableList<String>,
) : NativeAudioGateway {
    var startCount = 0
    var stopCount = 0

    override fun create() = Unit
    override fun start(config: AudioSessionConfig): Boolean {
        startCount++
        return true
    }
    override fun stop() { stopCount++ }
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun pollEvent(): String? = events.removeFirstOrNull()
    override fun destroy() = Unit
}

private class FakeRouteMonitor(
    override val currentRoute: AudioRoute,
) : RouteMonitor {
    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) = Unit
    override fun start() = Unit
    override fun close() = Unit
}
