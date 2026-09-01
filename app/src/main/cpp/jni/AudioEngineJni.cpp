#include "audio/AudioEngine.h"

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <memory>

namespace { std::unique_ptr<AudioEngine> engine; }
extern "C" JNIEXPORT void JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativeCreate(JNIEnv* env, jobject, jobject assets) { engine = std::make_unique<AudioEngine>(AAssetManager_fromJava(env, assets)); }
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativeStart(JNIEnv*, jobject, jint mode, jint input, jfloat gain) { return engine && engine->start(mode, input, gain) ? JNI_TRUE : JNI_FALSE; }
extern "C" JNIEXPORT void JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativeStop(JNIEnv*, jobject) { if (engine) engine->stop(); }
extern "C" JNIEXPORT void JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativeSetFilterMode(JNIEnv*, jobject, jint mode) { if (engine) engine->setFilterMode(mode); }
extern "C" JNIEXPORT jstring JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativePollEvent(JNIEnv* env, jobject) { if (!engine) return nullptr; const auto event = engine->pollEvent(); if (!event) return nullptr; const char* kind[] = {"Starting", "Running", "Recovering", "Failed", "Stopped"}; const std::string text = std::string(kind[static_cast<int>(event->kind)]) + ":" + (event->text.empty() ? std::to_string(event->value) : event->text); return env->NewStringUTF(text.c_str()); }
extern "C" JNIEXPORT void JNICALL Java_com_example_bubbel_audio_NativeAudioEngine_nativeDestroy(JNIEnv*, jobject) { engine.reset(); }
