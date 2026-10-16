package com.vixmobi.auto.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.*;

public class ShellApiTest {
    private Path directory;
    private ShellApi.RootConfig config;
    private ShellApi shell;
    private final List<String> logs = new ArrayList<>();

    @Before public void setUp() throws IOException {
        directory = Files.createTempDirectory("xpauto shell '");
        config = new ShellApi.RootConfig();
        config.candidates.clear(); // 测试不能申请电脑的真实 Root 权限。
        shell = new ShellApi(config, logs::add, () -> false);
    }

    @After public void tearDown() throws IOException {
        shell.close();
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }

    private Path executable(String name, String script) throws IOException {
        Path path = directory.resolve(name);
        Files.write(path, ("#!/bin/sh\n" + script).getBytes(StandardCharsets.UTF_8));
        assertTrue(path.toFile().setExecutable(true));
        return path;
    }

    // 使用受控的 su 模拟程序验证行为，不申请电脑的真实权限。
    private Path grant() throws IOException {
        return executable("custom su", "if [ \"$2\" = 'id -u' ]; then echo 0; exit; fi\n"
                + "PATH=" + ShellApi.quote(directory.toString()) + ":$PATH\nexport PATH\n"
                + "exec /bin/sh -c 'id() { echo 0; }; eval \"$1\"' fake-su \"$2\"\n");
    }

    @Test public void capturesUtf8StderrAndExitCode() {
        assertEquals("你好\n", shell.execCommand("printf '你好\\n'; printf 'denied' >&2; exit 7", false));
        assertEquals(7, shell.getLastResult().code);
        assertEquals("denied", shell.getLastResult().error);
        assertFalse(logs.isEmpty());
        assertEquals("", shell.execCommand("", false));
        assertEquals(0, shell.getLastResult().code);
    }

    @Test public void drainsBothPipesBeyondCaptureLimit() {
        String command = "i=0; while [ $i -lt 20000 ]; do "
                + "printf 'abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789\\n'; "
                + "printf 'abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz0123456789\\n' >&2; "
                + "i=$((i+1)); done";
        shell.execCommand(command, false, 15000);
        AbstractShell.Result result = shell.getLastResult();
        assertEquals(0, result.code);
        assertTrue(result.truncated);
        assertEquals(ShellProcess.OUTPUT_LIMIT, result.result.length());
        assertEquals(ShellProcess.OUTPUT_LIMIT, result.error.length());
    }

    @Test public void timeoutIsBounded() {
        long start = System.nanoTime();
        shell.execCommand("exec sleep 10", false, 100);
        assertTrue(shell.getLastResult().timedOut);
        assertTrue((System.nanoTime() - start) / 1_000_000 < 2500);
    }

    @Test public void cancellationStopsWaitAndCanReset() throws Exception {
        Thread thread = new Thread(() -> {
            try { shell.execCommand("exec sleep 10", false); }
            catch (CancellationException expected) { }
        });
        thread.start();
        for (int i = 0; i < 100 && !shell.isExecuting(); i++) Thread.sleep(10);
        shell.cancel();
        thread.join(2500);
        assertFalse(thread.isAlive());
        assertTrue(shell.getLastResult().cancelled);
        assertThrows(CancellationException.class, () -> shell.execCommand("echo must-not-run", false));
        shell.resetCancellation();
        assertEquals("again", shell.execCommand("printf again", false));
    }

    @Test public void observesLegacyCancellationToken() throws Exception {
        AtomicBoolean stopped = new AtomicBoolean();
        ShellApi legacy = new ShellApi(config, logs::add, stopped::get);
        Thread thread = new Thread(() -> {
            try { legacy.execCommand("exec sleep 10", false); }
            catch (CancellationException expected) { }
        });
        thread.start();
        for (int i = 0; i < 100 && !legacy.isExecuting(); i++) Thread.sleep(10);
        stopped.set(true);
        thread.join(2500);
        assertFalse(thread.isAlive());
        assertTrue(legacy.getLastResult().cancelled);
    }

    @Test public void customPathWithSpacesAndQuotesIsUsedByAllRootCalls() throws Exception {
        assertTrue(shell.addSuBin(grant().toString()));
        assertTrue(shell.su());
        assertEquals("0\n", shell.ROOT("id -u"));
        assertEquals("0\n", shell.sudo("id -u"));
        assertEquals("0\n", shell.execCommand("id -u"));
        assertEquals("ordinary", shell.execCommand("printf ordinary", false));
    }

