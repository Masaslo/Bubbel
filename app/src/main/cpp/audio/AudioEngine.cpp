#include "audio/AudioEngine.h"

#include <chrono>
#include <cstring>
#include <android/asset_manager.h>

AudioEngine::AudioEngine(AAssetManager* assets) {
    if (assets == nullptr) return;
    AAsset* asset = AAssetManager_open(assets, "models/deepfilternet3/DeepFilterNet3_onnx.tar.gz", AASSET_MODE_BUFFER);
    if (asset == nullptr) return;
    const auto length = static_cast<std::size_t>(AAsset_getLength(asset));
    const auto* bytes = static_cast<const std::uint8_t*>(AAsset_getBuffer(asset));
    modelReady_ = bytes != nullptr && filter_.initializeBytes(bytes, length) == FilterResult::Ok;
    AAsset_close(asset);
}
AudioEngine::~AudioEngine() { stop(); }
bool AudioEngine::FilterAdapter::process(const float in[480], float out[480]) noexcept { return filter_.process(in, out) == FilterResult::Ok; }
void AudioEngine::FilterAdapter::reset() noexcept { (void)filter_.reset(); }

bool AudioEngine::start(int filterMode, int /*inputPreference*/, float outputGain) {
    std::lock_guard<std::mutex> lock(controlMutex_);
    manualStop_ = false;
    if (running_) return true;
    pushEvent(AudioEventKind::Starting);
    setFilterMode(filterMode); outputGain_ = outputGain;
    if (!modelReady_) { pushEvent(AudioEventKind::Failed, 0, "model unavailable"); return false; }
    // Model initialization is deliberately owned by application setup; audio callbacks never access storage.
    worker_.reset();
    if (!openStreams()) { pushEvent(AudioEventKind::Failed, 0, "could not open audio streams"); return false; }
    running_ = true;
    workerThread_ = std::thread(&AudioEngine::workerLoop, this);
    pushEvent(AudioEventKind::Running, outputStream_->getSampleRate());
    return true;
}
void AudioEngine::stop() {
    manualStop_ = true;
    running_ = false;
    if (workerThread_.joinable()) workerThread_.join();
    if (recoveryThread_.joinable() && recoveryThread_.get_id() != std::this_thread::get_id()) recoveryThread_.join();
    std::lock_guard<std::mutex> lock(controlMutex_);
    closeStreams(); worker_.reset(); pushEvent(AudioEventKind::Stopped);
}
void AudioEngine::setFilterMode(int mode) noexcept { worker_.setFilterMode(mode); }
std::optional<AudioEvent> AudioEngine::pollEvent() {
    if (recoveryRequested_.exchange(false) && !manualStop_) recover();
    if (terminalFailure_.exchange(false)) { stop(); pushEvent(AudioEventKind::Failed, 0, "audio callback capacity exceeded"); }
    if (worker_.takeDeadlineFailure()) pushEvent(AudioEventKind::Failed, 0, "model deadline exceeded");
    std::lock_guard<std::mutex> lock(eventMutex_);
    if (eventCount_ == 0) return std::nullopt;
    AudioEvent result = std::move(events_[eventRead_]); eventRead_ = (eventRead_ + 1) % events_.size(); --eventCount_;
    return result;
}
void AudioEngine::pushEvent(AudioEventKind kind, int value, const char* text) {
    std::lock_guard<std::mutex> lock(eventMutex_);
    if (eventCount_ == events_.size()) { eventRead_ = (eventRead_ + 1) % events_.size(); --eventCount_; }
    events_[eventWrite_] = AudioEvent{kind, value, text}; eventWrite_ = (eventWrite_ + 1) % events_.size(); ++eventCount_;
}

