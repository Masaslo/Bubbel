# Voice Isolation and Live Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bouw Bubbel v1 als volledig lokale Android-luisterketen die alle spraak behoudt, niet-spraak dempt en bij iedere fout stil en veilig stopt.

**Architecture:** Een native C++-engine bezit outputgestuurde Oboe full-duplex streams. De realtime callback verplaatst alleen vooraf gealloceerde samples; een aparte worker voert officiële DeepFilterNet3-verwerking uit in 480-sample hops via een gepinde Rust `libDF`/Tract-library achter een smalle C ABI. Een niet-geëxporteerde Kotlin foreground service bezit de engine en publiceert de werkelijke sessiestatus aan de Compose-UI.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, Android foreground services, C++17, CMake, Oboe 1.10, Rust, upstream DeepFilterNet `libDF`/Tract, JUnit 4, AndroidX instrumentation.

**Spec:** `C:/Users/Floris/.codex/attachments/2a63ea66-6b19-4e6e-8e06-30de274ca6ad/pasted-text.txt`

## Global Constraints

- Alle audioverwerking is lokaal; voeg geen `INTERNET`-permission, netwerkcode, audio-opslag, tijdelijke PCM-bestanden of transcripties toe.
- Verwerk het officiële model in hops van exact 480 mono `float32`-samples op 48 kHz (`fft_size=960`, `hop_size=480`).
- Doe nooit allocaties, mutexgebruik, logging, JNI-calls of model-inference vanuit een Oboe-callback.
- Een lege outputbuffer wordt stilte; ongefilterde audio is nooit een foutfallback.
- Stop met `ProcessingTooSlow` na drie opeenvolgende gemiste modeldeadlines of 250 ms zonder geldige output, afhankelijk van wat eerst komt.
- Open output vóór input, gebruik de outputcallback als klok, vraag low-latency/exclusive aan en val terug op shared mode.
- Recurrente modelstate is sessiegebonden en wordt gereset bij start, stop en routeherstel.
- `Balanced` is standaard; profielwissels gebruiken een 50 ms ramp.
- Start de microfoon-foreground-service alleen vanuit een zichtbare Activity nadat `RECORD_AUDIO` is verleend.
- De UI is alleen actief wanneer de engine `Running` rapporteert.
- Achtergrond, scherm-uit en een sessie van minimaal 90 minuten behoren tot de acceptatiecriteria.

---

### Task 1: Native realtime DSP primitives

**Files:**
- Create: `app/src/main/cpp/CMakeLists.txt`
- Create: `app/src/main/cpp/audio/SpscRingBuffer.h`
- Create: `app/src/main/cpp/audio/FrameAssembler.h`
- Create: `app/src/main/cpp/audio/ProfileMixer.h`
- Create: `app/src/main/cpp/audio/DeadlineWatchdog.h`
- Create: `app/src/test/cpp/AudioCoreTests.cpp`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `SpscRingBuffer<float>`, generic `FrameAssembler<N>` (used as `FrameAssembler<480>` from Task 2 onward), `FilterProfile { Natural, Balanced, Strong }`, `ProfileMixer::process(dry, enhanced, output, count)`, `DeadlineWatchdog::recordFrame(bool, int64_t)`.
- `Natural` dry floor is `10^(-12/20)`, `Balanced` dry floor is `10^(-24/20)`, `Strong` dry floor is `0`; ramps are exactly 2400 samples at 48 kHz.
- `DeadlineWatchdog` returns `ProcessingTooSlow` on the third consecutive miss or elapsed invalid-output time `>= 250 ms`.

- [ ] Write Catch2-free native assertions for ring wrap-around, bounded overrun/underrun, arbitrary callback framing, exact profile endpoints, 2400-sample ramps and both watchdog limits.
- [ ] Run the native test target and verify it fails because the headers do not exist.
- [ ] Implement only the preallocated, non-blocking primitives required by the tests; no Android or ONNX dependencies in these units.
- [ ] Run the native tests and `gradlew test`; both must pass.
- [ ] Commit with message `feat: add realtime audio primitives`.

### Task 2: Official DeepFilterNet3 package and libDF inference boundary

