package com.example.bubbel.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultAudioSessionControllerTest {
    @Test
    fun startPublishesStartingWithoutWaitingForBlockingNativeWork() {
        val engine = BlockingEngine()
        val controller = DefaultAudioSessionController(engine, FakeRoutes(), pollNativeEvents = false)
        check(engine.createEntered.await(1, TimeUnit.SECONDS))

        controller.start()

        assertEquals(AudioSessionState.Starting, controller.state.value)
        assertFalse("start must return before JNI open completes", engine.startEntered.await(100, TimeUnit.MILLISECONDS))
        engine.allowCreate.countDown()
        check(engine.startEntered.await(1, TimeUnit.SECONDS))
        engine.allowStart.countDown()
        controller.close()
    }

    @Test
    fun controllableExecutorOrdersStopAndCloseAfterQueuedStart() {
        val executor = ManualExecutorService()
        val engine = RecordingEngine()
        val controller = DefaultAudioSessionController(engine, FakeRoutes(), false, executor)

        controller.start()
        controller.stop()
        controller.close()
        executor.runAll()

        assertEquals(listOf("create", "stop", "stop", "destroy"), engine.calls)
    }

    @Test
    fun controlTaskFailureIsVisibleAsFailed() {
        val executor = ManualExecutorService()
        val engine = RecordingEngine().apply { startError = IllegalStateException("Oboe open failed") }
        val controller = DefaultAudioSessionController(engine, FakeRoutes(), false, executor)

        controller.start()
        executor.runAll()

        assertEquals(AudioSessionState.Failed("Oboe open failed"), controller.state.value)
    }

    @Test
    fun staleNativeRunningEventDoesNotReactivateStoppedSession() {
        val executor = ManualExecutorService()
        val engine = RecordingEngine(events = mutableListOf("Running:48000"))
        val controller = DefaultAudioSessionController(engine, FakeRoutes(), false, executor)

        controller.start()
        controller.stop()
        controller.pollNativeEvents()
        executor.runAll()

        assertEquals(AudioSessionState.Idle, controller.state.value)
    }

    @Test
    fun nativeFailureReleasesSessionSoRetryStartsAgain() {
        val executor = ManualExecutorService()
        val engine = RecordingEngine()
        val routes = FakeRoutes()
        val controller = DefaultAudioSessionController(engine, routes, false, executor)

        controller.start()
        executor.runAll()
        engine.events += "Failed:audio recovery exhausted"
        controller.pollNativeEvents()
        executor.runAll()
        controller.start()
        executor.runAll()

        assertEquals(2, engine.startCount)
        assertEquals(1, routes.endCount)
        assertEquals(AudioSessionState.Starting, controller.state.value)
    }

    @Test
    fun queuedStoppedFromRouteRestartDoesNotStopReplacementSession() {
        val executor = ManualExecutorService()
        val engine = RecordingEngine()
        val routes = FakeRoutes()
        val controller = DefaultAudioSessionController(engine, routes, false, executor)

        controller.start()
        executor.runAll()
        engine.events += "Stopped:0"
        routes.notifyRouteChanged(AudioRoute.Speaker)
        executor.runAll()
        controller.stop()
        executor.runAll()

        assertEquals(2, engine.startCount)
        assertEquals(2, engine.stopCount)
        assertEquals(AudioSessionState.Idle, controller.state.value)
    }

    @Test
    fun classifiesActiveDeviceTypesWithStableWarnings() {
        assertEquals(AudioRoute.Bluetooth, audioRouteForDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals(AudioRoute.Wired, audioRouteForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioRoute.Usb, audioRouteForDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(AudioRoute.Speaker, audioRouteForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }
}

private class ManualExecutorService : AbstractExecutorService() {
    private val tasks = ArrayDeque<Runnable>()
    private var shutDown = false
    override fun execute(command: Runnable) { check(!shutDown); tasks += command }
    fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    override fun shutdown() { shutDown = true }
    override fun shutdownNow(): MutableList<Runnable> = tasks.toMutableList().also { tasks.clear(); shutDown = true }
    override fun isShutdown() = shutDown
    override fun isTerminated() = shutDown && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
}

private class FakeRoutes : RouteMonitor {
    override var currentRoute = AudioRoute.Wired
    var closeCount = 0
    var endCount = 0
    private var listener: (AudioRoute) -> Unit = {}
    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) { this.listener = listener }
    override fun start() = Unit
    override fun beginCommunication(inputPreference: InputPreference) = Unit
    override fun endCommunication() { endCount++ }
    override fun close() { closeCount++ }
    fun notifyRouteChanged(route: AudioRoute) { currentRoute = route; listener(route) }
}

private class RecordingEngine(
    val events: MutableList<String> = mutableListOf(),
) : NativeAudioGateway {
    val calls = mutableListOf<String>()
    var startError: Throwable? = null
    var startCount = 0
    var stopCount = 0
    override fun create() { calls += "create" }
    override fun start(config: AudioSessionConfig): Boolean { startError?.let { throw it }; startCount++; calls += "start"; return true }
    override fun stop() { stopCount++; calls += "stop" }
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun pollEvent(): String? = events.removeFirstOrNull()
    override fun destroy() { calls += "destroy" }
}

private class BlockingEngine : NativeAudioGateway {
    val createEntered = CountDownLatch(1)
    val allowCreate = CountDownLatch(1)
    val startEntered = CountDownLatch(1)
    val allowStart = CountDownLatch(1)
    override fun create() { createEntered.countDown(); check(allowCreate.await(1, TimeUnit.SECONDS)) }
    override fun start(config: AudioSessionConfig): Boolean { startEntered.countDown(); check(allowStart.await(1, TimeUnit.SECONDS)); return true }
    override fun stop() = Unit
    override fun setFilterMode(mode: FilterMode) = Unit
    override fun pollEvent(): String? = null
    override fun destroy() = Unit
}
