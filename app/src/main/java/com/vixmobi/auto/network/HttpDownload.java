package com.vixmobi.auto.network;

import com.google.gson.*;
import okhttp3.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.*;

/** 下载先写旁路文件；只有完整校验通过才替换目标文件，失败时保留续传进度。 */
final class HttpDownload {
    private final HttpApi owner;
    HttpDownload(HttpApi owner) { this.owner = owner; }
    boolean download(JsonObject options) throws IOException {
        String url = HttpApi.string(options, "url", "");
        File target = owner.resolve(HttpApi.string(options, "path", "")).getCanonicalFile();
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建下载目录");
        File part = new File(target + ".xpauto.part"), metadata = new File(target + ".xpauto.part.json");
        JsonObject saved = new JsonObject();
        if (metadata.isFile() && metadata.length() < 65536) {
            try (Reader reader = new InputStreamReader(new FileInputStream(metadata), StandardCharsets.UTF_8)) { saved = JsonParser.parseReader(reader).getAsJsonObject(); }
            catch (Exception ignored) { /* 无效的元数据按重新下载处理。 */ }
        }
        String validator = HttpApi.string(saved, "validator", "");
        long offset = url.equals(HttpApi.string(saved, "url", "")) && !validator.isEmpty() ? part.length() : 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            owner.check();
            Request.Builder builder = new Request.Builder().url(url);
            HttpApi.applyHeaders(builder, HttpApi.object(options, "header"));
            builder.header("Accept-Encoding", "identity").removeHeader("Range").removeHeader("If-Range");
            if (offset > 0) builder.header("Range", "bytes=" + offset + "-").header("If-Range", validator);
            Call call = owner.client(options).newCall(builder.build());
            try {
                owner.track(call);
                try (Response response = call.execute()) {
                    if (response.code() == 416 && offset > 0) { offset = 0; continue; }
                    if (!response.isSuccessful() || response.body() == null) throw new IOException("下载失败：HTTP " + response.code());
                    boolean append = response.code() == 206;
                    long expected = response.body().contentLength(), total = -1;
                    if (append) {
                        Matcher range = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(response.header("Content-Range", ""));
                        if (!range.matches() || Long.parseLong(range.group(1)) != offset) throw new IOException("服务器返回了不匹配的 Content-Range");
                        long end = Long.parseLong(range.group(2)); total = Long.parseLong(range.group(3));
                        if (end < offset || end >= total || (expected >= 0 && expected != end - offset + 1)) throw new IOException("服务器返回了无效的下载长度");
                        expected = end - offset + 1;
                        String current = response.header("ETag", response.header("Last-Modified", ""));
                        if (offset > 0 && !validator.equals(current)) throw new IOException("续传文件版本已改变，请重新下载");
                    } else offset = 0;
                    String tag = response.header("ETag", "");
                    if (tag.isEmpty() || tag.startsWith("W/")) tag = response.header("Last-Modified", "");
                    JsonObject state = new JsonObject(); state.addProperty("url", url); state.addProperty("validator", tag);
                    // 先截断旧片段，再更新元数据，避免中途失败把旧内容误认成新版本。
                    try (FileOutputStream output = new FileOutputStream(part, append && offset > 0)) {
                        Files.write(metadata.toPath(), state.toString().getBytes(StandardCharsets.UTF_8));
                        byte[] buffer = new byte[32768]; long received = 0; int count;
                        try (InputStream input = response.body().byteStream()) {
                            while ((count = input.read(buffer)) != -1) { owner.check(); output.write(buffer, 0, count); received += count; }
                        }
                        if (expected >= 0 && received != expected) throw new IOException("下载内容不完整，已保留续传进度");
                        if (total >= 0 && offset + received != total) throw new IOException("下载尚未完成，已保留续传进度");
                        output.getFD().sync();
                    }
                    owner.check();
                    try { Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING); }
                    Files.deleteIfExists(metadata.toPath());
                    return true;
                }
            } finally { owner.untrack(call); }
        }
        throw new IOException("服务器拒绝续传");
    }
}
