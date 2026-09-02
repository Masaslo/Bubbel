package com.example.bubbel.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import android.media.AudioDeviceInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultAudioSessionControllerTest {
    @Test
    fun classifiesActiveDeviceTypesWithStableWarnings() {
        assertEquals(AudioRoute.Bluetooth, audioRouteForDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals(AudioRoute.Wired, audioRouteForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRoute.Usb, audioRouteForDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(AudioRoute.Speaker, audioRouteForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
    @Test
    fun mapsNativeEventsAndMakesStartStopIdempotent() {
        val events = mutableListOf<String>()
        val engine = FakeNativeAudioEngine(events)
        val route = AudioRoute.Wired
        val controller = DefaultAudioSessionController(engine, FakeRouteMonitor(route), pollNativeEvents = false)

        controller.start()
        controller.start()
        events += "Starting:0"
        events += "Running:48000"
        events += "Recovering:2"
        events += "Failed:audio recovery exhausted"
        events += "Stopped:0"
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
        assertEquals(0, engine.stopCount)
    }

    @Test
    fun nativeFailureReleasesSessionSoRetryStartsAgain() {
        val events = mutableListOf<String>()
        val engine = FakeNativeAudioEngine(events)
        val routes = FakeRouteMonitor(AudioRoute.Wired)
        val controller = DefaultAudioSessionController(engine, routes, pollNativeEvents = false)

        controller.start()
        events += "Failed:audio recovery exhausted"
        controller.pollNextNativeEvent()
        controller.start()

        assertEquals(2, engine.startCount)
        assertEquals(1, routes.endCount)
        assertEquals(AudioSessionState.Starting, controller.state.value)
    }

    @Test
    fun queuedStoppedFromRouteRestartDoesNotStopTheReplacementSession() {
        val events = mutableListOf<String>()
        val engine = FakeNativeAudioEngine(events)
        val routes = FakeRouteMonitor(AudioRoute.Wired)
        val controller = DefaultAudioSessionController(engine, routes, pollNativeEvents = false)

        controller.start()
        events += "Stopped:0"
        routes.notifyRouteChanged(AudioRoute.Speaker)
        events += "Starting:0"
        events += "Running:48000"
        controller.pollNativeEvents()
        controller.stop()

        assertEquals(2, engine.startCount)
        assertEquals(2, engine.stopCount)
        assertEquals(AudioSessionState.Idle, controller.state.value)
    }

    @Test
    fun prefersExternalCommunicationRoutesAndHonorsPhonePreference() {
        assertEquals(
            AudioRoute.Wired,
            preferredCommunicationRoute(listOf(AudioRoute.Speaker, AudioRoute.Wired), InputPreference.Automatic),
        )
        assertEquals(
            AudioRoute.Speaker,
            preferredCommunicationRoute(listOf(AudioRoute.Wired, AudioRoute.Speaker), InputPreference.Phone),
        )
    }

    @Test
    fun closeWaitsForAnInFlightPollBeforeDestroyingNativeAudio() {
        val engine = BlockingNativeAudioEngine()
        val controller = DefaultAudioSessionController(engine, FakeRouteMonitor(AudioRoute.Wired), pollNativeEvents = false)
        val pollThread = Thread { controller.pollNextNativeEvent() }
        val closeThread = Thread { controller.close() }

        pollThread.start()
        check(engine.pollEntered.await(1, TimeUnit.SECONDS))
        closeThread.start()
        val destroyBeforePollReturned = engine.destroyed.await(200, TimeUnit.MILLISECONDS)
        engine.allowPollReturn.countDown()
        pollThread.join(1_000)
        closeThread.join(1_000)

        assertFalse("destroy must wait for the active poll", destroyBeforePollReturned)
        assertFalse("the poll must not return after native destroy", engine.pollReturnedAfterDestroy)
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
    initialRoute: AudioRoute,
) : RouteMonitor {
    override var currentRoute = initialRoute
    var endCount = 0
    private var routeChangedListener: (AudioRoute) -> Unit = {}
    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) {
        routeChangedListener = listener
    }
    override fun start() = Unit
    override fun beginCommunication(inputPreference: InputPreference) = Unit
    override fun endCommunication() { endCount++ }
    override fun close() = Unit
    fun notifyRouteChanged(route: AudioRoute) {
        currentRoute = route
        routeChangedListener(route)
    }
}

private class BlockingNativeAudioEngine : NativeAudioGateway {
    val pollEntered = CountDownLatch(1)
    val allowPollReturn = CountDownLatch(1)
    val destroyed = CountDownLatch(1)
    @Volatile var pollReturnedAfterDestroy = false
    @Volatile private var isDestroyed = false

    override fun create() = Unit
    override fun start(config: AudioSessionConfig) = true
    override fun stop() = Unit
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun pollEvent(): String? {
        pollEntered.countDown()
        check(allowPollReturn.await(1, TimeUnit.SECONDS))
        pollReturnedAfterDestroy = isDestroyed
        return null
    }
    override fun destroy() {
        isDestroyed = true
        destroyed.countDown()
    }
}
