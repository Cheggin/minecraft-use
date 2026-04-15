package com.minecraftuse.gui;

import com.minecraftuse.bridge.AnsiToMinecraft;
import com.minecraftuse.bridge.FormattedText;
import com.minecraftuse.villager.VillagerRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard GUI showing all active agents and their status.
 * Open with /agents command.
 */
public class AgentDashboardScreen extends Screen {

    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 50;
    private static final int HEADER_HEIGHT = 30;
    private static final int CARD_PADDING = 8;
    private static final int BG_COLOR = 0xCC101010;
    private static final int CARD_COLOR = 0xFF1A1A2E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int CARD_ACTIVE = 0xFF16213E;
    private static final int NAME_COLOR = 0xFFFFAA00;
    private static final int STATUS_RUNNING = 0xFF55FF55;
    private static final int STATUS_IDLE = 0xFF888888;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int TITLE_COLOR = 0xFFFFFFFF;

    private int scrollOffset = 0;

    public AgentDashboardScreen() {
        super(Text.literal("Agent Dashboard"));
    }

    @Override
    protected void init() {
        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(width - 70, 6, 60, 18)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full screen dark background
        context.fill(0, 0, width, height, BG_COLOR);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("Agent Dashboard"), width / 2, PADDING, TITLE_COLOR);

        VillagerRegistry registry = VillagerRegistry.getInstance();
        List<Map.Entry<String, VillagerRegistry.AgentVillagerData>> agents = new ArrayList<>(registry.getAll());

        if (agents.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("No active agents. Use /agent <name> to spawn one."),
                width / 2, height / 2, STATUS_IDLE);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        int cardWidth = Math.min(width - PADDING * 4, 500);
        int startX = (width - cardWidth) / 2;
        int startY = HEADER_HEIGHT + PADDING;

        for (int i = 0; i < agents.size(); i++) {
            int y = startY + i * (ROW_HEIGHT + PADDING) - scrollOffset;
            if (y + ROW_HEIGHT < HEADER_HEIGHT || y > height) continue;

            Map.Entry<String, VillagerRegistry.AgentVillagerData> entry = agents.get(i);
            String name = entry.getKey();
            VillagerRegistry.AgentVillagerData data = entry.getValue();

            // Card background
            context.fill(startX - 1, y - 1, startX + cardWidth + 1, y + ROW_HEIGHT + 1, CARD_BORDER);
            context.fill(startX, y, startX + cardWidth, y + ROW_HEIGHT, CARD_COLOR);

            // Agent name
            context.drawText(textRenderer,
                Text.literal(name),
                startX + CARD_PADDING, y + CARD_PADDING, NAME_COLOR, true);

            // Mob type
            String mobType = data.villager().getType().getName().getString();
            context.drawText(textRenderer,
                Text.literal(mobType),
                startX + CARD_PADDING + textRenderer.getWidth(name) + 10,
                y + CARD_PADDING, STATUS_IDLE, false);

            // Status: alive or dead
            boolean alive = data.villager().isAlive();
            String statusText = alive ? "Running" : "Dead";
            int statusColor = alive ? STATUS_RUNNING : 0xFFFF5555;
            context.drawText(textRenderer,
                Text.literal(statusText),
                startX + cardWidth - CARD_PADDING - textRenderer.getWidth(statusText),
                y + CARD_PADDING, statusColor, false);

            // Pane name
            context.drawText(textRenderer,
                Text.literal("pane: " + data.paneName()),
                startX + CARD_PADDING, y + CARD_PADDING + 12, STATUS_IDLE, false);

            // Last output line (from poller)
            List<String> lastLines = data.outputPoller().getLastLines();
            if (!lastLines.isEmpty()) {
                String lastLine = lastLines.get(lastLines.size() - 1);
                // Strip formatting for display length check, keep for rendering
                String plain = lastLine.replaceAll("§.", "");
                if (plain.length() > 60) {
                    lastLine = lastLine.substring(0, Math.min(60, lastLine.length())) + "...";
                }
                context.drawText(textRenderer,
                    FormattedText.parse(lastLine),
                    startX + CARD_PADDING, y + CARD_PADDING + 24, TEXT_COLOR, false);
            } else {
                context.drawText(textRenderer,
                    Text.literal("(no output)"),
                    startX + CARD_PADDING, y + CARD_PADDING + 24, STATUS_IDLE, false);
            }
        }

        // Agent count
        context.drawText(textRenderer,
            Text.literal(agents.size() + " agent(s) active"),
            PADDING, height - 14, STATUS_IDLE, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) verticalAmount * 20;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
