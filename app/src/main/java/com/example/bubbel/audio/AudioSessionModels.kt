package com.example.bubbel.audio

enum class FilterMode { Natural, Balanced, Strong }

enum class InputPreference { Automatic, Phone, Headset }

data class AudioSessionConfig(
    val filterMode: FilterMode = FilterMode.Balanced,
    val inputPreference: InputPreference = InputPreference.Automatic,
    val outputGain: Float = 1.0f,
)

enum class AudioRouteType { Bluetooth, Wired, Usb, Speaker, Other }

data class AudioRoute(
    val type: AudioRouteType,
    val label: String,
    val warning: String? = null,
) {
    companion object {
        val Bluetooth = AudioRoute(AudioRouteType.Bluetooth, "Bluetooth", "Bluetooth can add latency")
        val Wired = AudioRoute(AudioRouteType.Wired, "Wired headphones")
        val Usb = AudioRoute(AudioRouteType.Usb, "USB audio")
        val Speaker = AudioRoute(AudioRouteType.Speaker, "Phone speaker", "Speaker can cause feedback")
        val Other = AudioRoute(AudioRouteType.Other, "Other output")
    }
}

sealed interface AudioSessionState {
    data object Idle : AudioSessionState
    data object Starting : AudioSessionState
    data class Running(val route: AudioRoute) : AudioSessionState
    data class Recovering(val attempt: Int) : AudioSessionState
    data class Failed(val reason: String) : AudioSessionState
}

internal interface NativeAudioGateway {
    fun create()
    fun start(config: AudioSessionConfig): Boolean
    fun stop()
    fun setFilterMode(mode: FilterMode)
    fun pollEvent(): String?
    fun destroy()
}

interface RouteMonitor {
    val currentRoute: AudioRoute
    val routeIdentity: String get() = "${currentRoute.type}:${currentRoute.label}"
    fun setRouteChangedListener(listener: (AudioRoute) -> Unit)
    fun start()
    fun beginCommunication(inputPreference: InputPreference = InputPreference.Automatic)
    fun refreshCommunication() = Unit
    fun endCommunication()
    fun close()
}
