package com.minecraftuse.villager;

import com.minecraftuse.bridge.AnsiToMinecraft;
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
    private volatile boolean soundPlayedForCurrentResponse = false;
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
                    String raw = bridge.readWithColor(paneName).get();
                    String stripped = AnsiToMinecraft.convert(raw);

                    if (!stripped.equals(lastRaw)) {
                        lastRaw = stripped;

                        List<String> allLines = Arrays.asList(stripped.split("\n"));
                        // Filter using plain text (strip § codes) but keep formatted lines
                        List<String> lines = allLines.stream()
                            .map(String::trim)
                            .filter(line -> !isChromeLine(line))
                            .collect(java.util.stream.Collectors.toList());
                        lastLines.set(lines);

                        // For floating text above head: only show the last agent response
                        // Use plain text (strip §) for detection, keep formatted for display
                        List<String> agentLines = new java.util.ArrayList<>();
                        int lastAgentStart = -1;
                        // Search ALL lines for the last agent marker — don't stop at ❯
                        for (int i = lines.size() - 1; i >= 0; i--) {
                            String plain = stripFormatting(lines.get(i));
                            if (plain.startsWith("●") || plain.startsWith("⏺")) {
                                lastAgentStart = i;
                                break;
                            }
                        }
                        if (lastAgentStart >= 0) {
                            for (int i = lastAgentStart; i < lines.size(); i++) {
                                String plain = stripFormatting(lines.get(i));
                                if (i > lastAgentStart && (plain.startsWith("❯") || plain.startsWith("> "))) break;
                                agentLines.add(lines.get(i));
                            }
                        }

                        // Show "thinking..." only if the LAST non-empty line is a user prompt
                        // (meaning user just asked something and agent hasn't responded yet)
                        boolean lastLineIsUserPrompt = false;
                        for (int i = lines.size() - 1; i >= 0; i--) {
                            String p = stripFormatting(lines.get(i));
                            if (!p.isEmpty()) {
                                lastLineIsUserPrompt = p.startsWith("❯ ") && p.length() > 2;
                                break;
                            }
                        }
                        final List<String> floatingLines;
                        if (agentLines.isEmpty() && lastLineIsUserPrompt) {
                            floatingLines = List.of("thinking...");
                        } else if (agentLines.isEmpty() && !lastDisplayedLines.isEmpty()) {
                            floatingLines = lastDisplayedLines;
                        } else if (agentLines.isEmpty()) {
                            floatingLines = Collections.emptyList();
                        } else {
                            floatingLines = agentLines;
                        }
                        // Play sound only on transition from empty/thinking to having a response
                        boolean wasEmpty = lastDisplayedLines.isEmpty();
                        boolean nowHasContent = !agentLines.isEmpty();
                        final boolean shouldPlaySound = wasEmpty && nowHasContent && !soundPlayedForCurrentResponse;

                        // Only update lastDisplayedLines when we have actual new content
                        if (!agentLines.isEmpty()) {
                            lastDisplayedLines = agentLines;
                        }

                        // Reset when user sends a new message (prompt with text is last line)
                        if (lastLineIsUserPrompt) {
                            lastDisplayedLines = Collections.emptyList();
                            soundPlayedForCurrentResponse = false;
                        }

                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client != null) {
                            client.execute(() -> {
                                floatingText.update(floatingLines);
                                if (shouldPlaySound && client.player != null) {
                                    client.player.playSound(
                                        SoundEvents.ENTITY_VILLAGER_YES, 0.5f, 1.2f);
                                }
                            });
                            if (shouldPlaySound) {
                                soundPlayedForCurrentResponse = true;
                            }
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

    /** Strip § formatting codes to get plain text for content checks */
    private static String stripFormatting(String input) {
        return input.replaceAll("§.", "");
    }

    /** Check if a line is Claude Code chrome that should be filtered out */
    private static boolean isChromeLine(String line) {
        String plain = stripFormatting(line);
        if (plain.isEmpty()) return true;
        if (plain.matches("^[─━\\-\\u2500-\\u257F]{3,}$")) return true;
        if (plain.startsWith("[OMC#")) return true;
        if (plain.contains("bypass permissions")) return true;
        if (plain.contains("shift+tab to cycle")) return true;
        if (plain.contains("Claude Code v")) return true;
        if (plain.contains("Claude Max")) return true;
        if (plain.contains("/remote-control")) return true;
        if (plain.contains("session:")) return true;
        if (plain.contains("context)")) return true;
        if (plain.startsWith("~/")) return true;
        if (plain.contains("claude.ai/code/")) return true;
        if (plain.contains("Code in CLI")) return true;
        if (plain.contains("active ·")) return true;
        if (plain.contains("MCP server")) return true;
        if (plain.contains("mcp server")) return true;
        if (plain.contains("/mcp")) return true;
        if (plain.equals(">") || plain.equals("❯") || plain.equals(")")) return true;
        long boxChars = plain.chars().filter(c -> c >= 0x2500 && c <= 0x257F).count();
        if (boxChars > 0 && boxChars >= plain.length() / 2) return true;
        return false;
    }
}
