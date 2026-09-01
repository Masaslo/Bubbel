cmake_minimum_required(VERSION 3.22.1)

project(bubbel_audio_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_executable(audio_core_tests
    ${CMAKE_CURRENT_LIST_DIR}/../../test/cpp/AudioCoreTests.cpp
)

target_include_directories(audio_core_tests PRIVATE ${CMAKE_CURRENT_LIST_DIR})

if(ANDROID)
    set(BUBBEL_LIBDF_PATH
        ${CMAKE_CURRENT_LIST_DIR}/../../../build/rust/${ANDROID_ABI}/release/libbubbel_libdf.so)
    if(NOT EXISTS ${BUBBEL_LIBDF_PATH})
        message(FATAL_ERROR
            "Missing ${BUBBEL_LIBDF_PATH}. Run app/src/main/rust/build-android.sh first.")
    endif()

    add_library(bubbel_libdf SHARED IMPORTED)
    set_target_properties(bubbel_libdf PROPERTIES
        IMPORTED_LOCATION ${BUBBEL_LIBDF_PATH}
        IMPORTED_NO_SONAME TRUE)

    add_executable(voice_filter_tests
        ${CMAKE_CURRENT_LIST_DIR}/../../test/cpp/VoiceFilterTests.cpp
        ${CMAKE_CURRENT_LIST_DIR}/model/LibDfVoiceFilter.cpp
    )
    target_include_directories(voice_filter_tests PRIVATE ${CMAKE_CURRENT_LIST_DIR})
    target_link_libraries(voice_filter_tests PRIVATE bubbel_libdf)
endif()
