#pragma once

#include <cstddef>

enum class FilterProfile {
    Natural,
    Balanced,
    Strong,
};

// Mixes dry and enhanced samples with a 50 ms (2400 sample at 48 kHz) dry-gain
// transition. Processing performs no allocation or locking.
class ProfileMixer {
public:
    static constexpr std::size_t kRampSamples = 2400U;

    explicit ProfileMixer(FilterProfile profile = FilterProfile::Natural) noexcept
        : profile_(profile), currentDryFloor_(dryFloor(profile)), targetDryFloor_(currentDryFloor_) {}

    void setProfile(FilterProfile profile) noexcept {
        if (profile == profile_) {
            return;
        }
        profile_ = profile;
        targetDryFloor_ = dryFloor(profile);
        rampSamplesRemaining_ = kRampSamples;
    }

    void process(const float* dry, const float* enhanced, float* output, std::size_t count) noexcept {
        for (std::size_t index = 0; index < count; ++index) {
            advanceRamp();
            output[index] = (1.0F - currentDryFloor_) * enhanced[index] + currentDryFloor_ * dry[index];
        }
    }

private:
    static constexpr float dryFloor(FilterProfile profile) noexcept {
        switch (profile) {
            case FilterProfile::Natural:
                return 0.251188643150958F;  // 10^(-12 / 20)
            case FilterProfile::Balanced:
                return 0.0630957344480193F;  // 10^(-24 / 20)
            case FilterProfile::Strong:
                return 0.0F;
        }
        return 0.0F;
    }

    void advanceRamp() noexcept {
        if (rampSamplesRemaining_ == 0U) {
            return;
        }
        if (rampSamplesRemaining_ == 1U) {
            currentDryFloor_ = targetDryFloor_;
        } else {
            currentDryFloor_ += (targetDryFloor_ - currentDryFloor_) /
                                static_cast<float>(rampSamplesRemaining_);
        }
        --rampSamplesRemaining_;
    }

    FilterProfile profile_;
    float currentDryFloor_;
    float targetDryFloor_;
    std::size_t rampSamplesRemaining_ = 0U;
};
