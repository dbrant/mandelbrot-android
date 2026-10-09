#!/bin/bash
set -e

# Adjust to your local NDK path
NDK=$HOME/android/ndk/27.0.12077973
API=21
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/darwin-x86_64

# Use the NDK's LLVM binutils. Without these, configure falls back to the
# host (macOS) ar/ranlib, which silently drop ELF objects and produce empty
# .a files.
AR=$TOOLCHAIN/bin/llvm-ar
RANLIB=$TOOLCHAIN/bin/llvm-ranlib
NM=$TOOLCHAIN/bin/llvm-nm
STRIP=$TOOLCHAIN/bin/llvm-strip

# nproc doesn't exist on macOS
JOBS=$(sysctl -n hw.ncpu 2>/dev/null || nproc)

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
    shift 2
    GMP_EXTRA_ARGS=("$@")   # any remaining args go to GMP's configure
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
        --with-pic \
        "${GMP_EXTRA_ARGS[@]}" \
        CC=$CC AR=$AR RANLIB=$RANLIB NM=$NM STRIP=$STRIP
    make -j$JOBS
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
        --enable-thread-safe \
        --with-pic \
        CC=$CC AR=$AR RANLIB=$RANLIB NM=$NM STRIP=$STRIP
    make -j$JOBS
    make install
}

# Build for each ABI
build_for_abi arm64-v8a   aarch64-linux-android
# GMP's 32-bit ARM assembly references the global __gmp_binvert_limb_table
# with a PC-relative relocation (R_ARM_REL32), which lld rejects when linking
# into a shared library ("recompile with -fPIC"), so use the generic C code.
build_for_abi armeabi-v7a armv7a-linux-androideabi --disable-assembly
build_for_abi x86_64      x86_64-linux-android
