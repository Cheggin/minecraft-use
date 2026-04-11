package com.minecraftuse.bridge;

import com.minecraftuse.MinecraftUseMod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TmuxBridge {

    private static final String TMUX_BRIDGE_PATH = System.getProperty("user.home") + "/.smux/bin/tmux-bridge";
    private static final String HOMEBREW_BIN = "/opt/homebrew/bin";
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 5000;
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    private final String socketPath;

    public TmuxBridge(String socketPath) {
        this.socketPath = socketPath;
    }

    public boolean isAvailable() {
        return new java.io.File(TMUX_BRIDGE_PATH).canExecute();
    }

    /**
     * Read the last lines from the given pane.
     */
    public CompletableFuture<String> read(String pane) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> cmd = new ArrayList<>();
            cmd.add(TMUX_BRIDGE_PATH);
            cmd.add("read");
            cmd.add(pane);
            return runProcess(cmd);
        });
    }

    /**
     * Type text into the given pane (no Enter key).
     */
    public CompletableFuture<String> type(String pane, String text) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> cmd = new ArrayList<>();
            cmd.add(TMUX_BRIDGE_PATH);
            cmd.add("type");
            cmd.add(pane);
            cmd.add(text);
            return runProcess(cmd);
        });
    }

    /**
     * Send key sequences to the given pane.
     */
    public CompletableFuture<String> keys(String pane, String keys) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> cmd = new ArrayList<>();
            cmd.add(TMUX_BRIDGE_PATH);
            cmd.add("keys");
            cmd.add(pane);
            cmd.add(keys);
            return runProcess(cmd);
        });
    }

    /**
     * Send text (with Enter) and wait for output to settle.
     * Sequence: read (guard) -> type -> keys Enter -> poll read until stable.
     */
    public CompletableFuture<String> sendAndWait(String pane, String text) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Snapshot current output (also satisfies read guard)
            String before = runBridgeCommand("read", pane, "50");

            // 2. Type the text
            runBridgeCommand("type", pane, text);

            // 3. Read again to satisfy read guard before keys
            runBridgeCommand("read", pane, "1");

            // 4. Press Enter
            runBridgeCommand("keys", pane, "Enter");

            // 4. Poll until output stabilizes
            int maxPolls = 60;
            int pollIntervalMs = 500;
            String previousRead = "";
            String currentRead = "";
            int stableCount = 0;

            for (int i = 0; i < maxPolls; i++) {
                try {
                    Thread.sleep(pollIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                currentRead = runBridgeCommand("read", pane, "50");
                if (currentRead.equals(previousRead) && !currentRead.equals(before)) {
                    stableCount++;
                    if (stableCount >= 2) {
                        break;
                    }
                } else {
                    stableCount = 0;
                }
                previousRead = currentRead;
            }

            // 6. Return the full current output — callers handle filtering
            return currentRead.trim();
        });
    }

    private String runBridgeCommand(String command, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(TMUX_BRIDGE_PATH);
        cmd.add(command);
        for (String arg : args) {
            cmd.add(arg);
        }
        return runProcess(cmd);
    }

    private String runProcess(List<String> cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        if (socketPath != null && !socketPath.isEmpty()) {
            pb.environment().put("TMUX_BRIDGE_SOCKET", socketPath);
        }
        // Ensure tmux and other homebrew tools are on PATH for the subprocess
        String currentPath = pb.environment().getOrDefault("PATH", "/usr/bin:/bin");
        if (!currentPath.contains(HOMEBREW_BIN)) {
            pb.environment().put("PATH", HOMEBREW_BIN + ":" + currentPath);
        }

        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("tmux-bridge process timed out");
            }
            return AnsiStripper.strip(output.toString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("tmux-bridge error: " + e.getMessage(), e);
        }
    }
}
