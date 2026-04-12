package com.minecraftuse.gui;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.minecraftuse.villager.VillagerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.File;
import java.util.List;

public class VillagerChatScreen extends Screen {

    private static final int INPUT_HEIGHT = 20;
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_DISPLAY_LINES = 10;
    private static final int BACKGROUND_COLOR = 0xCC000000;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFFFFFF;
    private static final int HEADER_COLOR = 0xFFFFAA00;
    private static final int TICK_REFRESH_INTERVAL = 20;

    private final String villageName;
    private final VillagerRegistry.AgentVillagerData data;

    private TextFieldWidget inputField;
    private int tickCount = 0;
    private List<String> displayLines = List.of();
    private List<String> wrappedLines = List.of();
    private int scrollOffset = 0;

    public VillagerChatScreen(String villageName, VillagerRegistry.AgentVillagerData data) {
        super(Text.literal(villageName));
        this.villageName = villageName;
        this.data = data;
    }

    @Override
    protected void init() {
        inputField = new TextFieldWidget(
            textRenderer,
            PADDING,
            height - INPUT_HEIGHT - PADDING,
            width - PADDING * 2,
            INPUT_HEIGHT,
            Text.literal("Type a message...")
        );
        inputField.setPlaceholder(Text.literal("Type a message..."));
        inputField.setMaxLength(256);
        addDrawableChild(inputField);
        setInitialFocus(inputField);
        inputField.setFocused(true);
        inputField.active = true;
        refreshLines();
    }

    @Override
    public void tick() {
        tickCount++;
        if (tickCount >= TICK_REFRESH_INTERVAL) {
            tickCount = 0;
            refreshLines();
        }
    }

