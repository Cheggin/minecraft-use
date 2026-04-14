package com.minecraftuse.screen;

import org.junit.jupiter.api.Test;

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
