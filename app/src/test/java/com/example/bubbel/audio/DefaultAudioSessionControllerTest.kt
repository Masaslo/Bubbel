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
    override val currentRoute: AudioRoute,
) : RouteMonitor {
    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) = Unit
    override fun start() = Unit
    override fun beginCommunication() = Unit
    override fun endCommunication() = Unit
    override fun close() = Unit
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
