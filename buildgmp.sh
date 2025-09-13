#!/bin/bash
set -e

# Adjust to your local NDK path
NDK=$HOME/android/ndk/27.0.12077973
API=21
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64

# Root prebuilt output (relative to this script)
PREBUILT_DIR=$(pwd)/prebuilt
mkdir -p "$PREBUILT_DIR"

# Source dirs (adjust to your unpacked tarballs)
GMP_SRC=$(pwd)/gmp-6.3.0
MPFR_SRC=$(pwd)/mpfr-4.2.2

# Build function
build_for_abi() {
    ABI=$1
    TARGET_HOST=$2
    CC=$TOOLCHAIN/bin/${TARGET_HOST}${API}-clang
    CXX=$TOOLCHAIN/bin/${TARGET_HOST}${API}-clang++

    OUTDIR=$PREBUILT_DIR/$ABI
    mkdir -p "$OUTDIR"

    echo "===== Building for $ABI ====="

    # GMP
    cd "$GMP_SRC"
    make distclean || true
    ./configure \
        --host=$TARGET_HOST \
        --prefix=$OUTDIR \
        --enable-static \
        --disable-shared \
        --disable-assembly \
        CC=$CC \
        CFLAGS="-fPIC"
    make -j$(nproc)
    make install

    # MPFR
    cd "$MPFR_SRC"
    make distclean || true
    ./configure \
        --host=$TARGET_HOST \
        --prefix=$OUTDIR \
        --with-gmp=$OUTDIR \
        --enable-static \
        --disable-shared \
        CC=$CC \
        CFLAGS="-fPIC"
    make -j$(nproc)
    make install
}

# Build for each ABI
build_for_abi arm64-v8a   aarch64-linux-android
build_for_abi armeabi-v7a armv7a-linux-androideabi
build_for_abi x86_64      x86_64-linux-android
