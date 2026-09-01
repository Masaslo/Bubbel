#include "model/VoiceFilter.h"

#include <fstream>
#include <iterator>
#include <utility>
#include <vector>

namespace {

bool readArchive(const std::string& path, std::vector<std::uint8_t>* bytes) {
    std::ifstream archive(path, std::ios::binary);
    if (!archive) {
        return false;
    }
    bytes->assign(std::istreambuf_iterator<char>(archive), std::istreambuf_iterator<char>());
    return archive.good() || archive.eof();
}

}  // namespace

VoiceFilter::~VoiceFilter() {
    if (session_ != nullptr) {
        (void)bubbel_libdf_close(session_);
        session_ = nullptr;
    }
}

FilterResult VoiceFilter::initialize(const ModelFiles& files) {
    if (files.archivePath.empty()) {
        return FilterResult::InvalidArgument;
    }

    std::vector<std::uint8_t> modelBytes;
    if (!readArchive(files.archivePath, &modelBytes) || modelBytes.empty()) {
        return FilterResult::ModelError;
    }

    BubbelLibDfSession* newSession = nullptr;
    const FilterResult result = statusToResult(
        bubbel_libdf_open(modelBytes.data(), modelBytes.size(), &newSession));
    if (result != FilterResult::Ok) {
        return result;
    }

    if (session_ != nullptr) {
        (void)bubbel_libdf_close(session_);
    }
    session_ = newSession;
    return FilterResult::Ok;
}

FilterResult VoiceFilter::reset() {
    if (session_ == nullptr) {
        return FilterResult::NotInitialized;
    }
    return statusToResult(bubbel_libdf_reopen(session_));
}

FilterResult VoiceFilter::process(const float input[kHopSize], float output[kHopSize]) {
    if (session_ == nullptr) {
        return FilterResult::NotInitialized;
    }
    if (input == nullptr || output == nullptr) {
        return FilterResult::InvalidArgument;
    }
    return statusToResult(bubbel_libdf_process(session_, input, kHopSize, output, kHopSize));
}

FilterResult VoiceFilter::statusToResult(std::int32_t status) {
    switch (status) {
        case BUBBEL_LIBDF_STATUS_OK:
            return FilterResult::Ok;
        case BUBBEL_LIBDF_STATUS_INVALID_ARGUMENT:
            return FilterResult::InvalidArgument;
        case BUBBEL_LIBDF_STATUS_MODEL_ERROR:
            return FilterResult::ModelError;
        case BUBBEL_LIBDF_STATUS_PROCESSING_ERROR:
            return FilterResult::ProcessingError;
        case BUBBEL_LIBDF_STATUS_PANIC:
        default:
            return FilterResult::Panic;
    }
}
