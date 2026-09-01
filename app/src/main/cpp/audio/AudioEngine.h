#pragma once

#include "audio/VoiceWorker.h"
#include "model/VoiceFilter.h"

#include <atomic>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <thread>

#include <oboe/Oboe.h>
struct AAssetManager;

enum class AudioEventKind { Starting, Running, Recovering, Failed, Stopped };
struct AudioEvent { AudioEventKind kind; int value = 0; std::string text; };

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
    void closeStreams();
    void pushEvent(AudioEventKind kind, int value = 0, const char* text = "");
    void recover();

    static constexpr std::size_t kQueueSamples = 48'000;
    SpscRingBuffer<float> input_{kQueueSamples}, output_{kQueueSamples};
    VoiceFilter filter_;
    FilterAdapter filterAdapter_{filter_};
    VoiceWorker worker_{input_, output_, filterAdapter_};
    std::shared_ptr<oboe::AudioStream> outputStream_, inputStream_;
    std::thread workerThread_, recoveryThread_;
    std::mutex controlMutex_, eventMutex_;
    std::optional<AudioEvent> event_;
    std::atomic<bool> running_{false}, recovering_{false};
    std::atomic<float> outputGain_{1.0F};
    std::atomic<int> outputChannels_{1};
    std::array<float, 1920> inputScratch_ = {};
    bool modelReady_ = false;
};
