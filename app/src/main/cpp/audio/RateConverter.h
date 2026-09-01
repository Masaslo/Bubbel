#pragma once

#include <algorithm>
#include <cmath>
#include <cstddef>

// Stateless, callback-safe linear conversion for one preallocated block.
inline std::size_t resampleMono(const float* input, std::size_t inputCount, int inputRate,
                                float* output, std::size_t outputCapacity, int outputRate) noexcept {
    if (inputCount == 0 || inputRate <= 0 || outputRate <= 0) return 0;
    const std::size_t count = std::min(outputCapacity, static_cast<std::size_t>(std::ceil(inputCount * static_cast<double>(outputRate) / inputRate)));
    for (std::size_t i = 0; i < count; ++i) {
        const double position = i * static_cast<double>(inputRate) / outputRate;
        const std::size_t left = std::min(static_cast<std::size_t>(position), inputCount - 1);
        const std::size_t right = std::min(left + 1, inputCount - 1);
        const float fraction = static_cast<float>(position - left);
        output[i] = input[left] + (input[right] - input[left]) * fraction;
    }
    return count;
}
