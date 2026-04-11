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
}
