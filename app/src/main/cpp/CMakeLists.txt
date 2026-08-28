cmake_minimum_required(VERSION 3.22.1)
project(life_needle)

add_library(life_needle SHARED needle_jni.cpp)

target_include_directories(life_needle PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

set(NEEDLE_A ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libneedle.a)
if(NOT EXISTS ${NEEDLE_A})
    message(FATAL_ERROR "Missing ${NEEDLE_A}. Run ./scripts/fetch-needle.sh")
endif()

# Pixel 8 / Android 15 need 16 KB ELF segments.
target_link_options(life_needle PRIVATE "-Wl,-z,max-page-size=16384")

# Model weights live in the static archive; keep unreferenced sections.
target_link_libraries(
    life_needle
    PRIVATE
    "-Wl,--whole-archive,${NEEDLE_A},--no-whole-archive"
    android
    log
    atomic
    m
)
