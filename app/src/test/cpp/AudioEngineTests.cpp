#include "audio/VoiceWorker.h"
#include "audio/RateConverter.h"
#include "audio/LifecycleState.h"

#include <array>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <mutex>
#include <thread>

namespace {
int failures = 0;
void expect(bool value, const char* name) { if (!value) { std::printf("FAIL: %s\n", name); ++failures; } }

class DoublingFilter final : public IVoiceFilter {
public:
    bool process(const float input[480], float output[480]) noexcept override {
        ++calls;
        for (std::size_t i = 0; i < 480; ++i) output[i] = input[i] * 2.0F;
        return true;
    }
    void reset() noexcept override { ++resets; }
    int calls = 0;
    int resets = 0;
};
class FailingFilter final : public IVoiceFilter {
public:
    bool process(const float[480], float[480]) noexcept override { return false; }
    void reset() noexcept override {}
};

void outputSilencesUnderrunAndDuplicatesMono() {
    SpscRingBuffer<float> output(8);
    std::array<float, 4> interleaved = {9, 9, 9, 9};
    renderMonoToOutput(output, interleaved.data(), 2, 2);
    expect(interleaved == std::array<float, 4>{0, 0, 0, 0}, "underrun is silence");
    const std::array<float, 2> mono = {0.25F, -0.5F};
    output.write(mono.data(), mono.size());
    renderMonoToOutput(output, interleaved.data(), 2, 2);
    expect(interleaved == std::array<float, 4>{0.25F, 0.25F, -0.5F, -0.5F}, "mono duplicates channels");
}

void workerFramesInputAndWritesOneHop() {
    SpscRingBuffer<float> input(1024), output(1024);
    DoublingFilter filter;
    VoiceWorker worker(input, output, filter);
    worker.setRouteSampleRate(48000);
    worker.setFilterMode(2);
    std::array<float, 479> first{};
    std::array<float, 1> last = {3};
    input.write(first.data(), first.size());
    worker.processAvailable();
    expect(filter.calls == 0, "worker waits for complete frame");
    input.write(last.data(), last.size());
    worker.processAvailable();
    std::array<float, 480> processed{};
    expect(filter.calls == 1 && output.read(processed.data(), processed.size()) == 480, "worker writes exactly one hop");
    expect(processed[479] > 3 && processed[479] <= 6, "worker filters assembled hop");
}
void workerSupportsDistinctInputAndOutputRates() {
    SpscRingBuffer<float> input(4096), output(4096);
    DoublingFilter filter;
    VoiceWorker worker(input, output, filter);
    worker.setRouteSampleRates(24000, 44100);
    std::array<float, 480> samples{};
    std::array<float, 1000> converted{};
    input.write(samples.data(), samples.size());
    worker.processAvailable();
    expect(filter.calls == 1 && output.read(converted.data(), converted.size()) > 0 && !worker.deadlineFailed(),
           "worker converts between distinct input and output route rates");
}
void workerDrainsLowRateBacklogWithoutConverterCapacityFailure() {
    // No output consumer runs here: retain all 23 generated 480-sample hops.
    SpscRingBuffer<float> input(4096), output(48000);
    DoublingFilter filter;
    VoiceWorker worker(input, output, filter);
    worker.setRouteSampleRates(8000, 48000);
    std::array<float, 1920> samples{};
    input.write(samples.data(), samples.size());
    worker.processAvailable();
    expect(filter.calls == 23 && !worker.deadlineFailed(),
           "worker drains low-rate backlog in bounded conversion chunks");
}

void resetAndRetryUseFixedDelays() {
    SpscRingBuffer<float> input(1), output(1);
    DoublingFilter filter;
    VoiceWorker worker(input, output, filter);
    worker.reset();
    expect(filter.resets == 1, "reset reaches filter");
    expect(recoveryDelayMillis(1) == 100 && recoveryDelayMillis(2) == 250 && recoveryDelayMillis(3) == 500, "retry delays are fixed");
}
void resamplingPreservesEndpointsAcrossRates() {
    RateConverter converter(24000, 48000); std::array<float, 2> input = {0, 1}; std::array<float, 4> output = {}; std::size_t count = 0;
    expect(converter.process(input.data(), 1, output.data(), output.size(), &count) && count == 0, "converter retains initial carry");
    expect(converter.process(input.data() + 1, 1, output.data(), output.size(), &count) && count == 2 && output[1] == 1, "converter preserves phase across blocks");
}
void workerStopsAfterThreeInvalidHops() {
    SpscRingBuffer<float> input(2048), output(2048); FailingFilter filter; VoiceWorker worker(input, output, filter); worker.setRouteSampleRate(48000);
    std::array<float, 1440> samples = {}; input.write(samples.data(), samples.size());
    worker.processAvailable(); worker.processAvailable(); worker.processAvailable();
    expect(worker.takeDeadlineFailure(), "worker reports third invalid hop");
}
void workerStopsAfterInvalidDurationAndResetClearsFailure() {
    SpscRingBuffer<float> input(1), output(1); FailingFilter filter; VoiceWorker worker(input, output, filter);
    worker.recordResultForTest(false, 10); worker.recordResultForTest(false, 260);
    expect(worker.takeDeadlineFailure(), "worker reports 250ms invalid output");
    worker.reset(); expect(!worker.takeDeadlineFailure(), "reset clears deadline failure");
}
void lifecycleQueuesEventsAndSuppressesRecoveryAfterManualStop() {
    LifecycleState lifecycle;
    lifecycle.pushEvent(AudioEventKind::Starting);
    lifecycle.pushEvent(AudioEventKind::Running, 48000);
    const auto starting = lifecycle.popEvent();
    const auto running = lifecycle.popEvent();
    expect(starting && starting->kind == AudioEventKind::Starting && running && running->kind == AudioEventKind::Running,
           "lifecycle events preserve FIFO order");

    lifecycle.requestRecovery();
    expect(lifecycle.takeRecoveryRequest(), "recovery request is accepted after a completed transition");
    lifecycle.manualStop();
    lifecycle.requestRecovery();
    expect(!lifecycle.takeRecoveryRequest(), "manual stop suppresses recovery");
}
void recoveryRetriesCanRepeatAndManualStopCancelsWait() {
    LifecycleState lifecycle;
    for (int request = 0; request < 3; ++request) {
        lifecycle.requestRecovery();
        expect(lifecycle.takeRecoveryRequest(), "each recovery request is accepted after the preceding recovery");
    }

    std::mutex controlMutex;
    std::condition_variable cancellation;
    std::mutex enteredMutex;
    std::condition_variable entered;
    bool recoveryEntered = false;
    bool retryCompleted = true;
    std::thread recovery([&] {
        std::unique_lock<std::mutex> controlLock(controlMutex);
        {
            std::lock_guard<std::mutex> enteredLock(enteredMutex);
            recoveryEntered = true;
        }
        entered.notify_one();
        retryCompleted = waitForRecoveryRetry(cancellation, controlLock, lifecycle, std::chrono::seconds(5));
    });
    {
        std::unique_lock<std::mutex> enteredLock(enteredMutex);
        entered.wait(enteredLock, [&] { return recoveryEntered; });
    }
    {
        std::lock_guard<std::mutex> controlLock(controlMutex);
        lifecycle.manualStop();
        cancellation.notify_all();
    }
    recovery.join();
    expect(!retryCompleted, "manual stop cancels an in-progress retry wait");
}
}

int main() {
    outputSilencesUnderrunAndDuplicatesMono();
    workerFramesInputAndWritesOneHop();
    workerSupportsDistinctInputAndOutputRates();
    workerDrainsLowRateBacklogWithoutConverterCapacityFailure();
    resetAndRetryUseFixedDelays();
    resamplingPreservesEndpointsAcrossRates();
    workerStopsAfterThreeInvalidHops();
    workerStopsAfterInvalidDurationAndResetClearsFailure();
    lifecycleQueuesEventsAndSuppressesRecoveryAfterManualStop();
    recoveryRetriesCanRepeatAndManualStopCancelsWait();
    std::printf(failures == 0 ? "PASS: AudioEngineTests\n" : "FAIL: AudioEngineTests\n");
    return failures == 0 ? 0 : 1;
}
