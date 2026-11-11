cmake_minimum_required(VERSION 3.22.1)
project(djengine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe from Google's Maven repo via prefab (see app/build.gradle.kts).
find_package(oboe REQUIRED CONFIG)

add_library(
    djengine
    SHARED
    jni_bridge.cpp
    AudioEngine.cpp
    Deck.cpp
)

target_include_directories(djengine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(
    djengine
    PRIVATE
    oboe::oboe
    log
    aaudio
    android
)
