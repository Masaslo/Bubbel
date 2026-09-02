package com.example.bubbel.audio

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DefaultAudioSessionController internal constructor(
    private val nativeEngine: NativeAudioGateway,
    private val routeMonitor: RouteMonitor,
    private val pollNativeEvents: Boolean = true,
    private val controlExecutor: ExecutorService = newControlExecutor(),
) : AudioSessionController {
    constructor(context: Context) : this(NativeAudioEngine(context.assets), AudioRouteMonitor(context))

    private val mutableState = MutableStateFlow<AudioSessionState>(AudioSessionState.Idle)
    override val state: StateFlow<AudioSessionState> = mutableState
    private val stateLock = Any()
    private val poller = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "BubbelAudioEvents").apply { isDaemon = true }
    }

    // Only the serial control executor accesses the native gateway and route monitor.
    private var initialized = false
    private var requestedRunning = false
    private var requestGeneration = 0L
    private var config = AudioSessionConfig()
    private var lastRouteIdentity: String? = null
    @Volatile private var closed = false
    private val pollPending = AtomicBoolean(false)

    init {
        submitControl("initialize") {
            nativeEngine.create()
            routeMonitor.setRouteChangedListener(::onRouteChanged)
            routeMonitor.start()
            initialized = true
            log("native audio gateway created")
        }
        if (pollNativeEvents) {
            poller.scheduleWithFixedDelay(
                ::schedulePoll, 0, 50, TimeUnit.MILLISECONDS,
            )
        }
    }

    override fun start(config: AudioSessionConfig) {
        val generation = synchronized(stateLock) {
            check(!closed) { "Audio session controller is closed" }
            if (requestedRunning) return
            requestedRunning = true
            this.config = config
            mutableState.value = AudioSessionState.Starting
            ++requestGeneration
        }
        submitControl("start") { startOnControlThread(generation) }
    }

    override fun stop() {
        val generation = synchronized(stateLock) {
            if (closed || !requestedRunning) return
            requestedRunning = false
            mutableState.value = AudioSessionState.Idle
            ++requestGeneration
        }
        submitControl("stop") { stopOnControlThread(generation) }
    }

    override fun setFilterMode(mode: FilterMode) {
        synchronized(stateLock) {
            if (closed) return
            config = config.copy(filterMode = mode)
        }
        submitControl("set filter mode") { nativeEngine.setFilterMode(mode) }
    }

    private fun startOnControlThread(generation: Long) {
        if (!isCurrentRunningRequest(generation)) return
        check(initialized) { "audio engine is not initialized" }
        val requestedConfig = synchronized(stateLock) { config }
        routeMonitor.beginCommunication(requestedConfig.inputPreference)
        lastRouteIdentity = routeMonitor.routeIdentity
        discardPendingNativeEvents()
        if (!nativeEngine.start(requestedConfig)) {
            drainNativeEvents()
            if (isCurrentRunningRequest(generation)) {
                publishFailure(null, "could not start audio engine", generation)
            }
        } else if (!isCurrentRunningRequest(generation)) {
            // A stop/start arrived while JNI opened streams: suppress that stale session.
            stopNativeAndDiscardPendingEvents()
            routeMonitor.endCommunication()
        }
    }

    private fun stopOnControlThread(generation: Long) {
        stopNativeAndDiscardPendingEvents()
        routeMonitor.endCommunication()
    }

    private fun onRouteChanged(route: AudioRoute) {
        submitControl("route change") { restartForRouteChange(route) }
    }

    private fun restartForRouteChange(route: AudioRoute) {
        val generation = synchronized(stateLock) { requestGeneration }
        routeMonitor.refreshCommunication()
        if (!isCurrentRunningRequest(generation) || routeMonitor.routeIdentity == lastRouteIdentity) return
        lastRouteIdentity = routeMonitor.routeIdentity
        stopNativeAndDiscardPendingEvents()
        if (!publishIfCurrent(generation, AudioSessionState.Starting)) return
        if (!nativeEngine.start(synchronized(stateLock) { config })) {
            drainNativeEvents()
            if (isCurrentRunningRequest(generation)) {
                publishFailure(null, "could not restart audio engine for ${route.label}", generation)
            }
        }
    }

    internal fun pollNativeEvents() = schedulePoll()

    internal fun pollNextNativeEvent(): Boolean {
        submitControl("poll") { pollOneNativeEvent() }
        return true
    }

    private fun drainNativeEvents() {
        while (pollOneNativeEvent()) Unit
    }

    private fun pollOneNativeEvent(): Boolean {
        if (closed) return false
        val event = nativeEngine.pollEvent() ?: return false
        when (event.substringBefore(':')) {
            "Starting" -> publishWhileRequested(AudioSessionState.Starting)
            "Running" -> {
                val route = routeMonitor.currentRoute
                if (publishWhileRequested(AudioSessionState.Running(route))) {
                    lastRouteIdentity = routeMonitor.routeIdentity
                }
            }
            "Recovering" -> publishWhileRequested(AudioSessionState.Recovering(event.substringAfter(':').toIntOrNull() ?: 0))
            "Failed" -> failureState(event.substringAfter(':'))
            "Stopped" -> stopFromNative()
            else -> failureState("unknown native audio event: $event")
        }
        return true
    }

    private fun failureState(reason: String): AudioSessionState.Failed {
        val published = synchronized(stateLock) {
            if (!requestedRunning || closed) false else {
                requestedRunning = false
                ++requestGeneration
                mutableState.value = AudioSessionState.Failed(reason)
                true
            }
        }
        if (!published) return AudioSessionState.Failed(reason)
        routeMonitor.endCommunication()
        log("audio session failed: ${reason.ifBlank { "native engine stopped" }}")
        return AudioSessionState.Failed(reason)
    }

    private fun stopFromNative() {
        val published = synchronized(stateLock) {
            if (!requestedRunning || closed) false else {
                requestedRunning = false
                ++requestGeneration
                mutableState.value = AudioSessionState.Idle
                true
            }
        }
        if (published) routeMonitor.endCommunication()
    }

    private fun publishWhileRequested(newState: AudioSessionState): Boolean = synchronized(stateLock) {
        if (!closed && requestedRunning) {
            mutableState.value = newState
            true
        } else false
    }

    private fun publishIfCurrent(generation: Long, newState: AudioSessionState): Boolean = synchronized(stateLock) {
        if (!closed && requestedRunning && requestGeneration == generation) {
            mutableState.value = newState
            true
        } else false
    }

    private fun publishFailure(error: Throwable?, fallback: String, generation: Long?) {
        if (generation != null && !isCurrentRequest(generation)) return
        val reason = error?.message?.takeIf { it.isNotBlank() } ?: fallback
        synchronized(stateLock) { requestedRunning = false; ++requestGeneration }
        runCatching { routeMonitor.endCommunication() }
        log("$fallback: $reason", error)
        mutableState.value = AudioSessionState.Failed(reason)
    }

    private fun stopNativeAndDiscardPendingEvents() {
        nativeEngine.stop()
        discardPendingNativeEvents()
    }

    private fun discardPendingNativeEvents() {
        while (nativeEngine.pollEvent() != null) Unit
    }

    override fun close() {
        val shouldClose = synchronized(stateLock) {
            if (closed) false else {
                closed = true
                requestedRunning = false
                ++requestGeneration
                true
            }
        }
        if (!shouldClose) return
        mutableState.value = AudioSessionState.Idle
        poller.shutdownNow()
        submitControl("close") {
            try {
                routeMonitor.close()
                nativeEngine.stop()
                nativeEngine.destroy()
                log("native audio gateway destroyed")
            } finally {
                controlExecutor.shutdown()
            }
        }
    }

    private fun isCurrentRunningRequest(generation: Long): Boolean = synchronized(stateLock) {
        !closed && requestedRunning && requestGeneration == generation
    }

    private fun isCurrentRequest(generation: Long): Boolean = synchronized(stateLock) {
        !closed && requestGeneration == generation
    }

    private fun submitControl(operation: String, task: () -> Unit) {
        if (closed && operation != "close") return
        try {
            controlExecutor.execute {
                try {
                    task()
                } catch (error: Throwable) {
                    publishFailure(error, "audio control task '$operation' failed", null)
                }
            }
        } catch (_: RejectedExecutionException) {
            if (!closed) publishFailure(null, "audio control executor rejected $operation", null)
        }
    }

    private fun schedulePoll() {
        if (closed || !pollPending.compareAndSet(false, true)) return
        submitControl("poll") {
            try {
                drainNativeEvents()
            } finally {
                pollPending.set(false)
            }
        }
    }

    private fun log(message: String, error: Throwable? = null) {
        // JVM unit tests have the Android Log stub; production still writes all control diagnostics to Logcat.
        runCatching { if (error == null) Log.i(LOG_TAG, message) else Log.e(LOG_TAG, message, error) }
    }

    private companion object {
        const val LOG_TAG = "BubbelAudio"
        fun newControlExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "BubbelAudioControl").apply { isDaemon = true }
        }
    }
}
