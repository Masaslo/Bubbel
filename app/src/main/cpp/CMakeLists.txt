cmake_minimum_required(VERSION 3.22.1)

project(bubbel_audio_core LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_executable(audio_core_tests
    ${CMAKE_CURRENT_LIST_DIR}/../../test/cpp/AudioCoreTests.cpp
)

target_include_directories(audio_core_tests PRIVATE ${CMAKE_CURRENT_LIST_DIR})
