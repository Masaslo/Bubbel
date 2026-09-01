#pragma once

#include "model/LibDfApi.h"

#include <cstddef>
#include <string>

struct ModelFiles {
    std::string archivePath;
};

enum class FilterResult {
    Ok,
    NotInitialized,
    InvalidArgument,
    ModelError,
    ProcessingError,
    Panic,
};

class VoiceFilter {
public:
    static constexpr std::size_t kHopSize = 480U;

    VoiceFilter() = default;
    ~VoiceFilter();
    VoiceFilter(const VoiceFilter&) = delete;
    VoiceFilter& operator=(const VoiceFilter&) = delete;

    [[nodiscard]] FilterResult initialize(const ModelFiles& files);
    [[nodiscard]] FilterResult initializeBytes(const std::uint8_t* bytes, std::size_t size);
    // This reconstructs the complete Rust DfTract. Invoke it outside callbacks.
    [[nodiscard]] FilterResult reset();
    [[nodiscard]] FilterResult process(const float input[kHopSize], float output[kHopSize]);

private:
    static FilterResult statusToResult(std::int32_t status);
    BubbelLibDfSession* session_ = nullptr;
};
