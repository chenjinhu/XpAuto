package com.vixmobi.auto.devtools;

import androidx.annotation.Keep;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.callback.IJavetDirectCallable;
import com.caoccao.javet.interop.callback.JavetCallbackContext;
import com.caoccao.javet.interop.callback.JavetCallbackType;
import com.caoccao.javet.values.reference.V8ValueFunction;

/** 把暂停请求交给 V8 的执行线程；普通脚本运行时不增加轮询或改写源码。 */
@Keep
final class InspectorInterrupt {
    static { System.loadLibrary("xpauto-inspector"); }
    private final Runnable callback;
    private long handle;
    private boolean closing;

    InspectorInterrupt(V8Runtime runtime, Runnable callback) throws JavetException {
        this.callback = callback;
        // 必须在 V8 回调内获取当前 Isolate，不能根据 Javet 私有对象布局猜测地址。
        JavetCallbackContext capture = new JavetCallbackContext("attachInspectorInterrupt",
                JavetCallbackType.DirectCallNoThisAndNoResult,
                (IJavetDirectCallable.NoThisAndNoResult<RuntimeException>) values -> handle = attach());
        try (V8ValueFunction function = runtime.createV8ValueFunction(capture)) {
            function.callVoid(null);
        }
        if (handle == 0) throw new IllegalStateException("脚本中断接口初始化失败");
    }

    synchronized void requestPause() {
        if (!closing && handle != 0) request(handle);
    }

    synchronized void beginClose() { closing = true; }

    /** 只能在脚本执行线程完成 runtime.close() 后调用。 */
    synchronized void releaseAfterRuntimeClose() {
        if (handle != 0) { release(handle); handle = 0; }
    }

    // 由 JNI 在脚本线程调用。这里只进入调试暂停，不运行用户 JS，也不获取本对象的同步锁。
    @SuppressWarnings("unused")
    private void onInterrupt() { callback.run(); }

    private native long attach();
    private static native void request(long handle);
    private static native void release(long handle);
}
