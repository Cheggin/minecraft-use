package com.minecraftuse.villager;

import com.minecraftuse.bridge.AnsiStripper;
import com.minecraftuse.bridge.TmuxBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OutputPoller {

    private static final long POLL_INTERVAL_MS = 200;

    private final TmuxBridge bridge;
    private final String paneName;
    private final FloatingText floatingText;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<List<String>> lastLines = new AtomicReference<>(Collections.emptyList());
    private volatile List<String> lastDisplayedLines = Collections.emptyList();
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

                        List<String> allLines = Arrays.asList(stripped.split("\n"));
                        // Filter out empty lines, separator lines, and Claude Code chrome
                        List<String> lines = allLines.stream()
                            .map(String::trim)
                            .filter(line -> !line.isEmpty())
                            .filter(line -> !line.matches("^[─━\\-─\\u2500-\\u257F]{3,}$"))
                            .filter(line -> !line.startsWith("[OMC#"))
                            .filter(line -> !line.contains("bypass permissions"))
                            .filter(line -> !line.contains("shift+tab to cycle"))
                            .filter(line -> !line.contains("Claude Code v"))
                            .filter(line -> !line.contains("Claude Max"))
                            .filter(line -> !line.contains("/remote-control"))
                            .filter(line -> !line.contains("session:"))
                            .filter(line -> !line.contains("context)"))
                            .filter(line -> !line.startsWith("~/"))
                            .filter(line -> !line.contains("claude.ai/code/"))
                            .filter(line -> !line.contains("Code in CLI"))
                            .filter(line -> !line.contains("active ·"))
                            .filter(line -> !line.contains("MCP server"))
                            .filter(line -> !line.contains("mcp server"))
                            .filter(line -> !line.contains("/mcp"))
                            .filter(line -> !line.equals(">"))
                            .filter(line -> !line.equals("❯"))
                            .filter(line -> !line.equals(")"))
                            .filter(line -> line.chars().filter(c -> c > 0x2500).count() < line.length() / 2)
                            .collect(java.util.stream.Collectors.toList());
                        lastLines.set(lines);

                        // For floating text above head: only show the last agent response
                        // Find the last line starting with ● or ⏺ (agent response marker)
                        // Then collect that line and all continuation lines after it
                        List<String> agentLines = new java.util.ArrayList<>();
                        int lastAgentStart = -1;
                        for (int i = lines.size() - 1; i >= 0; i--) {
                            String l = lines.get(i);
                            if (l.startsWith("●") || l.startsWith("⏺")) {
                                lastAgentStart = i;
                                break;
                            }
                            // Stop searching if we hit a user prompt
                            if (l.startsWith("❯") || l.startsWith("> ")) break;
                        }
                        if (lastAgentStart >= 0) {
                            for (int i = lastAgentStart; i < lines.size(); i++) {
                                String l = lines.get(i);
                                // Stop if we hit another user prompt
                                if (i > lastAgentStart && (l.startsWith("❯") || l.startsWith("> "))) break;
                                agentLines.add(l);
                            }
                        }

                        // Show "thinking..." if no agent response yet
                        final List<String> floatingLines;
                        if (agentLines.isEmpty()) {
                            floatingLines = List.of("thinking...");
                        } else {
                            floatingLines = agentLines;
                        }
                        final boolean hasNewResponse = !agentLines.isEmpty() && !agentLines.equals(lastDisplayedLines);
                        lastDisplayedLines = agentLines;
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            client.execute(() -> {
                                floatingText.update(floatingLines);
                                // Play sound when new response arrives
                                if (hasNewResponse && client.player != null) {
                                    client.player.playSound(
                                        SoundEvents.ENTITY_VILLAGER_YES, 0.5f, 1.2f);
                                }
                            });
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
