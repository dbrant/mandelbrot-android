cmake_minimum_required(VERSION 3.22)
project(mandel_native)

# Build our shared library
add_library(mandel_native SHARED mandel_native.cpp)

# Import GMP + MPFR as prebuilt static libs
add_library(gmp STATIC IMPORTED)
set_target_properties(gmp PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libgmp.a)

add_library(mpfr STATIC IMPORTED)
set_target_properties(mpfr PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/libmpfr.a)

set(CMAKE_SHARED_LINKER_FLAGS
        "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# Headers
target_include_directories(mandel_native PRIVATE ${CMAKE_SOURCE_DIR}/include)

# Link
target_link_libraries(mandel_native mpfr gmp log)
