package com.minecraftuse.screen;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.minecraftuse.MinecraftUseMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class CodeScreenGUI extends Screen {

    private static final String DEFAULT_URL = "https://vscode.dev";
    private static final int URL_BAR_HEIGHT = 20;
    private static final int URL_BAR_PADDING = 4;
    private static final int PANEL_BORDER = 2;
    private static final int PANEL_MIN_WIDTH = 640;
    private static final int PANEL_MIN_HEIGHT = 360;
    private static final double PANEL_WIDTH_RATIO = 0.84;
    private static final double PANEL_HEIGHT_RATIO = 0.78;
    private static final double ZOOM_STEP = 0.5D;
    private static final double MIN_ZOOM_LEVEL = -4.0D;
    private static final double MAX_ZOOM_LEVEL = 4.0D;
    private static final int BACKDROP_COLOR = 0xA0000000;
    private static final int PANEL_BG_COLOR = 0xFF101010;
    private static final int PANEL_BORDER_COLOR = 0xFF5A5A5A;
    private static final int URL_BAR_BG_COLOR = 0xCC1E1E1E;
    private static final int URL_BAR_TEXT_COLOR = 0xFFCCCCCC;
    private static final int URL_BAR_LABEL_COLOR = 0xFF888888;
    private static final String CLOSE_HINT = "ESC closes";
    private static final String ZOOM_HINT = "Cmd/Ctrl +/- zoom";

    private MCEFBrowser browser;
    private final String url;
    private BrowserLayout browserLayout = BrowserLayout.empty();

    public CodeScreenGUI(String url) {
        super(Text.literal("Code Screen"));
        this.url = url;
    }

    public CodeScreenGUI() {
        this(DEFAULT_URL);
    }

    @Override
    protected void init() {
        super.init();
        MinecraftUseMod.LOGGER.info("[CodeScreen] init() called, URL={}, MCEF.isInitialized={}", url, MCEF.isInitialized());
        if (!MCEF.isInitialized()) {
            MinecraftClient.getInstance().execute(() -> {
                if (client != null && client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§e[MCUse] §cMCEF is not initialized. Install MCEF and restart."),
                        false
                    );
                }
            });
            close();
            return;
        }

        if (browser == null) {
            MinecraftUseMod.LOGGER.info("[CodeScreen] Creating MCEF browser for: {}", url);
            browser = MCEF.createBrowser(url, false);
            // Disable cursor change listener to avoid GL cursor errors on macOS
            browser.setCursorChangeListener(cursor -> {});
            // Get both client types
            org.cef.CefClient cefClient = browser.getClient();
            com.cinemamod.mcef.MCEFClient mcefClient = MCEF.getClient();
            MinecraftUseMod.LOGGER.info("[CodeScreen] CefClient: {}, MCEFClient: {}",
                cefClient != null ? cefClient.getClass().getName() : "null",
                mcefClient != null ? mcefClient.getClass().getName() : "null");

            // Handle popups via CefClient (has addLifeSpanHandler)
            if (cefClient != null) {
                cefClient.addLifeSpanHandler(new org.cef.handler.CefLifeSpanHandlerAdapter() {
                @Override
                public boolean onBeforePopup(org.cef.browser.CefBrowser cefBrowser,
                        org.cef.browser.CefFrame frame, String targetUrl, String targetFrameName) {
                    MinecraftUseMod.LOGGER.info("[CodeScreen] Popup requested: {}", targetUrl);
                    MinecraftClient.getInstance().execute(() -> {
                        if (browser != null) {
                            browser.loadURL(targetUrl);
                        }
                    });
                    return true;
                }
            });
                MinecraftUseMod.LOGGER.info("[CodeScreen] LifeSpanHandler registered on CefClient");
            }

            // Override window.open on every page load via MCEFClient (has addLoadHandler)
            if (mcefClient != null) {
                mcefClient.addLoadHandler(new org.cef.handler.CefLoadHandlerAdapter() {
                @Override
                public void onLoadStart(org.cef.browser.CefBrowser cefBrowser, org.cef.browser.CefFrame frame,
                        org.cef.network.CefRequest.TransitionType transitionType) {
                    if (frame.isMain()) {
                        MinecraftUseMod.LOGGER.info("[CodeScreen] Page loading: {}", frame.getURL());
                        cefBrowser.executeJavaScript(
                            "window.open = function(url, target, features) { " +
                            "  console.log('[MC] window.open intercepted: ' + url); " +
                            "  if(url) { window.location.href = url; } " +
                            "  return window; " +
                            "};",
                            frame.getURL(), 0
                        );
                    }
                }

                @Override
                public void onLoadEnd(org.cef.browser.CefBrowser cefBrowser, org.cef.browser.CefFrame frame, int httpStatusCode) {
                    if (frame.isMain()) {
                        MinecraftUseMod.LOGGER.info("[CodeScreen] Page loaded: {} (status {})", frame.getURL(), httpStatusCode);
                        // Re-inject after load in case scripts overwrote it
                        cefBrowser.executeJavaScript(
                            "window.open = function(url, target, features) { " +
                            "  console.log('[MC] window.open intercepted: ' + url); " +
                            "  if(url) { window.location.href = url; } " +
                            "  return window; " +
                            "};",
                            frame.getURL(), 0
                        );
                    }
                }
                });
                MinecraftUseMod.LOGGER.info("[CodeScreen] LoadHandler registered on MCEFClient");
            }
            MinecraftUseMod.LOGGER.info("[CodeScreen] Browser created: {}", browser != null);
        }
        resizeBrowser();
        MinecraftUseMod.LOGGER.info("[CodeScreen] Screen initialized with panel {}", browserLayout);
    }

    private void resizeBrowser() {
        browserLayout = computeBrowserLayout(width, height);
        if (browser == null) {
            MinecraftUseMod.LOGGER.info("[CodeScreen] Computed panel layout without browser: {}", browserLayout);
            return;
        }
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int browserWidth = (int) Math.round(browserLayout.browserWidth() * scale);
        int browserHeight = (int) Math.round(browserLayout.browserHeight() * scale);
        if (browserWidth > 0 && browserHeight > 0) {
            browser.resize(browserWidth, browserHeight);
            MinecraftUseMod.LOGGER.info(
                "[CodeScreen] Resized browser to {}x{} px at scale {} using panel {}",
                browserWidth,
                browserHeight,
                scale,
                browserLayout
            );
        } else {
            MinecraftUseMod.LOGGER.warn(
                "[CodeScreen] Skipped browser resize because computed size was {}x{} for panel {}",
                browserWidth,
                browserHeight,
                browserLayout
            );
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        resizeBrowser();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKDROP_COLOR);
        context.fill(
            browserLayout.panelLeft() - PANEL_BORDER,
            browserLayout.panelTop() - PANEL_BORDER,
            browserLayout.panelRight() + PANEL_BORDER,
            browserLayout.panelBottom() + PANEL_BORDER,
            PANEL_BORDER_COLOR
        );
        context.fill(
            browserLayout.panelLeft(),
            browserLayout.panelTop(),
            browserLayout.panelRight(),
            browserLayout.panelBottom(),
            PANEL_BG_COLOR
        );

        if (browser != null) {
            MCEFRenderer renderer = browser.getRenderer();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, renderer.getTextureID());

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            float x0 = browserLayout.browserLeft();
            float y0 = browserLayout.browserTop();
            float x1 = browserLayout.browserRight();
            float y1 = browserLayout.browserBottom();

            buf.vertex(x0, y1, 0).texture(0, 1).color(255, 255, 255, 255);
            buf.vertex(x1, y1, 0).texture(1, 1).color(255, 255, 255, 255);
            buf.vertex(x1, y0, 0).texture(1, 0).color(255, 255, 255, 255);
            buf.vertex(x0, y0, 0).texture(0, 0).color(255, 255, 255, 255);

            BufferRenderer.drawWithGlobalProgram(buf.end());

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }

        // URL bar overlay
        context.fill(
            browserLayout.panelLeft(),
            browserLayout.panelTop(),
            browserLayout.panelRight(),
            browserLayout.browserTop(),
            URL_BAR_BG_COLOR
        );
        int urlY = browserLayout.panelTop() + URL_BAR_PADDING;
        int urlX = browserLayout.panelLeft() + URL_BAR_PADDING;
        int zoomHintWidth = textRenderer.getWidth(ZOOM_HINT);
        String zoomLabel = String.format(Locale.ROOT, "%.1fx", zoomFactorForDisplay(currentZoomLevel()));
        int zoomLabelWidth = textRenderer.getWidth(zoomLabel);
        int closeHintWidth = textRenderer.getWidth(CLOSE_HINT);
        context.drawText(textRenderer, Text.literal(CLOSE_HINT), urlX, urlY, URL_BAR_LABEL_COLOR, false);
        // Truncate URL to fit between close hint and zoom hint
        int availableWidth = browserLayout.panelWidth() - closeHintWidth - zoomHintWidth - zoomLabelWidth - URL_BAR_PADDING * 6;
        String displayUrl = url;
        while (textRenderer.getWidth(displayUrl) > availableWidth && displayUrl.length() > 10) {
            displayUrl = displayUrl.substring(0, displayUrl.length() - 1);
        }
        if (displayUrl.length() < url.length()) displayUrl += "...";
        int urlCenterX = browserLayout.panelLeft() + closeHintWidth + URL_BAR_PADDING * 2 +
            (availableWidth - textRenderer.getWidth(displayUrl)) / 2;
        context.drawText(textRenderer, Text.literal(displayUrl), urlCenterX, urlY, URL_BAR_TEXT_COLOR, false);
        context.drawText(
            textRenderer,
            Text.literal(ZOOM_HINT),
            browserLayout.panelRight() - zoomHintWidth - zoomLabelWidth - (URL_BAR_PADDING * 3),
            urlY,
            URL_BAR_LABEL_COLOR,
            false
        );
        context.drawText(
            textRenderer,
            Text.literal(zoomLabel),
            browserLayout.panelRight() - zoomLabelWidth - URL_BAR_PADDING,
            urlY,
            URL_BAR_TEXT_COLOR,
            false
        );
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't render the default background — the inset panel handles its own backdrop.
    }

    private int scaledMouseX(double mouseX) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return (int) Math.round((mouseX - browserLayout.browserLeft()) * scale);
    }

    private int scaledMouseY(double mouseY) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return (int) Math.round((mouseY - browserLayout.browserTop()) * scale);
    }

    private boolean isInsideBrowser(double mouseX, double mouseY) {
        return browserLayout.contains(mouseX, mouseY);
    }

    private double currentZoomLevel() {
        if (browser == null) {
            return 0.0D;
        }
        return browser.getZoomLevel();
    }

    private void adjustZoom(double delta) {
        if (browser == null) {
            MinecraftUseMod.LOGGER.warn("[CodeScreen] Ignored zoom delta {} because browser is null", delta);
            return;
        }
        double previous = currentZoomLevel();
        double next = clampZoomLevel(previous + delta);
        if (Double.compare(previous, next) == 0) {
            return;
        }
        browser.setZoomLevel(next);
    }

    static BrowserLayout computeBrowserLayout(int screenWidth, int screenHeight) {
        int panelWidth = Math.max(PANEL_MIN_WIDTH, (int) Math.round(screenWidth * PANEL_WIDTH_RATIO));
        int panelHeight = Math.max(PANEL_MIN_HEIGHT + URL_BAR_HEIGHT, (int) Math.round(screenHeight * PANEL_HEIGHT_RATIO));
        panelWidth = Math.min(panelWidth, Math.max(PANEL_MIN_WIDTH, screenWidth - 32));
        panelHeight = Math.min(panelHeight, Math.max(PANEL_MIN_HEIGHT + URL_BAR_HEIGHT, screenHeight - 32));
        int panelLeft = Math.max(16, (screenWidth - panelWidth) / 2);
        int panelTop = Math.max(16, (screenHeight - panelHeight) / 2);
        int panelRight = Math.min(screenWidth - 16, panelLeft + panelWidth);
        int panelBottom = Math.min(screenHeight - 16, panelTop + panelHeight);
        panelWidth = panelRight - panelLeft;
        panelHeight = panelBottom - panelTop;
        return new BrowserLayout(
            panelLeft,
            panelTop,
            panelWidth,
            panelHeight,
            panelLeft,
            panelTop + URL_BAR_HEIGHT,
            panelWidth,
            panelHeight - URL_BAR_HEIGHT
        );
    }

    static boolean isZoomInShortcut(int keyCode, int modifiers) {
        return hasZoomModifier(modifiers)
            && (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD);
    }

    static boolean isZoomOutShortcut(int keyCode, int modifiers) {
        return hasZoomModifier(modifiers)
            && (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT);
    }

    static boolean isResetZoomShortcut(int keyCode, int modifiers) {
        return hasZoomModifier(modifiers) && keyCode == GLFW.GLFW_KEY_0;
    }

    static boolean hasZoomModifier(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SUPER) != 0 || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    static double clampZoomLevel(double zoomLevel) {
        return Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
    }

    static double zoomFactorForDisplay(double zoomLevel) {
        return Math.pow(1.2D, zoomLevel);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMousePress(scaledMouseX(mouseX), scaledMouseY(mouseY), button);
            browser.setFocus(true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMouseRelease(scaledMouseX(mouseX), scaledMouseY(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMouseMove(scaledMouseX(mouseX), scaledMouseY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null && isInsideBrowser(mouseX, mouseY)) {
            browser.sendMouseWheel(scaledMouseX(mouseX), scaledMouseY(mouseY), verticalAmount, 0);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Escape closes the screen — do not forward to browser
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (isZoomInShortcut(keyCode, modifiers)) {
            adjustZoom(ZOOM_STEP);
            return true;
        }
        if (isZoomOutShortcut(keyCode, modifiers)) {
            adjustZoom(-ZOOM_STEP);
            return true;
        }
        if (isResetZoomShortcut(keyCode, modifiers)) {
            if (browser != null) {
                browser.setZoomLevel(0.0D);
            }
            return true;
        }
        if (browser != null) {
            browser.sendKeyPress(keyCode, scanCode, modifiers);
            browser.setFocus(true);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr == 0) return false;
        if (browser != null) {
            browser.sendKeyTyped(chr, modifiers);
            browser.setFocus(true);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void removed() {
        MinecraftUseMod.LOGGER.info("[CodeScreen] removed() called for URL {} with final zoom {}", url, currentZoomLevel());
        if (browser != null) {
            MinecraftUseMod.LOGGER.info("[CodeScreen] Closing browser instance");
            browser.close();
            browser = null;
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    static record BrowserLayout(
        int panelLeft,
        int panelTop,
        int panelWidth,
        int panelHeight,
        int browserLeft,
        int browserTop,
        int browserWidth,
        int browserHeight
    ) {
        static BrowserLayout empty() {
            return new BrowserLayout(0, 0, 0, 0, 0, URL_BAR_HEIGHT, 0, 0);
        }

        int panelRight() {
            return panelLeft + panelWidth;
        }

        int panelBottom() {
            return panelTop + panelHeight;
        }

        int browserRight() {
            return browserLeft + browserWidth;
        }

        int browserBottom() {
            return browserTop + browserHeight;
        }

        boolean contains(double x, double y) {
            return x >= browserLeft && x <= browserRight() && y >= browserTop && y <= browserBottom();
        }
    }
}
