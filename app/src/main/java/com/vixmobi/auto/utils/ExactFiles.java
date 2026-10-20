package com.vixmobi.auto.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** 文本与字节读写共用实现，保留换行；覆盖写入成功后才替换目标文件。 */
public final class ExactFiles {
    private ExactFiles() { }
    public static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
    /** 删除指定的零基行或包含文字的行，其余行的原始换行符保持不变。 */
    public static String removeLines(String content, int line, String contains) {
        StringBuilder result = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([^\\r\\n]*)(\\r\\n|\\r|\\n|$)").matcher(content);
        int index = 0;
        while (matcher.find() && matcher.start() < content.length()) {
            if (index != line && (contains == null || !matcher.group(1).contains(contains))) result.append(matcher.group());
            index++;
        }
        return result.toString();
    }
    public static void write(String path, byte[] bytes, boolean append) throws IOException {
        Path target = Paths.get(path).toAbsolutePath();
        Files.createDirectories(target.getParent());
        if (append) {
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return;
        }
        // 跟随已有符号链接，避免覆盖链接本身；临时文件和目标位于同一文件系统。
        if (Files.isSymbolicLink(target)) target = target.toRealPath();
        Path temporary;
        try {
            // 与普通创建文件相同：0666 再由系统 umask 收紧，避免临时文件默认 0600 改变新文件语义。
            temporary = Files.createTempFile(target.getParent(), ".xpauto-write-", ".tmp",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw-rw-")));
        } catch (UnsupportedOperationException ignored) {
            temporary = Files.createTempFile(target.getParent(), ".xpauto-write-", ".tmp");
        }
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            if (Files.isRegularFile(target)) {
                try { Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(target)); }
                catch (UnsupportedOperationException ignored) { /* 共享存储等文件系统不一定支持 POSIX 权限。 */ }
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
