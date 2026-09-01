package com.example.bubbel.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AudioRouteMonitor(
    context: Context,
) : RouteMonitor {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var closed = false
    private var onRouteChanged: (AudioRoute) -> Unit = {}
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = notifyAfterSettling()
    }

    override val currentRoute: AudioRoute
        get() = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map(::classify)
            .let { routes ->
                routes.firstOrNull { it.type == AudioRouteType.Bluetooth }
                    ?: routes.firstOrNull { it.type == AudioRouteType.Wired }
                    ?: routes.firstOrNull { it.type == AudioRouteType.Usb }
                    ?: routes.firstOrNull { it.type == AudioRouteType.Speaker }
                    ?: routes.firstOrNull()
                    ?: AudioRoute.Other
            }

    override fun setRouteChangedListener(listener: (AudioRoute) -> Unit) {
        onRouteChanged = listener
    }

    override fun start() = audioManager.registerAudioDeviceCallback(callback, handler)

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacksAndMessages(null)
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun notifyAfterSettling() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (!closed) onRouteChanged(currentRoute) }, ROUTE_SETTLE_MILLIS)
    }

    private fun classify(device: AudioDeviceInfo): AudioRoute = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioRoute.Bluetooth
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioRoute.Wired
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioRoute.Usb
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.Speaker
        else -> AudioRoute.Other
    }

    private companion object { const val ROUTE_SETTLE_MILLIS = 250L }
}
