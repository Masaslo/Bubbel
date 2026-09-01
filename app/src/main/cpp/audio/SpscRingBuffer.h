#pragma once

#include <atomic>
#include <cstddef>
#include <vector>

// One producer and one consumer may call write/read concurrently. Storage is
// allocated once in the constructor; neither callback-facing method allocates
// or blocks.
template <typename T>
class SpscRingBuffer {
public:
    explicit SpscRingBuffer(std::size_t capacity)
        : storage_(capacity), capacity_(capacity) {}

    std::size_t write(const T* input, std::size_t count) noexcept {
        if (capacity_ == 0U || count == 0U) {
            return 0U;
        }

        const std::size_t writeIndex = writeIndex_.load(std::memory_order_relaxed);
        const std::size_t readIndex = readIndex_.load(std::memory_order_acquire);
        const std::size_t writable = capacity_ - (writeIndex - readIndex);
        const std::size_t written = count < writable ? count : writable;
        for (std::size_t offset = 0; offset < written; ++offset) {
            storage_[(writeIndex + offset) % capacity_] = input[offset];
        }
        writeIndex_.store(writeIndex + written, std::memory_order_release);
        return written;
    }

    std::size_t read(T* output, std::size_t count) noexcept {
        if (capacity_ == 0U || count == 0U) {
            return 0U;
        }

        const std::size_t readIndex = readIndex_.load(std::memory_order_relaxed);
        const std::size_t writeIndex = writeIndex_.load(std::memory_order_acquire);
        const std::size_t readable = writeIndex - readIndex;
        const std::size_t read = count < readable ? count : readable;
        for (std::size_t offset = 0; offset < read; ++offset) {
            output[offset] = storage_[(readIndex + offset) % capacity_];
        }
        readIndex_.store(readIndex + read, std::memory_order_release);
        return read;
    }

    // Only call after both producer and consumer have stopped.
    void clear() noexcept {
        const std::size_t writeIndex = writeIndex_.load(std::memory_order_acquire);
        readIndex_.store(writeIndex, std::memory_order_release);
    }

private:
    std::vector<T> storage_;
    const std::size_t capacity_;
    std::atomic<std::size_t> writeIndex_{0U};
    std::atomic<std::size_t> readIndex_{0U};
};
