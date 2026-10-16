package com.vixmobi.auto.debug;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.caoccao.javet.exceptions.JavetException;
import com.caoccao.javet.interop.IV8InspectorListener;
import com.caoccao.javet.interop.V8Host;
import com.caoccao.javet.interop.V8Inspector;
import com.caoccao.javet.interop.V8Runtime;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Debug-build-only Inspector experiment. All user JavaScript comes from the client. */
public final class InspectorProbeActivity extends Activity {
    private static final String TAG = "XpAutoInspector";
    private final AtomicReference<Session> activeSession = new AtomicReference<>();
    private WebSocketServer server;
    private TextView status;
    private String entrySource;
    private String entryResourceName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        status = new TextView(this);
        status.setPadding(40, 80, 40, 40);
        status.setTextSize(18);
        status.setText("正在启动 JS 调试验证服务…");
        setContentView(status);

        String entryName = getIntent().getStringExtra("entryName");
        if (entryName != null) {
            try {
                // A debug entry is uploaded to this app's external files directory by ADB.
                if (!new File(entryName).getName().equals(entryName)) {
                    throw new IllegalArgumentException("entryName must be a file name");
                }
                File entry = new File(getExternalFilesDir(null), entryName);
                entrySource = new String(Files.readAllBytes(entry.toPath()), StandardCharsets.UTF_8);
                entryResourceName = getIntent().getStringExtra("resourceName");
                if (entryResourceName == null) {
                    throw new IllegalArgumentException("resourceName is required");
                }
            } catch (Exception error) {
                Log.e(TAG, "Cannot load debug entry", error);
                status.setText("无法加载调试脚本：" + error.getMessage());
                return;
            }
        }

