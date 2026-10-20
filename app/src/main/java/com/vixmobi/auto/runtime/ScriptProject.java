package com.vixmobi.auto.runtime;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 将开发者工具的项目快照写入独立目录；校验失败不会替换上一份可运行项目。 */
public final class ScriptProject {
    private ScriptProject() { }
    public static JsonObject install(File parent, String payload) throws IOException {
        JsonObject project = JsonParser.parseString(payload).getAsJsonObject();
        String entry = relative(project.get("entry").getAsString());
        String sourceRoot = project.get("sourceRoot").getAsString();
        if (!sourceRoot.startsWith("file:/") || sourceRoot.indexOf('\n') >= 0 || sourceRoot.indexOf('\r') >= 0)
            throw new IOException("项目源码根地址必须为 file URL");
        String mode = project.has("nodeMode") ? project.get("nodeMode").getAsString() : "xpauto";
        if (!mode.equals("xpauto") && !mode.equals("easyclick")) throw new IOException("未知节点兼容模式");
        JsonObject files = project.getAsJsonObject("files");
        if (files.size() > 512 || !files.has(entry)) throw new IOException("项目最多 512 个文件，且必须包含入口");
        Map<String, byte[]> decoded = new LinkedHashMap<>();
        long total = 0;
        for (Map.Entry<String, JsonElement> item : files.entrySet()) {
            String path = relative(item.getKey());
            String encoded = item.getValue().getAsString();
            if (encoded.length() > 22369624) throw new IOException("单文件最多 16 MiB");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > 16 * 1024 * 1024 || (total += bytes.length) > 32 * 1024 * 1024)
                throw new IOException("单文件最多 16 MiB，项目最多 32 MiB");
            decoded.put(path, bytes);
        }
        Files.createDirectories(parent.toPath());
        Path root = Files.createTempDirectory(parent.toPath(), "project-");
        try {
            for (Map.Entry<String, byte[]> item : decoded.entrySet()) {
                Path file = root.resolve(item.getKey());
                Files.createDirectories(file.getParent());
                Files.write(file, item.getValue());
            }
            JsonObject result = new JsonObject();
            result.addProperty("directory", root.toString());
            result.addProperty("entry", entry);
            result.addProperty("sourceRoot", sourceRoot);
            result.addProperty("nodeMode", mode);
            return result;
        } catch (Exception error) {
            delete(root);
            throw new IOException("项目同步失败：" + error.getMessage(), error);
        }
    }
    static String relative(String path) throws IOException {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.indexOf('\0') >= 0)
            throw new IOException("无效项目路径：" + path);
        for (String part : path.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals(".."))
            throw new IOException("项目路径不能越界：" + path);
        return path;
    }
    public static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(path);
        }
    }
}