    private void refreshLines() {
        displayLines = data.outputPoller().getLastLines();
        // Re-wrap all lines for scroll calculation
        int maxTextWidth = width - PADDING * 2 - 12;
        List<String> newWrapped = new java.util.ArrayList<>();
        for (String line : displayLines) {
            newWrapped.addAll(wrapText(line, maxTextWidth));
        }
        int maxScroll = Math.max(0, wrappedLines.size() - MAX_DISPLAY_LINES);
        boolean wasAtBottom = wrappedLines.isEmpty() || scrollOffset >= maxScroll;
        wrappedLines = newWrapped;
        // Only auto-scroll to bottom if user was already at bottom
        if (wasAtBottom) {
            scrollOffset = Math.max(0, wrappedLines.size() - MAX_DISPLAY_LINES);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) verticalAmount * 3;
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, wrappedLines.size() - MAX_DISPLAY_LINES)));
        return true;
    }

    private boolean voiceRecording = false;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Backtick (GLFW_KEY_GRAVE_ACCENT = 96) — start voice recording
        if (keyCode == 96 && !voiceRecording) {
            voiceRecording = true;
            inputField.setText("");
            inputField.setPlaceholder(Text.literal("Recording... release ` to stop"));
            // Start recording on sidecar
            Thread startThread = new Thread(() -> {
                try {
                    java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:8765/transcribe/start-recording"))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()
                    );
                } catch (Exception e) {
                    com.minecraftuse.MinecraftUseMod.LOGGER.error("[Voice] Start recording failed: {}", e.getMessage());
                }
            }, "VoiceStart");
            startThread.setDaemon(true);
            startThread.start();
            return true;
        }
        // Enter key = 257, KP Enter = 335
        if (keyCode == 257 || keyCode == 335) {
            String text = inputField.getText().trim();
            if (!text.isEmpty()) {
                sendToPane(text);
                inputField.setText("");
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Backtick released — stop recording and transcribe
        if (keyCode == 96 && voiceRecording) {
            voiceRecording = false;
            inputField.setPlaceholder(Text.literal("Transcribing..."));
            // Stop recording and get transcription
            Thread thread = new Thread(() -> {
                try {
                    var response = java.net.http.HttpClient.newHttpClient().send(
                        java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://localhost:8765/transcribe/stop-recording"))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString()
                    );

                    var result = new com.google.gson.Gson().fromJson(
                        response.body(), com.google.gson.JsonObject.class);
                    if (result.has("text")) {
                        String text = result.get("text").getAsString().trim();
                        if (!text.isEmpty()) {
                            MinecraftClient.getInstance().execute(() -> setInputText(text));
                        } else {
                            MinecraftClient.getInstance().execute(() ->
                                inputField.setPlaceholder(Text.literal("Type a message...")));
                        }
                    }
                } catch (Exception e) {
                    MinecraftClient.getInstance().execute(() -> {
                        inputField.setPlaceholder(Text.literal("Voice error — type instead"));
                    });
                }
            }, "VoiceStop");
            thread.setDaemon(true);
            thread.start();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Block backtick from typing into text field when recording
        if (chr == '`') return true;
        return super.charTyped(chr, modifiers);
    }

    private void sendToPane(String message) {
        String paneName = data.paneName();
        PaneConfig config = PaneConfig.load(new File("."));
        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

        bridge.read(paneName)
            .thenCompose(ignored -> bridge.type(paneName, message))
            .thenCompose(ignored -> bridge.read(paneName))
            .thenCompose(ignored -> bridge.keys(paneName, "Enter"))
            .exceptionally(err -> {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§e[MCUse] §cFailed to send to " + villageName + ": " + err.getMessage()),
                        false
                    );
                }
                return null;
            });
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't render the default dark overlay — keep the game world visible
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Compact chat panel at the bottom of the screen (like Minecraft chat)
        int panelWidth = width - PADDING * 2;
        int panelHeight = (MAX_DISPLAY_LINES * LINE_HEIGHT) + INPUT_HEIGHT + PADDING * 3 + LINE_HEIGHT;
        int panelTop = height - panelHeight - PADDING;
        int panelBottom = height - PADDING;

        // Semi-transparent background — only the bottom panel, not full screen
        context.fill(PADDING - 2, panelTop - 2, PADDING + panelWidth + 2, panelBottom + 2, BORDER_COLOR);
        context.fill(PADDING, panelTop, PADDING + panelWidth, panelBottom, BACKGROUND_COLOR);

        // Header: villager name
        context.drawText(textRenderer, Text.literal(villageName).formatted(net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.BOLD), PADDING + 4, panelTop + 4, HEADER_COLOR, true);

        // Output lines (pre-wrapped, scrollable)
        int lineY = panelTop + LINE_HEIGHT + 8;
        int maxVisibleLines = (panelBottom - INPUT_HEIGHT - PADDING * 2 - lineY) / LINE_HEIGHT;
        int end = Math.min(scrollOffset + maxVisibleLines, wrappedLines.size());
        for (int i = scrollOffset; i < end; i++) {
            String line = wrappedLines.get(i);
            Text formatted = com.minecraftuse.bridge.FormattedText.parse(line);
            context.drawText(textRenderer, formatted, PADDING + 4, lineY, 0xFFFFFF, true);
            lineY += LINE_HEIGHT;
        }

        // Scroll indicator
        if (wrappedLines.size() > maxVisibleLines) {
            String indicator = "▲▼ scroll (" + (scrollOffset + 1) + "/" + wrappedLines.size() + ")";
            int indicatorWidth = textRenderer.getWidth(indicator);
            context.drawText(textRenderer, indicator, PADDING + panelWidth - indicatorWidth - 4, panelTop + 4, 0xFF888888, false);
        }

        // Reposition input field to bottom of panel
        inputField.setY(panelBottom - INPUT_HEIGHT - PADDING);

        // Render widgets (input field)
        super.render(context, mouseX, mouseY, delta);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        while (textRenderer.getWidth(text) > maxWidth && text.length() > 1) {
            int cutIndex = text.length();
            while (cutIndex > 1 && textRenderer.getWidth(text.substring(0, cutIndex)) > maxWidth) {
                cutIndex--;
            }
            // Try to break at a space
            int spaceIndex = text.lastIndexOf(' ', cutIndex);
            if (spaceIndex > 0) {
                cutIndex = spaceIndex;
            }
            lines.add(text.substring(0, cutIndex));
            text = text.substring(cutIndex).trim();
        }
        if (!text.isEmpty()) {
            lines.add(text);
        }
        return lines;
    }

    /** Set text in the input field — used by voice transcription */
    public void setInputText(String text) {
        if (inputField != null) {
            inputField.setPlaceholder(Text.literal("Type a message..."));
            inputField.setText(text);
            inputField.setCursorToEnd(false);
            inputField.setFocused(true);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
