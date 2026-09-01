# Task 3 report: Oboe full-duplex engine and JNI bridge

## Delivered

- Added `AudioEngine`, `VoiceWorker`, and the `NativeAudioEngine` JNI/Kotlin bridge.
- Output stream is opened and started before input. The output callback is the clock, reads only from a preallocated SPSC queue, outputs zero on underrun, and duplicates mono into each output channel.
- Input is downmixed into preallocated storage and queued. `VoiceWorker` is the only `VoiceFilter` caller; it only writes one 480-sample result after a successful hop. The default profile is Balanced and changes run through `ProfileMixer`'s existing 2400-sample / 50 ms ramp.
- Lifecycle events are stored for polling (`Starting`, `Running`, `Recovering`, `Failed`, `Stopped`); recovery is outside the data callback and uses exactly 100, 250, then 500 ms.
- Model bytes are read from the packaged asset once at `nativeCreate`; no callback performs storage I/O, JNI, allocation, locking, logging, stream close/reopen, or inference.

## TDD evidence

### RED

Added `app/src/test/cpp/AudioEngineTests.cpp` before the engine/worker source, covering underrun silence/channel duplication, 480-hop worker framing, filter/reset boundary, and fixed recovery delays.

Attempted command:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmake\3.22.1\bin\cmake.exe" -S app/src/main/cpp -B build/audio-engine-red -DANDROID=OFF
```

Output: CMake selected `NMake Makefiles`, but this desktop session has no host C++ compiler/NMake (`CMAKE_CXX_COMPILER not set`). This was an environment configuration failure before it could compile the intentionally missing `audio/VoiceWorker.h`; it is not counted as a valid behavioral RED run.

### GREEN

Focused native command (x86_64 emulator):

```powershell
adb -s emulator-5554 push app/build/intermediates/cxx/Debug/8312it5t/obj/x86_64/audio_engine_tests /data/local/tmp/audio_engine_tests
adb -s emulator-5554 shell chmod 755 /data/local/tmp/audio_engine_tests
adb -s emulator-5554 shell 'LD_LIBRARY_PATH=/data/local/tmp /data/local/tmp/audio_engine_tests'
```

Output: `PASS: AudioEngineTests`

Build command:

```powershell
.\gradlew.bat :app:assembleDebug
```

Output: `BUILD SUCCESSFUL in 7s` (both `arm64-v8a` and `x86_64` CMake builds ran).

## Files

- Created: `app/src/main/cpp/audio/AudioEngine.{h,cpp}`, `VoiceWorker.{h,cpp}`, `app/src/main/cpp/jni/AudioEngineJni.cpp`, `app/src/main/java/com/example/bubbel/audio/NativeAudioEngine.kt`, and `app/src/test/cpp/AudioEngineTests.cpp`.
- Modified: CMake, `VoiceFilter`/`LibDfVoiceFilter` (asset-memory initialization without callback storage access), and `PROJECT_SUMMARY.md`.

## Self-review and concerns

- `git diff --check` completed without whitespace errors.
- The executable test validates queue/worker behavior, not real microphone playback. A physical-device route-change, permission, and screen-off test remains required.
- Route sample-rate conversion around the fixed 48 kHz model still needs a dedicated resampler; this skeleton requests the natural output route rate but does not yet convert non-48 kHz routes. This must be resolved before claiming live isolation works on all routes.
- Recovery is structurally off-callback and bounded, but it needs an instrumented disconnect test and lifecycle hardening before production use.
