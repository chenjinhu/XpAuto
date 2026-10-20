package com.vixmobi.auto.utils;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ExactFilesTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void preservesBytesAndTruncatesReplacement() throws Exception {
        Path file = folder.getRoot().toPath().resolve("子目录/script.js");
        for (String content : new String[]{"中文\r\n第二行\r第三行", "末尾无换行", "x", "", "\ufeff内容\n"}) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            ExactFiles.write(file.toString(), bytes, false);
            assertArrayEquals(bytes, Files.readAllBytes(file));
            assertEquals(content, ExactFiles.read(file.toString()));
        }
        ExactFiles.write(file.toString(), new byte[]{0, -1, 13, 10}, false);
        ExactFiles.write(file.toString(), new byte[]{42}, true);
        assertArrayEquals(new byte[]{0, -1, 13, 10, 42}, Files.readAllBytes(file));
    }
    @Test public void missingFileAndFailedWritePreserveDestination() throws Exception {
        Path target = folder.getRoot().toPath().resolve("directory");
        try { ExactFiles.read(target.toString()); fail(); } catch (java.io.IOException expected) { }
        Files.createDirectories(target);
        Files.write(target.resolve("keep"), new byte[]{1});
        try { ExactFiles.write(target.toString(), new byte[]{2}, false); fail(); } catch (java.io.IOException expected) { }
        assertTrue(Files.exists(target.resolve("keep")));
        try (java.util.stream.Stream<Path> files = Files.list(folder.getRoot().toPath())) { assertEquals(1, files.count()); }
    }
    @Test public void preservesExistingPermissionsAndUsesNormalCreationMode() throws Exception {
        Path ordinary=folder.getRoot().toPath().resolve("ordinary"), target=folder.getRoot().toPath().resolve("target");
        Files.write(ordinary,new byte[]{1});
        ExactFiles.write(target.toString(),new byte[]{2},false);
        assertEquals(Files.getPosixFilePermissions(ordinary),Files.getPosixFilePermissions(target));
        java.util.Set<java.nio.file.attribute.PosixFilePermission> privateMode=
            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(target,privateMode);
        ExactFiles.write(target.toString(),new byte[]{3},false);
        assertEquals(privateMode,Files.getPosixFilePermissions(target));
    }
}
