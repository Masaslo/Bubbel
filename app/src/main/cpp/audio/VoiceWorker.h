#pragma once

#include "audio/FrameAssembler.h"
#include "audio/DeadlineWatchdog.h"
#include "audio/ProfileMixer.h"
#include "audio/SpscRingBuffer.h"

#include <array>
#include <atomic>
#include <cstddef>

class IVoiceFilter {
public:
    virtual ~IVoiceFilter() = default;
    virtual bool process(const float input[480], float output[480]) noexcept = 0;
    virtual void reset() noexcept = 0;
};

// Worker-owned filter boundary. The audio callbacks only move samples through
// the two SPSC queues; all model execution happens when processAvailable() is
// invoked by the engine's worker thread.
class VoiceWorker {
public:
    VoiceWorker(SpscRingBuffer<float>& input, SpscRingBuffer<float>& output, IVoiceFilter& filter) noexcept;
    void processAvailable() noexcept;
    void reset() noexcept;
    void setFilterMode(int mode) noexcept;
    bool takeDeadlineFailure() noexcept;
    bool deadlineFailed() const noexcept { return deadlineFailure_.load(std::memory_order_acquire); }

private:
    SpscRingBuffer<float>& input_;
    SpscRingBuffer<float>& output_;
    IVoiceFilter& filter_;
    FrameAssembler<480> assembler_;
    std::array<float, 480> readBuffer_ = {};
    std::array<float, 480> enhanced_ = {};
    ProfileMixer mixer_{FilterProfile::Balanced};
    std::atomic<int> requestedMode_{1};
    DeadlineWatchdog watchdog_;
    std::atomic<bool> deadlineFailure_{false};
};

// Callback-safe interleaving helper: an empty output queue is rendered as
// silence, and the mono model output is duplicated for every output channel.
void renderMonoToOutput(SpscRingBuffer<float>& source, float* output, std::size_t frames,
                        std::size_t channelCount) noexcept;
constexpr int recoveryDelayMillis(int attempt) noexcept {
    return attempt == 1 ? 100 : (attempt == 2 ? 250 : (attempt == 3 ? 500 : 0));
}
