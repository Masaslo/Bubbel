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
    private var onRouteChanged: (AudioRoute) -> Unit = {}
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
    }

    override val currentRoute: AudioRoute
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { audioRouteForDeviceType(it.type) } ?: AudioRoute.Other
        } else {
            legacyActiveRoute()
        }

    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) {
        onRouteChanged = listener
    }

    override fun start() = audioManager.registerAudioDeviceCallback(callback, handler)

    override fun beginCommunication() {
        if (modeBeforeSession != null) return
        modeBeforeSession = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    override fun endCommunication() {
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
        handler.postDelayed({ if (!closed) onRouteChanged(currentRoute) }, ROUTE_SETTLE_MILLIS)
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
