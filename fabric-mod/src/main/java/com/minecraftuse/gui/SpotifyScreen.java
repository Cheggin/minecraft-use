package com.minecraftuse.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.minecraftuse.network.SpotifyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class SpotifyScreen extends Screen {

    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 320;

    // Section Y offsets relative to panel top (kept in one place so spacing is easy to tweak).
    private static final int NOW_PLAYING_Y = 36;
    private static final int NOW_PLAYING_H = 78;
    private static final int CONTROLS_LABEL_Y = NOW_PLAYING_Y + NOW_PLAYING_H + 10;   // 124
    private static final int CONTROLS_ROW_Y = CONTROLS_LABEL_Y + 14;                  // 138
    private static final int SEARCH_LABEL_Y = CONTROLS_ROW_Y + 30;                    // 168
    private static final int SEARCH_FIELD_Y = SEARCH_LABEL_Y + 14;                    // 182
    private static final int RESULTS_Y = SEARCH_FIELD_Y + 28;                         // 210
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 22;
    private static final int RESULT_ROW_HEIGHT = 24;

    private static final int BG_COLOR = 0xCC0A0A12;
    private static final int PANEL_COLOR = 0xFF161622;
    private static final int CARD_COLOR = 0xFF1F1F2E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int ACCENT = 0xFF1DB954;
    private static final int ACCENT_DIM = 0xFF0A6B2C;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int MUTED_COLOR = 0xFF888888;
    private static final int LABEL_COLOR = 0xFFAAAA00;

    private static final int POLL_INTERVAL_TICKS = 20;

    private static final Item[] DISCS = new Item[] {
        Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
        Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
        Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
        Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
        Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5,
    };

    private final SpotifyClient client;

    private TextFieldWidget searchField;
    private ButtonWidget playPauseButton;
    private VolumeSlider volumeSlider;

    private NowPlaying nowPlaying = NowPlaying.unknown();
    private List<SearchResult> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private int tickCount = 0;
    private boolean searching = false;
    private String searchStatus = "";

    public SpotifyScreen(SpotifyClient client) {
        super(Text.literal("Spotify Jukebox"));
        this.client = client;
    }

    @Override
    protected void init() {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        int transportY = panelY + CONTROLS_ROW_Y;
        int btnW = 50;
        int btnGap = 6;
        int transportStartX = panelX + PADDING + 8;

        addDrawableChild(ButtonWidget.builder(Text.literal("\u23EE Prev"), b -> doPrev())
            .dimensions(transportStartX, transportY, btnW, 20).build());

        playPauseButton = ButtonWidget.builder(Text.literal("\u23EF Play"), b -> doPlayPause())
            .dimensions(transportStartX + btnW + btnGap, transportY, btnW + 10, 20).build();
        addDrawableChild(playPauseButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Next \u23ED"), b -> doNext())
            .dimensions(transportStartX + (btnW + btnGap) * 2 + 10, transportY, btnW, 20).build());

        int volX = transportStartX + (btnW + btnGap) * 3 + 20;
        int volW = panelX + PANEL_WIDTH - PADDING - 8 - volX;
        volumeSlider = new VolumeSlider(volX, transportY, volW, 20, 0.5);
        addDrawableChild(volumeSlider);

        searchField = new TextFieldWidget(textRenderer,
            panelX + PADDING + 8, panelY + SEARCH_FIELD_Y,
            PANEL_WIDTH - PADDING * 2 - 16, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search Spotify\u2026"));
        searchField.setMaxLength(128);
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
            b -> close()).dimensions(panelX + PANEL_WIDTH - 60, panelY + 6, 52, 18).build());

        refreshNowPlaying();
    }

    @Override
    public void tick() {
        tickCount++;
        if (tickCount >= POLL_INTERVAL_TICKS) {
            tickCount = 0;
            refreshNowPlaying();
        }
    }

    private void refreshNowPlaying() {
        client.nowPlaying().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            nowPlaying = NowPlaying.fromJson(json);
            playPauseButton.setMessage(Text.literal(
                "playing".equals(nowPlaying.state) ? "\u23EF Pause" : "\u23EF Play"));
            if (nowPlaying.volume >= 0 && !volumeSlider.dragging) {
                volumeSlider.setSilently(nowPlaying.volume / 100.0);
            }
        })).exceptionally(err -> null);
    }

    private void doPlayPause() {
        client.playPause().thenRun(this::refreshNowPlaying);
    }

    private void doNext() {
        client.next().thenRun(this::refreshNowPlaying);
    }

    private void doPrev() {
        client.previous().thenRun(this::refreshNowPlaying);
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        searching = true;
        searchStatus = "Searching\u2026";
        client.search(q).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            searching = false;
            if (json.has("error")) {
                searchStatus = "Search failed";
                searchResults = new ArrayList<>();
                return;
            }
            JsonArray tracks = json.getAsJsonArray("tracks");
            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < tracks.size(); i++) {
                JsonObject t = tracks.get(i).getAsJsonObject();
                results.add(new SearchResult(
                    t.get("uri").getAsString(),
                    t.get("name").getAsString(),
                    t.get("artist").getAsString(),
                    t.get("album").getAsString()
                ));
            }
            searchResults = results;
            searchScroll = 0;
            searchStatus = results.isEmpty() ? "No results" : results.size() + " results";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> {
                searching = false;
                searchStatus = "Search failed";
            });
            return null;
        });
    }

    private void playTrack(SearchResult result) {
        client.playUri(result.uri).thenRun(() -> MinecraftClient.getInstance().execute(this::refreshNowPlaying));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && searchField.isFocused()) {
            doSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't render the default blur — we draw our own background in render()
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full-screen dimming overlay drawn first, before panel + widgets
        context.fill(0, 0, width, height, BG_COLOR);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        // Outer panel border + fill (drawn before children)
        context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, CARD_BORDER);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_COLOR);

        // Title bar
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 28, CARD_COLOR);
        context.drawText(textRenderer, Text.literal("\u266B JUKEBOX"),
            panelX + PADDING, panelY + 10, ACCENT, true);

        // Now-playing card
        int npX = panelX + PADDING;
        int npY = panelY + NOW_PLAYING_Y;
        int npW = PANEL_WIDTH - PADDING * 2;
        renderNowPlayingCard(context, npX, npY, npW, NOW_PLAYING_H);

        // Section label: Controls
        context.drawText(textRenderer, Text.literal("CONTROLS"),
            panelX + PADDING + 8, panelY + CONTROLS_LABEL_Y, LABEL_COLOR, false);

        // Volume status, right-aligned in the same row as the CONTROLS label
        String volStatus = "\uD83D\uDD0A " + volumeSlider.volumePercent();
        int volStatusW = textRenderer.getWidth(volStatus);
        context.drawText(textRenderer, Text.literal(volStatus),
            panelX + PANEL_WIDTH - PADDING - 8 - volStatusW,
            panelY + CONTROLS_LABEL_Y, TEXT_COLOR, false);

        // Section label: Search
        context.drawText(textRenderer, Text.literal("SEARCH"),
            panelX + PADDING + 8, panelY + SEARCH_LABEL_Y, LABEL_COLOR, false);

        // Search status / hint
        if (!searchStatus.isEmpty()) {
            int statusW = textRenderer.getWidth(searchStatus);
            context.drawText(textRenderer, Text.literal(searchStatus),
                panelX + PANEL_WIDTH - PADDING - 8 - statusW,
                panelY + SEARCH_LABEL_Y, MUTED_COLOR, false);
        }

        // Search field card (drawn behind the TextFieldWidget so the box is obvious)
        int sfX = searchField.getX();
        int sfY = searchField.getY();
        int sfW = searchField.getWidth();
        int sfH = searchField.getHeight();
        boolean focused = searchField.isFocused();
        context.fill(sfX - 2, sfY - 2, sfX + sfW + 2, sfY + sfH + 2,
            focused ? ACCENT : CARD_BORDER);
        context.fill(sfX - 1, sfY - 1, sfX + sfW + 1, sfY + sfH + 1, 0xFF0E0E18);

        // Results list
        int listX = panelX + PADDING + 8;
        int listY = panelY + RESULTS_Y;
        int listW = PANEL_WIDTH - PADDING * 2 - 16;
        int listH = panelY + PANEL_HEIGHT - PADDING - listY;
        renderSearchResults(context, listX, listY, listW, listH, mouseX, mouseY);

        // Children (buttons, slider, text field) drawn last so they sit on top of cards
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderNowPlayingCard(DrawContext context, int x, int y, int w, int h) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, CARD_BORDER);
        context.fill(x, y, x + w, y + h, CARD_COLOR);

        if (!nowPlaying.running) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Spotify is not running"), x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }
        if ("stopped".equals(nowPlaying.state) || nowPlaying.name == null) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Nothing playing"), x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }

        // Music disc icon picked from track id hash, scaled 2x
        Item disc = pickDisc(nowPlaying.trackId);
        ItemStack stack = new ItemStack(disc);
        context.getMatrices().push();
        context.getMatrices().translate(x + 12, y + 14, 0);
        context.getMatrices().scale(2.0f, 2.0f, 1.0f);
        context.drawItem(stack, 0, 0);
        context.getMatrices().pop();

        int textX = x + 60;
        int textY = y + 10;

        context.drawText(textRenderer, Text.literal(truncate(nowPlaying.name, w - 70)),
            textX, textY, TITLE_COLOR, true);
        context.drawText(textRenderer, Text.literal(truncate(nowPlaying.artist, w - 70)),
            textX, textY + 12, TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal(truncate(nowPlaying.album, w - 70)),
            textX, textY + 24, MUTED_COLOR, false);

        // Progress bar
        int barX = x + 12;
        int barY = y + h - 22;
        int barW = w - 24;
        int barH = 4;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF333344);
        if (nowPlaying.durationSeconds > 0) {
            int filled = (int) (barW * Math.min(1.0, nowPlaying.positionSeconds / nowPlaying.durationSeconds));
            context.fill(barX, barY, barX + filled, barY + barH, ACCENT);
            context.fill(barX, barY + barH - 1, barX + filled, barY + barH, ACCENT_DIM);
        }
        String timeStr = formatTime(nowPlaying.positionSeconds) + " / " + formatTime(nowPlaying.durationSeconds);
        context.drawText(textRenderer, Text.literal(timeStr),
            barX + barW - textRenderer.getWidth(timeStr), barY + 8, MUTED_COLOR, false);

        // State badge
        String badge = "playing".equals(nowPlaying.state) ? "\u25B6 PLAYING" : "\u275A\u275A PAUSED";
        int badgeColor = "playing".equals(nowPlaying.state) ? ACCENT : MUTED_COLOR;
        context.drawText(textRenderer, Text.literal(badge),
            x + w - PADDING - textRenderer.getWidth(badge), y + 10, badgeColor, false);
    }

    private void renderSearchResults(DrawContext context, int x, int y, int w, int h,
                                      int mouseX, int mouseY) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, CARD_BORDER);
        context.fill(x, y, x + w, y + h, CARD_COLOR);

        if (searchResults.isEmpty()) {
            String hint = searching
                ? "Searching\u2026"
                : "Type a query and press Enter";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }

        int visibleRows = h / RESULT_ROW_HEIGHT;
        for (int i = 0; i < visibleRows && (i + searchScroll) < searchResults.size(); i++) {
            SearchResult r = searchResults.get(i + searchScroll);
            int rowY = y + i * RESULT_ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + RESULT_ROW_HEIGHT;
            if (hover) {
                context.fill(x, rowY, x + w, rowY + RESULT_ROW_HEIGHT, 0xFF2A2A45);
            }

            ItemStack stack = new ItemStack(pickDisc(r.uri));
            context.drawItem(stack, x + 4, rowY + 4);

            String line1 = truncate(r.name, w - 28);
            String line2 = truncate(r.artist + " \u00B7 " + r.album, w - 28);
            context.drawText(textRenderer, Text.literal(line1),
                x + 24, rowY + 4, TITLE_COLOR, false);
            context.drawText(textRenderer, Text.literal(line2),
                x + 24, rowY + 14, MUTED_COLOR, false);
        }

        if (searchResults.size() > visibleRows) {
            String scrollIndicator = (searchScroll + 1) + "\u2013"
                + Math.min(searchScroll + visibleRows, searchResults.size())
                + " / " + searchResults.size();
            context.drawText(textRenderer, Text.literal(scrollIndicator),
                x + w - textRenderer.getWidth(scrollIndicator) - 4, y + h - 12, MUTED_COLOR, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int listX = panelX + PADDING + 8;
        int listY = panelY + RESULTS_Y;
        int listW = PANEL_WIDTH - PADDING * 2 - 16;
        int listH = panelY + PANEL_HEIGHT - PADDING - listY;

        if (button == 0 && !searchResults.isEmpty()
                && mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int index = ((int) (mouseY - listY)) / RESULT_ROW_HEIGHT + searchScroll;
            if (index >= 0 && index < searchResults.size()) {
                playTrack(searchResults.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        searchScroll = Math.max(0,
            Math.min(searchResults.size() - 1, searchScroll - (int) verticalAmount));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static Item pickDisc(String key) {
        if (key == null || key.isEmpty()) return DISCS[0];
        return DISCS[Math.floorMod(key.hashCode(), DISCS.length)];
    }

    private static String truncate(String text, int pixelWidth) {
        if (text == null) return "";
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.textRenderer.getWidth(text) <= pixelWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (mc.textRenderer.getWidth(sb.toString() + "\u2026") > pixelWidth) {
                sb.deleteCharAt(sb.length() - 1);
                break;
            }
        }
        return sb.toString() + "\u2026";
    }

    private static String formatTime(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) return "0:00";
        int total = (int) seconds;
        int min = total / 60;
        int sec = total % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    // ---- inner types ----

    private record SearchResult(String uri, String name, String artist, String album) {}

    private static final class NowPlaying {
        boolean running;
        String state;
        String name;
        String artist;
        String album;
        String trackId;
        double durationSeconds;
        double positionSeconds;
        int volume = -1;

        static NowPlaying unknown() {
            NowPlaying np = new NowPlaying();
            np.running = true;
            np.state = "unknown";
            return np;
        }

        static NowPlaying fromJson(JsonObject json) {
            NowPlaying np = new NowPlaying();
            if (json == null || json.has("error")) {
                np.running = false;
                return np;
            }
            np.running = json.has("running") && json.get("running").getAsBoolean();
            np.state = json.has("state") ? json.get("state").getAsString() : "unknown";
            if (json.has("name")) np.name = json.get("name").getAsString();
            if (json.has("artist")) np.artist = json.get("artist").getAsString();
            if (json.has("album")) np.album = json.get("album").getAsString();
            if (json.has("track_id")) np.trackId = json.get("track_id").getAsString();
            if (json.has("duration_seconds")) np.durationSeconds = json.get("duration_seconds").getAsDouble();
            if (json.has("position_seconds")) np.positionSeconds = json.get("position_seconds").getAsDouble();
            if (json.has("volume")) np.volume = json.get("volume").getAsInt();
            return np;
        }
    }

    private final class VolumeSlider extends ClickableWidget {
        private double value;
        boolean dragging = false;

        VolumeSlider(int x, int y, int w, int h, double initial) {
            super(x, y, w, h, Text.literal("Volume"));
            this.value = clamp(initial);
        }

        void setSilently(double newValue) {
            this.value = clamp(newValue);
        }

        private double clamp(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }

        private void updateFromMouse(double mouseX) {
            if (getWidth() <= 0) return;
            value = clamp((mouseX - getX()) / (double) getWidth());
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            int trackY = y + h / 2 - 2;
            int trackH = 4;

            ctx.fill(x - 1, trackY - 1, x + w + 1, trackY + trackH + 1, CARD_BORDER);
            ctx.fill(x, trackY, x + w, trackY + trackH, 0xFF2A2A38);

            int filled = (int) (w * value);
            ctx.fill(x, trackY, x + filled, trackY + trackH, ACCENT);

            int knobX = x + filled - 3;
            int knobY = y + 2;
            int knobW = 6;
            int knobH = h - 4;
            ctx.fill(knobX - 1, knobY - 1, knobX + knobW + 1, knobY + knobH + 1, 0xFFFFFFFF);
            ctx.fill(knobX, knobY, knobX + knobW, knobY + knobH, ACCENT);
        }

        int volumePercent() {
            return (int) Math.round(value * 100);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            updateFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
            if (dragging) updateFromMouse(mouseX);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (dragging) {
                client.setVolume((int) Math.round(value * 100));
                dragging = false;
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
