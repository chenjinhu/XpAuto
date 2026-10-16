package com.vixmobi.auto.network;

import com.google.gson.*;
import okhttp3.*;
import okio.ByteString;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** 网络线程只入队数据；JS 回调由 http.js 在脚本线程中派发。 */
public final class ScriptSocket {
    private final HttpApi owner;
    private final String url;
    private final JsonObject headers;
    private final int type;
    private final ArrayDeque<JsonObject> events = new ArrayDeque<>();
    private int queuedBytes, generation;
    private volatile boolean connected, closed = true, disposed;
    private volatile CountDownLatch opening = new CountDownLatch(0);
    private okhttp3.WebSocket okhttp;
    private WebSocketClient javaSocket;
    private int callTimeout = 30, readTimeout = 0, writeTimeout = 30, pingInterval, lostTimeout = 60;

    ScriptSocket(HttpApi owner, JsonObject options) {
        this.owner = owner; url = HttpApi.string(options, "url", ""); headers = HttpApi.object(options, "header");
        type = HttpApi.integer(options, "type", 1);
        if (type != 1 && type != 2) throw new IllegalArgumentException("WebSocket type 只能为 1 或 2");
        URI uri = URI.create(url);
        if (!("ws".equals(uri.getScheme()) || "wss".equals(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException("WebSocket 地址必须使用 ws:// 或 wss://");
    }
    public boolean connect(int timeout) {
        if (timeout <= 0) throw new IllegalArgumentException("连接超时必须大于 0 毫秒");
        owner.enter();
        try {
            startConnect(timeout);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
            while (!opening.await(50, TimeUnit.MILLISECONDS)) {
                owner.check();
                if (System.nanoTime() >= deadline) { close(); error("WebSocket 连接超时"); return false; }
            }
            owner.check(); return connected;
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new CancellationException("连接已停止"); }
        finally { owner.leave(); }
    }
    public synchronized void startConnect(int timeout) {
        owner.check();
        if (disposed) throw new IllegalStateException("WebSocket 已随脚本结束释放");
        if (!closed) return;
        abort();
        connected = false; closed = false; opening = new CountDownLatch(1);
        int ticket = ++generation;
        try {
            if (type == 1) {
                JsonObject options = new JsonObject(); options.addProperty("timeout", timeout); options.addProperty("url", url);
                OkHttpClient client = owner.client(options).newBuilder().callTimeout(callTimeout, TimeUnit.SECONDS)
                        .readTimeout(readTimeout, TimeUnit.SECONDS).writeTimeout(writeTimeout, TimeUnit.SECONDS)
                        .pingInterval(pingInterval, TimeUnit.SECONDS).build();
                Request.Builder request = new Request.Builder().url(url); HttpApi.applyHeaders(request, headers);
                okhttp = client.newWebSocket(request.build(), new WebSocketListener() {
                    @Override public void onOpen(okhttp3.WebSocket ws, Response response) { opened(ticket, response.code(), response.message()); }
                    @Override public void onMessage(okhttp3.WebSocket ws, String text) { message(ticket, "text", text); }
                    @Override public void onMessage(okhttp3.WebSocket ws, ByteString bytes) { message(ticket, "binary", bytes.base64()); }
                    @Override public void onClosing(okhttp3.WebSocket ws, int code, String reason) { ws.close(code, reason); }
                    @Override public void onClosed(okhttp3.WebSocket ws, int code, String reason) { ended(ticket, code, reason, null); }
                    @Override public void onFailure(okhttp3.WebSocket ws, Throwable error, Response response) { ended(ticket, 1006, "", error.getMessage()); if (response != null) response.close(); }
                });
            } else {
                Map<String, String> map = new HashMap<>(); headers.entrySet().forEach(entry -> map.put(entry.getKey(), HttpApi.value(entry.getValue())));
                javaSocket = new WebSocketClient(URI.create(url), new org.java_websocket.drafts.Draft_6455(), map, timeout) {
                    @Override protected void onSetSSLParameters(SSLParameters parameters) { super.onSetSSLParameters(parameters); parameters.setEndpointIdentificationAlgorithm("HTTPS"); }
                    @Override public void onOpen(ServerHandshake handshake) { opened(ticket, handshake.getHttpStatus(), handshake.getHttpStatusMessage()); }
                    @Override public void onMessage(String text) { message(ticket, "text", text); }
                    @Override public void onMessage(ByteBuffer bytes) { byte[] data = new byte[bytes.remaining()]; bytes.get(data); message(ticket, "binary", Base64.getEncoder().encodeToString(data)); }
                    @Override public void onClose(int code, String reason, boolean remote) { ended(ticket, code, reason, null); }
                    @Override public void onError(Exception error) { ended(ticket, 1006, "", error.getMessage()); }
                };
                javaSocket.setConnectionLostTimeout(lostTimeout); javaSocket.connect();
            }
        } catch (Exception error) { ended(ticket, 1006, "", error.getMessage()); }
    }
    private synchronized void opened(int ticket, int code, String text) {
        if (ticket != generation || disposed) return;
        connected = true; closed = false;
        JsonObject event = event("open", text); event.addProperty("code", code); enqueue(event); opening.countDown();
    }
    private synchronized void message(int ticket, String kind, String data) { if (ticket == generation && !disposed) enqueue(event(kind, data)); }
    private synchronized void ended(int ticket, int code, String reason, String failure) {
        if (ticket != generation || disposed || closed) return;
        connected = false; closed = true;
        if (failure != null) enqueue(event("error", failure));
        JsonObject event = event("close", reason); event.addProperty("code", code); enqueue(event); opening.countDown();
    }
    private JsonObject event(String kind, String data) { JsonObject event = new JsonObject(); event.addProperty("kind", kind); event.addProperty("data", data == null ? "" : data); return event; }
    private synchronized void error(String message) { enqueue(event("error", message)); }
    private void enqueue(JsonObject event) {
        int size = event.toString().length() * 2;
        if (events.size() >= 256 || queuedBytes + size > 4 * 1024 * 1024) {
            // 调试暂停或脚本长时间不让出执行权时，有限队列保护手机内存。
            events.clear(); queuedBytes = 0; abort();
            event = event("error", "WebSocket 事件队列已满，连接已关闭；请定期调用 sleep 或 http.pollEvents");
            size = event.toString().length() * 2;
        }
        events.add(event); queuedBytes += size;
    }
    public synchronized String pollJson() {
        owner.check(); JsonArray result = new JsonArray();
        while (!events.isEmpty()) result.add(events.remove()); queuedBytes = 0; return result.toString();
    }
    public synchronized boolean sendText(String text) { owner.check(); if (!connected) return false; if (text.getBytes(StandardCharsets.UTF_8).length > 1024 * 1024) throw new IllegalArgumentException("单条消息不能超过 1 MiB"); try { if (type == 1) return okhttp.send(text); javaSocket.send(text); return true; } catch (Exception error) { error(error.getMessage()); return false; } }
    public synchronized boolean sendBinaryJson(String json) {
        owner.check(); JsonArray values = JsonParser.parseString(json).getAsJsonArray();
        if (values.size() > 1024 * 1024) throw new IllegalArgumentException("单条消息不能超过 1 MiB");
        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) { int value = values.get(i).getAsInt(); if (value < -128 || value > 255) throw new IllegalArgumentException("字节值必须介于 -128～255"); bytes[i] = (byte) value; }
        if (!connected) return false;
        try { if (type == 1) return okhttp.send(ByteString.of(bytes)); javaSocket.send(bytes); return true; } catch (Exception error) { error(error.getMessage()); return false; }
    }
    public boolean isConnected() { return connected; }
    public boolean isClosed() { return closed; }
    public synchronized void close() {
        boolean notify = !closed;
        generation++; connected = false; closed = true; opening.countDown();
        // 主动关闭先发送正常关闭帧；会话销毁或再次连接时再强制释放残留连接。
        if (okhttp != null) okhttp.close(1000, "");
        if (javaSocket != null) javaSocket.close();
        if (notify) { JsonObject event = event("close", "主动关闭"); event.addProperty("code", 1000); enqueue(event); }
    }
    public synchronized boolean reset() { close(); events.clear(); queuedBytes = 0; return !disposed; }
    private void abort() { generation++; connected = false; closed = true; opening.countDown(); if (okhttp != null) { okhttp.cancel(); okhttp = null; } if (javaSocket != null) { javaSocket.close(); javaSocket = null; } }
    synchronized void dispose() { disposed = true; abort(); events.clear(); queuedBytes = 0; }
    private int seconds(int value) { if (value < 0 || value > 86400) throw new IllegalArgumentException("超时时间必须为 0～86400 秒"); return value; }
    public void setCallTimeout(int value) { callTimeout = seconds(value); }
    public void setReadTimeout(int value) { readTimeout = seconds(value); }
    public void setWriteTimeout(int value) { writeTimeout = seconds(value); }
    public void setPingInterval(int value) { pingInterval = seconds(value); }
    public synchronized void setConnectionLostTimeout(int value) { lostTimeout = seconds(value); if (javaSocket != null) javaSocket.setConnectionLostTimeout(lostTimeout); }
}
