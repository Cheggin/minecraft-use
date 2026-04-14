package com.minecraftuse.villager;

import com.minecraftuse.bridge.AnsiToMinecraft;
import com.minecraftuse.bridge.TmuxBridge;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simplified output poller for log monitoring (used by /listen).
 * Unlike OutputPoller, this does NOT filter Claude Code chrome or detect agent responses.
 * It simply shows the last N non-empty lines of tmux pane output as floating text.
 */
public class LogOutputPoller extends OutputPoller {

    private static final long POLL_INTERVAL_MS = 500;
    private static final int MAX_DISPLAY_LINES = 6;

    private final TmuxBridge bridge;
    private final String paneName;
    private final FloatingText floatingText;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<List<String>> lastLines = new AtomicReference<>(Collections.emptyList());
    private Thread pollThread;

    public LogOutputPoller(TmuxBridge bridge, String paneName, FloatingText floatingText) {
        super(bridge, paneName, floatingText);
        this.bridge = bridge;
        this.paneName = paneName;
        this.floatingText = floatingText;
    }

    @Override
    public void start() {
        if (running.getAndSet(true)) return;

        pollThread = new Thread(() -> {
            String lastRaw = null;
            while (running.get()) {
                try {
                    String raw = bridge.readWithColor(paneName).get();
                    String converted = AnsiToMinecraft.convert(raw);

                    if (!converted.equals(lastRaw)) {
                        lastRaw = converted;

                        // Collect non-empty lines
                        List<String> nonEmpty = new ArrayList<>();
                        for (String line : converted.split("\n")) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty()) {
                                nonEmpty.add(trimmed);
                            }
                        }

                        lastLines.set(List.copyOf(nonEmpty));

                        // Take last N lines for floating text display
                        int start = Math.max(0, nonEmpty.size() - MAX_DISPLAY_LINES);
                        List<String> displayLines = List.copyOf(nonEmpty.subList(start, nonEmpty.size()));

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            client.execute(() -> floatingText.update(displayLines));
                        }
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "LogOutputPoller-" + paneName);

        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    @Override
    public List<String> getLastLines() {
        return lastLines.get();
    }
}
