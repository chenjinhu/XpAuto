// Java 包名变化时同步修改下方 JNI 导出前缀，并执行真机暂停回归。
#include <jni.h>
#include <dlfcn.h>
#include <new>

namespace {
// 只把 Isolate 当作不透明指针，不读取 Javet 或 V8 对象的内存布局。
using Interrupt = void (*)(void*, void*);
using CurrentIsolate = void* (*)();
using RequestInterrupt = void (*)(void*, Interrupt, void*);

struct Connection {
    void* library;
    void* isolate;
    RequestInterrupt request;
    JavaVM* vm;
    jobject owner;
    jmethodID callback;
};

void fail(JNIEnv* env, const char* message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(type, message);
    env->DeleteLocalRef(type);
}

void onInterrupt(void*, void* data) {
    auto* connection = static_cast<Connection*>(data);
    JNIEnv* env = nullptr;
    // 执行线程由 Java 创建并一直附着在 JVM 上，无需额外创建或附着线程。
    if (connection->vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        env->CallVoidMethod(connection->owner, connection->callback);
    }
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vixmobi_auto_devtools_InspectorInterrupt_attach(JNIEnv* env, jobject owner) {
    // 对应 Gradle 固定的 Javet 版本。升级依赖时必须核对两个公开 V8 方法的导出符号。
    void* library = dlopen("libjavet-v8-android.v.5.0.11.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) { fail(env, "无法加载 Javet 中断接口"); return 0; }
    auto current = reinterpret_cast<CurrentIsolate>(dlsym(library, "_ZN2v87Isolate13TryGetCurrentEv"));
    auto request = reinterpret_cast<RequestInterrupt>(
            dlsym(library, "_ZN2v87Isolate16RequestInterruptEPFvPS0_PvES2_"));
    void* isolate = current ? current() : nullptr;
    if (!isolate || !request) {
        dlclose(library);
        fail(env, "当前 Javet 版本不支持运行中暂停");
        return 0;
    }
    jclass type = env->GetObjectClass(owner);
    jmethodID callback = env->GetMethodID(type, "onInterrupt", "()V");
    env->DeleteLocalRef(type);
    if (!callback) { dlclose(library); return 0; }
    jobject reference = env->NewGlobalRef(owner);
    if (!reference) { dlclose(library); return 0; }
    JavaVM* vm = nullptr;
    env->GetJavaVM(&vm);
    auto* connection = new (std::nothrow) Connection{library, isolate, request, vm, reference, callback};
    if (!connection) {
        env->DeleteGlobalRef(reference);
        dlclose(library);
        fail(env, "无法创建脚本中断连接");
    }
    return reinterpret_cast<jlong>(connection);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vixmobi_auto_devtools_InspectorInterrupt_request(JNIEnv*, jclass, jlong handle) {
    auto* connection = reinterpret_cast<Connection*>(handle);
    // RequestInterrupt 是 V8 提供的跨线程入口，不等待正在执行脚本的隔离锁。
    connection->request(connection->isolate, onInterrupt, connection);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vixmobi_auto_devtools_InspectorInterrupt_release(JNIEnv* env, jclass, jlong handle) {
    auto* connection = reinterpret_cast<Connection*>(handle);
    // Java 侧先关闭 V8，再释放这里的引用，保证排队的回调不可能访问已释放内存。
    env->DeleteGlobalRef(connection->owner);
    dlclose(connection->library);
    delete connection;
}
