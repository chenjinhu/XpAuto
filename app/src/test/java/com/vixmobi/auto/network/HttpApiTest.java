package com.vixmobi.auto.network;

import com.google.gson.*;
import okhttp3.mockwebserver.*;
import okio.Buffer;
import org.junit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

/** 用本地真实 HTTP 服务验证编码、文件完整性和取消行为。 */
public class HttpApiTest {
    private MockWebServer server;
    private ExecutorService workers;
    private HttpApi api;
    private File directory;
    private String base;
    private final byte[] payload = new byte[200000];
    private final List<String> ranges = new CopyOnWriteArrayList<>();
    private int downloads;
    @Before public void setup() throws Exception {
        directory = Files.createTempDirectory("xpauto-http-test").toFile();
        api = new HttpApi(directory, message -> {}, () -> false);
        server = new MockWebServer(); workers = Executors.newCachedThreadPool();
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i % 251);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path = request.getRequestUrl().encodedPath();
                if (path.equals("/echo")) {
                    JsonObject echo = new JsonObject(); echo.addProperty("method", request.getMethod());
                    echo.addProperty("query", request.getRequestUrl().encodedQuery());
                    echo.addProperty("body", request.getBody().readUtf8()); echo.addProperty("cookie", request.getHeader("Cookie"));
                    echo.addProperty("agent", request.getHeader("User-Agent")); echo.addProperty("contentType", request.getHeader("Content-Type"));
                    return new MockResponse().setBody(echo.toString()).addHeader("Content-Type", "application/json; charset=utf-8").addHeader("Set-Cookie", "session=hello; Path=/");
                }
                if (path.equals("/error")) return new MockResponse().setResponseCode(418).setBody("茶壶");
                if (path.equals("/binary")) return new MockResponse().setBody(new Buffer().write(payload)).addHeader("Content-Type", "application/octet-stream");
                if (path.equals("/charset")) return new MockResponse().setBody(new Buffer().write("中文".getBytes(StandardCharsets.UTF_16LE)));
                if (path.equals("/redirect")) return new MockResponse().setResponseCode(302).addHeader("Location", "/echo");
                if (path.equals("/slow")) return new MockResponse().setBody("slow").setHeadersDelay(3, TimeUnit.SECONDS);
                if (path.equals("/download")) {
                    String range = request.getHeader("Range"); ranges.add(range == null ? "none" : range);
                    int start = range == null ? 0 : Integer.parseInt(range.substring(6, range.length() - 1));
                    MockResponse response = new MockResponse().addHeader("ETag", "\"version1\"").setBody(new Buffer().write(payload, start, payload.length - start));
                    if (range != null) response.setResponseCode(206).addHeader("Content-Range", "bytes " + start + "-" + (payload.length - 1) + "/" + payload.length);
                    if (downloads++ == 0) response.setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
                    return response;
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start(); base = server.url("/").toString().replaceFirst("/$", "");
    }
    private JsonObject options(String path) { JsonObject options = new JsonObject(); options.addProperty("url", base + path); return options; }
    private JsonObject request(JsonObject options) { JsonElement result = JsonParser.parseString(api.requestJson(options.toString())); return result.isJsonNull() ? null : result.getAsJsonObject(); }
    private JsonObject echo(JsonObject options) { return JsonParser.parseString(request(options).get("body").getAsString()).getAsJsonObject(); }
    @Test public void queryHeadersAndCookies() {
        JsonObject options = options("/echo?old=1"), data = new JsonObject(), cookies = new JsonObject();
        data.addProperty("中文", "a b+&"); cookies.addProperty("token", "yes");
        options.add("data", data); options.add("cookie", cookies); options.addProperty("userAgent", "XpAuto-test");
        JsonObject response = request(options), echo = JsonParser.parseString(response.get("body").getAsString()).getAsJsonObject();
        assertEquals("old=1&%E4%B8%AD%E6%96%87=a%20b%2B%26", echo.get("query").getAsString());
        assertEquals("token=yes", echo.get("cookie").getAsString()); assertEquals("hello", response.getAsJsonObject("cookie").get("session").getAsString());
        assertEquals("XpAuto-test", echo.get("agent").getAsString());
    }
    @Test public void formAndRawBody() {
        JsonObject options = options("/echo"); options.addProperty("method", "POST");
        JsonObject data = new JsonObject(); data.addProperty("name", "中文 &"); options.add("data", data);
        assertEquals("name=%E4%B8%AD%E6%96%87%20%26", echo(options).get("body").getAsString());
        options.remove("data"); options.addProperty("requestBody", "{\"中文\":1}");
        assertEquals("{\"中文\":1}", echo(options).get("body").getAsString());
    }
    @Test public void multipartUploadsRealFile() throws Exception {
        Files.write(new File(directory, "hello.txt").toPath(), "上传内容".getBytes(StandardCharsets.UTF_8));
        JsonObject options = options("/echo"); options.addProperty("method", "POST");
        options.add("file", JsonParser.parseString("[{\"key\":\"asset\",\"filePath\":\"hello.txt\",\"fileName\":\"a.txt\"}]"));
        JsonObject response = echo(options); String body = response.get("body").getAsString();
        assertTrue(body.contains("name=\"asset\"; filename=\"a.txt\"")); assertTrue(body.contains("上传内容"));
        assertTrue(response.get("contentType").getAsString().startsWith("multipart/form-data; boundary="));
    }
    @Test public void errorsLimitsCharsetAndRedirects() {
        JsonObject options = options("/error"); assertNull(request(options)); options.addProperty("ignoreHttpErrors", true);
        assertEquals(418, request(options).get("statusCode").getAsInt());
        options = options("/binary"); options.addProperty("maxBodySize", 1000); assertNull(request(options));
        options.addProperty("maxBodySize", 300000); options.addProperty("ignoreContentType", false); assertNull(request(options));
        options = options("/charset"); options.addProperty("responseCharset", "UTF-16LE"); assertEquals("中文", request(options).get("body").getAsString());
        options = options("/redirect"); options.addProperty("followRedirects", false); assertEquals(302, request(options).get("statusCode").getAsInt());
        options.addProperty("followRedirects", true); assertEquals(200, request(options).get("statusCode").getAsInt());
    }
    @Test public void timeoutAndCancellationReturnPromptly() throws Exception {
        JsonObject options = options("/slow"); options.addProperty("timeout", 100); assertNull(request(options));
        options.addProperty("timeout", 30000);
        Future<?> pending = workers.submit(() -> request(options));
        while (!api.isExecuting()) Thread.sleep(5);
        api.cancel();
        try { pending.get(1, TimeUnit.SECONDS); fail("应取消请求"); } catch (ExecutionException error) { assertTrue(error.getCause() instanceof CancellationException); }
        api.resetCancellation(); assertNotNull(request(options("/echo")));
    }
    @Test public void browserPostIsNotReplayedWhenResponseConnectionDrops() {
        // 服务端已收到页面命令但响应丢失时，不能自动执行第二遍。
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST);
            }
        });
        JsonObject options = options("/api/js/execute");
        options.addProperty("method", "POST");
        options.addProperty("requestBody", "{\"script\":\"sideEffect()\"}");
        options.addProperty("retryOnConnectionFailure", false);
        assertNull(request(options));
        assertEquals(1, server.getRequestCount());
    }
    @Test public void interruptedDownloadResumesAndPreservesExistingTarget() throws Exception {
        File target = new File(directory, "asset.bin"); Files.write(target.toPath(), new byte[] { 7 });
        JsonObject options = options("/download"); options.addProperty("path", "asset.bin"); options.addProperty("timeout", 1000);
        assertFalse(api.downloadJson(options.toString())); assertArrayEquals(new byte[] { 7 }, Files.readAllBytes(target.toPath()));
        assertTrue(new File(target + ".xpauto.part").length() > 0);
        assertTrue(api.downloadJson(options.toString())); assertArrayEquals(payload, Files.readAllBytes(target.toPath()));
        assertTrue(ranges.get(1).startsWith("bytes=")); assertFalse(new File(target + ".xpauto.part").exists());
    }
    @Test public void serverIgnoringRangeRestartsInsteadOfAppending() throws Exception {
        File target = new File(directory, "asset.bin");
        Files.write(new File(target + ".xpauto.part").toPath(), new byte[] { 1, 2, 3 });
        Files.write(new File(target + ".xpauto.part.json").toPath(), ("{\"url\":\"" + base + "/binary\",\"validator\":\"old\"}").getBytes(StandardCharsets.UTF_8));
        JsonObject options = options("/binary"); options.addProperty("path", "asset.bin");
        assertTrue(api.downloadJson(options.toString())); assertArrayEquals(payload, Files.readAllBytes(target.toPath()));
    }
    @After public void teardown() throws Exception {
        api.dispose(); server.shutdown(); workers.shutdownNow();
        try (java.util.stream.Stream<Path> paths = Files.walk(directory.toPath())) { paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (IOException ignored) {} }); }
    }
}
