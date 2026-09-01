#pragma once

#include "audio/VoiceWorker.h"
#include "audio/RateConverter.h"
#include "audio/LifecycleState.h"
#include "model/VoiceFilter.h"

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>

#include <oboe/Oboe.h>
struct AAssetManager;

class AudioEngine final : public oboe::AudioStreamCallback {
public:
    explicit AudioEngine(AAssetManager* assets = nullptr);
    ~AudioEngine() override;
    bool start(int filterMode, int inputPreference, float outputGain);
    void stop();
    void setFilterMode(int mode) noexcept;
    std::optional<AudioEvent> pollEvent();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream*, void*, int32_t) override;
    void onErrorAfterClose(oboe::AudioStream*, oboe::Result) override;

private:
    class FilterAdapter final : public IVoiceFilter {
    public:
        explicit FilterAdapter(VoiceFilter& filter) : filter_(filter) {}
        bool process(const float input[480], float output[480]) noexcept override;
        void reset() noexcept override;
    private: VoiceFilter& filter_;
    };
    void workerLoop();
    bool openStreams();
    void closeStreamsLocked();
    bool startLocked(int filterMode, float outputGain);
    void stopLocked(bool emitStopped);
    void recoverLocked(std::unique_lock<std::mutex>& controlLock);
    void failLocked(const char* reason, int value = 0);

    static constexpr std::size_t kQueueSamples = 48'000;
    SpscRingBuffer<float> input_{kQueueSamples}, output_{kQueueSamples};
    VoiceFilter filter_;
    FilterAdapter filterAdapter_{filter_};
    VoiceWorker worker_{input_, output_, filterAdapter_};
    std::shared_ptr<oboe::AudioStream> outputStream_, inputStream_;
    std::thread workerThread_;
    std::mutex controlMutex_;
    std::condition_variable recoveryCancellation_;
    LifecycleState lifecycle_;
    std::atomic<bool> running_{false};
    std::atomic<float> outputGain_{1.0F};
    std::atomic<int> outputChannels_{1};
    std::array<float, 1920> inputScratch_ = {};
    std::atomic<bool> terminalFailure_{false};
    bool modelReady_ = false;
};
