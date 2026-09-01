#pragma once

#include <cstddef>

// Continuous linear converter retaining phase/carry across callback blocks.
class RateConverter {
public:
    RateConverter() = default;
    RateConverter(int inputRate, int outputRate) noexcept : inputRate_(inputRate), outputRate_(outputRate) {}
    void configure(int inputRate, int outputRate) noexcept { inputRate_ = inputRate; outputRate_ = outputRate; reset(); }
    void reset() noexcept { phase_ = 0; havePrevious_ = false; previous_ = 0; }
    bool process(const float* input, std::size_t inputCount, float* output, std::size_t capacity, std::size_t* produced) noexcept {
        *produced = 0;
        if (inputRate_ <= 0 || outputRate_ <= 0) return false;
        if (inputRate_ == outputRate_) {
            if (inputCount > capacity) return false;
            for (std::size_t i = 0; i < inputCount; ++i) output[i] = input[i];
            *produced = inputCount;
            return true;
        }
        for (std::size_t i = 0; i < inputCount; ++i) {
            const float current = input[i];
            if (!havePrevious_) { previous_ = current; havePrevious_ = true; continue; }
            phase_ += outputRate_;
            while (phase_ >= inputRate_) {
                if (*produced == capacity) return false;
                const float fraction = 1.0F - static_cast<float>(phase_ - inputRate_) / static_cast<float>(outputRate_);
                output[(*produced)++] = previous_ + (current - previous_) * fraction;
                phase_ -= inputRate_;
            }
            previous_ = current;
        }
        return true;
    }
private:
    int inputRate_, outputRate_, phase_ = 0;
    float previous_ = 0;
    bool havePrevious_ = false;
};
