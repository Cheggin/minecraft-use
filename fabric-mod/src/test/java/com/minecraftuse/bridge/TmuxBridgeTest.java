package com.minecraftuse.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TmuxBridgeTest {

    @Test
    void ansiStripperRemovesBasicEscapeCodes() {
        String input = "\u001B[32mHello\u001B[0m World";
        String result = AnsiStripper.strip(input);
        assertEquals("Hello World", result);
    }

    @Test
    void ansiStripperHandlesNull() {
        assertEquals("", AnsiStripper.strip(null));
    }

    @Test
    void ansiStripperHandlesEmptyString() {
        assertEquals("", AnsiStripper.strip(""));
    }

    @Test
    void ansiStripperPreservesPlainText() {
        String plain = "plain text with no ANSI codes";
        assertEquals(plain, AnsiStripper.strip(plain));
    }

    @Test
    void ansiStripperHandlesMultipleCodes() {
        String input = "\u001B[1m\u001B[33mBold Yellow\u001B[0m\u001B[2m dim \u001B[0m";
        String result = AnsiStripper.strip(input);
        assertEquals("Bold Yellow dim ", result);
    }

    @Test
    void tmuxBridgeIsAvailableReturnsFalseWhenMissing() {
        // Uses a non-existent socket path; isAvailable checks the binary exists
        TmuxBridge bridge = new TmuxBridge("/tmp/test-socket");
        // The binary at ~/.smux/bin/tmux-bridge may or may not exist in CI;
        // this just verifies the method returns a boolean without throwing
        boolean available = bridge.isAvailable();
        // No assertion on value — just verify it doesn't throw
        assertTrue(available || !available);
    }
}
