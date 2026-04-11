package com.minecraftuse.bridge;

import java.util.regex.Pattern;

public class AnsiStripper {

    private static final Pattern ANSI_PATTERN = Pattern.compile(
        "\\x1B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])"
    );

    public static String strip(String input) {
        if (input == null) return "";
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }

    /**
     * Strip non-color ANSI sequences (cursor movement, erase, etc.)
     * but preserve § formatting codes that were already inserted.
     */
    public static String stripNonColor(String input) {
        if (input == null) return "";
        // Remove any remaining raw ANSI escapes that aren't SGR (color) codes
        // SGR codes were already converted to § by AnsiToMinecraft
        return ANSI_PATTERN.matcher(input).replaceAll("");
    }
}
