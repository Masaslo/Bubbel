#include "model/VoiceFilter.h"

#include <array>
#include <cmath>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

namespace {

int failures = 0;

void expectTrue(bool condition, const char* expression, const char* testName) {
    if (!condition) {
        std::printf("FAIL [%s] %s\n", testName, expression);
        ++failures;
    }
}

#define EXPECT_TRUE(testName, expression) expectTrue((expression), #expression, testName)

std::vector<float> readFloatFile(const std::string& path) {
    std::ifstream stream(path, std::ios::binary | std::ios::ate);
    if (!stream) {
        return {};
    }
    const std::streamsize byteCount = stream.tellg();
    if (byteCount <= 0 || byteCount % static_cast<std::streamsize>(sizeof(float)) != 0) {
        return {};
    }
    stream.seekg(0);
    std::vector<float> values(static_cast<std::size_t>(byteCount) / sizeof(float));
    if (!stream.read(reinterpret_cast<char*>(values.data()), byteCount)) {
        return {};
    }
    return values;
}

float readGoldenTolerance(const std::string& metadataPath) {
    std::ifstream stream(metadataPath);
    std::ostringstream text;
    text << stream.rdbuf();
    const std::string key = "\"golden_tolerance_abs\"";
    const std::size_t keyPosition = text.str().find(key);
    if (keyPosition == std::string::npos) {
        return -1.0F;
    }
    const std::size_t colon = text.str().find(':', keyPosition + key.size());
    if (colon == std::string::npos) {
        return -1.0F;
    }
    try {
        return std::stof(text.str().substr(colon + 1));
    } catch (...) {
        return -1.0F;
    }
}

void uninitializedVoiceFilterRejectsA480SampleHop() {
    constexpr char testName[] = "uninitialized voice filter rejects a 480 sample hop";
    VoiceFilter filter;
    std::array<float, VoiceFilter::kHopSize> input = {};
    std::array<float, VoiceFilter::kHopSize> output = {};

    EXPECT_TRUE(testName, filter.process(input.data(), output.data()) == FilterResult::NotInitialized);
}

void outputMatchesPinnedGolden(
    const char* modelPath,
    const char* inputPath,
    const char* outputPath,
    const char* metadataPath) {
    constexpr char testName[] = "official libDF output matches pinned golden";
    const std::vector<float> input = readFloatFile(inputPath);
    const std::vector<float> expected = readFloatFile(outputPath);
    const float tolerance = readGoldenTolerance(metadataPath);
    EXPECT_TRUE(testName, input.size() == VoiceFilter::kHopSize);
    EXPECT_TRUE(testName, expected.size() == VoiceFilter::kHopSize);
    EXPECT_TRUE(testName, tolerance > 0.0F);
    if (input.size() != VoiceFilter::kHopSize || expected.size() != VoiceFilter::kHopSize || tolerance <= 0.0F) {
        return;
    }

    VoiceFilter filter;
    std::array<float, VoiceFilter::kHopSize> actual = {};
    EXPECT_TRUE(testName, filter.initialize({modelPath}) == FilterResult::Ok);
    EXPECT_TRUE(testName, filter.process(input.data(), actual.data()) == FilterResult::Ok);
    for (std::size_t index = 0; index < actual.size(); ++index) {
        EXPECT_TRUE(testName, std::fabs(actual[index] - expected[index]) <= tolerance);
    }
}

void resetReconstructsTheCompleteSession(const char* modelPath, const char* inputPath) {
    constexpr char testName[] = "reset reconstructs complete session state";
    const std::vector<float> input = readFloatFile(inputPath);
    EXPECT_TRUE(testName, input.size() == VoiceFilter::kHopSize);
    if (input.size() != VoiceFilter::kHopSize) {
        return;
    }

    VoiceFilter filter;
    std::array<float, VoiceFilter::kHopSize> first = {};
    std::array<float, VoiceFilter::kHopSize> afterReset = {};
    EXPECT_TRUE(testName, filter.initialize({modelPath}) == FilterResult::Ok);
    EXPECT_TRUE(testName, filter.process(input.data(), first.data()) == FilterResult::Ok);
    EXPECT_TRUE(testName, filter.reset() == FilterResult::Ok);
    EXPECT_TRUE(testName, filter.process(input.data(), afterReset.data()) == FilterResult::Ok);
    EXPECT_TRUE(testName, first == afterReset);
}

}  // namespace

int main(int argc, char** argv) {
    uninitializedVoiceFilterRejectsA480SampleHop();
    if (argc != 5) {
        std::printf("FAIL: expected model, golden input, golden output and metadata paths\n");
        return 2;
    }
    outputMatchesPinnedGolden(argv[1], argv[2], argv[3], argv[4]);
    resetReconstructsTheCompleteSession(argv[1], argv[2]);

    if (failures == 0) {
        std::printf("PASS: VoiceFilterTests\n");
        return 0;
    }
    std::printf("FAIL: %d assertion(s) failed\n", failures);
    return 1;
}
