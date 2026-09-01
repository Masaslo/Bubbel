package com.example.bubbel.audio

import android.content.res.AssetManager

/** Thin control-thread bridge; native events are polled, never emitted by audio callbacks. */
class NativeAudioEngine(private val assets: AssetManager) : NativeAudioGateway {
    init { System.loadLibrary("bubbel_audio") }
    external fun nativeCreate(assetManager: AssetManager)
    external fun nativeStart(filterMode: Int, inputPreference: Int, outputGain: Float): Boolean
    external fun nativeStop()
    external fun nativeSetFilterMode(mode: Int)
    external fun nativePollEvent(): String?
    external fun nativeDestroy()

    override fun create() = nativeCreate(assets)
    override fun start(config: AudioSessionConfig) = nativeStart(
        filterMode = config.filterMode.ordinal,
        inputPreference = config.inputPreference.ordinal,
        outputGain = config.outputGain,
    )
    override fun stop() = nativeStop()
    override fun setFilterMode(mode: FilterMode) = nativeSetFilterMode(mode.ordinal)
    override fun pollEvent(): String? = nativePollEvent()
    override fun destroy() = nativeDestroy()
}
