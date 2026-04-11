package com.minecraftuse.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PaneConfig {

    private static final String CONFIG_PATH = "config/minecraft-use.json";
    private static final long DEFAULT_RESPONSE_TIMEOUT_MS = 10000;
    private static final long DEFAULT_POLL_INTERVAL_MS = 200;
    private static final Gson GSON = new Gson();

    private final String tmuxSocket;
    private final Map<String, String> panes;
    private final long responseTimeoutMs;
    private final long pollIntervalMs;

    private PaneConfig(String tmuxSocket, Map<String, String> panes,
                       long responseTimeoutMs, long pollIntervalMs) {
        this.tmuxSocket = tmuxSocket;
        this.panes = Collections.unmodifiableMap(panes);
        this.responseTimeoutMs = responseTimeoutMs;
        this.pollIntervalMs = pollIntervalMs;
    }

    public String getTmuxSocket() {
        return tmuxSocket;
    }

    public Map<String, String> getPanes() {
        return panes;
    }

    public String getPane(String name) {
        return panes.get(name);
    }

    public long getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Load config from config/minecraft-use.json relative to the game directory.
     * Falls back to defaults if the file does not exist or cannot be parsed.
     */
    public static PaneConfig load(File gameDir) {
        File configFile = new File(gameDir, CONFIG_PATH);

        if (!configFile.exists()) {
            return defaults();
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                return defaults();
            }

            String socket = resolveSocket(json);
            Map<String, String> panes = parsePanes(json);
            long timeoutMs = getLong(json, "response_timeout_ms", DEFAULT_RESPONSE_TIMEOUT_MS);
            long pollMs = getLong(json, "poll_interval_ms", DEFAULT_POLL_INTERVAL_MS);

            return new PaneConfig(socket, panes, timeoutMs, pollMs);
        } catch (IOException e) {
            return defaults();
        }
    }

    private static String resolveSocket(JsonObject json) {
        if (json.has("tmux_socket") && !json.get("tmux_socket").isJsonNull()) {
            return json.get("tmux_socket").getAsString();
        }
        return autoDetectSocket();
    }

    /**
     * Auto-detect the default tmux socket on macOS at /private/tmp/tmux-{uid}/default.
     */
    static String autoDetectSocket() {
        String uid = getUid();
        if (uid != null) {
            Path candidate = Paths.get("/private/tmp/tmux-" + uid + "/default");
            if (Files.exists(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static String getUid() {
        try {
            Process process = new ProcessBuilder("id", "-u").start();
            byte[] bytes = process.getInputStream().readAllBytes();
            process.waitFor();
            return new String(bytes).trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Map<String, String> parsePanes(JsonObject json) {
        Map<String, String> result = new HashMap<>();
        if (!json.has("panes")) return result;
        JsonElement panesEl = json.get("panes");
        if (!panesEl.isJsonObject()) return result;
        JsonObject panesObj = panesEl.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : panesObj.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getAsString());
        }
        return result;
    }

    private static long getLong(JsonObject json, String key, long defaultValue) {
        if (!json.has(key)) return defaultValue;
        JsonElement el = json.get(key);
        if (el.isJsonNull()) return defaultValue;
        return el.getAsLong();
    }

    private static PaneConfig defaults() {
        return new PaneConfig(autoDetectSocket(), new HashMap<>(),
                DEFAULT_RESPONSE_TIMEOUT_MS, DEFAULT_POLL_INTERVAL_MS);
    }
}
