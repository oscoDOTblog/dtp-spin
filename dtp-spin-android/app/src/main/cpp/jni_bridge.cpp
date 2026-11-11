// JNI bridge for com.dtp.spin.audio.NativeLib.
//
// Threading rules (see plan: nothing real-time-hostile here runs in the
// audio callback):
//  - Control calls (play/pause/seek/params): build a Command and tryPush it.
//    Never blocks; drops only if the 256-slot queue is full (logged, retried
//    by the next UI poll — effectively impossible in practice).
//  - feedPcm: called on decoder worker threads with a direct ByteBuffer.
//    Zero-copy read via GetDirectBufferAddress; guarded by generation.
//  - Getters: plain atomic loads for the ~25 Hz Kotlin state poller.

#include <jni.h>

#include <android/log.h>
#include <cstring>

#include "AudioEngine.h"

#define LOG_TAG "djengine"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

dj::AudioEngine* handleToEngine(jlong handle) {
    return reinterpret_cast<dj::AudioEngine*>(handle);
}

bool pushCommand(jlong handle, const dj::Command& cmd) {
    auto* engine = handleToEngine(handle);
    if (engine == nullptr) return false;
    if (!engine->commands().tryPush(cmd)) {
        LOGW("command queue full, dropping command");
        return false;
    }
    return true;
}

dj::Command deckCmd(dj::CommandType type, jint deck) {
    dj::Command c;
    c.type = type;
    c.deck = static_cast<uint8_t>(deck);
    return c;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeCreate(JNIEnv* /*env*/, jobject /*thiz*/) {
    return reinterpret_cast<jlong>(new dj::AudioEngine());
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    delete handleToEngine(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeStart(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr && engine->start();
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (auto* engine = handleToEngine(handle)) engine->stop();
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativePump(JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (auto* engine = handleToEngine(handle)) engine->pump();
}

JNIEXPORT jdouble JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeSampleRate(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr ? engine->sampleRate() : 48000.0;
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckLoad(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck,
        jfloat sourceSampleRate, jlong totalFrames, jint generation) {
    dj::Command c = deckCmd(dj::CommandType::Load, deck);
    c.floats[0] = sourceSampleRate;
    c.ints[0] = totalFrames;
    c.ints[1] = generation;
    pushCommand(handle, c);
}

JNIEXPORT jint JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckFeedPcm(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jint deck,
        jobject byteBuffer, jint frames, jint generation, jlong absStartFrame) {
    auto* engine = handleToEngine(handle);
    if (engine == nullptr || byteBuffer == nullptr || frames <= 0) return 0;
    float* data = static_cast<float*>(env->GetDirectBufferAddress(byteBuffer));
    if (data == nullptr) return 0;
    return engine->deck(deck).feedPcm(
        data, frames, generation, static_cast<uint64_t>(absStartFrame));
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckPlay(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    pushCommand(handle, deckCmd(dj::CommandType::Play, deck));
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckPause(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    pushCommand(handle, deckCmd(dj::CommandType::Pause, deck));
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckCueDown(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    pushCommand(handle, deckCmd(dj::CommandType::CueDown, deck));
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckCueUp(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    pushCommand(handle, deckCmd(dj::CommandType::CueUp, deck));
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSetCue(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck, jlong cueFrame) {
    dj::Command c = deckCmd(dj::CommandType::SetCue, deck);
    c.ints[0] = cueFrame;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSeek(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck,
        jlong targetFrame, jint generation) {
    dj::Command c = deckCmd(dj::CommandType::Seek, deck);
    c.ints[0] = targetFrame;
    c.ints[1] = generation;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSetRate(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck, jfloat rate) {
    dj::Command c = deckCmd(dj::CommandType::SetRate, deck);
    c.floats[0] = rate;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSetVolume(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck, jfloat volume) {
    dj::Command c = deckCmd(dj::CommandType::SetVolume, deck);
    c.floats[0] = volume;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSetGain(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck, jfloat gain) {
    dj::Command c = deckCmd(dj::CommandType::SetGain, deck);
    c.floats[0] = gain;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckSetEq(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck,
        jfloat lowDb, jfloat midDb, jfloat highDb) {
    dj::Command c = deckCmd(dj::CommandType::SetEq, deck);
    c.floats[0] = lowDb;
    c.floats[1] = midDb;
    c.floats[2] = highDb;
    pushCommand(handle, c);
}

JNIEXPORT void JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeSetCrossfade(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jfloat crossfade) {
    dj::Command c;
    c.type = dj::CommandType::SetCrossfade;
    c.floats[0] = crossfade;
    pushCommand(handle, c);
}

// --- state snapshot getters (Kotlin poller thread) ---

JNIEXPORT jdouble JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckGetPositionFrames(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr ? engine->deck(deck).positionFrames() : 0.0;
}

JNIEXPORT jboolean JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckIsPlaying(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr && engine->deck(deck).isPlaying();
}

JNIEXPORT jboolean JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckIsStarved(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr && engine->deck(deck).isStarved();
}

JNIEXPORT jboolean JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckHasEnded(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr && engine->deck(deck).hasEnded();
}

JNIEXPORT jfloat JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckGetLevel(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr ? engine->deck(deck).level() : 0.f;
}

JNIEXPORT jint JNICALL
Java_com_dtp_spin_audio_NativeLib_nativeDeckGetGeneration(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle, jint deck) {
    auto* engine = handleToEngine(handle);
    return engine != nullptr ? engine->deck(deck).generation() : 0;
}

} // extern "C"
