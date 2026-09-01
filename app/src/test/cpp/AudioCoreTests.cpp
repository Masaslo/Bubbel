#include "audio/DeadlineWatchdog.h"
#include "audio/FrameAssembler.h"
#include "audio/ProfileMixer.h"
#include "audio/SpscRingBuffer.h"

#include <array>
#include <cmath>
#include <cstddef>
#include <cstdio>

namespace {

int failures = 0;

void expectTrue(bool condition, const char* expression, const char* testName) {
    if (!condition) {
        std::printf("FAIL [%s] %s\n", testName, expression);
        ++failures;
    }
}

void expectNear(float actual, float expected, float tolerance, const char* expression, const char* testName) {
    if (std::fabs(actual - expected) > tolerance) {
        std::printf("FAIL [%s] %s (expected %.9f, got %.9f)\n", testName, expression, expected, actual);
        ++failures;
    }
}

#define EXPECT_TRUE(testName, expression) expectTrue((expression), #expression, testName)
#define EXPECT_NEAR(testName, actual, expected, tolerance) expectNear((actual), (expected), (tolerance), #actual, testName)

void spscRingBufferWrapsWithoutOverwritingUnreadSamples() {
    constexpr char testName[] = "spsc ring buffer wraps without overwriting unread samples";
    SpscRingBuffer<float> ring(4);
    const std::array<float, 4> first = {1.0F, 2.0F, 3.0F, 4.0F};
    const std::array<float, 2> second = {5.0F, 6.0F};
    std::array<float, 4> output = {};

    EXPECT_TRUE(testName, ring.write(first.data(), first.size()) == 4U);
    EXPECT_TRUE(testName, ring.write(second.data(), 1U) == 0U);
    EXPECT_TRUE(testName, ring.read(output.data(), 2U) == 2U);
    EXPECT_NEAR(testName, output[0], 1.0F, 0.0F);
    EXPECT_NEAR(testName, output[1], 2.0F, 0.0F);
    EXPECT_TRUE(testName, ring.write(second.data(), second.size()) == 2U);
    EXPECT_TRUE(testName, ring.read(output.data(), output.size()) == 4U);
    EXPECT_NEAR(testName, output[0], 3.0F, 0.0F);
    EXPECT_NEAR(testName, output[1], 4.0F, 0.0F);
    EXPECT_NEAR(testName, output[2], 5.0F, 0.0F);
    EXPECT_NEAR(testName, output[3], 6.0F, 0.0F);
    EXPECT_TRUE(testName, ring.read(output.data(), 1U) == 0U);
}

void frameAssemblerEmitsContiguous480SampleHopsAcrossCallbacks() {
    constexpr char testName[] = "frame assembler emits contiguous 480 sample hops across callbacks";
    FrameAssembler<480> assembler;
    std::array<float, 479> first = {};
    std::array<float, 481> second = {};
    for (std::size_t index = 0; index < first.size(); ++index) {
        first[index] = static_cast<float>(index);
    }
    for (std::size_t index = 0; index < second.size(); ++index) {
        second[index] = static_cast<float>(479U + index);
    }

    std::size_t frames = 0;
    assembler.append(first.data(), first.size(), [&](const float* frame) {
        EXPECT_TRUE(testName, false);
        (void)frame;
    });
    assembler.append(second.data(), second.size(), [&](const float* frame) {
        for (std::size_t index = 0; index < 480U; ++index) {
            EXPECT_NEAR(testName, frame[index], static_cast<float>(frames * 480U + index), 0.0F);
        }
        ++frames;
    });
    EXPECT_TRUE(testName, frames == 2U);
    EXPECT_TRUE(testName, assembler.pendingSamples() == 0U);
}

void profileMixerUsesExactDryFloorEndpointsAnd2400SampleRamps() {
    constexpr char testName[] = "profile mixer uses exact dry floor endpoints and 2400 sample ramps";
    constexpr float naturalFloor = 0.25118864F;
    constexpr float balancedFloor = 0.06309573F;
    std::array<float, 2400> dry = {};
    std::array<float, 2400> enhanced = {};
    std::array<float, 2400> output = {};
    dry.fill(1.0F);

    ProfileMixer mixer(FilterProfile::Natural);
    mixer.process(dry.data(), enhanced.data(), output.data(), 1U);
    EXPECT_NEAR(testName, output[0], naturalFloor, 0.000001F);

    mixer.setProfile(FilterProfile::Balanced);
    mixer.process(dry.data(), enhanced.data(), output.data(), output.size());
    EXPECT_TRUE(testName, output[2398] > balancedFloor);
    EXPECT_NEAR(testName, output[2399], balancedFloor, 0.000001F);

    mixer.setProfile(FilterProfile::Strong);
    mixer.process(dry.data(), enhanced.data(), output.data(), output.size());
    EXPECT_TRUE(testName, output[2398] > 0.0F);
    EXPECT_NEAR(testName, output[2399], 0.0F, 0.000001F);

    enhanced[0] = 1.0F;
    mixer.process(dry.data(), enhanced.data(), output.data(), 1U);
    EXPECT_NEAR(testName, output[0], 1.0F, 0.000001F);
}

void deadlineWatchdogFlagsThirdConsecutiveMissAnd250MillisecondInvalidOutput() {
    constexpr char testName[] = "deadline watchdog flags third consecutive miss and 250 millisecond invalid output";
    DeadlineWatchdog consecutiveMisses;
    EXPECT_TRUE(testName, consecutiveMisses.recordFrame(true, 0) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, consecutiveMisses.recordFrame(false, 10) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, consecutiveMisses.recordFrame(false, 20) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, consecutiveMisses.recordFrame(false, 30) == DeadlineWatchdog::Result::ProcessingTooSlow);

    DeadlineWatchdog invalidOutputDuration;
    EXPECT_TRUE(testName, invalidOutputDuration.recordFrame(true, 0) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, invalidOutputDuration.recordFrame(false, 100) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, invalidOutputDuration.recordFrame(false, 350) == DeadlineWatchdog::Result::ProcessingTooSlow);

    DeadlineWatchdog resetOnValidOutput;
    EXPECT_TRUE(testName, resetOnValidOutput.recordFrame(false, 10) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, resetOnValidOutput.recordFrame(false, 20) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, resetOnValidOutput.recordFrame(true, 21) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, resetOnValidOutput.recordFrame(false, 30) == DeadlineWatchdog::Result::Healthy);
    EXPECT_TRUE(testName, resetOnValidOutput.recordFrame(false, 31) == DeadlineWatchdog::Result::Healthy);
}

}  // namespace

int main() {
    spscRingBufferWrapsWithoutOverwritingUnreadSamples();
    frameAssemblerEmitsContiguous480SampleHopsAcrossCallbacks();
    profileMixerUsesExactDryFloorEndpointsAnd2400SampleRamps();
    deadlineWatchdogFlagsThirdConsecutiveMissAnd250MillisecondInvalidOutput();

    if (failures == 0) {
        std::printf("PASS: AudioCoreTests\n");
        return 0;
    }
    std::printf("FAIL: %d assertion(s) failed\n", failures);
    return 1;
}
