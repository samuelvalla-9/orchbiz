package com.orchbiz.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Minimal git helpers; return null when git is unavailable. */
final class Git {

    private Git() { }

    static Boolean isIgnored(Path root, Path file) {
        Result r = run(root, "check-ignore", "-q", root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/'));
        if (r == null) return null;
        if (r.exitCode == 0) return Boolean.TRUE;
        if (r.exitCode == 1) return Boolean.FALSE;
        return null;
    }

    static List<String> stagedFiles(Path root) {
        Result r = run(root, "diff", "--cached", "--name-only", "--diff-filter=ACMR");
        if (r == null || r.exitCode != 0) return null;
        List<String> files = new ArrayList<>();
        for (String line : r.stdout.split("\\R")) {
            if (!line.isBlank()) files.add(line.trim());
        }
        return files;
    }

    private record Result(int exitCode, String stdout) { }

    private static Result run(Path root, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-c");
        cmd.add("core.quotepath=off");
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(false).start();
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new Result(p.exitValue(), out);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
