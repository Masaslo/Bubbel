#pragma once

#include <array>
#include <atomic>
#include <cstddef>
#include <mutex>
#include <optional>
#include <string>

enum class AudioEventKind { Starting, Running, Recovering, Failed, Stopped };
struct AudioEvent { AudioEventKind kind; int value = 0; std::string text; };

// Callback error delivery only requests recovery atomically. All transition
// decisions and stream ownership remain with AudioEngine's control mutex.
class LifecycleState {
public:
    void beginStart() noexcept {
        manualStop_.store(false, std::memory_order_release);
        recoveryRequested_.store(false, std::memory_order_release);
    }
    void manualStop() noexcept {
        manualStop_.store(true, std::memory_order_release);
        recoveryRequested_.store(false, std::memory_order_release);
    }
    void requestRecovery() noexcept {
        if (!manualStop_.load(std::memory_order_acquire)) {
            recoveryRequested_.store(true, std::memory_order_release);
        }
    }
    bool takeRecoveryRequest() noexcept {
        if (manualStop_.load(std::memory_order_acquire)) {
            recoveryRequested_.store(false, std::memory_order_release);
            return false;
        }
        return recoveryRequested_.exchange(false, std::memory_order_acq_rel);
    }
    bool isManualStop() const noexcept { return manualStop_.load(std::memory_order_acquire); }

    void pushEvent(AudioEventKind kind, int value = 0, const char* text = "") {
        std::lock_guard<std::mutex> lock(eventMutex_);
        if (eventCount_ == events_.size()) { eventRead_ = (eventRead_ + 1) % events_.size(); --eventCount_; }
        events_[eventWrite_] = AudioEvent{kind, value, text};
        eventWrite_ = (eventWrite_ + 1) % events_.size();
        ++eventCount_;
    }
    std::optional<AudioEvent> popEvent() {
        std::lock_guard<std::mutex> lock(eventMutex_);
        if (eventCount_ == 0) return std::nullopt;
        AudioEvent result = std::move(events_[eventRead_]);
        eventRead_ = (eventRead_ + 1) % events_.size();
        --eventCount_;
        return result;
    }

private:
    std::atomic<bool> manualStop_{false};
    std::atomic<bool> recoveryRequested_{false};
    std::mutex eventMutex_;
    std::array<AudioEvent, 16> events_{};
    std::size_t eventRead_ = 0, eventWrite_ = 0, eventCount_ = 0;
};
