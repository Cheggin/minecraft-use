package com.minecraftuse.gui;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.catalog.CatalogIndex;
import com.minecraftuse.catalog.SchematicEntry;
import com.minecraftuse.catalog.ThumbnailManager;
import com.minecraftuse.schematic.SchematicParser;
import com.minecraftuse.schematic.SchematicPlacer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen catalog browser: search bar, category filters, scrollable list, detail panel, action buttons.
 * Open with the K keybind.
 */
public class CatalogScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int DETAIL_WIDTH = 200;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 36;
    private static final int CATEGORY_BAR_HEIGHT = 24;

    private static final String[] CATEGORIES = {"All", "Buildings", "Redstone", "Farms", "Decorations", "Misc"};

    private static final String CATALOG_FILE = "minecraftuse/catalog_index.json";
    private static final String THUMBNAIL_DIR = "minecraftuse/thumbnails";

    private TextFieldWidget searchField;
    private SchematicListWidget listWidget;
    private ButtonWidget placeButton;
    private ButtonWidget closeButton;
    private List<ButtonWidget> categoryButtons = new ArrayList<>();

    private CatalogIndex catalogIndex;
    private ThumbnailManager thumbnailManager;
    private SchematicEntry selectedEntry;
    private String activeCategory = "All";

    public CatalogScreen() {
        super(Text.literal("Schematic Catalog"));
    }

    @Override
    protected void init() {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Load catalog index
        File gameDir = mc.runDirectory;
        File catalogFile = new File(gameDir, CATALOG_FILE);
        File thumbnailDir = new File(gameDir, THUMBNAIL_DIR);
        thumbnailManager = new ThumbnailManager(thumbnailDir);

        if (catalogFile.exists()) {
            try {
                catalogIndex = CatalogIndex.load(catalogFile);
            } catch (IOException e) {
                MinecraftUseMod.LOGGER.error("[CatalogScreen] Failed to load catalog: {}", e.getMessage());
                catalogIndex = new CatalogIndex(List.of());
            }
        } else {
            MinecraftUseMod.LOGGER.warn("[CatalogScreen] No catalog file found at {}", catalogFile.getAbsolutePath());
            catalogIndex = new CatalogIndex(List.of());
        }

        int listWidth = width - DETAIL_WIDTH - 20;
        int listTop = HEADER_HEIGHT + CATEGORY_BAR_HEIGHT;
        int listBottom = height - FOOTER_HEIGHT;

        // Search field
        searchField = new TextFieldWidget(
            textRenderer,
            10, 10,
            listWidth - 10, 20,
            Text.literal("Search schematics...")
        );
        searchField.setPlaceholder(Text.literal("Search schematics..."));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        // Category filter buttons
        int catX = 10;
        for (String category : CATEGORIES) {
            final String cat = category;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(category), b -> {
                activeCategory = cat;
                refreshList();
                updateCategoryButtons();
            })
            .dimensions(catX, HEADER_HEIGHT + 2, 60, 18)
            .build();
            categoryButtons.add(btn);
            addDrawableChild(btn);
            // catX increments but buttons are positioned absolutely, re-position:
        }
        // Reposition category buttons evenly
        repositionCategoryButtons();

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

        refreshList();
    }

    private void repositionCategoryButtons() {
        int catX = 10;
        int btnWidth = 62;
        for (ButtonWidget btn : categoryButtons) {
            btn.setX(catX);
            btn.setY(HEADER_HEIGHT + 2);
            catX += btnWidth + 4;
        }
    }

    private void updateCategoryButtons() {
        for (int i = 0; i < categoryButtons.size(); i++) {
            ButtonWidget btn = categoryButtons.get(i);
            boolean active = CATEGORIES[i].equals(activeCategory);
            // Active category gets a slightly different message (bold would require Text.of with formatting)
            btn.setMessage(active
                ? Text.literal("§f§l" + CATEGORIES[i])
                : Text.literal(CATEGORIES[i])
            );
        }
    }

    private void onSearchChanged(String query) {
        refreshList();
    }

    private void refreshList() {
        if (catalogIndex == null) return;

        String query = searchField != null ? searchField.getText() : "";
        List<SchematicEntry> results;

        if ("All".equals(activeCategory)) {
            results = catalogIndex.search(query);
        } else {
            List<SchematicEntry> byCategory = catalogIndex.filterByCategory(activeCategory);
            if (!query.isBlank()) {
                // Apply fuzzy search within the category filter
                CatalogIndex filtered = new CatalogIndex(byCategory);
                results = filtered.search(query);
            } else {
                results = byCategory;
            }
        }

        listWidget.setEntries(results);
        selectedEntry = null;
        if (placeButton != null) placeButton.active = false;
    }

    private void onPlaceClicked() {
        if (selectedEntry == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        File gameDir = mc.runDirectory;
        File schemFile = new File(gameDir, "minecraftuse/cache/" + selectedEntry.file());

        if (!schemFile.exists()) {
            MinecraftUseMod.LOGGER.warn("[CatalogScreen] Schematic file not found: {}", schemFile.getAbsolutePath());
            return;
        }

        // Close the screen, then place on next tick
        close();
        mc.execute(() -> {
            if (mc.player == null || mc.world == null) return;
            BlockPos origin = mc.player.getBlockPos();
            try {
                SchematicParser.Schematic schematic = SchematicParser.parse(schemFile);
                // Use a lightweight feedback mechanism since we don't have a command source here
                SchematicPlacer.place(schematic, origin, null, null);
                mc.player.sendMessage(Text.literal("§e[MCUse] §fPlacing: §a" + selectedEntry.name()), false);
            } catch (IOException e) {
                MinecraftUseMod.LOGGER.error("[CatalogScreen] Place failed: {}", e.getMessage());
                mc.player.sendMessage(Text.literal("§e[MCUse] §cFailed to parse schematic: " + e.getMessage()), false);
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        renderDetailPanel(context);
    }

    private void renderDetailPanel(DrawContext context) {
        int panelX = width - DETAIL_WIDTH - 5;
        int panelY = HEADER_HEIGHT;
        int panelH = height - HEADER_HEIGHT - FOOTER_HEIGHT;

        // Panel background
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

        context.drawText(textRenderer, "§f§l" + selectedEntry.name(), tx, ty, 0xFFFFFF, true);
        ty += 14;
        context.drawText(textRenderer, "§7by " + selectedEntry.author(), tx, ty, 0xAAAAAA, false);
        ty += 12;
        context.drawText(textRenderer, "§eCategory: §f" + selectedEntry.category(), tx, ty, 0xFFFFFF, false);
        ty += 12;

        int[] dims = selectedEntry.dimensions();
        context.drawText(textRenderer, "§eSize: §f" + dims[0] + "x" + dims[1] + "x" + dims[2], tx, ty, 0xFFFFFF, false);
        ty += 12;

        context.drawText(textRenderer, "§eStar: §f" + String.format("%.1f", selectedEntry.rating()), tx, ty, 0xFFFFFF, false);
        ty += 12;

        context.drawText(textRenderer, "§eDownloads: §f" + selectedEntry.downloads(), tx, ty, 0xFFFFFF, false);
        ty += 12;

        if (!selectedEntry.tags().isEmpty()) {
            context.drawText(textRenderer, "§eTags: §7" + String.join(", ", selectedEntry.tags()), tx, ty, 0xAAAAAA, false);
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
