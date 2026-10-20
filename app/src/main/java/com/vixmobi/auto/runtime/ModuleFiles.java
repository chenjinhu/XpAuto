package com.vixmobi.auto.runtime;

import com.google.gson.*;
import com.vixmobi.auto.utils.ExactFiles;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** CommonJS 的文件定位与读取；Java 不执行 JS，模块缓存及循环依赖由 modules.js 管理。 */
public final class ModuleFiles {
    private File root, entry;
    private URI sourceRoot;
    public ModuleFiles(File root) { configure(root, "main.js", root.toURI().toString()); }
    public void configure(File root, String entry, String sourceRoot) {
        try {
            this.root = root.getCanonicalFile();
            this.entry = new File(this.root, entry).getCanonicalFile();
            this.sourceRoot = URI.create(sourceRoot.endsWith("/") ? sourceRoot : sourceRoot + "/");
        } catch (IOException error) { throw new IllegalArgumentException(error); }
    }
    public String entryJson() { return describe(entry, false).toString(); }
    public String resolveJson(String request, String parent) {
        try {
            File file = resolve(request, new File(parent));
            return describe(file, false).toString();
        } catch (Exception error) {
            JsonObject result = new JsonObject();
            result.addProperty("error", error.getMessage());
            result.addProperty("code", error instanceof FileNotFoundException ? "MODULE_NOT_FOUND" : "MODULE_READ_FAILED");
            return result.toString();
        }
    }
    /** 定位与读取分离；命中 JS 缓存时无需再次读取文件。 */
    public String readJson(String filename) {
        try { return describe(new File(filename), true).toString(); }
        catch (Exception error) {
            JsonObject result = new JsonObject();
            result.addProperty("error", error.getMessage()); result.addProperty("code", "MODULE_READ_FAILED");
            return result.toString();
        }
    }
    private JsonObject describe(File file, boolean read) {
        try {
            String url = file.toURI().toString();
            if (file.toPath().startsWith(root.toPath())) {
                String relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
                // 保留 file:///，并把非 ASCII 字符编码；必须与 VS Code pathToFileURL 完全一致。
                url = sourceRoot.toASCIIString() + new URI(null, null, relative, null).toASCIIString();
            }
            JsonObject result = new JsonObject();
            result.addProperty("filename", file.getPath());
            result.addProperty("dirname", file.getParent());
            result.addProperty("url", url);
            result.addProperty("json", file.getName().endsWith(".json"));
            if (read) {
                if (file.length() > 16 * 1024 * 1024) throw new IOException("单个模块不能超过 16 MiB");
                result.addProperty("source", ExactFiles.read(file.getPath()));
            }
            return result;
        } catch (Exception error) { throw new IllegalArgumentException(error.getMessage(), error); }
    }
    File resolve(String name, File parent) throws IOException {
        if (name == null || name.isEmpty() || name.indexOf('\0') >= 0) throw new IOException("模块名称不能为空或包含 NUL");
        if (name.startsWith("node:") || name.contains("://")) throw new FileNotFoundException("不支持 Node 内置模块或 URL：" + name);
        File result = null;
        if (new File(name).isAbsolute() || name.startsWith("./") || name.startsWith("../")) {
            File candidate = new File(name).isAbsolute() ? new File(name) : new File(parent.getParentFile(), name);
            result = asFileOrDirectory(candidate, new HashSet<>());
        } else {
            // npm 目录按调用者位置向上寻找；仍只提供 CommonJS，不提供 Node 系统 API。
            for (File dir = parent.getParentFile(); dir != null && result == null; dir = dir.getParentFile()) {
                result = asFileOrDirectory(new File(new File(dir, "node_modules"), name), new HashSet<>());
                if (dir.equals(root)) break;
            }
            if (result == null) result = asFileOrDirectory(new File(root, name), new HashSet<>());
        }
        if (result == null) throw new FileNotFoundException("找不到模块 " + name + "，调用文件：" + parent);
        return result.getCanonicalFile();
    }
    private File asFileOrDirectory(File file, Set<String> seen) throws IOException {
        if (!seen.add(file.getCanonicalPath()) || seen.size() > 32) return null;
        for (String extension : new String[]{"", ".js", ".cjs", ".json"}) {
            File candidate = new File(file.getPath() + extension);
            if (candidate.isFile()) return candidate;
        }
        if (!file.isDirectory()) return null;
        File config = new File(file, "package.json");
        if (config.isFile()) {
            if (config.length() > 1024 * 1024) throw new IOException("package.json 超过 1 MiB");
            JsonObject pkg = JsonParser.parseString(ExactFiles.read(config.getPath())).getAsJsonObject();
            if (pkg.has("main") && pkg.get("main").isJsonPrimitive()) {
                File main = asFileOrDirectory(new File(file, pkg.get("main").getAsString()), seen);
                if (main != null) return main;
            }
        }
        for (String name : new String[]{"index.js", "index.cjs", "index.json"}) {
            File candidate = new File(file, name);
            if (candidate.isFile()) return candidate;
        }
        return null;
    }
}
