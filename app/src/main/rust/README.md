# DeepFilterNet Android toolchain

The Android Rust wrapper uses WSL 2 on Windows, Rust 1.98.0, Google NDK r28c/API 24 and Zig 0.16.0. Provision the project-local ignored tool cache once:

```powershell
.\gradlew.bat setupLibDfToolchain
```

After setup, normal Gradle CMake/assemble tasks build both `arm64-v8a` and `x86_64` libraries automatically. The setup script verifies the NDK and Zig archives against their pinned hashes. Generated toolchains and libraries stay under ignored `.local-tools/` and `app/build/` directories.
