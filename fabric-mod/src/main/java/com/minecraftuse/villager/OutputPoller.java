package com.minecraftuse.villager;

import com.minecraftuse.bridge.AnsiStripper;
import com.minecraftuse.bridge.TmuxBridge;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OutputPoller {

    private static final long POLL_INTERVAL_MS = 2000;

    private final TmuxBridge bridge;
    private final String paneName;
    private final FloatingText floatingText;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<List<String>> lastLines = new AtomicReference<>(Collections.emptyList());
    private Thread pollThread;

    public OutputPoller(TmuxBridge bridge, String paneName, FloatingText floatingText) {
        this.bridge = bridge;
        this.paneName = paneName;
        this.floatingText = floatingText;
    }

    public void start() {
        if (running.getAndSet(true)) return;

        pollThread = new Thread(() -> {
            String lastRaw = null;
            while (running.get()) {
                try {
                    String raw = bridge.read(paneName).get();
                    String stripped = AnsiStripper.strip(raw);

                    if (!stripped.equals(lastRaw)) {
                        lastRaw = stripped;

                        List<String> lines = Arrays.asList(stripped.split("\n"));
                        lastLines.set(lines);

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            client.execute(() -> floatingText.update(lines));
                        }
                    }

                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Silently continue on read errors
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "OutputPoller-" + paneName);

        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
    }

    public List<String> getLastLines() {
        return lastLines.get();
    }
}
