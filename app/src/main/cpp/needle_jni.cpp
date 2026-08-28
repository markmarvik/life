#include <jni.h>
#include "needle.h"

extern "C" JNIEXPORT jint JNICALL
Java_com_life_app_needle_NeedleJni_nativeInit(
    JNIEnv* env,
    jclass,
    jstring systemPrompt,
    jstring toolsJson,
    jstring toolIndexPath
) {
    const char* system = systemPrompt ? env->GetStringUTFChars(systemPrompt, nullptr) : "";
    const char* tools = toolsJson ? env->GetStringUTFChars(toolsJson, nullptr) : "";
    const char* index = toolIndexPath
        ? env->GetStringUTFChars(toolIndexPath, nullptr)
        : nullptr;
    int rc = needle_init(system, tools, index);
    if (systemPrompt) env->ReleaseStringUTFChars(systemPrompt, system);
    if (toolsJson) env->ReleaseStringUTFChars(toolsJson, tools);
    if (toolIndexPath && index) env->ReleaseStringUTFChars(toolIndexPath, index);
    return rc;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_life_app_needle_NeedleJni_nativeComplete(
    JNIEnv* env,
    jclass,
    jstring input,
    jint maxNewTokens
) {
    if (input == nullptr) {
        return env->NewStringUTF("{\"type\":\"call\",\"success\":false,\"function_calls\":[]}");
    }
    const char* text = env->GetStringUTFChars(input, nullptr);
    char out[65536];
    out[0] = '\0';
    int rc = needle_complete(text, maxNewTokens, out, (int) sizeof(out));
    env->ReleaseStringUTFChars(input, text);
    if (rc < 0) {
        return nullptr;
    }
    out[sizeof(out) - 1] = '\0';
    return env->NewStringUTF(out);
}

extern "C" JNIEXPORT void JNICALL
Java_com_life_app_needle_NeedleJni_nativeReset(JNIEnv*, jclass) {
    needle_reset();
}
