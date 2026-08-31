#pragma once

#include <array>
#include <cstddef>

// Accumulates arbitrarily sized callback payloads into fixed-size frames
// without allocating. The producer and its sink are expected on one thread.
template <std::size_t FrameSize>
class FrameAssembler {
public:
    template <typename FrameSink>
    void append(const float* input, std::size_t count, FrameSink&& onFrame) noexcept {
        for (std::size_t index = 0; index < count; ++index) {
            frame_[pending_++] = input[index];
            if (pending_ == FrameSize) {
                onFrame(frame_.data());
                pending_ = 0U;
            }
        }
    }

    std::size_t pendingSamples() const noexcept {
        return pending_;
    }

private:
    std::array<float, FrameSize> frame_ = {};
    std::size_t pending_ = 0U;
};
