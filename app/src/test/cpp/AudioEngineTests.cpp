#include "audio/VoiceWorker.h"

#include <array>
#include <cstdio>

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

void resetAndRetryUseFixedDelays() {
    SpscRingBuffer<float> input(1), output(1);
    DoublingFilter filter;
    VoiceWorker worker(input, output, filter);
    worker.reset();
    expect(filter.resets == 1, "reset reaches filter");
    expect(recoveryDelayMillis(1) == 100 && recoveryDelayMillis(2) == 250 && recoveryDelayMillis(3) == 500, "retry delays are fixed");
}
}

int main() {
    outputSilencesUnderrunAndDuplicatesMono();
    workerFramesInputAndWritesOneHop();
    resetAndRetryUseFixedDelays();
    std::printf(failures == 0 ? "PASS: AudioEngineTests\n" : "FAIL: AudioEngineTests\n");
    return failures == 0 ? 0 : 1;
}
