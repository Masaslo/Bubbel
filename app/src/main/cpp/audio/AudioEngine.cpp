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
    lifecycle_.beginStart();
    if (running_) return true;
    return startLocked(filterMode, outputGain);
}
bool AudioEngine::startLocked(int filterMode, float outputGain) {
    lifecycle_.pushEvent(AudioEventKind::Starting);
    worker_.setFilterMode(filterMode); outputGain_ = outputGain;
    if (!modelReady_) { lifecycle_.pushEvent(AudioEventKind::Failed, 0, "model unavailable"); return false; }
    // Model initialization is deliberately owned by application setup; audio callbacks never access storage.
    worker_.reset();
    if (!openStreams()) { lifecycle_.pushEvent(AudioEventKind::Failed, 0, "could not open audio streams"); return false; }
    running_ = true;
    workerThread_ = std::thread(&AudioEngine::workerLoop, this);
    lifecycle_.pushEvent(AudioEventKind::Running, outputStream_->getSampleRate());
    return true;
}
void AudioEngine::stop() {
    std::unique_lock<std::mutex> lock(controlMutex_);
    lifecycle_.manualStop();
    recoveryCancellation_.notify_all();
    stopLocked(true);
}
void AudioEngine::setFilterMode(int mode) noexcept { worker_.setFilterMode(mode); }
std::optional<AudioEvent> AudioEngine::pollEvent() {
    {
        std::unique_lock<std::mutex> lock(controlMutex_);
        if (lifecycle_.takeRecoveryRequest()) recoverLocked(lock);
        if (terminalFailure_.exchange(false)) failLocked("audio callback capacity exceeded");
        if (worker_.takeDeadlineFailure()) failLocked("model deadline exceeded");
    }
    return lifecycle_.popEvent();
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
    if (inputStream_->getSampleRate() != outputStream_->getSampleRate()) { closeStreamsLocked(); return false; }
    worker_.setRouteSampleRate(outputStream_->getSampleRate());
    if (outputStream_->requestStart() != oboe::Result::OK || inputStream_->requestStart() != oboe::Result::OK) { closeStreamsLocked(); return false; }
    return true;
}
void AudioEngine::closeStreamsLocked() { if (inputStream_) { inputStream_->requestStop(); inputStream_->close(); inputStream_.reset(); } if (outputStream_) { outputStream_->requestStop(); outputStream_->close(); outputStream_.reset(); } }
void AudioEngine::stopLocked(bool emitStopped) {
    running_ = false;
    if (workerThread_.joinable()) workerThread_.join();
    closeStreamsLocked();
    input_.clear(); output_.clear(); worker_.reset();
    if (emitStopped) lifecycle_.pushEvent(AudioEventKind::Stopped);
}
void AudioEngine::failLocked(const char* reason, int value) {
    stopLocked(false);
    lifecycle_.pushEvent(AudioEventKind::Failed, value, reason);
}
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
void AudioEngine::onErrorAfterClose(oboe::AudioStream*, oboe::Result) { lifecycle_.requestRecovery(); }
void AudioEngine::recoverLocked(std::unique_lock<std::mutex>& controlLock) {
    stopLocked(false);
    for (int attempt = 1; attempt <= 3; ++attempt) {
        if (lifecycle_.isManualStop()) return;
        lifecycle_.pushEvent(AudioEventKind::Recovering, attempt);
        if (!waitForRecoveryRetry(recoveryCancellation_, controlLock, lifecycle_,
                                  std::chrono::milliseconds(recoveryDelayMillis(attempt)))) return;
        if (lifecycle_.isManualStop()) return;
        if (openStreams()) {
            running_ = true;
            workerThread_ = std::thread(&AudioEngine::workerLoop, this);
            lifecycle_.pushEvent(AudioEventKind::Running, outputStream_->getSampleRate());
            return;
        }
    }
    lifecycle_.pushEvent(AudioEventKind::Failed, 3, "audio recovery exhausted");
}
