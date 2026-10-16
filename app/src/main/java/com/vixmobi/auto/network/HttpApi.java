package com.vixmobi.auto.network;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

/** 每个脚本会话独享网络资源；JSON 桥接使返回值成为普通 JS 对象。 */
public final class HttpApi {
    private final File directory;
    private final Consumer<String> logger;
    private final BooleanSupplier externalCancelled;
    private final OkHttpClient client = new OkHttpClient();
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private final Set<ScriptSocket> sockets = ConcurrentHashMap.newKeySet();
    private final AtomicInteger executing = new AtomicInteger();
    private volatile boolean cancelled;

    public HttpApi(File directory, Consumer<String> logger, BooleanSupplier cancelled) {
        this.directory = directory; this.logger = logger; this.externalCancelled = cancelled;
    }
    public String requestJson(String json) {
        executing.incrementAndGet();
        try {
            check();
            JsonObject options = JsonParser.parseString(json).getAsJsonObject();
            Request request = buildRequest(options);
            Call call = client(options).newCall(request);
            calls.add(call);
            try {
                check();
                try (Response response = call.execute()) {
                    if (!bool(options, "ignoreHttpErrors", false) && response.code() >= 400)
                        throw new IOException("HTTP " + response.code() + " " + response.message());
                    ResponseBody body = response.body();
                    String contentType = response.header("Content-Type", "");
                    if (!bool(options, "ignoreContentType", true) && !contentType.isEmpty()
                            && !contentType.toLowerCase(Locale.ROOT).matches(".*(text/|json|xml|javascript|x-www-form-urlencoded).*"))
                        throw new IOException("响应不是文本类型，请使用 downloadFile");
                    int limit = integer(options, "maxBodySize", 8 * 1024 * 1024);
                    if (limit < 1 || limit > 32 * 1024 * 1024) throw new IllegalArgumentException("maxBodySize 必须为 1～33554432 字节");
                    Charset charset = options.has("responseCharset") ? Charset.forName(string(options, "responseCharset", "UTF-8"))
                            : body != null && body.contentType() != null ? body.contentType().charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
                    JsonObject result = new JsonObject(), headers = new JsonObject(), cookies = new JsonObject();
                    for (String name : response.headers().names()) headers.addProperty(name, response.header(name));
                    for (Cookie cookie : Cookie.parseAll(response.request().url(), response.headers())) cookies.addProperty(cookie.name(), cookie.value());
                    result.add("header", headers); result.add("cookie", cookies);
                    result.addProperty("statusCode", response.code()); result.addProperty("statusMessage", response.message());
                    result.addProperty("charset", charset.name()); result.addProperty("contentType", contentType);
                    result.addProperty("body", body == null ? "" : new String(readLimited(body.byteStream(), limit), charset));
                    return result.toString();
                }
            } finally { calls.remove(call); }
        } catch (Exception error) { return failure("request", error, "null"); }
        finally { executing.decrementAndGet(); }
    }
    public boolean downloadJson(String json) {
        executing.incrementAndGet();
        try { check(); return new HttpDownload(this).download(JsonParser.parseString(json).getAsJsonObject()); }
        catch (Exception error) { return failure("downloadFile", error, false); }
        finally { executing.decrementAndGet(); }
    }
    public ScriptSocket newSocketJson(String json) {
        check();
        if (sockets.size() >= 16) throw new IllegalStateException("每个脚本最多创建 16 个 WebSocket，请复用连接");
        ScriptSocket socket = new ScriptSocket(this, JsonParser.parseString(json).getAsJsonObject());
        sockets.add(socket); return socket;
    }
    Request buildRequest(JsonObject options) throws IOException {
        HttpUrl url = HttpUrl.get(string(options, "url", ""));
        String method = string(options, "method", "GET").toUpperCase(Locale.ROOT);
        JsonElement data = options.get("data");
        boolean query = method.equals("GET") || method.equals("HEAD");
        if (query && data != null && !data.isJsonNull()) {
            HttpUrl.Builder builder = url.newBuilder();
            if (data.isJsonObject()) for (Map.Entry<String, JsonElement> item : data.getAsJsonObject().entrySet())
                builder.addQueryParameter(item.getKey(), value(item.getValue()));
            else {
                HttpUrl parsed = HttpUrl.get("http://localhost/?" + data.getAsString().replaceFirst("^\\?", ""));
                for (int i = 0; i < parsed.querySize(); i++) builder.addQueryParameter(parsed.queryParameterName(i), parsed.queryParameterValue(i));
            }
            url = builder.build();
        }
        Request.Builder request = new Request.Builder().url(url);
        applyHeaders(request, object(options, "header"));
        if (options.has("userAgent")) request.header("User-Agent", string(options, "userAgent", ""));
        if (options.has("referrer")) request.header("Referer", string(options, "referrer", ""));
        JsonObject cookies = object(options, "cookie");
        if (cookies.size() > 0) {
            StringJoiner joined = new StringJoiner("; ");
            cookies.entrySet().forEach(entry -> joined.add(entry.getKey() + "=" + value(entry.getValue())));
            request.header("Cookie", joined.toString());
        }
        RequestBody body = null;
        if (!query) {
            JsonArray files = options.has("file") && options.get("file").isJsonArray() ? options.getAsJsonArray("file") : new JsonArray();
            if (files.size() > 0) {
                if (options.has("requestBody")) throw new IllegalArgumentException("上传文件时请使用 data 表单字段，不能同时传 requestBody");
                MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
                if (data != null && data.isJsonObject()) data.getAsJsonObject().entrySet().forEach(entry -> multipart.addFormDataPart(entry.getKey(), value(entry.getValue())));
                else if (data != null && !data.isJsonNull()) throw new IllegalArgumentException("上传文件时 params 必须为对象");
                for (JsonElement entry : files) {
                    JsonObject item = entry.getAsJsonObject();
                    File file = resolve(string(item, "filePath", ""));
                    if (!file.isFile() || !file.canRead()) throw new IOException("上传文件不可读：" + file);
                    multipart.addFormDataPart(string(item, "key", "file"), string(item, "fileName", file.getName()),
                            RequestBody.create(MediaType.parse(string(item, "contentType", "application/octet-stream")), file));
                }
                body = multipart.build();
                // 边界由 OkHttp 生成，不能沿用调用方手写的 Content-Type。
                request.removeHeader("Content-Type");
            } else if (options.has("requestBody")) {
                String type = request.build().header("Content-Type");
                body = RequestBody.create(MediaType.parse(type == null ? "text/plain; charset=utf-8" : type), string(options, "requestBody", ""));
            } else if (data != null && data.isJsonObject()) {
                FormBody.Builder form = new FormBody.Builder(StandardCharsets.UTF_8);
                data.getAsJsonObject().entrySet().forEach(entry -> form.add(entry.getKey(), value(entry.getValue())));
                body = form.build();
            } else if (data != null && !data.isJsonNull()) {
                body = RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), data.getAsString());
            } else if (Arrays.asList("POST", "PUT", "PATCH", "PROPPATCH", "REPORT").contains(method)) {
                body = RequestBody.create(null, new byte[0]);
            }
        } else if (options.has("requestBody") || options.has("file")) throw new IllegalArgumentException("GET/HEAD 不支持请求体或文件");
        return request.method(method, body).build();
    }
    OkHttpClient client(JsonObject options) {
        int timeout = integer(options, "timeout", 30000);
        if (timeout < 1) throw new IllegalArgumentException("timeout 必须大于 0，单位毫秒");
        OkHttpClient.Builder builder = client.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS).readTimeout(timeout, TimeUnit.MILLISECONDS).writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(bool(options, "followRedirects", true)).followSslRedirects(bool(options, "followRedirects", true));
        JsonObject proxy = object(options, "proxy");
        if (proxy.size() > 0) builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(string(proxy, "host", ""), integer(proxy, "port", 0))));
        else {
            String host = URI.create(string(options, "url", "")).getHost();
            boolean direct = options.has("proxy") && options.get("proxy").isJsonPrimitive() && !options.get("proxy").getAsBoolean();
            // 手机本机服务不应被系统抓包代理转发到其他机器。
            if (direct || "localhost".equalsIgnoreCase(host) || "::1".equals(host) || "[::1]".equals(host)
                    || host != null && host.matches("127\\.\\d+\\.\\d+\\.\\d+")) builder.proxy(Proxy.NO_PROXY);
        }
        return builder.build();
    }
    byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = input.read(buffer)) != -1) { check(); if (count > limit - output.size()) throw new IOException("响应超过 maxBodySize 限制"); output.write(buffer, 0, count); }
        return output.toByteArray();
    }
    File resolve(String path) { if (path.isEmpty()) throw new IllegalArgumentException("文件路径不能为空"); File file = new File(path); return file.isAbsolute() ? file : new File(directory, path); }
    void track(Call call) { calls.add(call); check(); }
    void untrack(Call call) { calls.remove(call); }
    void enter() { executing.incrementAndGet(); }
    void leave() { executing.decrementAndGet(); }
    void check() { if (cancelled || externalCancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("网络操作已停止"); }
    private <T> T failure(String operation, Exception error, T fallback) { check(); if (error instanceof CancellationException) throw (CancellationException) error; logger.accept("[http." + operation + "] " + error.getMessage()); return fallback; }
    public boolean isExecuting() { return executing.get() > 0; }
    public void clearConnections() { for (Call call : calls) call.cancel(); for (ScriptSocket socket : sockets) socket.dispose(); sockets.clear(); }
    public void cancel() { cancelled = true; clearConnections(); }
    public void resetCancellation() { clearConnections(); cancelled = false; }
    public void dispose() { cancel(); client.dispatcher().executorService().shutdown(); client.connectionPool().evictAll(); }
    static void applyHeaders(Request.Builder request, JsonObject headers) { headers.entrySet().forEach(entry -> request.header(entry.getKey(), value(entry.getValue()))); }
    static String value(JsonElement value) { return value.isJsonNull() ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString(); }
    static String string(JsonObject object, String key, String fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback; }
    static int integer(JsonObject object, String key, int fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : fallback; }
    static boolean bool(JsonObject object, String key, boolean fallback) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsBoolean() : fallback; }
    static JsonObject object(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject(); }
}
