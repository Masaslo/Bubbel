#include "audio/VoiceWorker.h"

#include <algorithm>

VoiceWorker::VoiceWorker(SpscRingBuffer<float>& input, SpscRingBuffer<float>& output,
                         IVoiceFilter& filter) noexcept
    : input_(input), output_(output), filter_(filter) {}

void VoiceWorker::processAvailable() noexcept {
    const int mode = requestedMode_.load(std::memory_order_relaxed);
    mixer_.setProfile(mode == 0 ? FilterProfile::Natural : (mode == 2 ? FilterProfile::Strong : FilterProfile::Balanced));
    const std::size_t count = input_.read(readBuffer_.data(), readBuffer_.size());
    assembler_.append(readBuffer_.data(), count, [this](const float* frame) noexcept {
        if (filter_.process(frame, enhanced_.data())) {
            mixer_.process(frame, enhanced_.data(), enhanced_.data(), enhanced_.size());
            (void)output_.write(enhanced_.data(), enhanced_.size());
        }
    });
}

void VoiceWorker::reset() noexcept { filter_.reset(); }
void VoiceWorker::setFilterMode(int mode) noexcept { requestedMode_.store(mode, std::memory_order_relaxed); }

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