**Files:**
- Create: `app/src/main/assets/models/deepfilternet3/DeepFilterNet3_onnx.tar.gz`
- Create: `app/src/main/assets/models/deepfilternet3/metadata.json`
- Create: `app/src/main/assets/models/deepfilternet3/LICENSES.md`
- Create: `app/src/main/assets/models/deepfilternet3/SHA256SUMS`
- Create: `app/src/main/rust/Cargo.toml`
- Create: `app/src/main/rust/Cargo.lock`
- Create: `app/src/main/rust/src/lib.rs`
- Create: `app/src/main/cpp/model/LibDfApi.h`
- Create: `app/src/test/resources/deepfilternet3/golden_input_f32le.bin`
- Create: `app/src/test/resources/deepfilternet3/golden_output_f32le.bin`
- Create: `app/src/main/cpp/model/VoiceFilter.h`
- Create: `app/src/main/cpp/model/LibDfVoiceFilter.cpp`
- Create: `app/src/test/cpp/VoiceFilterTests.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/src/test/cpp/AudioCoreTests.cpp`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `VoiceFilter::initialize(ModelFiles)`, `VoiceFilter::reset()`, `VoiceFilter::process(const float input[480], float output[480]) -> FilterResult`.
- The Rust library owns STFT/iSTFT, Tract execution and every temporal/model buffer; the C++ wrapper owns one opaque session handle and exposes no Rust/Tract objects to the audio callback.
- `metadata.json` records the immutable upstream URL/revision, license, archive SHA-256, sample rate 48000, FFT size 960, hop size 480, lookahead and whether reliable speech/LSNR output is exposed.

- [ ] Replace the existing 512-specific framing assertions with 480-hop assertions and capture a failing test before changing production-facing frame constants.
- [ ] Freeze the official `DeepFilterNet3_onnx.tar.gz` from upstream commit `d375b2d8309e0935d165700c91da9de862a99c31`; independently verify SHA-256 `c94d91f70911001c946e0fabb4aa9adc37045f45a03b56008cb0c8244cb63616` and include the chosen upstream license text.
- [ ] Build a minimal Rust `cdylib` for Android that wraps official `libDF`/Tract with opaque create/process/reset/destroy functions and catches Rust panics before they cross the C ABI.
- [ ] Generate one deterministic golden vector with the pinned upstream desktop implementation and verify the Android/native comparison fails before the wrapper is implemented.
- [ ] Make `reset()` recreate or fully reset every upstream analysis, synthesis and model buffer; verify two sessions produce identical first-hop output.
- [ ] Compare Android output to the golden output using the tolerance declared in metadata; run Rust tests, native emulator tests and `gradlew test`.
- [ ] Remove the unused ONNX Runtime Android dependency after the libDF route is linked successfully.
- [ ] Commit with message `feat: integrate official DeepFilterNet voice filter`.

### Task 3: Oboe full-duplex engine and JNI bridge

**Files:**
- Create: `app/src/main/cpp/audio/AudioEngine.h`
- Create: `app/src/main/cpp/audio/AudioEngine.cpp`
- Create: `app/src/main/cpp/audio/VoiceWorker.h`
- Create: `app/src/main/cpp/audio/VoiceWorker.cpp`
- Create: `app/src/main/cpp/jni/AudioEngineJni.cpp`
- Create: `app/src/main/java/com/example/bubbel/audio/NativeAudioEngine.kt`
- Create: `app/src/test/cpp/AudioEngineTests.cpp`
- Modify: `app/src/main/cpp/CMakeLists.txt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces native events `Starting`, `Running(route)`, `Recovering(attempt)`, `Failed(reason)`, `Stopped` through a polling/control-thread bridge, never from the callback.
- JNI methods: `nativeCreate(assetManager)`, `nativeStart(filterMode, inputPreference, outputGain)`, `nativeStop()`, `nativeSetFilterMode(mode)`, `nativePollEvent()`, `nativeDestroy()`.
- The worker is the sole caller of `VoiceFilter` and writes exactly 480 filtered samples per successful hop.

- [ ] Write tests around injected fake streams/filter/clock for output-before-input ordering, silence on underrun, channel duplication, worker framing, state reset and retry delays 100/250/500 ms.
- [ ] Verify the tests fail before engine classes exist.
- [ ] Implement output-driven full duplex using natural route sample rate, conversion only around the 48 kHz mono model, low-latency/exclusive request with shared fallback, and no callback-unsafe operations.
- [ ] On disconnect/route change: silence, close outside callback, reset model state, retry three times, then emit a terminal failure.
- [ ] Run native tests, `gradlew test`, and `gradlew assembleDebug`; inspect callback code against the global constraints.
- [ ] Commit with message `feat: add native full duplex audio engine`.

### Task 4: Session API and microphone foreground service

**Files:**
- Create: `app/src/main/java/com/example/bubbel/audio/AudioSessionModels.kt`
- Create: `app/src/main/java/com/example/bubbel/audio/AudioSessionController.kt`
- Create: `app/src/main/java/com/example/bubbel/audio/DefaultAudioSessionController.kt`
- Create: `app/src/main/java/com/example/bubbel/audio/AudioRouteMonitor.kt`
- Create: `app/src/main/java/com/example/bubbel/service/ListeningService.kt`
- Create: `app/src/main/java/com/example/bubbel/service/ListeningNotification.kt`
- Create: `app/src/test/java/com/example/bubbel/audio/DefaultAudioSessionControllerTest.kt`
- Create: `app/src/test/java/com/example/bubbel/service/ListeningServiceStateTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces `FilterMode { Natural, Balanced, Strong }`, `InputPreference { Automatic, Phone, Headset }`, `AudioSessionConfig(filterMode, inputPreference, outputGain)`.
- Produces sealed `AudioSessionState`: `Idle`, `Starting`, `Running(route)`, `Recovering(attempt)`, `Failed(reason)`.
- `AudioSessionController` exposes `val state: StateFlow<AudioSessionState>`, `start(config)` and `stop()`.

