#!/bin/bash
#
# Build GMP and MPFR for iOS (device + simulator)
#
# Usage: ./build_libs.sh
#
# Output:
#   libs/device/      - arm64 (device) static libraries
#   libs/simulator/   - fat arm64+x86_64 (simulator) static libraries
#   include/          - headers
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build_tmp"
OUTPUT_DIR="$SCRIPT_DIR/libs"
INCLUDE_DIR="$SCRIPT_DIR/include"

GMP_VERSION="6.3.0"
MPFR_VERSION="4.2.1"

GMP_URL="https://ftp.gnu.org/gnu/gmp/gmp-${GMP_VERSION}.tar.xz"
MPFR_URL="https://ftp.gnu.org/gnu/mpfr/mpfr-${MPFR_VERSION}.tar.xz"

MIN_IOS_VERSION="16.0"

# Clean up previous builds
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR/device"
mkdir -p "$OUTPUT_DIR/simulator"
mkdir -p "$INCLUDE_DIR"

cd "$BUILD_DIR"

# Download sources
echo "=== Downloading GMP ${GMP_VERSION} ==="
curl -L -o "gmp-${GMP_VERSION}.tar.xz" "$GMP_URL"
tar xf "gmp-${GMP_VERSION}.tar.xz"

echo "=== Downloading MPFR ${MPFR_VERSION} ==="
curl -L -o "mpfr-${MPFR_VERSION}.tar.xz" "$MPFR_URL"
tar xf "mpfr-${MPFR_VERSION}.tar.xz"

build_gmp() {
    local LABEL=$1
    local ARCH=$2
    local PLATFORM=$3
    local HOST=$4
    local TARGET=$5
    local SDK_PATH

    if [ "$PLATFORM" = "iPhoneOS" ]; then
        SDK_PATH=$(xcrun --sdk iphoneos --show-sdk-path)
    else
        SDK_PATH=$(xcrun --sdk iphonesimulator --show-sdk-path)
    fi

    local PREFIX="$BUILD_DIR/gmp-${LABEL}"
    local SRC_DIR="$BUILD_DIR/gmp-${GMP_VERSION}"

    echo "=== Building GMP for ${LABEL} (${PLATFORM}) ==="

    mkdir -p "$BUILD_DIR/gmp-build-${LABEL}"
    cd "$BUILD_DIR/gmp-build-${LABEL}"

    local CFLAGS="-arch ${ARCH} -isysroot ${SDK_PATH} -target ${TARGET} -O2"

    CC="$(xcrun --find clang)" \
    CXX="$(xcrun --find clang++)" \
    CFLAGS="$CFLAGS" \
    CXXFLAGS="$CFLAGS" \
    LDFLAGS="-arch ${ARCH} -isysroot ${SDK_PATH} -target ${TARGET}" \
    "$SRC_DIR/configure" \
        --host="${HOST}" \
        --prefix="${PREFIX}" \
        --disable-shared \
        --enable-static \
        --disable-assembly

    make -j$(sysctl -n hw.ncpu)
    make install

    cd "$BUILD_DIR"
}

build_mpfr() {
    local LABEL=$1
    local ARCH=$2
    local PLATFORM=$3
    local HOST=$4
    local TARGET=$5
    local SDK_PATH

    if [ "$PLATFORM" = "iPhoneOS" ]; then
        SDK_PATH=$(xcrun --sdk iphoneos --show-sdk-path)
    else
        SDK_PATH=$(xcrun --sdk iphonesimulator --show-sdk-path)
    fi

    local GMP_PREFIX="$BUILD_DIR/gmp-${LABEL}"
    local PREFIX="$BUILD_DIR/mpfr-${LABEL}"
    local SRC_DIR="$BUILD_DIR/mpfr-${MPFR_VERSION}"

    echo "=== Building MPFR for ${LABEL} (${PLATFORM}) ==="

    mkdir -p "$BUILD_DIR/mpfr-build-${LABEL}"
    cd "$BUILD_DIR/mpfr-build-${LABEL}"

    local CFLAGS="-arch ${ARCH} -isysroot ${SDK_PATH} -target ${TARGET} -O2"

    CC="$(xcrun --find clang)" \
    CXX="$(xcrun --find clang++)" \
    CFLAGS="$CFLAGS" \
    CXXFLAGS="$CFLAGS" \
    LDFLAGS="-arch ${ARCH} -isysroot ${SDK_PATH} -target ${TARGET}" \
    "$SRC_DIR/configure" \
        --host="${HOST}" \
        --prefix="${PREFIX}" \
        --disable-shared \
        --enable-static \
        --with-gmp="${GMP_PREFIX}"

    make -j$(sysctl -n hw.ncpu)
    make install

    cd "$BUILD_DIR"
}

# Build for device (arm64)
#                  LABEL        ARCH     PLATFORM        HOST                    TARGET
build_gmp  "arm64"      "arm64"  "iPhoneOS"      "aarch64-apple-darwin"  "arm64-apple-ios${MIN_IOS_VERSION}"
build_mpfr "arm64"      "arm64"  "iPhoneOS"      "aarch64-apple-darwin"  "arm64-apple-ios${MIN_IOS_VERSION}"

# Build for simulator (arm64)
build_gmp  "arm64-sim"  "arm64"  "iPhoneSimulator" "aarch64-apple-darwin" "arm64-apple-ios${MIN_IOS_VERSION}-simulator"
build_mpfr "arm64-sim"  "arm64"  "iPhoneSimulator" "aarch64-apple-darwin" "arm64-apple-ios${MIN_IOS_VERSION}-simulator"

# Build for simulator (x86_64)
build_gmp  "x86_64"     "x86_64" "iPhoneSimulator" "x86_64-apple-darwin"  "x86_64-apple-ios${MIN_IOS_VERSION}-simulator"
build_mpfr "x86_64"     "x86_64" "iPhoneSimulator" "x86_64-apple-darwin"  "x86_64-apple-ios${MIN_IOS_VERSION}-simulator"

# Copy device libs
echo "=== Copying device libraries ==="
cp "$BUILD_DIR/gmp-arm64/lib/libgmp.a" "$OUTPUT_DIR/device/"
cp "$BUILD_DIR/mpfr-arm64/lib/libmpfr.a" "$OUTPUT_DIR/device/"

# Create fat simulator libs
echo "=== Creating fat simulator libraries ==="
lipo -create \
    "$BUILD_DIR/gmp-arm64-sim/lib/libgmp.a" \
    "$BUILD_DIR/gmp-x86_64/lib/libgmp.a" \
    -output "$OUTPUT_DIR/simulator/libgmp.a"

lipo -create \
    "$BUILD_DIR/mpfr-arm64-sim/lib/libmpfr.a" \
    "$BUILD_DIR/mpfr-x86_64/lib/libmpfr.a" \
    -output "$OUTPUT_DIR/simulator/libmpfr.a"

# Copy headers
echo "=== Copying headers ==="
cp "$BUILD_DIR/gmp-arm64/include/gmp.h" "$INCLUDE_DIR/"
cp "$BUILD_DIR/mpfr-arm64/include/mpfr.h" "$INCLUDE_DIR/"

# Clean up build directory
echo "=== Cleaning up ==="
rm -rf "$BUILD_DIR"

echo "=== Done! ==="
echo "Device libs:    $OUTPUT_DIR/device/"
echo "Simulator libs: $OUTPUT_DIR/simulator/"
echo "Headers:        $INCLUDE_DIR/"
