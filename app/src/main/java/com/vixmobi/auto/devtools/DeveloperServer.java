package com.vixmobi.auto.devtools;

import android.content.Context;
import android.util.Log;
import android.util.AtomicFile;

import com.caoccao.javet.exceptions.BaseJavetScriptingException;
import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.exceptions.JavetScriptingError;
import com.caoccao.javet.interop.IV8InspectorListener;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Inspector;
import com.caoccao.javet.interop.V8Runtime;
import com.caoccao.javet.interop.converters.JavetProxyConverter;
import com.caoccao.javet.values.reference.V8ValueGlobalObject;
import com.vixmobi.auto.Constant;
import com.vixmobi.auto.node.TaskBase;
import com.vixmobi.auto.node.Utils;
import com.vixmobi.auto.network.HttpApi;
import com.vixmobi.auto.logging.ScriptLogApi;
import com.vixmobi.auto.utils.FileUtils;
import com.vixmobi.auto.utils.ShellUtils;
import com.vixmobi.auto.utils.ShellApi;
import com.vixmobi.auto.floaty.FloatyApi;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 运行环境中的 USB 调试服务；每个调试连接拥有独立的 V8 运行环境。 */
public final class DeveloperServer extends WebSocketServer {
    private static final String TAG = "XpAutoDevTools";
    private final Context context;
    private final Consumer<String> statusListener;
    private final Consumer<String> logListener;
    private final AtomicFile cachedScript;
    private volatile String lastSource;
    private volatile String lastResourceName = "";
    private final Set<WebSocket> controllers = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Session> active = new AtomicReference<>();
    private final AtomicBoolean capturingUi = new AtomicBoolean();
    private final ExecutorService uiCapture = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "XpAuto-ui-capture"));
    private volatile JSONObject state = object("state", "starting", "detail", "正在启动开发服务");

    public DeveloperServer(Context context, Consumer<String> statusListener, Consumer<String> logListener) {
        super(new InetSocketAddress("127.0.0.1", 9229));
        this.context = context.getApplicationContext();
        this.statusListener = statusListener;
        this.logListener = logListener;
        cachedScript = new AtomicFile(new File(context.getFilesDir(), "xpauto-entry.json"));
        try {
            JSONObject cached = new JSONObject(new String(cachedScript.readFully(), StandardCharsets.UTF_8));
            lastSource = cached.getString("source");
            lastResourceName = cached.getString("resourceName");
        } catch (Exception ignored) { /* 尚未同步过脚本，保持空缓存。 */ }
        setReuseAddr(true);
        setConnectionLostTimeout(15);
    }

    static JSONObject object(Object... pairs) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        } catch (Exception error) {
            throw new IllegalArgumentException(error);
        }
        return result;
    }

    private void setState(String name, String detail, String resourceName) {
        state = object("state", name, "detail", detail, "resourceName", resourceName);
        statusListener.accept(detail);
        for (WebSocket control : controllers) event(control, "XpAuto.state", state);
    }

    private static void event(WebSocket socket, String method, JSONObject params) {
        send(socket, object("method", method, "params", params).toString());
    }

    private static void send(WebSocket socket, String message) {
        if (socket == null || !socket.isOpen()) return;
        // 检查连接后仍可能立刻断开，例如用户在断点处停止调试，因此发送时也要处理断连。
        try { socket.send(message); }
        catch (WebsocketNotConnectedException ignored) { }
    }

    private static void reply(WebSocket socket, JSONObject request, JSONObject result) {
        if (request.has("id")) {
            send(socket, object("id", request.opt("id"), "result", result).toString());
        }
    }

    private static void failure(WebSocket socket, JSONObject request, Exception error) {
        if (request != null && request.has("id")) {
            send(socket, object("id", request.opt("id"), "error",
                    object("code", -32000, "message", error.getMessage())).toString());
        }
    }

    @Override public void onStart() {
        setState("idle", "开发服务已开启，等待 VS Code 连接", "");
    }

    @Override public void onOpen(WebSocket socket, ClientHandshake handshake) {
        if ("/control".equals(handshake.getResourceDescriptor())) {
            controllers.add(socket);
            event(socket, "XpAuto.ready", object("protocolVersion", 1, "app", "XpAuto",
                    "capabilities", new JSONArray().put("captureNodes")));
            event(socket, "XpAuto.state", state);
            return;
        }
        if (!"/debug".equals(handshake.getResourceDescriptor()) && !"/".equals(handshake.getResourceDescriptor())) {
            socket.close(1008, "Unknown endpoint");
            return;
        }
        Session session = new Session(socket);
        if (!active.compareAndSet(null, session)) {
            socket.close(1008, "已有脚本调试会话，请先停止当前会话");
            session.executor.shutdown();
            return;
        }
        session.open();
    }

    @Override public void onMessage(WebSocket socket, String message) {
        if (controllers.contains(socket)) {
            JSONObject request = null;
            try {
                request = new JSONObject(message);
                switch (request.getString("method")) {
                    case "XpAuto.status": reply(socket, request, state); break;
                    case "XpAuto.captureNodes":
                        if (!capturingUi.compareAndSet(false, true)) throw new IllegalStateException("正在抓取节点，请稍后再试");
                        JSONObject captureRequest = request;
                        // 抓取可能等待系统 Binder，单独执行，控制连接仍能立即处理停止命令。
                        uiCapture.execute(() -> {
                            try { reply(socket, captureRequest, new UiNodeCapture().capture(context)); }
                            catch (Exception error) { failure(socket, captureRequest, error); }
                            finally { capturingUi.set(false); }
                        });
                        break;
                    case "XpAuto.stop":
                        Session session = active.get();
                        if (session != null) session.stopExecution();
                        reply(socket, request, object("accepted", true));
                        break;
                    default: throw new IllegalArgumentException("Unsupported control method");
                }
            } catch (Exception error) { failure(socket, request, error); }
        } else {
            Session session = active.get();
            if (session != null && session.socket == socket) session.receive(message);
        }
    }

    @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        controllers.remove(socket);
        Session session = active.get();
        if (session != null && session.socket == socket) {
            session.close();
        }
    }

    @Override public void onError(WebSocket socket, Exception error) {
        Log.e(TAG, "Developer transport error", error);
        if (socket == null) setState("error", "开发服务异常：" + error.getMessage(), "");
    }

    public void shutdown() {
        uiCapture.shutdownNow();
        Session session = active.getAndSet(null);
        if (session != null) session.close();
        try { stop(1000); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    public String lastScript() { return lastResourceName; }
    public String stateName() { return state.optString("state", "idle"); }
    public boolean canRunLastScript() { return lastSource != null && active.get() == null; }

    /** 暂停请求异步生效，只有收到 Debugger.paused 才把状态改成真正的“已暂停”。 */
    public void pauseOrResumeScript() {
        Session session = active.get();
        if (session == null || !session.running.get() || session.inspector == null) return;
        if (session.paused.get()) { resumeScript(); return; }
        session.requestPause();
    }

    public void stopScript() {
        Session session = active.get();
        if (session != null) session.stopExecution();
    }

    public void resumeScript() {
        Session session = active.get();
        if (session != null && session.paused.get() && session.inspector != null) {
            try { session.inspector.sendRequest("{\"id\":-2,\"method\":\"Debugger.resume\"}"); }
            catch (JavetException error) { logListener.accept(error.getMessage()); }
        }
    }

    public void runLastScript() {
        String missing = AutomationPermissions.missing(context);
        if (!missing.isEmpty()) { logListener.accept("请先授权：" + missing); return; }
        if (lastSource == null) { logListener.accept("尚无脚本，请先在 VS Code 运行一次以同步脚本。"); return; }
        Session previous = active.get();
        if (previous != null) {
            logListener.accept(previous.running.get() ? "已有脚本正在运行，暂停时可点击继续。" : "请先在 VS Code 停止调试会话，再从手机运行。");
            return;
        }
        Session local = new Session(null);
        if (!active.compareAndSet(null, local)) return;
        local.open();
        local.executor.execute(() -> {
            if (local.runtime == null || local.closed.get()) return;
            local.running.set(true);
            local.resourceName = lastResourceName;
            local.execute(lastSource, lastResourceName, ++local.nextRunId);
        });
    }

    private final class Session implements IV8InspectorListener {
        private final WebSocket socket;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "XpAuto-script"));
        private final CountDownLatch initialized = new CountDownLatch(1);
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean pausePending = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final ShellApi shell = ShellUtils.createSession(
                text -> consoleMessage("error", text), cancelled::get);
        private final FloatyApi floaty = new FloatyApi(context,
                text -> consoleMessage("error", text), cancelled::get);
        private final HttpApi http = new HttpApi(context.getFilesDir(),
                text -> consoleMessage("error", text), cancelled::get);
        private final ScriptLogApi scriptLog = new ScriptLogApi(context, this::consoleMessage,
                DeveloperService::clearLogs, cancelled::get);
        private volatile V8Runtime runtime;
        private volatile V8Inspector inspector;
        private volatile InspectorInterrupt interrupt;
        private volatile int executionContextId = 1;
        private String resourceName = "";
        private int nextRunId;
        private boolean noDebug;

        Session(WebSocket socket) { this.socket = socket; }

        void open() {
            executor.execute(() -> {
                try {
                    Constant.killThread = false;
                    Constant.isStop = false;
                    runtime = V8Host.getV8Instance().createV8Runtime();
                    inspector = runtime.createV8Inspector("XpAuto");
                    inspector.addListeners(this);
                    runtime.setConverter(new JavetProxyConverter());
                    TaskBase.hashMapBuffer = new HashMap<>();
                    try (V8ValueGlobalObject global = runtime.getGlobalObject()) {
                        global.set("engines", TaskBase.class);
                        global.set("__xpautoHttp", http);
                        global.set("__xpautoLog", scriptLog);
                        global.set("utils", Utils.class);
                        global.set("file", FileUtils.class);
                        global.set("shell", shell);
                        global.set("floaty", floaty);
                    }
                    for (String asset : new String[] { "logging.js", "http.js", "base.js" }) {
                        try (InputStream stream = context.getAssets().open(asset)) {
                            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
                            runtime.getExecutor(new String(bytes.toByteArray(), StandardCharsets.UTF_8))
                                    .setResourceName("xpauto://sdk/" + asset).executeVoid();
                        }
                    }
                    if (socket == null) {
                        inspector.sendRequest("{\"id\":-3,\"method\":\"Runtime.enable\"}");
                    }
                    // 在执行业务脚本前启用调试器，本地运行也能暂停。
                    inspector.sendRequest("{\"id\":-4,\"method\":\"Debugger.enable\"}");
                    interrupt = new InspectorInterrupt(runtime, () -> {
                        if (pausePending.get() && running.get() && !closed.get()) {
                            // 此处已在 V8 执行线程和 JS 调用栈内，直接进入调试暂停循环。
                            // “不调试运行”忽略源码断点，但仍允许用户主动点击暂停。
                            if (noDebug) inspector.setSkipAllPauses(false);
                            try { inspector.breakProgram("xpauto-pause", "{}"); }
                            finally { if (noDebug) inspector.setSkipAllPauses(true); }
                        }
                    });
                    setState("ready", socket == null ? "准备运行本地脚本" : "VS Code 已连接，等待运行脚本", "");
                    event(socket, "XpAuto.ready", object("protocolVersion", 1, "engine", "Javet/V8"));
                } catch (Exception error) {
                    setState("error", "脚本引擎启动失败：" + error.getMessage(), "");
                    event(socket, "XpAuto.error", object("message", error.toString()));
                    if (socket != null) socket.close(1011, "Cannot initialize script runtime");
                    else close();
                } finally { initialized.countDown(); }
            });
        }

        void receive(String message) {
            JSONObject request = null;
            try {
                request = new JSONObject(message);
                if (!initialized.await(10, TimeUnit.SECONDS) || closed.get() || inspector == null) {
                    throw new IllegalStateException("脚本引擎尚未就绪");
                }
                String method = request.getString("method");
                if ("XpAuto.run".equals(method)) {
                    String missing = AutomationPermissions.missing(context);
                    if (!missing.isEmpty()) throw new IllegalStateException("运行脚本前请先授权：" + missing);
                    JSONObject params = request.getJSONObject("params");
                    String source = params.getString("source");
                    String sourceName = params.getString("resourceName");
                    if (source.getBytes(StandardCharsets.UTF_8).length > 16 * 1024 * 1024) {
                        throw new IllegalArgumentException("脚本不能超过 16 MB");
                    }
                    if (!running.compareAndSet(false, true)) throw new IllegalStateException("已有脚本正在运行");
                    cancelled.set(false);
                    shell.resetCancellation();
                    floaty.resetCancellation();
                    http.resetCancellation();
                    scriptLog.reset();
                    floaty.clearWindows();
                    resourceName = sourceName;
                    noDebug = params.optBoolean("noDebug", false);
                    int runId = ++nextRunId;
                    reply(socket, request, object("runId", runId));
                    executor.execute(() -> execute(source, sourceName, runId));
                } else if ("XpAuto.stop".equals(method)) {
                    stopExecution();
                    reply(socket, request, object("accepted", true));
                } else if ("Debugger.pause".equals(method)) {
                    // 电脑和手机共用中断入口，避免普通 Inspector 请求被忙循环持有的锁挡住。
                    requestPause();
                    reply(socket, request, new JSONObject());
                } else {
                    inspector.sendRequest(message);
                }
            } catch (Exception error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                failure(socket, request, error);
            }
        }

        private void consoleMessage(String type, String text) {
            logListener.accept(text);
            JSONObject message = object("type", type,
                    "args", new JSONArray().put(object("type", "string", "value", text)),
                    "executionContextId", executionContextId, "timestamp", System.currentTimeMillis());
            event(socket, "Runtime.consoleAPICalled", message);
            for (WebSocket control : controllers) event(control, "XpAuto.log", message);
        }

        private void execute(String source, String sourceName, int runId) {
            long startedAt = System.currentTimeMillis();
            boolean success = false;
            String errorText = "";
            Constant.isStop = false;
            Constant.killThread = false;
            Constant.isRunning = true;
            try {
                if (closed.get() || cancelled.get()) return;
                lastSource = source;
                lastResourceName = sourceName;
                FileOutputStream output = null;
                try {
                    output = cachedScript.startWrite();
                    output.write(object("source", source, "resourceName", sourceName).toString().getBytes(StandardCharsets.UTF_8));
                    cachedScript.finishWrite(output);
                } catch (Exception error) {
                    if (output != null) cachedScript.failWrite(output);
                    logListener.accept("脚本缓存失败：" + error.getMessage());
                }
                setState("running", "运行中：" + sourceName, sourceName);
                consoleMessage("info", "[XpAuto] 开始运行 " + sourceName);
                event(socket, "XpAuto.running", object("runId", runId));
                inspector.setSkipAllPauses(noDebug);
                runtime.getExecutor("__xpautoResetNetwork();").executeVoid();
                runtime.getExecutor(source).setResourceName(sourceName).executeVoid();
                success = true;
            } catch (Exception error) {
                errorText = error.toString();
                if (error instanceof BaseJavetScriptingException) {
                    JavetScriptingError scriptError = ((BaseJavetScriptingException) error).getScriptingError();
                    errorText = scriptError.getDetailedMessage() + "\n" + sourceName + ":"
                            + scriptError.getLineNumber() + ":" + (scriptError.getStartColumn() + 1);
                    if (scriptError.getStack() != null) errorText += "\n" + scriptError.getStack();
                }
                if (!cancelled.get()) consoleMessage("error", errorText);
                Log.i(TAG, "Script ended: " + errorText);
            } finally {
                floaty.clearWindows();
                http.clearConnections();
                scriptLog.finish(success && !cancelled.get());
                // 完成后立即释放 JS 监听器，调试控制台仍可继续使用 sleep 或新建连接。
                if (!closed.get() && !cancelled.get() && runtime != null) {
                    try { runtime.getExecutor("__xpautoResetNetwork();").executeVoid(); }
                    catch (JavetException cleanupError) { Log.w(TAG, "Cannot clear network callbacks", cleanupError); }
                }
                paused.set(false);
                pausePending.set(false);
                Constant.isRunning = false;
                Constant.isStop = false;
                long elapsed = System.currentTimeMillis() - startedAt;
                String endState = cancelled.get() ? "stopped" : success ? "completed" : "error";
                String detail = cancelled.get() ? "脚本已停止" : success ? "运行完成（" + elapsed + " ms）" : "运行失败：" + errorText;
                if (active.get() == this) setState(endState, detail, sourceName);
                consoleMessage("info", "[XpAuto] " + detail);
                running.set(false);
                event(socket, "XpAuto.finished", object("runId", runId, "success", success,
                        "cancelled", cancelled.get(), "error", errorText, "elapsedMs", elapsed));
                if (socket == null) close();
            }
        }

        void stopExecution() { stopExecution(false); }

        private void stopExecution(boolean closing) {
            cancelled.set(true);
            scriptLog.stop(closing);
            boolean shellExecuting = shell.isExecuting();
            boolean floatyExecuting = floaty.isExecuting();
            boolean httpExecuting = http.isExecuting();
            boolean nodeExecuting = com.vixmobi.auto.node.NodeQuery.isExecuting();
            shell.cancel();
            floaty.cancel();
            http.cancel();
            Constant.killThread = true;
            Constant.isStop = false;
            V8Runtime current = runtime;
            if (current != null && (running.get() || shellExecuting || floatyExecuting || httpExecuting || nodeExecuting)) {
                current.terminateExecution();
                if (paused.get() && inspector != null) {
                    try { inspector.sendRequest("{\"id\":-1,\"method\":\"Debugger.resume\"}"); }
                    catch (JavetException error) { Log.w(TAG, "Cannot resume stopped script", error); }
                }
            }
        }

        void requestPause() {
            if (!running.get() || paused.get() || closed.get() || interrupt == null) return;
            if (!pausePending.compareAndSet(false, true)) return;
            setState("pausing", "正在暂停，等待当前操作返回", resourceName);
            interrupt.requestPause();
        }

        void close() {
            if (!closed.compareAndSet(false, true)) return;
            if (interrupt != null) interrupt.beginClose();
            stopExecution(true);
            floaty.dispose();
            http.dispose();
            executor.execute(() -> {
                try {
                    if (inspector != null) inspector.close();
                    if (runtime != null) runtime.close();
                    if (interrupt != null) interrupt.releaseAfterRuntimeClose();
                } catch (JavetException error) { Log.e(TAG, "Cannot close runtime", error); }
                finally {
                    if (active.compareAndSet(this, null) && socket != null) {
                        setState("idle", "VS Code 已断开，开发服务仍在运行", lastResourceName);
                    }
                }
            });
            executor.shutdown();
        }

        @Override public void receiveNotification(String message) {
            try {
                JSONObject notification = new JSONObject(message);
                String method = notification.optString("method");
                JSONObject params = notification.optJSONObject("params");
                if ("Runtime.executionContextCreated".equals(method)) {
                    executionContextId = params.getJSONObject("context").getInt("id");
                } else if ("Debugger.paused".equals(method)) {
                    pausePending.set(false);
                    paused.set(true);
                    JSONObject frame = params.getJSONArray("callFrames").getJSONObject(0);
                    int line = frame.getJSONObject("location").getInt("lineNumber") + 1;
                    setState("paused", "已暂停：" + resourceName + ":" + line, resourceName);
                } else if ("Debugger.resumed".equals(method)) {
                    paused.set(false);
                    setState("running", "运行中：" + resourceName, resourceName);
                } else if ("Runtime.consoleAPICalled".equals(method)) {
                    JSONArray args = params.getJSONArray("args");
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < args.length(); i++) {
                        JSONObject value = args.getJSONObject(i);
                        if (i > 0) text.append(' ');
                        text.append(value.has("value") ? value.opt("value") : value.optString("description", value.optString("type")));
                    }
                    logListener.accept(text.toString());
                    for (WebSocket control : controllers) event(control, "XpAuto.log", params);
                }
            } catch (Exception error) { Log.w(TAG, "Cannot inspect notification", error); }
            DeveloperServer.send(socket, message);
        }

        @Override public void receiveResponse(String message) { DeveloperServer.send(socket, message); }
        @Override public void flushProtocolNotifications() { }
        @Override public void runIfWaitingForDebugger(int contextGroupId) { }
        @Override public void sendRequest(String message) { }
    }
}
