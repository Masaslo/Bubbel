package com.example.bubbel.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DefaultAudioSessionController internal constructor(
    private val nativeEngine: NativeAudioGateway,
    private val routeMonitor: RouteMonitor,
    private val pollNativeEvents: Boolean = true,
) : AudioSessionController {
    constructor(context: Context) : this(
        nativeEngine = NativeAudioEngine(context.assets),
        routeMonitor = AudioRouteMonitor(context),
    )

    private val mutableState = MutableStateFlow<AudioSessionState>(AudioSessionState.Idle)
    override val state: StateFlow<AudioSessionState> = mutableState
    private val poller = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "BubbelAudioEvents").apply { isDaemon = true }
    }
    private var requestedRunning = false
    private var config = AudioSessionConfig()
    private var closed = false

    init {
        nativeEngine.create()
        routeMonitor.setRouteChangedListener(::restartForRouteChange)
        routeMonitor.start()
        if (pollNativeEvents) {
            poller.scheduleWithFixedDelay(::pollNativeEvents, 0, 50, TimeUnit.MILLISECONDS)
        }
    }

    @Synchronized
    override fun start(config: AudioSessionConfig) {
        check(!closed) { "Audio session controller is closed" }
        if (requestedRunning) return
        requestedRunning = true
        this.config = config
        mutableState.value = AudioSessionState.Starting
        if (!nativeEngine.start(config)) mutableState.value = AudioSessionState.Failed("could not start audio engine")
    }

    @Synchronized
    override fun stop() {
        if (!requestedRunning) return
        requestedRunning = false
        nativeEngine.stop()
        mutableState.value = AudioSessionState.Idle
    }

    @Synchronized
    override fun setFilterMode(mode: FilterMode) {
        config = config.copy(filterMode = mode)
        nativeEngine.setFilterMode(mode)
    }

    @Synchronized
    private fun restartForRouteChange(route: AudioRoute) {
        if (!requestedRunning || closed) return
        nativeEngine.stop()
        mutableState.value = AudioSessionState.Starting
        if (!nativeEngine.start(config)) {
            mutableState.value = AudioSessionState.Failed("could not restart audio engine for ${route.label}")
        }
    }

    internal fun pollNativeEvents() {
        while (true) {
            if (!pollNextNativeEvent()) return
        }
    }

    internal fun pollNextNativeEvent(): Boolean {
        val event = nativeEngine.pollEvent() ?: return false
        mutableState.value = when (event.substringBefore(':')) {
            "Starting" -> AudioSessionState.Starting
            "Running" -> AudioSessionState.Running(routeMonitor.currentRoute)
            "Recovering" -> AudioSessionState.Recovering(event.substringAfter(':').toIntOrNull() ?: 0)
            "Failed" -> AudioSessionState.Failed(event.substringAfter(':'))
            "Stopped" -> AudioSessionState.Idle
            else -> AudioSessionState.Failed("unknown native audio event: $event")
        }
        return true
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        requestedRunning = false
        poller.shutdownNow()
        routeMonitor.close()
        nativeEngine.stop()
        nativeEngine.destroy()
        mutableState.value = AudioSessionState.Idle
    }
}