- [ ] Write JVM tests with a fake `NativeAudioEngine` for exact state mapping, idempotent start/stop, terminal failures and no raw-passthrough recovery.
- [ ] Verify tests fail before the public API exists.
- [ ] Declare `RECORD_AUDIO`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MICROPHONE`; declare a non-exported service with `foregroundServiceType="microphone"`; do not add internet permission.
- [ ] Implement a persistent notification showing route and a `Stoppen` action that fully stops worker, streams and service.
- [ ] Monitor wired/USB/Bluetooth/speaker route changes; expose Bluetooth latency and speaker feedback warnings without blocking routes.
- [ ] Run focused tests, full unit tests and `assembleDebug`.
- [ ] Commit with message `feat: add listening foreground service`.

### Task 5: Permission flow and UI state integration

**Files:**
- Modify: `app/src/main/java/com/example/bubbel/MainActivity.kt`
- Modify: `app/src/main/java/com/example/bubbel/presentation/home/ListeningViewModel.kt`
- Create: `app/src/test/java/com/example/bubbel/presentation/home/ListeningViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/example/bubbel/BubbelHomeScreenTest.kt`
- Delete: `app/src/main/java/com/example/bubbel/data/repository/InMemoryListeningStateRepository.kt`
- Delete: `app/src/main/java/com/example/bubbel/domain/usecase/ToggleListeningUseCase.kt`

**Interfaces:**
- Consumes `AudioSessionController.state` and default `AudioSessionConfig(Balanced, Automatic, 1.0f)`.
- Produces UI rendering for Idle, Starting, Running, Recovering and Failed; only Running maps to the active bubble.

- [ ] Write ViewModel tests for start request, stop request and every state; write Compose assertions for non-active Starting/Recovering/Failed and active Running.
- [ ] Verify focused tests fail against the current local toggle implementation.
- [ ] Ask `RECORD_AUDIO` from the visible Activity; only after grant call `start`, and display a clear failed/denied state when rejected or revoked.
- [ ] Replace the in-memory toggle wiring with the service-backed controller without changing the established bubble animation or theme.
- [ ] Show the current route and route warning in the existing screen/settings area.
- [ ] Run unit tests, instrumentation compilation and `assembleDebug`.
- [ ] Commit with message `feat: connect listening UI to audio service`.

### Task 6: Acceptance harness, privacy checks and project documentation

**Files:**
- Create: `app/src/androidTest/java/com/example/bubbel/ListeningLifecycleTest.kt`
- Create: `docs/testing/voice-isolation-device-checklist.md`
- Modify: `PROJECT_SUMMARY.md`
- Modify: `project.json`

**Interfaces:**
- The device checklist records reference device/Android version, route, median round-trip latency, 90-minute stability, battery delta, Bluetooth observation and every speech/trigger scenario from the spec.

- [ ] Add instrumentation coverage for granted/denied/revoked permission, visible start/stop, notification stop, background/return, screen-off/manual checkpoint and disconnect recovery.
- [ ] Add deterministic package checks that the merged manifest has no internet permission and the APK/assets contain no runtime-created audio path or sample recordings.
- [ ] Run `gradlew test connectedDebugAndroidTest` when a device is attached; otherwise run unit tests, instrumentation compilation and record the physical-device cases as not yet measured rather than passing them.
- [ ] Run `gradlew assembleDebug`, inspect the merged manifest and APK contents, and record exact evidence.
- [ ] Update `PROJECT_SUMMARY.md` with architecture, tests, limitations and unmeasured device criteria; mark `voice-isolation` complete in `project.json` only if the model, runtime and device acceptance criteria are genuinely complete.
- [ ] Commit with message `docs: record voice isolation verification`.
