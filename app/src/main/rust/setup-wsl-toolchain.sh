#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:?repository root is required}"
script_root="$repo_root/app/src/main/rust"
tool_root="$repo_root/.local-tools/wsl"
ndk_archive="$tool_root/android-ndk-r28c-linux.zip"
ndk_root="$tool_root/ndk-links"
zig_archive="$tool_root/zig-x86_64-linux-0.16.0.tar.xz"
zig_root="$tool_root/zig-x86_64-linux-0.16.0"

mkdir -p "$tool_root"

if [[ ! -x "$ndk_root/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang-19" ]]; then
  curl --fail --location --retry 3 \
    --output "$ndk_archive" \
    https://dl.google.com/android/repository/android-ndk-r28c-linux.zip
  echo "a7b54a5de87fecd125a17d54f73c446199e72a64  $ndk_archive" | sha1sum --check -
  rm -rf "$ndk_root/android-ndk-r28c"
  mkdir -p "$ndk_root"
  python3 "$script_root/tooling/extract_zip_preserving_links.py" "$ndk_archive" "$ndk_root"
fi

if [[ ! -x "$zig_root/zig" ]]; then
  curl --fail --location --retry 3 \
    --output "$zig_archive" \
    https://ziglang.org/download/0.16.0/zig-x86_64-linux-0.16.0.tar.xz
  echo "70e49664a74374b48b51e6f3fdfbf437f6395d42509050588bd49abe52ba3d00  $zig_archive" | sha256sum --check -
  tar -xJf "$zig_archive" -C "$tool_root"
fi

install -m 755 "$script_root/tooling/host-linker.sh" "$tool_root/host-linker.sh"

export RUSTUP_HOME="$tool_root/rustup"
export CARGO_HOME="$tool_root/cargo"
if [[ ! -x "$CARGO_HOME/bin/rustup" ]]; then
  curl --proto '=https' --tlsv1.2 --silent --show-error --fail \
    https://sh.rustup.rs \
    --output "$tool_root/rustup-init.sh"
  sh "$tool_root/rustup-init.sh" -y --no-modify-path --profile minimal --default-toolchain 1.98.0
fi

"$CARGO_HOME/bin/rustup" toolchain install 1.98.0 --profile minimal
"$CARGO_HOME/bin/rustup" target add --toolchain 1.98.0 \
  aarch64-linux-android \
  x86_64-linux-android
"$CARGO_HOME/bin/rustc" +1.98.0 --version
"$ndk_root/android-ndk-r28c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --version | head -n 1
"$zig_root/zig" version
