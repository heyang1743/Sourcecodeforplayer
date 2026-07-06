#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPENDENCY_DIR="$ROOT_DIR/dependency"

NDK="${NDK:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
if [[ -z "$NDK" ]]; then
    echo "NDK is not set. Export NDK or ANDROID_NDK_HOME first." >&2
    exit 1
fi

HOST_TAG="${HOST_TAG:-linux-x86_64}"
TOOLCHAIN="${TOOLCHAIN:-$NDK/toolchains/llvm/prebuilt/$HOST_TAG}"
if [[ ! -d "$TOOLCHAIN" ]]; then
    echo "Android LLVM toolchain not found: $TOOLCHAIN" >&2
    exit 1
fi

API="${API:-24}"
JOBS="${JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)}"
BUILD_ABIS="${BUILD_ABIS:-arm64-v8a}"
BUILD_PROGRAMS="${BUILD_PROGRAMS:-0}"
ENABLE_ARC_VIDEO="${ENABLE_ARC_VIDEO:-0}"
MODEL_FILE="$SCRIPT_DIR/model.bin"

if [[ ! -f "$MODEL_FILE" ]]; then
    echo "Missing AV3A binaural render model file: $MODEL_FILE" >&2
    exit 1
fi

program_flags=(
    --disable-ffplay
)
if [[ "$BUILD_PROGRAMS" == "1" ]]; then
    program_flags+=(--enable-ffmpeg --enable-ffprobe)
else
    program_flags+=(--disable-ffmpeg --disable-ffprobe)
fi

arc_video_flags=()
if [[ "$ENABLE_ARC_VIDEO" == "1" ]]; then
    arc_video_flags+=(--enable-libarcdavs2 --enable-libarcdavs3)
else
    arc_video_flags+=(--disable-libarcdavs2 --disable-libarcdavs3)
fi

normalize_abis() {
    local raw="$1"
    if [[ "$raw" == "all" ]]; then
        echo "arm64-v8a armeabi-v7a"
        return
    fi
    echo "$raw" | tr ',' ' '
}

configure_abi() {
    local abi="$1"

    case "$abi" in
        arm64-v8a|armv8-a)
            ARCH=arm64
            CPU=armv8-a
            DEP_CPU=armv8-a
            ANDROID_TRIPLE=aarch64-linux-android
            CROSS=aarch64-linux-android
            CC="$TOOLCHAIN/bin/${ANDROID_TRIPLE}${API}-clang"
            CXX="$TOOLCHAIN/bin/${ANDROID_TRIPLE}${API}-clang++"
            OPTIMIZE_CFLAGS="-march=armv8-a"
            ;;
        armeabi-v7a|armv7-a)
            ARCH=arm
            CPU=armv7-a
            DEP_CPU=armv7-a
            ANDROID_TRIPLE=armv7a-linux-androideabi
            CROSS=arm-linux-androideabi
            CC="$TOOLCHAIN/bin/${ANDROID_TRIPLE}${API}-clang"
            CXX="$TOOLCHAIN/bin/${ANDROID_TRIPLE}${API}-clang++"
            OPTIMIZE_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfp -marm"
            ;;
        *)
            echo "Unsupported ABI: $abi" >&2
            exit 1
            ;;
    esac

    SYSROOT="$TOOLCHAIN/sysroot"
    CROSS_PREFIX="$TOOLCHAIN/bin/$CROSS"
    PREFIX="$SCRIPT_DIR/android/$CPU"
    DEP_LIB_DIR="$DEPENDENCY_DIR/android/$DEP_CPU"
    OPENSSL_DIR="$DEP_LIB_DIR/openssl"

    if [[ ! -f "$DEP_LIB_DIR/libAVS3AudioDec.so" ||
          ! -f "$DEP_LIB_DIR/libav3a_binaural_render.so" ]]; then
        echo "Missing AV3A runtime dependency libraries under $DEP_LIB_DIR" >&2
        exit 1
    fi
    if [[ ! -f "$OPENSSL_DIR/libssl.so" || ! -f "$OPENSSL_DIR/libcrypto.so" ]]; then
        echo "Missing OpenSSL runtime dependency libraries under $OPENSSL_DIR" >&2
        exit 1
    fi

    echo "==> Configuring FFmpeg for $CPU"
    make distclean >/dev/null 2>&1 || true

    ./configure \
        --prefix="$PREFIX" \
        --disable-doc \
        --disable-debug \
        --disable-avdevice \
        --disable-libx264 \
        --disable-static \
        --enable-shared \
        --enable-nonfree \
        --enable-gpl \
        --enable-version3 \
        --enable-pthreads \
        --enable-neon \
        --enable-hwaccels \
        --enable-jni \
        --enable-mediacodec \
        --enable-libarcdav3a \
        "${arc_video_flags[@]}" \
        --enable-openssl \
        --enable-protocols \
        --enable-protocol=https \
        --enable-cross-compile \
        --cross-prefix="$CROSS_PREFIX-" \
        --target-os=android \
        --arch="$ARCH" \
        --cpu="$CPU" \
        --cc="$CC" \
        --cxx="$CXX" \
        --sysroot="$SYSROOT" \
        --extra-cflags="-O3 -fPIC -I$DEPENDENCY_DIR/include $OPTIMIZE_CFLAGS" \
        --extra-ldflags="-L$OPENSSL_DIR" \
        --extra-libs="-ldl -lssl -lcrypto" \
        "${program_flags[@]}"

    echo "==> Building FFmpeg for $CPU with $JOBS jobs"
    make -j"$JOBS"
    make install

    echo "==> Copying AV3A runtime dependencies for $CPU"
    mkdir -p "$PREFIX/runtime"
    cp -f "$DEP_LIB_DIR/libAVS3AudioDec.so" "$PREFIX/runtime/"
    cp -f "$DEP_LIB_DIR/libav3a_binaural_render.so" "$PREFIX/runtime/"
    cp -f "$MODEL_FILE" "$PREFIX/runtime/"
    cp -f "$OPENSSL_DIR"/libssl.so "$OPENSSL_DIR"/libcrypto.so "$PREFIX/runtime/"
    if [[ "$ENABLE_ARC_VIDEO" == "1" ]]; then
        find "$DEP_LIB_DIR" -maxdepth 1 -name '*.so' -exec cp -f {} "$PREFIX/runtime/" \;
    fi

    {
        echo "abi=$CPU"
        echo "api=$API"
        echo "build_programs=$BUILD_PROGRAMS"
        echo "enable_arc_video=$ENABLE_ARC_VIDEO"
        echo "runtime_files:"
        find "$PREFIX/runtime" -maxdepth 1 -type f -printf "  %f\n" | sort
    } > "$PREFIX/av3a-build-manifest.txt"

    echo "==> Completed FFmpeg for $CPU at $PREFIX"
}

cd "$SCRIPT_DIR"
for abi in $(normalize_abis "$BUILD_ABIS"); do
    configure_abi "$abi"
done
