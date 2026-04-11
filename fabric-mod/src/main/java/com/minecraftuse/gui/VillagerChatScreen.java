package com.minecraftuse.gui;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.minecraftuse.villager.VillagerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
        List<String> lines = data.outputPoller().getLastLines();
        int start = Math.max(0, lines.size() - MAX_DISPLAY_LINES);
        displayLines = lines.subList(start, lines.size());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

    private void sendToPane(String message) {
        String paneName = data.paneName();
        PaneConfig config = PaneConfig.load(new File("."));
        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

        bridge.type(paneName, message)
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Semi-transparent background panel
        int panelWidth = width - PADDING * 2;
        int outputAreaBottom = height - INPUT_HEIGHT - PADDING * 2;
        int outputAreaTop = PADDING + LINE_HEIGHT + 4;
        int outputAreaHeight = outputAreaBottom - outputAreaTop;

        context.fill(PADDING - 2, PADDING - 2, PADDING + panelWidth + 2, outputAreaBottom + 2, BORDER_COLOR);
        context.fill(PADDING, PADDING, PADDING + panelWidth, outputAreaBottom, BACKGROUND_COLOR);

        // Header: villager name
        context.drawText(textRenderer, "§l" + villageName, PADDING + 4, PADDING + 2, HEADER_COLOR, true);

        // Output lines
        int lineY = outputAreaTop + 4;
        int maxLines = outputAreaHeight / LINE_HEIGHT;
        int start = Math.max(0, displayLines.size() - maxLines);
        List<String> visible = displayLines.subList(start, displayLines.size());

        for (String line : visible) {
            context.drawText(textRenderer, line, PADDING + 4, lineY, TEXT_COLOR, false);
            lineY += LINE_HEIGHT;
        }

        // Render widgets (input field)
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
