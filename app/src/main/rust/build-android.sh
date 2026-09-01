#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:?repository root is required}"
android_abi="${2:?Android ABI is required}"
tool_root="$repo_root/.local-tools/wsl"
ndk_bin="$tool_root/ndk-links/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin"

case "$android_abi" in
  x86_64)
    rust_target="x86_64-linux-android"
    linker="$ndk_bin/x86_64-linux-android24-clang"
    ;;
  arm64-v8a)
    rust_target="aarch64-linux-android"
    linker="$ndk_bin/aarch64-linux-android24-clang"
    ;;
  *)
    echo "Unsupported Android ABI: $android_abi" >&2
    exit 2
    ;;
esac

export RUSTUP_HOME="$tool_root/rustup"
export CARGO_HOME="$tool_root/cargo"
export CARGO_TARGET_DIR="$repo_root/app/build/rust/cargo/$android_abi"
export CC="$tool_root/host-linker.sh"
export CARGO_TARGET_X86_64_UNKNOWN_LINUX_GNU_LINKER="$tool_root/host-linker.sh"
target_cc="CC_${rust_target//-/_}"
target_ar="AR_${rust_target//-/_}"
export "$target_cc=$linker"
export "$target_ar=$ndk_bin/llvm-ar"
target_env="${rust_target^^}"
target_env="${target_env//-/_}"
export "CARGO_TARGET_${target_env}_LINKER=$linker"

"$CARGO_HOME/bin/cargo" +1.98.0 build \
  --manifest-path "$repo_root/app/src/main/rust/Cargo.toml" \
  --locked \
  --release \
  --target "$rust_target"

output_dir="$repo_root/app/build/rust/$android_abi/release"
mkdir -p "$output_dir"
cp "$CARGO_TARGET_DIR/$rust_target/release/libbubbel_libdf.so" "$output_dir/libbubbel_libdf.so"
