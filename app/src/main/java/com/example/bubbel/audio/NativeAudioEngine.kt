package com.example.bubbel.audio

import android.content.res.AssetManager

/** Thin control-thread bridge; native events are polled, never emitted by audio callbacks. */
class NativeAudioEngine {
    init { System.loadLibrary("bubbel_audio") }
    external fun nativeCreate(assetManager: AssetManager)
    external fun nativeStart(filterMode: Int, inputPreference: Int, outputGain: Float): Boolean
    external fun nativeStop()
    external fun nativeSetFilterMode(mode: Int)
    external fun nativePollEvent(): String?
    external fun nativeDestroy()
}