    @Test public void missingPathFallsBackButDenialDoesNotRetry() throws Exception {
        Path allowed = grant();
        shell.addSuBin(allowed.toString());
        shell.addSuBin(directory.resolve("missing").toString());
        assertTrue(shell.su());
        shell.addSuBin(executable("denied", "echo denied >&2; exit 1\n").toString());
        assertFalse(shell.su());
        assertNull(config.selected);
        assertTrue(shell.getLastResult().error.contains("denied"));
    }

    @Test public void exitZeroWithoutUidZeroIsNotRoot() throws Exception {
        shell.addSuBin(executable("fake", "echo 2000\n").toString());
        assertFalse(shell.su());
        assertEquals(126, shell.getLastResult().code);
        assertEquals("", shell.sudo("echo must-not-run"));
        assertFalse(shell.getLastResult().result.contains("must-not-run"));
    }

    @Test public void revokedRootNeverRunsCommandOrFallsBack() throws Exception {
        Path fake = executable("revoked", "if [ \"$2\" = 'id -u' ]; then echo 0; exit; fi\n"
                + "exec /bin/sh -c 'id() { echo 2000; }; eval \"$1\"' fake-su \"$2\"\n");
        shell.addSuBin(fake.toString());
        assertTrue(shell.su());
        Path marker = directory.resolve("must-not-exist");
        shell.execCommand("touch " + ShellApi.quote(marker.toString()));
        assertFalse(Files.exists(marker));
        assertEquals(126, shell.getLastResult().code);
        assertNull(config.selected);
    }

    @Test public void commandFailureIsNotRetried() throws Exception {
        shell.addSuBin(grant().toString());
        Path marker = directory.resolve("count");
        shell.sudo("echo once >> " + ShellApi.quote(marker.toString()) + "; exit 9");
        assertEquals(Arrays.asList("once"), Files.readAllLines(marker));
        assertEquals(9, shell.getLastResult().code);
    }

    @Test public void packageOperationsUseActualResultsAndQuotedArguments() throws Exception {
        Path apk = directory.resolve("a ' ; echo injected.apk");
        Files.write(apk, "apk bytes".getBytes(StandardCharsets.UTF_8));
        Path captured = directory.resolve("stdin");
        Path calls = directory.resolve("calls");
        executable("stat", "wc -c < \"$3\" | tr -d ' '\n");
        executable("pm", "printf '%s\\n' \"$@\" >> " + ShellApi.quote(calls.toString()) + "\n"
                + "case $1 in install) cat > " + ShellApi.quote(captured.toString()) + "; echo Success;; "
                + "uninstall) echo Failure >&2; exit 1;; path) echo package:/data/app/test/base.apk;; esac\n");
        executable("am", "printf '%s\\n' \"$@\" >> " + ShellApi.quote(calls.toString()) + "\n");
        shell.addSuBin(grant().toString());
        assertTrue(shell.installApp(apk.toString()));
        assertArrayEquals(Files.readAllBytes(apk), Files.readAllBytes(captured));
        assertEquals(Arrays.asList("install", "-r", "--user", "current", "-S", "9", "--", "-"),
                Files.readAllLines(calls));
        assertFalse(shell.uninstallApp("com.example.fixture"));
        assertEquals(1, shell.getLastResult().code);
        assertTrue(shell.stopApp("com.example.fixture"));
        assertTrue(Files.readAllLines(calls).contains("force-stop"));
        assertFalse(shell.stopApp("com.example; echo injected"));
        assertFalse(shell.installApp("relative.apk"));
    }

    @Test public void packageManagerMustReportSuccessAndPackageMustExist() throws Exception {
        executable("pm", "exit 0\n");
        shell.addSuBin(grant().toString());
        assertFalse(shell.uninstallApp("com.example.fixture"));
        assertTrue(shell.getLastResult().error.contains("did not report Success"));
        assertFalse(shell.stopApp("com.example.missing"));
        assertEquals(1, shell.getLastResult().code);
    }

    @Test public void rejectsMalformedSuAndCommandAndTimeout() {
        assertFalse(shell.addSuBin("su -c id"));
        assertFalse(shell.addSuBin("su\nother"));
        assertFalse(shell.addSuBin("../su"));
        shell.execCommand("bad\0command", false);
        assertEquals(-1, shell.getLastResult().code);
        assertThrows(IllegalArgumentException.class, () -> shell.execCommand("id", false, 0));
        assertThrows(IllegalArgumentException.class, () -> shell.su(Long.MAX_VALUE));
    }

    @Test public void lastResultIsASnapshot() {
        shell.execCommand("echo first", false);
        AbstractShell.Result snapshot = shell.getLastResult();
        snapshot.code = 98;
        assertEquals(0, shell.getLastResult().code);
        shell.execCommand("echo second", false);
        assertEquals("first\n", snapshot.result);
    }
}
