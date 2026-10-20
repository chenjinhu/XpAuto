package com.vixmobi.auto.runtime;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import org.junit.*;
import static org.junit.Assert.*;

/** 真正落盘验证定位、源码映射和快照失败保护。 */
public class ModuleFilesTest {
    private Path root;
    @Before public void setup() throws Exception { root = Files.createTempDirectory("xpauto-modules"); }
    @After public void cleanup() throws Exception { ScriptProject.delete(root); }
    private void write(String path, String text) throws Exception {
        Path file=root.resolve(path); Files.createDirectories(file.getParent()); Files.write(file,text.getBytes(StandardCharsets.UTF_8));
    }
    @Test public void resolvesFilesPackagesAndEncodedSourceUrls() throws Exception {
        write("lib/中文 a.js", "module.exports=1");
        write("node_modules/pkg/package.json", "{\"main\":\"src/index.cjs\"}");
        write("node_modules/pkg/src/index.cjs", "exports.ok=true;");
        write("json/index.json", "{\"value\":2}");
        ModuleFiles loader=new ModuleFiles(root.toFile());
        loader.configure(root.toFile(),"main.js","file:///Users/test/my%20project/");
        JsonObject info=JsonParser.parseString(loader.resolveJson("./lib/中文 a",root.resolve("main.js").toString())).getAsJsonObject();
        assertEquals("file:///Users/test/my%20project/lib/%E4%B8%AD%E6%96%87%20a.js",info.get("url").getAsString());
        assertFalse(info.has("source"));
        assertEquals("module.exports=1",JsonParser.parseString(loader.readJson(info.get("filename").getAsString())).getAsJsonObject().get("source").getAsString());
        assertEquals(root.resolve("node_modules/pkg/src/index.cjs").toFile().getCanonicalFile(),loader.resolve("pkg",root.resolve("lib/main.js").toFile()));
        assertEquals(root.resolve("json/index.json").toFile().getCanonicalFile(),loader.resolve("./json",root.resolve("main.js").toFile()));
        assertTrue(loader.resolveJson("node:fs",root.resolve("main.js").toString()).contains("MODULE_NOT_FOUND"));
    }
    private JsonObject payload() {
        JsonObject project=new JsonObject(),files=new JsonObject();
        project.addProperty("entry","main.js"); project.addProperty("sourceRoot","file:///desktop/");
        files.addProperty("main.js", Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)));
        project.add("files",files); return project;
    }
    @Test public void installsBinarySnapshotAndRejectsTraversalWithoutDamagingPrevious() throws Exception {
        JsonObject request=payload();
        byte[] bytes={0,-1,127}; request.getAsJsonObject("files").addProperty("assets/a.bin",Base64.getEncoder().encodeToString(bytes));
        JsonObject installed=ScriptProject.install(root.toFile(),request.toString());
        Path saved=Paths.get(installed.get("directory").getAsString());
        assertArrayEquals(bytes,Files.readAllBytes(saved.resolve("assets/a.bin")));
        request.getAsJsonObject("files").addProperty("../escape", "AA==");
        assertThrows(IOException.class,()->ScriptProject.install(root.toFile(),request.toString()));
        assertTrue(Files.exists(saved.resolve("main.js")));
        try (java.util.stream.Stream<Path> files=Files.list(root)) { assertEquals(1,files.count()); }
    }
    @Test public void collidingPathsRollBackNewDirectory() throws Exception {
        JsonObject request=payload(); request.getAsJsonObject("files").addProperty("main.js/child", "AA==");
        assertThrows(IOException.class,()->ScriptProject.install(root.toFile(),request.toString()));
        try (java.util.stream.Stream<Path> files=Files.list(root)) { assertEquals(0,files.count()); }
    }
    @Test public void deleteLinesPreservesOriginalDelimiters() {
        assertEquals("a\r\nc",com.vixmobi.auto.utils.ExactFiles.removeLines("a\r\nb\rc",1,null));
        assertEquals("c",com.vixmobi.auto.utils.ExactFiles.removeLines("a\r\nb\rc",0,"b"));
    }
}
