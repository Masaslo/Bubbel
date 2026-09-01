#include "audio/VoiceWorker.h"

#include <algorithm>
#include <chrono>
#include <cmath>

VoiceWorker::VoiceWorker(SpscRingBuffer<float>& input, SpscRingBuffer<float>& output,
                         IVoiceFilter& filter) noexcept
    : input_(input), output_(output), filter_(filter) {}

void VoiceWorker::processAvailable() noexcept {
    if (!converterReady_.load(std::memory_order_acquire)) return;
    const int mode = requestedMode_.load(std::memory_order_relaxed);
    mixer_.setProfile(mode == 0 ? FilterProfile::Natural : (mode == 2 ? FilterProfile::Strong : FilterProfile::Balanced));
    const std::size_t count = input_.read(readBuffer_.data(), readBuffer_.size());
    std::size_t modelCount = 0;
    if (!inputConverter_.process(readBuffer_.data(), count, modelInput_.data(), modelInput_.size(), &modelCount)) { deadlineFailure_ = true; return; }
    assembler_.append(modelInput_.data(), modelCount, [this](const float* frame) noexcept {
        bool valid = filter_.process(frame, enhanced_.data());
        for (float sample : enhanced_) valid = valid && std::isfinite(sample);
        const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
        recordResultForTest(valid, now);
        if (valid) {
            mixer_.process(frame, enhanced_.data(), enhanced_.data(), enhanced_.size());
            std::size_t routeCount = 0;
            if (!outputConverter_.process(enhanced_.data(), enhanced_.size(), routeOutput_.data(), routeOutput_.size(), &routeCount) || output_.write(routeOutput_.data(), routeCount) != routeCount) deadlineFailure_ = true;
        }
    });
}

void VoiceWorker::reset() noexcept {
    filter_.reset();
    assembler_ = FrameAssembler<480>{};
    mixer_ = ProfileMixer{FilterProfile::Balanced};
    watchdog_ = DeadlineWatchdog{};
    inputConverter_ = RateConverter{};
    outputConverter_ = RateConverter{};
    converterReady_.store(false, std::memory_order_release);
    deadlineFailure_.store(false, std::memory_order_release);
}
void VoiceWorker::setFilterMode(int mode) noexcept { requestedMode_.store(mode, std::memory_order_relaxed); }
void VoiceWorker::setRouteSampleRate(int sampleRate) noexcept { inputConverter_.configure(sampleRate, 48000); outputConverter_.configure(48000, sampleRate); converterReady_.store(sampleRate > 0, std::memory_order_release); }
bool VoiceWorker::takeDeadlineFailure() noexcept { return deadlineFailure_.exchange(false, std::memory_order_acq_rel); }
void VoiceWorker::recordResultForTest(bool valid, std::int64_t timestampMillis) noexcept { if (watchdog_.recordFrame(valid, timestampMillis) == DeadlineWatchdog::Result::ProcessingTooSlow) deadlineFailure_.store(true, std::memory_order_release); }

void renderMonoToOutput(SpscRingBuffer<float>& source, float* output, std::size_t frames,
                        std::size_t channelCount) noexcept {
    for (std::size_t frame = 0; frame < frames; ++frame) {
        float sample = 0.0F;
        (void)source.read(&sample, 1U);
        for (std::size_t channel = 0; channel < channelCount; ++channel) {
            output[frame * channelCount + channel] = sample;
        }
    }
}
