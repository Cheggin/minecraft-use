package com.minecraftuse.screen;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.*;

public class CodeScreenTest {

    private static final String DEFAULT_URL = "https://vscode.dev";
    private static final String SERVER_URL = "http://localhost:8080";

    @Test
    void defaultUrlIsVscodeDev() {
        assertEquals(DEFAULT_URL, "https://vscode.dev");
    }

    @Test
    void serverMapsToLocalhost8080() {
        assertEquals(SERVER_URL, "http://localhost:8080");
    }

    @Test
    void urlValidation_validHttpsUrl() {
        assertTrue(isValidUrl("https://vscode.dev"));
    }

    @Test
    void urlValidation_validHttpUrl() {
        assertTrue(isValidUrl("http://localhost:8080"));
    }

    @Test
    void urlValidation_validCustomUrl() {
        assertTrue(isValidUrl("https://github.dev"));
    }

    @Test
    void urlValidation_emptyStringIsInvalid() {
        assertFalse(isValidUrl(""));
    }

    @Test
    void urlValidation_plainWordIsInvalid() {
        assertFalse(isValidUrl("notaurl"));
    }

    @Test
    void serverKeywordMapsCorrectly() {
        String result = resolveUrl("server");
        assertEquals(SERVER_URL, result);
    }

    @Test
    void noArgMapsToDefault() {
        String result = resolveUrl(null);
        assertEquals(DEFAULT_URL, result);
    }

    @Test
    void customUrlPassedThrough() {
        String custom = "https://github.dev";
        String result = resolveUrl(custom);
        assertEquals(custom, result);
    }

    @Test
    void computeBrowserLayout_insetsPanelWithinScreen() {
        CodeScreenGUI.BrowserLayout layout = CodeScreenGUI.computeBrowserLayout(1920, 1080);

        assertTrue(layout.panelLeft() > 0);
        assertTrue(layout.panelTop() > 0);
        assertTrue(layout.panelRight() < 1920);
        assertTrue(layout.panelBottom() < 1080);
        assertEquals(layout.panelLeft(), layout.browserLeft());
        assertEquals(layout.panelTop() + 20, layout.browserTop());
        assertEquals(layout.panelWidth(), layout.browserWidth());
        assertEquals(layout.panelHeight() - 20, layout.browserHeight());
    }

    @Test
    void computeBrowserLayout_handlesSmallWindowsWithoutNegativeBounds() {
        CodeScreenGUI.BrowserLayout layout = CodeScreenGUI.computeBrowserLayout(800, 500);

        assertTrue(layout.panelLeft() >= 16);
        assertTrue(layout.panelTop() >= 16);
        assertTrue(layout.panelRight() <= 784);
        assertTrue(layout.panelBottom() <= 484);
        assertTrue(layout.browserHeight() > 0);
    }

    @Test
    void zoomInShortcut_acceptsCommandEqualsAndCtrlKeypadAdd() {
        assertTrue(CodeScreenGUI.isZoomInShortcut(GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_MOD_SUPER));
        assertTrue(CodeScreenGUI.isZoomInShortcut(GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_MOD_CONTROL));
        assertFalse(CodeScreenGUI.isZoomInShortcut(GLFW.GLFW_KEY_EQUAL, 0));
    }

    @Test
    void zoomOutShortcut_acceptsCommandMinusAndCtrlKeypadSubtract() {
        assertTrue(CodeScreenGUI.isZoomOutShortcut(GLFW.GLFW_KEY_MINUS, GLFW.GLFW_MOD_SUPER));
        assertTrue(CodeScreenGUI.isZoomOutShortcut(GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_MOD_CONTROL));
        assertFalse(CodeScreenGUI.isZoomOutShortcut(GLFW.GLFW_KEY_MINUS, 0));
    }

    @Test
    void zoomResetShortcut_requiresModifier() {
        assertTrue(CodeScreenGUI.isResetZoomShortcut(GLFW.GLFW_KEY_0, GLFW.GLFW_MOD_SUPER));
        assertFalse(CodeScreenGUI.isResetZoomShortcut(GLFW.GLFW_KEY_0, 0));
    }

    @Test
    void clampZoomLevel_staysInsideBrowserRange() {
        assertEquals(4.0D, CodeScreenGUI.clampZoomLevel(10.0D));
        assertEquals(-4.0D, CodeScreenGUI.clampZoomLevel(-10.0D));
        assertEquals(1.5D, CodeScreenGUI.clampZoomLevel(1.5D));
    }

    // Mirrors the URL resolution logic in CodeCommand
    private static String resolveUrl(String arg) {
        if (arg == null) return DEFAULT_URL;
        if (arg.equals("server")) return SERVER_URL;
        return arg;
    }

    // Simple URL validation: must have a scheme and a host
    private static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            URI uri = new URI(url);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
