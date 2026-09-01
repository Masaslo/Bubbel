#pragma once

#include <cstddef>
#include <cstdint>

extern "C" {

struct BubbelLibDfSession;

enum BubbelLibDfStatus : std::int32_t {
    BUBBEL_LIBDF_STATUS_OK = 0,
    BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT = 1,
    BUBBEL_LIBDF_STATUS_MODEL_ERROR = 2,
    BUBBEL_LIBDF_STATUS_PROCESSING_ERROR = 3,
    BUBBEL_LIBDF_STATUS_PANIC = 4,
};

std::int32_t bubbel_libdf_open(
    const std::uint8_t* modelBytes,
    std::size_t modelLength,
    BubbelLibDfSession** outSession);
std::int32_t bubbel_libdf_process(
    BubbelLibDfSession* session,
    const float* input,
    std::size_t inputLength,
    float* output,
    std::size_t outputLength);
std::int32_t bubbel_libdf_reopen(BubbelLibDfSession* session);
std::int32_t bubbel_libdf_close(BubbelLibDfSession* session);

}  // extern "C"