bool AudioEngine::openStreams() {
    oboe::AudioStreamBuilder out;
    out.setDirection(oboe::Direction::Output)->setFormat(oboe::AudioFormat::Float)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)->setSharingMode(oboe::SharingMode::Exclusive)
        ->setDataCallback(this)->setErrorCallback(this);
    if (out.openStream(outputStream_) != oboe::Result::OK) {
        out.setSharingMode(oboe::SharingMode::Shared);
        if (out.openStream(outputStream_) != oboe::Result::OK) return false;
    }
    outputChannels_ = outputStream_->getChannelCount();
    oboe::AudioStreamBuilder in;
    in.setDirection(oboe::Direction::Input)->setFormat(oboe::AudioFormat::Float)
       ->setSampleRate(outputStream_->getSampleRate())->setPerformanceMode(oboe::PerformanceMode::LowLatency)
       ->setSharingMode(oboe::SharingMode::Exclusive)->setDataCallback(this)->setErrorCallback(this);
    if (in.openStream(inputStream_) != oboe::Result::OK) {
        in.setSharingMode(oboe::SharingMode::Shared);
        if (in.openStream(inputStream_) != oboe::Result::OK) { outputStream_->close(); outputStream_.reset(); return false; }
    }
    if (inputStream_->getSampleRate() != outputStream_->getSampleRate()) { closeStreams(); return false; }
    worker_.setRouteSampleRate(outputStream_->getSampleRate());
    if (outputStream_->requestStart() != oboe::Result::OK || inputStream_->requestStart() != oboe::Result::OK) { closeStreams(); return false; }
    return true;
}
void AudioEngine::closeStreams() { if (inputStream_) { inputStream_->requestStop(); inputStream_->close(); inputStream_.reset(); } if (outputStream_) { outputStream_->requestStop(); outputStream_->close(); outputStream_.reset(); } }
void AudioEngine::workerLoop() { while (running_) { worker_.processAvailable(); if (worker_.deadlineFailed()) { running_ = false; break; } std::this_thread::sleep_for(std::chrono::milliseconds(1)); } }
oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream* stream, void* audioData, int32_t frames) {
    if (stream->getDirection() == oboe::Direction::Output) {
        auto* rendered = static_cast<float*>(audioData);
        const std::size_t sampleCount = static_cast<std::size_t>(frames) * static_cast<std::size_t>(outputChannels_);
        renderMonoToOutput(output_, rendered, static_cast<std::size_t>(frames), static_cast<std::size_t>(outputChannels_));
        const float gain = outputGain_.load(std::memory_order_relaxed);
        for (std::size_t i = 0; i < sampleCount; ++i) rendered[i] *= gain;
        return oboe::DataCallbackResult::Continue;
    }
    const auto* source = static_cast<const float*>(audioData); const std::size_t channels = static_cast<std::size_t>(stream->getChannelCount());
    if (static_cast<std::size_t>(frames) > inputScratch_.size()) { terminalFailure_ = true; return oboe::DataCallbackResult::Continue; }
    const std::size_t count = static_cast<std::size_t>(frames);
    for (std::size_t i = 0; i < count; ++i) { float sum = 0; for (std::size_t c = 0; c < channels; ++c) sum += source[i * channels + c]; inputScratch_[i] = sum / static_cast<float>(channels); }
    if (input_.write(inputScratch_.data(), count) != count) terminalFailure_ = true;
    return oboe::DataCallbackResult::Continue;
}
void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result) { if (!manualStop_) recoveryRequested_ = true; }
void AudioEngine::recover() { running_ = false; if (workerThread_.joinable()) workerThread_.join(); closeStreams(); worker_.reset(); for (int attempt = 1; attempt <= 3; ++attempt) { pushEvent(AudioEventKind::Recovering, attempt); std::this_thread::sleep_for(std::chrono::milliseconds(recoveryDelayMillis(attempt))); if (openStreams()) { running_ = true; workerThread_ = std::thread(&AudioEngine::workerLoop, this); pushEvent(AudioEventKind::Running, outputStream_->getSampleRate()); recovering_ = false; return; } } pushEvent(AudioEventKind::Failed, 3, "audio recovery exhausted"); recovering_ = false; }
