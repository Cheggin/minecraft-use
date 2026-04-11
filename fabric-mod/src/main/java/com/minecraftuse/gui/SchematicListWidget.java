package com.minecraftuse.gui;

import com.minecraftuse.catalog.CatalogIndex;
import com.minecraftuse.catalog.SchematicEntry;
import com.minecraftuse.catalog.ThumbnailManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Scrollable list widget showing schematic entries with thumbnail, name, category, rating, and dimensions.
 */
public class SchematicListWidget extends EntryListWidget<SchematicListWidget.SchematicEntry> {

    private static final int ENTRY_HEIGHT = 36;
    private static final int THUMBNAIL_SIZE = 28;
    private static final int PADDING = 4;

    private final ThumbnailManager thumbnailManager;
    private SchematicEntry selectedEntry;
    private SelectionListener selectionListener;

    public interface SelectionListener {
        void onSelect(com.minecraftuse.catalog.SchematicEntry entry);
    }

    public SchematicListWidget(
        MinecraftClient client,
        int width, int height,
        int top, int bottom,
        ThumbnailManager thumbnailManager
    ) {
        super(client, width, height, top, ENTRY_HEIGHT);
        this.thumbnailManager = thumbnailManager;
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setEntries(List<com.minecraftuse.catalog.SchematicEntry> entries) {
        clearEntries();
        for (com.minecraftuse.catalog.SchematicEntry entry : entries) {
            addEntry(new SchematicEntry(entry));
        }
    }

    public com.minecraftuse.catalog.SchematicEntry getSelectedSchematic() {
        return selectedEntry != null ? selectedEntry.data : null;
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No narration needed for this widget
    }

    public class SchematicEntry extends EntryListWidget.Entry<SchematicEntry> {

        final com.minecraftuse.catalog.SchematicEntry data;

        SchematicEntry(com.minecraftuse.catalog.SchematicEntry data) {
            this.data = data;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {
            MinecraftClient mc = MinecraftClient.getInstance();

            // Background highlight for hovered/selected
            if (hovered || this == selectedEntry) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x44FFFFFF);
            }

            // Thumbnail
            Identifier thumbnail = thumbnailManager.getTexture(data.thumbnail());
            context.drawTexture(thumbnail, x + PADDING, y + PADDING, 0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE, THUMBNAIL_SIZE);

            int textX = x + PADDING + THUMBNAIL_SIZE + PADDING;
            int textY = y + PADDING;

            // Name
            context.drawText(mc.textRenderer, data.name(), textX, textY, 0xFFFFFF, true);

            // Category + rating on second line
            String meta = data.category() + "  ★" + String.format("%.1f", data.rating());
            context.drawText(mc.textRenderer, meta, textX, textY + 10, 0xAAAAAA, false);

            // Dimensions on third line
            int[] dims = data.dimensions();
            String dimStr = dims[0] + "x" + dims[1] + "x" + dims[2];
            context.drawText(mc.textRenderer, dimStr, textX, textY + 20, 0x888888, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            selectedEntry = this;
            if (selectionListener != null) {
                selectionListener.onSelect(data);
            }
            return true;
        }
    }
}
