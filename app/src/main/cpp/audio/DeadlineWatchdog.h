#pragma once

#include <cstdint>

// Feed each processed frame with whether it produced valid output and its
// monotonic timestamp in milliseconds. A valid frame resets both watchdog
// counters. This class is single-threaded and makes no callback-time
// allocations or blocking calls.
class DeadlineWatchdog {
public:
    enum class Result {
        Healthy,
        ProcessingTooSlow,
    };

    Result recordFrame(bool hasValidOutput, std::int64_t timestampMillis) noexcept {
        if (hasValidOutput) {
            consecutiveMisses_ = 0U;
            firstInvalidOutputMillis_ = -1;
            return Result::Healthy;
        }

        if (firstInvalidOutputMillis_ < 0) {
            firstInvalidOutputMillis_ = timestampMillis;
        }
        ++consecutiveMisses_;
        if (consecutiveMisses_ >= 3U ||
            timestampMillis - firstInvalidOutputMillis_ >= kMaximumInvalidOutputMillis) {
            return Result::ProcessingTooSlow;
        }
        return Result::Healthy;
    }

private:
    static constexpr std::int64_t kMaximumInvalidOutputMillis = 250;
    std::uint32_t consecutiveMisses_ = 0U;
    std::int64_t firstInvalidOutputMillis_ = -1;
};