        server = new WebSocketServer(new InetSocketAddress("127.0.0.1", 9229)) {
            @Override
            public void onOpen(WebSocket connection, ClientHandshake handshake) {
                Session session = new Session(connection, entrySource, entryResourceName);
                if (!activeSession.compareAndSet(null, session)) {
                    connection.close(1008, "A debugger is already connected");
                    session.close();
                    return;
                }
                session.open();
            }

            @Override
            public void onMessage(WebSocket connection, String message) {
                Session session = activeSession.get();
                if (session != null && session.connection == connection) {
                    session.receive(message);
                }
            }

            @Override
            public void onClose(WebSocket connection, int code, String reason, boolean remote) {
                Session session = activeSession.get();
                if (session != null && session.connection == connection
                        && activeSession.compareAndSet(session, null)) {
                    session.close();
                }
            }

            @Override
            public void onError(WebSocket connection, Exception error) {
                Log.e(TAG, "Inspector transport error", error);
                showStatus("调试连接异常：" + error.getMessage());
            }

            @Override
            public void onStart() {
                showStatus("JS 调试验证已就绪\n\n通过 USB / ADB 连接本机端口 9229。");
                Log.i(TAG, "Inspector listening on 127.0.0.1:9229");
            }
        };
        server.setReuseAddr(true);
        server.setConnectionLostTimeout(15);
        server.start();
    }

    private void showStatus(String message) {
        runOnUiThread(() -> status.setText(message));
    }

    @Override
    protected void onDestroy() {
        Session session = activeSession.getAndSet(null);
        if (session != null) {
            session.close();
        }
        WebSocketServer closingServer = server;
        if (closingServer != null) {
            new Thread(() -> {
                try {
                    closingServer.stop(1000);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }, "XpAuto-inspector-shutdown").start();
        }
        super.onDestroy();
    }

    private static final class Session implements IV8InspectorListener {
        private final WebSocket connection;
        private final ExecutorService executor = Executors.newSingleThreadExecutor(
                task -> new Thread(task, "XpAuto-probe-V8"));
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean running = new AtomicBoolean();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean entryStarted = new AtomicBoolean();
        private final String entrySource;
        private final String entryResourceName;
        private volatile V8Runtime runtime;
        private volatile V8Inspector inspector;
        private int nextRunId;

        Session(WebSocket connection, String entrySource, String entryResourceName) {
            this.connection = connection;
            this.entrySource = entrySource;
            this.entryResourceName = entryResourceName;
        }

        void open() {
            executor.execute(() -> {
                try {
                    runtime = V8Host.getV8Instance().createV8Runtime();
                    inspector = runtime.createV8Inspector("XpAuto JS Probe");
                    inspector.addListeners(this);
                    event("XpAuto.ready", new JSONObject().put("engine", "Javet/V8"));
                } catch (Exception error) {
                    Log.e(TAG, "Cannot initialize V8 Inspector", error);
                    event("XpAuto.error", errorDetails(error));
                    connection.close(1011, "Cannot initialize V8 Inspector");
                }
            });
        }

        void receive(String message) {
            JSONObject request = null;
            try {
                request = new JSONObject(message);
                String method = request.getString("method");
                if (closed.get() || inspector == null) {
                    throw new IllegalStateException("Inspector is not ready");
                }
                if ("XpAuto.run".equals(method)) {
                    JSONObject params = request.getJSONObject("params");
                    String source = params.getString("source");
                    String resourceName = params.getString("resourceName");
                    if (!running.compareAndSet(false, true)) {
                        throw new IllegalStateException("A script is already running");
                    }
                    cancelled.set(false);
                    int runId = ++nextRunId;
                    reply(request, new JSONObject().put("runId", runId));
                    executor.execute(() -> execute(source, resourceName, runId));
                } else if ("XpAuto.stop".equals(method)) {
                    stopExecution();
                    reply(request, new JSONObject().put("accepted", true));
                } else {
                    // Inspector requests must remain available while the JS thread is paused.
                    inspector.sendRequest(message);
                }
            } catch (Exception error) {
                try {
                    if (request != null && request.has("id")) {
                        send(new JSONObject().put("id", request.get("id")).put("error",
                                new JSONObject().put("code", -32000)
                                        .put("message", error.toString())).toString());
                    } else {
                        event("XpAuto.error", errorDetails(error));
                    }
                } catch (JSONException jsonError) {
                    Log.e(TAG, "Cannot report Inspector request failure", jsonError);
                }
            }
        }

        private void execute(String source, String resourceName, int runId) {
            JSONObject result = new JSONObject();
            try {
                result.put("runId", runId);
                if (closed.get() || cancelled.get()) {
                    return;
                }
                event("XpAuto.running", new JSONObject().put("runId", runId));
                runtime.getExecutor(source).setResourceName(resourceName).executeVoid();
                result.put("success", true);
            } catch (Exception error) {
                Log.i(TAG, "Script execution ended: " + error);
                try {
                    result.put("success", false).put("error", error.toString());
                } catch (JSONException jsonError) {
                    Log.e(TAG, "Cannot serialize script failure", jsonError);
                }
            } finally {
                paused.set(false);
                running.set(false);
                try {
                    result.put("cancelled", cancelled.get());
                } catch (JSONException jsonError) {
                    Log.e(TAG, "Cannot serialize script cancellation", jsonError);
                }
                event("XpAuto.finished", result);
            }
        }

        private void stopExecution() {
            cancelled.set(true);
            V8Runtime current = runtime;
            V8Inspector currentInspector = inspector;
            if (current != null && running.get()) {
                current.terminateExecution();
                if (paused.get() && currentInspector != null) {
                    try {
                        currentInspector.sendRequest(
                                "{\"id\":-1,\"method\":\"Debugger.resume\"}");
                    } catch (JavetException error) {
                        Log.w(TAG, "Cannot resume terminating script", error);
                    }
                }
            }
        }

        void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            stopExecution();
            try {
                executor.execute(() -> {
                    try {
                        if (runtime != null) {
                            if (inspector != null) {
                                inspector.close();
                            }
                            runtime.close();
                            runtime = null;
                            inspector = null;
                        }
                    } catch (JavetException error) {
                        Log.e(TAG, "Cannot release probe runtime", error);
                    }
                });
            } catch (RejectedExecutionException error) {
                Log.w(TAG, "Probe executor already closed", error);
            }
            executor.shutdown();
        }

        private void reply(JSONObject request, JSONObject result) throws JSONException {
            send(new JSONObject().put("id", request.get("id")).put("result", result).toString());
        }

        private void event(String method, JSONObject params) {
            try {
                send(new JSONObject().put("method", method).put("params", params).toString());
            } catch (JSONException error) {
                Log.e(TAG, "Cannot serialize probe event", error);
            }
        }

        private JSONObject errorDetails(Exception error) {
            JSONObject details = new JSONObject();
            try {
                details.put("message", error.toString());
            } catch (JSONException jsonError) {
                Log.e(TAG, "Cannot serialize probe error", jsonError);
            }
            return details;
        }

        private void send(String message) {
            if (connection.isOpen()) {
                connection.send(message);
            }
        }

        @Override
        public void flushProtocolNotifications() {
        }

        @Override
        public void receiveNotification(String message) {
            try {
                String method = new JSONObject(message).optString("method");
                if ("Debugger.paused".equals(method)) {
                    paused.set(true);
                } else if ("Debugger.resumed".equals(method)) {
                    paused.set(false);
                }
            } catch (JSONException error) {
                Log.w(TAG, "Cannot inspect V8 notification", error);
            }
            send(message);
        }

        @Override
        public void receiveResponse(String message) {
            send(message);
        }

        @Override
        public void runIfWaitingForDebugger(int contextGroupId) {
            // VS Code sends this after installing breakpoints. Preserve the original
            // source and file URL so the editor's line numbers map directly to V8.
            if (entrySource != null && !closed.get() && entryStarted.compareAndSet(false, true)
                    && running.compareAndSet(false, true)) {
                cancelled.set(false);
                int runId = ++nextRunId;
                executor.execute(() -> execute(entrySource, entryResourceName, runId));
            }
        }

        @Override
        public void sendRequest(String message) {
        }
    }
}
