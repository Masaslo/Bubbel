package com.example.bubbel.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build

class AudioRouteMonitor(
    context: Context,
) : RouteMonitor {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var modeBeforeSession: Int? = null
    private var inputPreference = InputPreference.Automatic
    private var selectedDeviceId: Int? = null
    private var onRouteChanged: (AudioRoute) -> Unit = {}
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
    }

    override val currentRoute: AudioRoute
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeCommunicationDevice()?.let { audioRouteForDeviceType(it.type) } ?: AudioRoute.Other
        } else {
            legacyActiveRoute()
        }

    override val routeIdentity: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activeCommunicationDevice()?.let { "${it.id}:${it.type}" } ?: "none"
        } else {
            "${currentRoute.type}:${currentRoute.label}"
        }

    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) {
        onRouteChanged = listener
    }

    override fun start() = audioManager.registerAudioDeviceCallback(callback, handler)

    override fun beginCommunication(inputPreference: InputPreference) {
        if (modeBeforeSession != null) return
        this.inputPreference = inputPreference
        modeBeforeSession = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        refreshCommunicationDevice()
    }

    override fun endCommunication() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager.clearCommunicationDevice()
        selectedDeviceId = null
        modeBeforeSession?.let { audioManager.mode = it }
        modeBeforeSession = null
    }

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        audioManager.unregisterAudioDeviceCallback(callback)
        endCommunication()
    }

    private fun notifyAfterSettling() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!closed) {
                refreshCommunicationDevice()
                onRouteChanged(currentRoute)
            }
        }, ROUTE_SETTLE_MILLIS)
    }

    private fun activeCommunicationDevice(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val devices = audioManager.availableCommunicationDevices
        return audioManager.communicationDevice?.takeIf { active -> devices.any { it.id == active.id } }
            ?: devices.firstOrNull { it.id == selectedDeviceId }
            ?: selectCommunicationDevice(devices, inputPreference)
    }

    private fun refreshCommunicationDevice() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || modeBeforeSession == null) return
        val devices = audioManager.availableCommunicationDevices
        val selected = selectCommunicationDevice(devices, inputPreference) ?: return
        if (audioManager.communicationDevice?.id != selected.id) {
            if (audioManager.setCommunicationDevice(selected)) selectedDeviceId = selected.id
        } else {
            selectedDeviceId = selected.id
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyActiveRoute(): AudioRoute = when {
        audioManager.isBluetoothScoOn -> AudioRoute.Bluetooth
        audioManager.isSpeakerphoneOn -> AudioRoute.Speaker
        else -> audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { audioRouteForDeviceType(it.type) }
            .firstOrNull { it.type == AudioRouteType.Wired || it.type == AudioRouteType.Usb }
            ?: AudioRoute.Other
    }

    private companion object { const val ROUTE_SETTLE_MILLIS = 250L }
}

internal fun audioRouteForDeviceType(deviceType: Int): AudioRoute = when (deviceType) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioRoute.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioRoute.Wired
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioRoute.Usb
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
    else -> AudioRoute.Other
}

internal fun preferredCommunicationRoute(routes: List<AudioRoute>, preference: InputPreference): AudioRoute =
    when (preference) {
        InputPreference.Phone -> routes.firstOrNull { it.type == AudioRouteType.Speaker }
        InputPreference.Headset, InputPreference.Automatic -> routes.firstOrNull {
            it.type == AudioRouteType.Wired || it.type == AudioRouteType.Usb || it.type == AudioRouteType.Bluetooth
        }
    } ?: routes.firstOrNull { it.type == AudioRouteType.Speaker } ?: routes.firstOrNull() ?: AudioRoute.Other

private fun selectCommunicationDevice(devices: List<AudioDeviceInfo>, preference: InputPreference): AudioDeviceInfo? {
    val preferredRoute = preferredCommunicationRoute(devices.map { audioRouteForDeviceType(it.type) }, preference)
    return devices.firstOrNull { audioRouteForDeviceType(it.type) == preferredRoute }
}
