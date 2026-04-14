package com.minecraftuse.screen;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.MCEFRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
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

public class CodeScreenGUI extends Screen {

    private static final String DEFAULT_URL = "https://vscode.dev";
    private static final String SERVER_URL = "http://localhost:8080";
    private static final int URL_BAR_HEIGHT = 20;
    private static final int URL_BAR_PADDING = 4;
    private static final int URL_BAR_BG_COLOR = 0xCC1E1E1E;
    private static final int URL_BAR_TEXT_COLOR = 0xFFCCCCCC;
    private static final int URL_BAR_LABEL_COLOR = 0xFF888888;

    private MCEFBrowser browser;
    private final String url;

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
            browser = MCEF.createBrowser(url, false);
        }
        resizeBrowser();
    }

    private void resizeBrowser() {
        if (browser == null) return;
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        int browserWidth = (int) (width * scale);
        int browserHeight = (int) ((height - URL_BAR_HEIGHT) * scale);
        if (browserWidth > 0 && browserHeight > 0) {
            browser.resize(browserWidth, browserHeight);
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        resizeBrowser();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (browser != null) {
            MCEFRenderer renderer = browser.getRenderer();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
            RenderSystem.setShaderTexture(0, renderer.getTextureID());

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buf = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            float x0 = 0;
            float y0 = URL_BAR_HEIGHT;
            float x1 = width;
            float y1 = height;

            buf.vertex(x0, y1, 0).texture(0, 1).color(255, 255, 255, 255);
            buf.vertex(x1, y1, 0).texture(1, 1).color(255, 255, 255, 255);
            buf.vertex(x1, y0, 0).texture(1, 0).color(255, 255, 255, 255);
            buf.vertex(x0, y0, 0).texture(0, 0).color(255, 255, 255, 255);

            BufferRenderer.drawWithGlobalProgram(buf.end());

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }

        // URL bar overlay
        context.fill(0, 0, width, URL_BAR_HEIGHT, URL_BAR_BG_COLOR);
        String label = "§7" + url;
        int labelWidth = textRenderer.getWidth(label);
        context.drawText(textRenderer, Text.literal(url), (width - labelWidth) / 2, URL_BAR_PADDING, URL_BAR_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("ESC to close"), URL_BAR_PADDING, URL_BAR_PADDING, URL_BAR_LABEL_COLOR, false);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't render the default background — the browser fills the screen
    }

    private int scaledMouseX(double mouseX) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return (int) (mouseX * scale);
    }

    private int scaledMouseY(double mouseY) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        return (int) ((mouseY - URL_BAR_HEIGHT) * scale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null && mouseY > URL_BAR_HEIGHT) {
            browser.sendMousePress(scaledMouseX(mouseX), scaledMouseY(mouseY), button);
            browser.setFocus(true);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null && mouseY > URL_BAR_HEIGHT) {
            browser.sendMouseRelease(scaledMouseX(mouseX), scaledMouseY(mouseY), button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            browser.sendMouseMove(scaledMouseX(mouseX), scaledMouseY(mouseY));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null && mouseY > URL_BAR_HEIGHT) {
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
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
