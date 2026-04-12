package com.minecraftuse.villager;

import com.minecraftuse.bridge.TmuxBridge;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Output poller for log file monitoring (used by /listen).
 * Reads a log file directly instead of going through tmux pane capture.
 * Falls back to tmux pane reading if no log file is available.
 */
public class LogOutputPoller extends OutputPoller {

    private static final long POLL_INTERVAL_MS = 200;
    private static final int MAX_DISPLAY_LINES = 6;

    private final TmuxBridge bridge;
    private final String paneName;
    private final FloatingText floatingText;
    private final File logFile;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<List<String>> lastLines = new AtomicReference<>(Collections.emptyList());
    private Thread pollThread;

    /** Constructor with a log file to read directly */
    public LogOutputPoller(TmuxBridge bridge, String paneName, FloatingText floatingText, File logFile) {
        super(bridge, paneName, floatingText);
        this.bridge = bridge;
        this.paneName = paneName;
        this.floatingText = floatingText;
        this.logFile = logFile;
    }

    /** Constructor without log file — falls back to tmux pane reading */
    public LogOutputPoller(TmuxBridge bridge, String paneName, FloatingText floatingText) {
        this(bridge, paneName, floatingText, null);
    }

    @Override
    public void start() {
        if (running.getAndSet(true)) return;

        pollThread = new Thread(() -> {
            long lastFileSize = 0;
            String lastContent = null;

            while (running.get()) {
                try {
                    List<String> lines;

                    if (logFile != null && logFile.exists()) {
                        // Direct file reading — fast and reliable
                        long currentSize = logFile.length();
                        if (currentSize != lastFileSize) {
                            lastFileSize = currentSize;
                            lines = readLastLines(logFile, MAX_DISPLAY_LINES);
                            lastLines.set(lines);
                            updateFloatingText(lines);
                        }
                    } else {
                        // Fallback: read from tmux pane
                        String raw = bridge.readWithColor(paneName).get();
                        if (!raw.equals(lastContent)) {
                            lastContent = raw;
                            String[] allLines = raw.split("\n");
                            List<String> nonEmpty = new ArrayList<>();
                            for (String line : allLines) {
                                String trimmed = line.trim();
                                if (!trimmed.isEmpty()) {
                                    nonEmpty.add(trimmed);
                                }
                            }
                            int start = Math.max(0, nonEmpty.size() - MAX_DISPLAY_LINES);
                            lines = nonEmpty.subList(start, nonEmpty.size());
                            lastLines.set(List.copyOf(lines));
                            updateFloatingText(List.copyOf(lines));
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

    private void updateFloatingText(List<String> lines) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            final List<String> finalLines = List.copyOf(lines);
            client.execute(() -> floatingText.update(finalLines));
        }
    }

    /** Read the last N lines from a file efficiently using RandomAccessFile */
    private static List<String> readLastLines(File file, int count) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return Collections.emptyList();

            // Read from the end to find enough newlines
            List<String> lines = new ArrayList<>();
            StringBuilder currentLine = new StringBuilder();
            long pos = fileLength - 1;

            while (pos >= 0 && lines.size() < count) {
                raf.seek(pos);
                int ch = raf.read();
                if (ch == '\n') {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.reverse().toString());
                        currentLine = new StringBuilder();
                    }
                } else {
                    currentLine.append((char) ch);
                }
                pos--;
            }
            // Add the last line (first line of file if we reached the beginning)
            if (currentLine.length() > 0 && lines.size() < count) {
                lines.add(currentLine.reverse().toString());
            }

            Collections.reverse(lines);
            return lines;
        } catch (Exception e) {
            return Collections.emptyList();
        }
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
