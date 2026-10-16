// 公开 ABI 来自 V8 15.2.124.17，与 Javet 5.0.11 一致；升级时同步核对头文件和真机线程测试。
#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include "v8-public/v8-locker.h"

namespace {
struct Api {
    void* library = dlopen("libjavet-v8-android.v.5.0.11.so", RTLD_NOW | RTLD_LOCAL);
    using Current = v8::Isolate* (*)();
    using IsolateCall = void (*)(v8::Isolate*);
    using Init = void (*)(void*, v8::Isolate*);
    using Destroy = void (*)(void*);
    Current current = reinterpret_cast<Current>(dlsym(library, "_ZN2v87Isolate13TryGetCurrentEv"));
    IsolateCall exit = reinterpret_cast<IsolateCall>(dlsym(library, "_ZN2v87Isolate4ExitEv"));
    IsolateCall enter = reinterpret_cast<IsolateCall>(dlsym(library, "_ZN2v87Isolate5EnterEv"));
    Init initialize = reinterpret_cast<Init>(dlsym(library, "_ZN2v88Unlocker10InitializeEPNS_7IsolateE"));
    Destroy destroy = reinterpret_cast<Destroy>(dlsym(library, "_ZN2v88UnlockerD1Ev"));
};
}
extern "C" JNIEXPORT void JNICALL
Java_com_vixmobi_auto_runtime_ScriptBlocking_invoke(JNIEnv* env, jclass, jobject action) {
    static Api api;
    if (!api.library || !api.current || !api.exit || !api.enter || !api.initialize || !api.destroy) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "当前 V8 不支持线程调度");
        return;
    }
    jclass type = env->GetObjectClass(action);
    jmethodID run = env->GetMethodID(type, "run", "()V");
    env->DeleteLocalRef(type);
    auto* isolate = api.current();
    if (!isolate) { env->CallVoidMethod(action, run); return; }
    // 必须先退出当前 isolate，再保存线程栈并解锁；阻塞段完全不能调用 JS 或触碰 V8 句柄。
    alignas(v8::Unlocker) unsigned char storage[sizeof(v8::Unlocker)];
    api.exit(isolate);
    api.initialize(storage, isolate);
    env->CallVoidMethod(action, run);
    // 即使 Java 抛出了异常，也先恢复 V8 栈和锁，再交给 Javet 传播异常。
    api.destroy(storage);
    api.enter(isolate);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vixmobi_auto_runtime_ScriptBlocking_capture(JNIEnv*, jclass) {
    static Api api;
    return reinterpret_cast<jlong>(api.current ? api.current() : nullptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vixmobi_auto_runtime_ScriptBlocking_locked(JNIEnv* env, jclass, jlong handle, jobject action) {
    static Api api;
    auto initialize = reinterpret_cast<Api::Init>(dlsym(api.library, "_ZN2v86Locker10InitializeEPNS_7IsolateE"));
    auto destroy = reinterpret_cast<Api::Destroy>(dlsym(api.library, "_ZN2v86LockerD1Ev"));
    if (!handle || !initialize || !destroy) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "无法进入脚本线程运行锁"); return;
    }
    alignas(v8::Locker) unsigned char storage[sizeof(v8::Locker)];
    // 先拿 V8 锁，再执行 Javet 的句柄登记/释放，保持锁顺序，避免 Java referenceLock 与 V8 互等。
    initialize(storage, reinterpret_cast<v8::Isolate*>(handle));
    jclass type = env->GetObjectClass(action);
    jmethodID run = env->GetMethodID(type, "run", "()V");
    env->DeleteLocalRef(type);
    env->CallVoidMethod(action, run);
    destroy(storage);
}
