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
    private val nativeLock = Any()
    private var requestedRunning = false
    private var config = AudioSessionConfig()
    private var lastRoute: AudioRoute? = null
    private var closed = false

    init {
        synchronized(nativeLock) { nativeEngine.create() }
        routeMonitor.setRouteChangedListener(::restartForRouteChange)
        routeMonitor.start()
        if (pollNativeEvents) {
            poller.scheduleWithFixedDelay(::pollNativeEvents, 0, 50, TimeUnit.MILLISECONDS)
        }
    }

    override fun start(config: AudioSessionConfig) {
        synchronized(nativeLock) {
            check(!closed) { "Audio session controller is closed" }
            if (requestedRunning) return
            requestedRunning = true
            this.config = config
            mutableState.value = AudioSessionState.Starting
            routeMonitor.beginCommunication()
            lastRoute = routeMonitor.currentRoute
            if (!nativeEngine.start(config)) {
                requestedRunning = false
                routeMonitor.endCommunication()
                mutableState.value = AudioSessionState.Failed("could not start audio engine")
            }
        }
    }

    override fun stop() {
        synchronized(nativeLock) {
            if (!requestedRunning || closed) return
            requestedRunning = false
            nativeEngine.stop()
            routeMonitor.endCommunication()
            mutableState.value = AudioSessionState.Idle
        }
    }

    override fun setFilterMode(mode: FilterMode) {
        synchronized(nativeLock) {
            if (closed) return
            config = config.copy(filterMode = mode)
            nativeEngine.setFilterMode(mode)
        }
    }

    private fun restartForRouteChange(route: AudioRoute) {
        synchronized(nativeLock) {
            if (!requestedRunning || closed || route == lastRoute) return
            lastRoute = route
            nativeEngine.stop()
            mutableState.value = AudioSessionState.Starting
            if (!nativeEngine.start(config)) {
                mutableState.value = AudioSessionState.Failed("could not restart audio engine for ${route.label}")
            }
        }
    }

    internal fun pollNativeEvents() {
        while (true) {
            if (!pollNextNativeEvent()) return
        }
    }

    internal fun pollNextNativeEvent(): Boolean {
        synchronized(nativeLock) {
            if (closed) return false
            val event = nativeEngine.pollEvent() ?: return false
            mutableState.value = when (event.substringBefore(':')) {
                "Starting" -> AudioSessionState.Starting
                "Running" -> AudioSessionState.Running(routeMonitor.currentRoute).also { lastRoute = it.route }
                "Recovering" -> AudioSessionState.Recovering(event.substringAfter(':').toIntOrNull() ?: 0)
                "Failed" -> AudioSessionState.Failed(event.substringAfter(':'))
                "Stopped" -> AudioSessionState.Idle
                else -> AudioSessionState.Failed("unknown native audio event: $event")
            }
            return true
        }
    }

    override fun close() {
        poller.shutdownNow()
        synchronized(nativeLock) {
            if (closed) return
            closed = true
            requestedRunning = false
            routeMonitor.close()
            nativeEngine.stop()
            nativeEngine.destroy()
            routeMonitor.endCommunication()
            mutableState.value = AudioSessionState.Idle
        }
    }
}
