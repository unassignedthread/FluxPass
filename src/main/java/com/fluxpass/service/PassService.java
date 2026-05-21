package com.fluxpass.service;

import com.fluxpass.model.PasswordEntry;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PassService {

    private static final String PASS_CMD = "pass";
    private final Path passwordStoreDir;

    public PassService() {
        String dir = System.getenv("PASSWORD_STORE_DIR");
        passwordStoreDir = Paths.get(dir != null ? dir : System.getProperty("user.home") + "/.password-store");
    }

    public boolean isPassAvailable() {
        try {
            Process p = new ProcessBuilder(PASS_CMD, "version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor(5, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public List<PasswordEntry> listEntries() throws IOException {
        List<PasswordEntry> entries = new ArrayList<>();
        File storeDir = passwordStoreDir.toFile();
        if (!storeDir.exists() || !storeDir.isDirectory()) {
            return entries;
        }
        collectGpgFiles(storeDir, "", entries);
        entries.sort(Comparator.comparing(PasswordEntry::getFullPath));
        return entries;
    }

    private void collectGpgFiles(File dir, String prefix, List<PasswordEntry> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".")) continue;
            if (file.isDirectory()) {
                collectGpgFiles(file, prefix + name + "/", result);
            } else if (name.endsWith(".gpg")) {
                String entryName = name.substring(0, name.length() - 4);
                result.add(new PasswordEntry(prefix + entryName));
            }
        }
    }

    public String show(String path) throws PassException {
        return runPass("show", path);
    }

    public String getPassword(String path) throws PassException {
        String output = show(path);
        int newline = output.indexOf('\n');
        return newline >= 0 ? output.substring(0, newline) : output;
    }

    public void insert(String path, String fullContent) throws PassException {
        ProcessBuilder pb = new ProcessBuilder(PASS_CMD, "insert", "-m", "-f", path);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (OutputStream os = p.getOutputStream()) {
                os.write(fullContent.getBytes());
                os.flush();
            }
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                String err = new String(p.getInputStream().readAllBytes());
                throw new PassException("Failed to insert entry: " + err);
            }
        } catch (IOException | InterruptedException e) {
            throw new PassException("Failed to insert entry: " + e.getMessage(), e);
        }
    }

    public String generate(String path, int length, boolean noSymbols) throws PassException {
        List<String> cmd = new ArrayList<>();
        cmd.add(PASS_CMD);
        cmd.add("generate");
        if (noSymbols) cmd.add("--no-symbols");
        cmd.add(path);
        cmd.add(String.valueOf(length));
        return runPass(cmd.toArray(new String[0]));
    }

    public void delete(String path) throws PassException {
        runPass("rm", "-f", path);
    }

    public List<String> find(String query) throws PassException {
        String output = runPass("find", query);
        List<String> results = new ArrayList<>();
        if (output != null && !output.isBlank()) {
            for (String line : output.split("\n")) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty()) {
                    results.add(trimmed);
                }
            }
        }
        return results;
    }

    public Path getStorePath() {
        return passwordStoreDir;
    }

    private String runPass(String... args) throws PassException {
        List<String> cmd = new ArrayList<>();
        cmd.add(PASS_CMD);
        cmd.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            int exitCode = p.waitFor();
            String output = new String(p.getInputStream().readAllBytes()).stripTrailing();
            if (exitCode != 0) {
                throw new PassException(output.isEmpty() ? "pass command failed" : output);
            }
            return output;
        } catch (IOException | InterruptedException e) {
            throw new PassException("Failed to run pass: " + e.getMessage(), e);
        }
    }

    public static class PassException extends Exception {
        public PassException(String message) {
            super(message);
        }
        public PassException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
