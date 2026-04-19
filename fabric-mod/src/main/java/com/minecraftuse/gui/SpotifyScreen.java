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
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class SpotifyScreen extends Screen {

    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 320;

    private static final int NOW_PLAYING_Y = 36;
    private static final int NOW_PLAYING_H = 78;
    private static final int CONTROLS_LABEL_Y = NOW_PLAYING_Y + NOW_PLAYING_H + 10;   // 124
    private static final int CONTROLS_ROW_Y = CONTROLS_LABEL_Y + 14;                  // 138
    private static final int TABS_Y = CONTROLS_ROW_Y + 30;                            // 168
    private static final int TOOLBAR_Y = TABS_Y + 14;                                 // 182
    private static final int RESULTS_Y = TOOLBAR_Y + 28;                              // 210
    private static final int PADDING = 10;
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
    private static final int AUTH_POLL_INTERVAL_TICKS = 20;

    private static final Item[] DISCS = new Item[] {
        Items.MUSIC_DISC_13, Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_BLOCKS,
        Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.MUSIC_DISC_MALL,
        Items.MUSIC_DISC_MELLOHI, Items.MUSIC_DISC_STAL, Items.MUSIC_DISC_STRAD,
        Items.MUSIC_DISC_WARD, Items.MUSIC_DISC_11, Items.MUSIC_DISC_WAIT,
        Items.MUSIC_DISC_PIGSTEP, Items.MUSIC_DISC_OTHERSIDE, Items.MUSIC_DISC_5,
    };

    private enum Tab { SEARCH, PLAYLISTS, LIKED, QUEUE }

    private final SpotifyClient client;

    private TextFieldWidget searchField;
    private ButtonWidget playPauseButton;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget signInButton;
    private ButtonWidget backButton;
    private VolumeSlider volumeSlider;

    private NowPlaying nowPlaying = NowPlaying.unknown();
    private List<ListItem> items = new ArrayList<>();
    private int scroll = 0;
    private int tickCount = 0;
    private int authPollTicks = 0;
    private String statusText = "";

    private Tab tab = Tab.SEARCH;
    private boolean authenticated = false;
    private boolean authChecked = false;
    private boolean authPolling = false;
    private String detailPlaylistId = null;
    private String detailPlaylistName = null;
    private String detailPlaylistUri = null;

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

        prevButton = ButtonWidget.builder(Text.literal("\u23EE Prev"), b -> doPrev())
            .dimensions(transportStartX, transportY, btnW, 20).build();
        addDrawableChild(prevButton);

        playPauseButton = ButtonWidget.builder(Text.literal("\u23EF Play"), b -> doPlayPause())
            .dimensions(transportStartX + btnW + btnGap, transportY, btnW + 10, 20).build();
        addDrawableChild(playPauseButton);

        nextButton = ButtonWidget.builder(Text.literal("Next \u23ED"), b -> doNext())
            .dimensions(transportStartX + (btnW + btnGap) * 2 + 10, transportY, btnW, 20).build();
        addDrawableChild(nextButton);

        int volX = transportStartX + (btnW + btnGap) * 3 + 20;
        int volW = panelX + PANEL_WIDTH - PADDING - 8 - volX;
        volumeSlider = new VolumeSlider(volX, transportY, volW, 20, 0.5);
        addDrawableChild(volumeSlider);

        searchField = new TextFieldWidget(textRenderer,
            panelX + PADDING + 8, panelY + TOOLBAR_Y,
            PANEL_WIDTH - PADDING * 2 - 16, 20, Text.literal(""));
        searchField.setPlaceholder(Text.literal("Search Spotify\u2026"));
        searchField.setMaxLength(128);
        searchField.setChangedListener(s -> scroll = 0);
        addDrawableChild(searchField);

        backButton = ButtonWidget.builder(Text.literal("\u2190 Back"), b -> exitPlaylistDetail())
            .dimensions(panelX + PADDING + 8, panelY + TOOLBAR_Y, 60, 20).build();
        backButton.visible = false;
        addDrawableChild(backButton);

        signInButton = ButtonWidget.builder(
                Text.literal("Sign in to Spotify"),
                b -> doSignIn())
            .dimensions((width - 180) / 2, panelY + 160, 180, 24)
            .build();
        signInButton.visible = false;
        addDrawableChild(signInButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
            b -> close()).dimensions(panelX + PANEL_WIDTH - 60, panelY + 6, 52, 18).build());

        applyMode();
        checkAuthStatus();
    }

    @Override
    public void tick() {
        tickCount++;
        if (authPolling) {
            authPollTicks++;
            if (authPollTicks >= AUTH_POLL_INTERVAL_TICKS) {
                authPollTicks = 0;
                checkAuthStatus();
            }
        }
        if (authenticated && tickCount >= POLL_INTERVAL_TICKS) {
            tickCount = 0;
            refreshNowPlaying();
        }
    }

    // ---------- auth flow ----------

    private void checkAuthStatus() {
        client.authStatus().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            authChecked = true;
            boolean wasAuthed = authenticated;
            authenticated = json != null && json.has("authenticated")
                && json.get("authenticated").getAsBoolean();
            if (authenticated && !wasAuthed) {
                authPolling = false;
                refreshNowPlaying();
                if (tab == Tab.PLAYLISTS) loadPlaylists();
                if (tab == Tab.LIKED) loadLiked();
            }
            applyMode();
        })).exceptionally(err -> null);
    }

    private void doSignIn() {
        statusText = "Opening browser\u2026";
        client.authLogin().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            if (json != null && json.has("auth_url")) {
                String url = json.get("auth_url").getAsString();
                Util.getOperatingSystem().open(URI.create(url));
                statusText = "Waiting for browser sign-in\u2026";
                authPolling = true;
            } else {
                statusText = "Sign-in failed";
            }
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Sign-in failed");
            return null;
        });
    }

    // ---------- mode / tab switching ----------

    private void applyMode() {
        boolean ready = authenticated;
        prevButton.active = ready;
        playPauseButton.active = ready;
        nextButton.active = ready;
        volumeSlider.active = ready;

        signInButton.visible = !ready;

        boolean inDetail = detailPlaylistId != null;
        searchField.visible = ready;
        backButton.visible = ready && inDetail;
        searchField.setPlaceholder(Text.literal(searchPlaceholder()));
    }

    private String searchPlaceholder() {
        if (detailPlaylistId != null) return "Search this playlist\u2026";
        return switch (tab) {
            case SEARCH -> "Search Spotify\u2026";
            case PLAYLISTS -> "Search playlists\u2026";
            case LIKED -> "Search liked songs\u2026";
            case QUEUE -> "Search queue\u2026";
        };
    }

    private void selectTab(Tab newTab) {
        if (tab == newTab && detailPlaylistId == null) return;
        tab = newTab;
        detailPlaylistId = null;
        detailPlaylistName = null;
        detailPlaylistUri = null;
        items = new ArrayList<>();
        scroll = 0;
        statusText = "";
        searchField.setText("");
        applyMode();
        if (!authenticated) return;
        switch (newTab) {
            case SEARCH -> { /* user must type */ }
            case PLAYLISTS -> loadPlaylists();
            case LIKED -> loadLiked();
            case QUEUE -> loadQueue();
        }
    }

    private void enterPlaylistDetail(String id, String name, String uri) {
        detailPlaylistId = id;
        detailPlaylistName = name;
        detailPlaylistUri = uri;
        items = new ArrayList<>();
        scroll = 0;
        statusText = "Loading\u2026";
        searchField.setText("");
        applyMode();
        client.playlistTracks(id).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            items = parseTracks(json);
            statusText = items.size() + " tracks";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    private void exitPlaylistDetail() {
        detailPlaylistId = null;
        detailPlaylistName = null;
        detailPlaylistUri = null;
        items = new ArrayList<>();
        scroll = 0;
        searchField.setText("");
        applyMode();
        loadPlaylists();
    }

    private void loadQueue() {
        statusText = "Loading\u2026";
        client.queue().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            items = parseTracks(json);
            statusText = items.size() + " up next";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    // ---------- data loading ----------

    private void loadPlaylists() {
        statusText = "Loading\u2026";
        client.playlists().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            items = new ArrayList<>();
            if (json != null && json.has("playlists")) {
                JsonArray arr = json.getAsJsonArray("playlists");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject p = arr.get(i).getAsJsonObject();
                    items.add(ListItem.playlist(
                        p.get("id").getAsString(),
                        p.has("uri") ? p.get("uri").getAsString() : null,
                        p.get("name").getAsString(),
                        p.has("owner") ? p.get("owner").getAsString() : "",
                        p.has("track_count") ? p.get("track_count").getAsInt() : 0));
                }
            }
            statusText = items.size() + " playlists";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    private void loadLiked() {
        statusText = "Loading\u2026";
        client.likedTracks().thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            items = parseTracks(json);
            statusText = items.size() + " liked";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Failed to load");
            return null;
        });
    }

    private List<ListItem> parseTracks(JsonObject json) {
        List<ListItem> out = new ArrayList<>();
        if (json == null || !json.has("tracks")) return out;
        JsonArray arr = json.getAsJsonArray("tracks");
        for (int i = 0; i < arr.size(); i++) {
            JsonObject t = arr.get(i).getAsJsonObject();
            out.add(ListItem.track(
                t.get("uri").getAsString(),
                t.get("name").getAsString(),
                t.has("artist") ? t.get("artist").getAsString() : "",
                t.has("album") ? t.get("album").getAsString() : ""));
        }
        return out;
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        statusText = "Searching\u2026";
        client.search(q).thenAccept(json -> MinecraftClient.getInstance().execute(() -> {
            items = parseTracks(json);
            scroll = 0;
            statusText = items.isEmpty() ? "No results" : items.size() + " results";
        })).exceptionally(err -> {
            MinecraftClient.getInstance().execute(() -> statusText = "Search failed");
            return null;
        });
    }

    // ---------- playback ----------

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

    private void doPlayPause() { client.playPause().thenRun(this::refreshNowPlaying); }
    private void doNext()      { client.next().thenRun(this::refreshNowPlaying); }
    private void doPrev()      { client.previous().thenRun(this::refreshNowPlaying); }

    private void playItem(ListItem item) {
        if (item.trackUri != null) {
            String contextUri = (detailPlaylistId != null) ? detailPlaylistUri : null;
            List<String> uris = (contextUri == null) ? collectTrackUris() : null;
            client.playUri(item.trackUri, contextUri, uris)
                .thenRun(() -> MinecraftClient.getInstance().execute(this::refreshNowPlaying));
        } else if (item.playlistId != null) {
            enterPlaylistDetail(item.playlistId, item.title, item.playlistUri);
        }
    }

    private List<String> collectTrackUris() {
        List<String> uris = new ArrayList<>();
        for (ListItem it : displayItems()) {
            if (it.trackUri != null) uris.add(it.trackUri);
        }
        return uris;
    }

    // ---------- input ----------

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && searchField.visible && searchField.isFocused()) {
            doSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!authenticated) return super.mouseClicked(mouseX, mouseY, button);

        // Tab clicks
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;
        int tabY = panelY + TABS_Y;
        Tab clicked = tabAtMouse(mouseX, mouseY, panelX, tabY);
        if (clicked != null) {
            selectTab(clicked);
            return true;
        }

        // List row clicks
        int listX = panelX + PADDING + 8;
        int listY = panelY + RESULTS_Y;
        int listW = PANEL_WIDTH - PADDING * 2 - 16;
        int listH = panelY + PANEL_HEIGHT - PADDING - listY;

        List<ListItem> visible = displayItems();
        if (button == 0 && !visible.isEmpty()
                && mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int index = ((int) (mouseY - listY)) / RESULT_ROW_HEIGHT + scroll;
            if (index >= 0 && index < visible.size()) {
                playItem(visible.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<ListItem> visible = displayItems();
        if (visible.isEmpty()) return false;
        scroll = Math.max(0, Math.min(visible.size() - 1, scroll - (int) verticalAmount));
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------- rendering ----------

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // overridden to skip default blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BG_COLOR);

        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - PANEL_HEIGHT) / 2;

        context.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, CARD_BORDER);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, PANEL_COLOR);

        // Title bar
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 28, CARD_COLOR);
        context.drawText(textRenderer, Text.literal("\u266B JUKEBOX"),
            panelX + PADDING, panelY + 10, ACCENT, true);

        if (!authenticated) {
            renderSignInPrompt(context, panelX, panelY);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // Now-playing card
        renderNowPlayingCard(context, panelX + PADDING, panelY + NOW_PLAYING_Y,
            PANEL_WIDTH - PADDING * 2, NOW_PLAYING_H);

        // Controls label + volume status
        context.drawText(textRenderer, Text.literal("CONTROLS"),
            panelX + PADDING + 8, panelY + CONTROLS_LABEL_Y, LABEL_COLOR, false);
        String volStatus = "\uD83D\uDD0A " + volumeSlider.volumePercent();
        int volStatusW = textRenderer.getWidth(volStatus);
        context.drawText(textRenderer, Text.literal(volStatus),
            panelX + PANEL_WIDTH - PADDING - 8 - volStatusW,
            panelY + CONTROLS_LABEL_Y, TEXT_COLOR, false);

        // Tabs
        renderTabs(context, panelX, panelY + TABS_Y, mouseX, mouseY);

        // Status text right-aligned in the tab row
        if (!statusText.isEmpty()) {
            int sw = textRenderer.getWidth(statusText);
            context.drawText(textRenderer, Text.literal(statusText),
                panelX + PANEL_WIDTH - PADDING - 8 - sw,
                panelY + TABS_Y, MUTED_COLOR, false);
        }

        // Toolbar row: search field card OR playlist detail header
        if (detailPlaylistId != null) {
            String header = "\u266A " + (detailPlaylistName == null ? "Playlist" : detailPlaylistName);
            context.drawText(textRenderer, Text.literal(truncate(header, PANEL_WIDTH - 100)),
                panelX + PADDING + 8 + 70, panelY + TOOLBAR_Y + 6, TITLE_COLOR, true);
        } else if (tab == Tab.SEARCH) {
            int sfX = searchField.getX();
            int sfY = searchField.getY();
            int sfW = searchField.getWidth();
            int sfH = searchField.getHeight();
            boolean focused = searchField.isFocused();
            context.fill(sfX - 2, sfY - 2, sfX + sfW + 2, sfY + sfH + 2,
                focused ? ACCENT : CARD_BORDER);
            context.fill(sfX - 1, sfY - 1, sfX + sfW + 1, sfY + sfH + 1, 0xFF0E0E18);
        }

        // Results list
        int listX = panelX + PADDING + 8;
        int listY = panelY + RESULTS_Y;
        int listW = PANEL_WIDTH - PADDING * 2 - 16;
        int listH = panelY + PANEL_HEIGHT - PADDING - listY;
        renderList(context, listX, listY, listW, listH, mouseX, mouseY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSignInPrompt(DrawContext context, int panelX, int panelY) {
        String headline = "\u266B Connect your Spotify account";
        String sub = authChecked
            ? "Spotify Premium is required for playback control."
            : "Checking sign-in status\u2026";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(headline),
            panelX + PANEL_WIDTH / 2, panelY + 110, ACCENT);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(sub),
            panelX + PANEL_WIDTH / 2, panelY + 130, MUTED_COLOR);
        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(statusText),
                panelX + PANEL_WIDTH / 2, panelY + 200, TEXT_COLOR);
        }
    }

    private Tab[] tabsOrder() { return new Tab[] { Tab.SEARCH, Tab.PLAYLISTS, Tab.LIKED, Tab.QUEUE }; }

    private static String tabLabel(Tab t) {
        return switch (t) {
            case SEARCH -> "SEARCH";
            case PLAYLISTS -> "PLAYLISTS";
            case LIKED -> "LIKED";
            case QUEUE -> "QUEUE";
        };
    }

    private void renderTabs(DrawContext context, int panelX, int y, int mouseX, int mouseY) {
        int x = panelX + PADDING + 8;
        int gap = 14;
        for (Tab t : tabsOrder()) {
            String label = tabLabel(t);
            int w = textRenderer.getWidth(label);
            boolean active = (t == tab);
            boolean hover = mouseX >= x && mouseX < x + w
                && mouseY >= y - 1 && mouseY < y + 10;
            int color = active ? ACCENT : (hover ? TITLE_COLOR : LABEL_COLOR);
            context.drawText(textRenderer, Text.literal(label), x, y, color, active);
            if (active) {
                context.fill(x, y + 10, x + w, y + 11, ACCENT);
            }
            x += w + gap;
        }
    }

    private Tab tabAtMouse(double mouseX, double mouseY, int panelX, int y) {
        int x = panelX + PADDING + 8;
        int gap = 14;
        for (Tab t : tabsOrder()) {
            int w = textRenderer.getWidth(tabLabel(t));
            if (mouseX >= x && mouseX < x + w && mouseY >= y - 1 && mouseY < y + 11) {
                return t;
            }
            x += w + gap;
        }
        return null;
    }

    private void renderNowPlayingCard(DrawContext context, int x, int y, int w, int h) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, CARD_BORDER);
        context.fill(x, y, x + w, y + h, CARD_COLOR);

        if ("no_device".equals(nowPlaying.state)) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("No active Spotify device — open Spotify on this Mac"),
                x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }
        if ("stopped".equals(nowPlaying.state) || nowPlaying.name == null) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Nothing playing"), x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }

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

        // Playback context (playlist / artist radio / show), if any
        if (nowPlaying.contextName != null && !nowPlaying.contextName.isEmpty()
                && !"album".equals(nowPlaying.contextType)) {
            String prefix = switch (nowPlaying.contextType == null ? "" : nowPlaying.contextType) {
                case "playlist" -> "\u266B ";
                case "show"     -> "\uD83C\uDF99 ";
                default          -> "from ";
            };
            String label = prefix + nowPlaying.contextName;
            context.drawText(textRenderer, Text.literal(truncate(label, w - 70)),
                textX, textY + 36, ACCENT, false);
        }

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

        String badge = "playing".equals(nowPlaying.state) ? "\u25B6 PLAYING" : "\u275A\u275A PAUSED";
        int badgeColor = "playing".equals(nowPlaying.state) ? ACCENT : MUTED_COLOR;
        context.drawText(textRenderer, Text.literal(badge),
            x + w - PADDING - textRenderer.getWidth(badge), y + 10, badgeColor, false);
    }

    private void renderList(DrawContext context, int x, int y, int w, int h,
                             int mouseX, int mouseY) {
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, CARD_BORDER);
        context.fill(x, y, x + w, y + h, CARD_COLOR);

        List<ListItem> visible = displayItems();
        if (visible.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(emptyHint()),
                x + w / 2, y + h / 2 - 4, MUTED_COLOR);
            return;
        }

        int visibleRows = h / RESULT_ROW_HEIGHT;
        for (int i = 0; i < visibleRows && (i + scroll) < visible.size(); i++) {
            ListItem r = visible.get(i + scroll);
            int rowY = y + i * RESULT_ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= rowY && mouseY < rowY + RESULT_ROW_HEIGHT;
            if (hover) {
                context.fill(x, rowY, x + w, rowY + RESULT_ROW_HEIGHT, 0xFF2A2A45);
            }

            ItemStack stack = new ItemStack(pickDisc(r.iconKey()));
            context.drawItem(stack, x + 4, rowY + 4);

            String line1 = truncate(r.title, w - 28);
            String line2 = truncate(r.subtitle, w - 28);
            context.drawText(textRenderer, Text.literal(line1),
                x + 24, rowY + 4, TITLE_COLOR, false);
            context.drawText(textRenderer, Text.literal(line2),
                x + 24, rowY + 14, MUTED_COLOR, false);
        }

        if (visible.size() > visibleRows) {
            String scrollIndicator = (scroll + 1) + "\u2013"
                + Math.min(scroll + visibleRows, visible.size())
                + " / " + visible.size();
            context.drawText(textRenderer, Text.literal(scrollIndicator),
                x + w - textRenderer.getWidth(scrollIndicator) - 4, y + h - 12, MUTED_COLOR, false);
        }
    }

    private String emptyHint() {
        boolean filterActive = !items.isEmpty()
            && searchField != null
            && !searchField.getText().trim().isEmpty();
        if (filterActive) return "No matches";
        if (detailPlaylistId != null) return "Loading playlist\u2026";
        return switch (tab) {
            case SEARCH -> "Type a query and press Enter";
            case PLAYLISTS -> "Loading playlists\u2026";
            case LIKED -> "Loading liked songs\u2026";
            case QUEUE -> "Queue is empty";
        };
    }

    // ---------- helpers ----------

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

    private List<ListItem> displayItems() {
        // SEARCH tab is server-filtered; everything else applies a local fuzzy filter
        // so the search field doubles as a live filter for playlists / liked / queue.
        if (tab == Tab.SEARCH && detailPlaylistId == null) return items;
        String q = searchField == null ? "" : searchField.getText().trim();
        if (q.isEmpty()) return items;
        List<ListItem> out = new ArrayList<>();
        for (ListItem it : items) {
            if (fuzzyMatch(it.title + " " + it.subtitle, q)) out.add(it);
        }
        return out;
    }

    private static boolean fuzzyMatch(String haystack, String needle) {
        if (needle.isEmpty()) return true;
        String h = haystack.toLowerCase();
        String n = needle.toLowerCase();
        int hi = 0, ni = 0;
        while (hi < h.length() && ni < n.length()) {
            if (h.charAt(hi) == n.charAt(ni)) ni++;
            hi++;
        }
        return ni == n.length();
    }

    private static String formatTime(double seconds) {
        if (seconds < 0 || Double.isNaN(seconds)) return "0:00";
        int total = (int) seconds;
        int min = total / 60;
        int sec = total % 60;
        return min + ":" + (sec < 10 ? "0" : "") + sec;
    }

    // ---------- inner types ----------

    private record ListItem(String trackUri, String playlistId, String playlistUri,
                             String title, String subtitle) {
        static ListItem track(String uri, String name, String artist, String album) {
            String sub = artist + (album.isEmpty() ? "" : " \u00B7 " + album);
            return new ListItem(uri, null, null, name, sub);
        }
        static ListItem playlist(String id, String uri, String name, String owner, int trackCount) {
            String sub = (owner.isEmpty() ? "" : owner + " \u00B7 ") + trackCount + " tracks";
            return new ListItem(null, id, uri, name, sub);
        }
        String iconKey() {
            return trackUri != null ? trackUri : playlistId;
        }
    }

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
        String contextType;   // "playlist", "album", "artist", "show"
        String contextName;

        static NowPlaying unknown() {
            NowPlaying np = new NowPlaying();
            np.running = false;
            np.state = "unknown";
            return np;
        }

        static NowPlaying fromJson(JsonObject json) {
            NowPlaying np = new NowPlaying();
            if (json == null) {
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
            if (json.has("context_type")) np.contextType = json.get("context_type").getAsString();
            if (json.has("context_name")) np.contextName = json.get("context_name").getAsString();
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

        void setSilently(double newValue) { this.value = clamp(newValue); }
        int volumePercent() { return (int) Math.round(value * 100); }

        private double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }

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
            int color = active ? ACCENT : ACCENT_DIM;
            ctx.fill(x, trackY, x + filled, trackY + trackH, color);
            int knobX = x + filled - 3;
            int knobY = y + 2;
            int knobW = 6;
            int knobH = h - 4;
            ctx.fill(knobX - 1, knobY - 1, knobX + knobW + 1, knobY + knobH + 1, active ? 0xFFFFFFFF : MUTED_COLOR);
            ctx.fill(knobX, knobY, knobX + knobW, knobY + knobH, color);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!active) return;
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
