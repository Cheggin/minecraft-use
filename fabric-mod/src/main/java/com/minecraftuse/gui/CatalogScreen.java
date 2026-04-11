package com.minecraftuse.gui;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.catalog.CatalogIndex;
import com.minecraftuse.catalog.SchematicEntry;
import com.minecraftuse.catalog.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.util.List;

public class CatalogScreen extends Screen {

    private static final int DETAIL_WIDTH = 200;
    private static final int HEADER_HEIGHT = 36;
    private static final int FOOTER_HEIGHT = 36;

    private TextFieldWidget searchField;
    private SchematicListWidget listWidget;
    private ButtonWidget placeButton;
    private ButtonWidget closeButton;

    private final CatalogIndex catalogIndex = new CatalogIndex();
    private ThumbnailManager thumbnailManager;
    private SchematicEntry selectedEntry;
    private boolean loading = true;

    public CatalogScreen() {
        super(Text.literal("Schematic Catalog"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();
        File thumbnailDir = new File(mc.runDirectory, "minecraftuse/thumbnails");
        thumbnailManager = new ThumbnailManager(thumbnailDir);

        int listWidth = width - DETAIL_WIDTH - 20;
        int listTop = HEADER_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;

        // Search field
        searchField = new TextFieldWidget(
            textRenderer,
            10, 8,
            listWidth - 10, 20,
            Text.literal("Search schematics...")
        );
        searchField.setPlaceholder(Text.literal("Search schematics..."));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        // List widget
        listWidget = new SchematicListWidget(mc, listWidth, listBottom - listTop, listTop, listBottom, thumbnailManager);
        listWidget.setX(10);
        listWidget.setSelectionListener(entry -> {
            selectedEntry = entry;
            placeButton.active = true;
        });
        addDrawableChild(listWidget);

        // Place button
        placeButton = ButtonWidget.builder(Text.literal("Place"), b -> onPlaceClicked())
            .dimensions(width - DETAIL_WIDTH - 5, height - FOOTER_HEIGHT + 6, 90, 20)
            .build();
        placeButton.active = false;
        addDrawableChild(placeButton);

        // Close button
        closeButton = ButtonWidget.builder(Text.literal("Close"), b -> close())
            .dimensions(width - 95, height - FOOTER_HEIGHT + 6, 90, 20)
            .build();
        addDrawableChild(closeButton);

        // Fetch from Convex
        loading = true;
        catalogIndex.refresh().thenAccept(entries -> {
            MinecraftClient.getInstance().execute(() -> {
                loading = false;
                refreshList();
            });
        });
    }

    private void onSearchChanged(String query) {
        refreshList();
    }

    private void refreshList() {
        if (loading) return;

        String query = searchField != null ? searchField.getText() : "";
        List<SchematicEntry> results = catalogIndex.search(query);
        listWidget.setEntries(results);
        selectedEntry = null;
        if (placeButton != null) placeButton.active = false;
    }

    private void onPlaceClicked() {
        if (selectedEntry == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        close();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendMessage(
                    Text.literal("§e[MCUse] §7Placement from Convex not yet implemented. Use /build <name> for local files."),
                    false
                );
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        if (loading) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Loading from Convex..."),
                width / 2, height / 2,
                0xFFFFAA
            );
        }

        renderDetailPanel(context);
    }

    private void renderDetailPanel(DrawContext context) {
        int panelX = width - DETAIL_WIDTH - 5;
        int panelY = HEADER_HEIGHT;
        int panelH = height - HEADER_HEIGHT - FOOTER_HEIGHT;

        context.fill(panelX, panelY, panelX + DETAIL_WIDTH, panelY + panelH, 0x88000000);

        if (selectedEntry == null) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal("Select a schematic"),
                panelX + DETAIL_WIDTH / 2,
                panelY + panelH / 2,
                0x888888
            );
            return;
        }

        int tx = panelX + 8;
        int ty = panelY + 8;

        context.drawText(textRenderer, selectedEntry.name(), tx, ty, 0xFFFFFF, true);
        ty += 14;

        if (!selectedEntry.author().isEmpty()) {
            context.drawText(textRenderer, "by " + selectedEntry.author(), tx, ty, 0xAAAAAA, false);
            ty += 12;
        }

        context.drawText(textRenderer, "Category: " + selectedEntry.category(), tx, ty, 0xFFFFFF, false);
        ty += 12;

        context.drawText(textRenderer, "File: " + selectedEntry.file(), tx, ty, 0xAAAAAA, false);
        ty += 12;

        if (!selectedEntry.tags().isEmpty()) {
            context.drawText(textRenderer, "Tags: " + String.join(", ", selectedEntry.tags()), tx, ty, 0xAAAAAA, false);
        }
    }

    @Override
    public void removed() {
        if (thumbnailManager != null) {
            thumbnailManager.dispose();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
