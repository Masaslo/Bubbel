#include "audio/VoiceWorker.h"

#include <algorithm>
#include <chrono>
#include <cmath>

VoiceWorker::VoiceWorker(SpscRingBuffer<float>& input, SpscRingBuffer<float>& output,
                         IVoiceFilter& filter) noexcept
    : input_(input), output_(output), filter_(filter) {}

void VoiceWorker::processAvailable() noexcept {
    const int mode = requestedMode_.load(std::memory_order_relaxed);
    mixer_.setProfile(mode == 0 ? FilterProfile::Natural : (mode == 2 ? FilterProfile::Strong : FilterProfile::Balanced));
    const std::size_t count = input_.read(readBuffer_.data(), readBuffer_.size());
    assembler_.append(readBuffer_.data(), count, [this](const float* frame) noexcept {
        bool valid = filter_.process(frame, enhanced_.data());
        for (float sample : enhanced_) valid = valid && std::isfinite(sample);
        const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();
        if (watchdog_.recordFrame(valid, now) == DeadlineWatchdog::Result::ProcessingTooSlow) deadlineFailure_.store(true, std::memory_order_release);
        if (valid) {
            mixer_.process(frame, enhanced_.data(), enhanced_.data(), enhanced_.size());
            (void)output_.write(enhanced_.data(), enhanced_.size());
        }
    });
}

void VoiceWorker::reset() noexcept { filter_.reset(); }
void VoiceWorker::setFilterMode(int mode) noexcept { requestedMode_.store(mode, std::memory_order_relaxed); }
bool VoiceWorker::takeDeadlineFailure() noexcept { return deadlineFailure_.exchange(false, std::memory_order_acq_rel); }

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
