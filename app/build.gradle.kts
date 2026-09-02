import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// AAPT transparently inflates .gz assets and strips their extension. libDF needs
// the original gzip archive bytes, so package the pinned source as an opaque file.
val prepareVoiceFilterAssets = tasks.register<Sync>("prepareVoiceFilterAssets") {
    from("src/main/assets") {
        rename("DeepFilterNet3_onnx.tar.gz", "DeepFilterNet3_onnx.bin")
    }
    into(layout.buildDirectory.dir("generated/voiceFilterAssets"))
}

android {
    namespace = "com.example.bubbel"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.bubbel"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        prefab = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main").assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/voiceFilterAssets")))
        getByName("main").jniLibs.directories.add(
            layout.buildDirectory.dir("generated/jniLibs").get().asFile.absolutePath,
        )
    }
}

val repositoryRoot = rootProject.projectDir.absolutePath.replace('\\', '/')
val repositoryRootForBash = if (System.getProperty("os.name").startsWith("Windows")) {
    val drive = repositoryRoot.substring(0, 1).lowercase()
    "/mnt/$drive/${repositoryRoot.substring(3)}"
} else {
    repositoryRoot
}

tasks.register<Exec>("setupLibDfToolchain") {
    val script = "$repositoryRootForBash/app/src/main/rust/setup-wsl-toolchain.sh"
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("wsl.exe", "bash", script, repositoryRootForBash)
    } else {
        commandLine("bash", script, repositoryRootForBash)
    }
}

fun registerLibDfBuild(name: String, abi: String) = tasks.register<Exec>(name) {
    val script = "$repositoryRootForBash/app/src/main/rust/build-android.sh"
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("wsl.exe", "bash", script, repositoryRootForBash, abi)
    } else {
        commandLine("bash", script, repositoryRootForBash, abi)
    }
    inputs.files(fileTree("src/main/rust") { exclude("target/**") })
    outputs.file(layout.buildDirectory.file("generated/jniLibs/$abi/libbubbel_libdf.so"))
}

val buildLibDfArm64 = registerLibDfBuild("buildLibDfArm64", "arm64-v8a")
val buildLibDfX86_64 = registerLibDfBuild("buildLibDfX86_64", "x86_64")

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareVoiceFilterAssets)
    }
    if (name.startsWith("configureCMake") ||
        (name.startsWith("merge") && name.endsWith("JniLibFolders"))) {
        dependsOn(buildLibDfArm64, buildLibDfX86_64)
    }
}

tasks.register<Exec>("connectedVoiceFilterTest") {
    dependsOn("externalNativeBuildDebug")
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    commandLine(
        "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        file("src/test/scripts/run-voice-filter-tests.ps1").absolutePath,
        repositoryRoot,
    )
}

tasks.register("verifyVoiceFilterPackaging") {
    dependsOn("assembleDebug")
    val debugApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    inputs.file(debugApk)
    doLast {
        ZipFile(debugApk.get().asFile).use { apk ->
            val modelEntry = checkNotNull(apk.getEntry("assets/models/deepfilternet3/DeepFilterNet3_onnx.bin")) {
                "Debug APK is missing the opaque model archive"
            }
            val expectedBytes = file("src/main/assets/models/deepfilternet3/DeepFilterNet3_onnx.tar.gz").readBytes()
            check(apk.getInputStream(modelEntry).use { it.readBytes() }.contentEquals(expectedBytes)) {
                "Packaged model differs from the pinned gzip archive"
            }
            listOf(
                "lib/arm64-v8a/libbubbel_libdf.so",
                "lib/x86_64/libbubbel_libdf.so",
            ).forEach { requiredEntry ->
                check(apk.getEntry(requiredEntry) != null) {
                    "Debug APK is missing $requiredEntry"
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.oboe)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
