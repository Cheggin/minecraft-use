package com.minecraftuse.bridge;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts ANSI escape codes to Minecraft § formatting codes.
 * Supports standard colors, bright colors, bold, and reset.
 */
public class AnsiToMinecraft {

    private static final Pattern ANSI_PATTERN = Pattern.compile(
        "\\x1B\\[([0-9;]*)m"
    );

    // Standard ANSI foreground colors (30-37) → Minecraft § codes
    private static final Map<Integer, String> FG_COLORS = Map.ofEntries(
        Map.entry(30, "§0"),  // black
        Map.entry(31, "§c"),  // red
        Map.entry(32, "§a"),  // green
        Map.entry(33, "§e"),  // yellow
        Map.entry(34, "§9"),  // blue
        Map.entry(35, "§d"),  // magenta
        Map.entry(36, "§b"),  // cyan
        Map.entry(37, "§f"),  // white
        // Bright colors (90-97)
        Map.entry(90, "§8"),  // bright black (gray)
        Map.entry(91, "§c"),  // bright red
        Map.entry(92, "§a"),  // bright green
        Map.entry(93, "§e"),  // bright yellow
        Map.entry(94, "§9"),  // bright blue
        Map.entry(95, "§d"),  // bright magenta
        Map.entry(96, "§b"),  // bright cyan
        Map.entry(97, "§f")   // bright white
    );

    /**
     * Convert ANSI escape codes in the input to Minecraft § formatting.
     * Unknown/unsupported codes are removed.
     */
    public static String convert(String input) {
        if (input == null) return "";

        Matcher matcher = ANSI_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // Append text before this escape code
            result.append(input, lastEnd, matcher.start());

            // Parse the SGR parameters
            String params = matcher.group(1);
            if (params.isEmpty()) {
                result.append("§r");
            } else {
                String[] parts = params.split(";");
                int i = 0;
                while (i < parts.length) {
                    try {
                        int code = Integer.parseInt(parts[i]);
                        // Handle 256-color: 38;5;N (foreground) or 48;5;N (background)
                        if ((code == 38 || code == 48) && i + 2 < parts.length
                                && parts[i + 1].equals("5")) {
                            int colorIndex = Integer.parseInt(parts[i + 2]);
                            if (code == 38) {
                                result.append(map256Color(colorIndex));
                            }
                            // Skip background colors (48;5;N) — can't render in MC
                            i += 3;
                            continue;
                        }
                        // Handle 24-bit color: 38;2;R;G;B
                        if ((code == 38 || code == 48) && i + 4 < parts.length
                                && parts[i + 1].equals("2")) {
                            if (code == 38) {
                                int r = Integer.parseInt(parts[i + 2]);
                                int g = Integer.parseInt(parts[i + 3]);
                                int b = Integer.parseInt(parts[i + 4]);
                                result.append(mapRgbColor(r, g, b));
                            }
                            i += 5;
                            continue;
                        }
                        result.append(mapCode(code));
                    } catch (NumberFormatException ignored) {
                    }
                    i++;
                }
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        result.append(input, lastEnd, input.length());

        // Also strip any remaining non-color ANSI sequences (cursor movement, etc.)
        return AnsiStripper.strip(result.toString());
    }

    /** Map 256-color index to nearest Minecraft § color */
    private static String map256Color(int index) {
        if (index < 8) return FG_COLORS.getOrDefault(30 + index, "§f");
        if (index < 16) return FG_COLORS.getOrDefault(90 + (index - 8), "§f");

        // Common Claude Code colors — hardcoded for accuracy
        // 174 = pink/salmon (tool names), 211 = light pink (highlights)
        // 246 = gray (muted text), 244 = dark gray (separators)
        // 231 = white (normal text), 239 = very dark gray
        switch (index) {
            case 174: return "§d";  // pink → light purple
            case 211: return "§d";  // light pink → light purple
            case 246: return "§7";  // gray → gray
            case 244: return "§8";  // dark gray → dark gray
            case 231: return "§f";  // white → white
            case 239: return "§8";  // very dark gray → dark gray
            case 237: return "§8";  // dark gray background → dark gray
        }

        // 216-color cube (16-231) and grayscale (232-255)
        if (index >= 232) {
            int level = index - 232; // 0-23
            if (level < 6) return "§0";
            if (level < 12) return "§8";
            if (level < 18) return "§7";
            return "§f";
        }
        // 216-color cube — approximate to nearest MC color
        index -= 16;
        int r = index / 36;
        int g = (index % 36) / 6;
        int b = index % 6;
        return mapRgbApprox(r * 51, g * 51, b * 51);
    }

    /** Map RGB to nearest Minecraft § color */
    private static String mapRgbColor(int r, int g, int b) {
        return mapRgbApprox(r, g, b);
    }

    private static String mapRgbApprox(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        if (max < 30) return "§0";           // black
        if (r > 180 && g < 80 && b < 80) return "§c";    // red
        if (g > 180 && r < 80 && b < 80) return "§a";    // green
        if (b > 180 && r < 80 && g < 80) return "§9";    // blue
        if (r > 180 && g > 180 && b < 80) return "§e";   // yellow
        if (r > 180 && g < 80 && b > 180) return "§d";   // magenta
        if (g > 180 && b > 180 && r < 80) return "§b";   // cyan
        if (r > 200 && g > 200 && b > 200) return "§f";  // white
        if (r > 150 && g > 100 && b < 100) return "§6";  // gold/orange
        if (max < 100) return "§8";                        // dark gray
        if (max < 180) return "§7";                        // gray
        return "§f";                                       // default white
    }

    private static String mapCode(int code) {
        if (code == 0) return "§r";       // reset
        if (code == 1) return "§l";       // bold
        if (code == 2) return "§7";       // dim → gray
        if (code == 3) return "§o";       // italic
        if (code == 4) return "§n";       // underline
        if (code == 7) return "";          // reverse video — skip
        if (code == 9) return "§m";       // strikethrough
        if (code == 22) return "§r";      // normal intensity → reset
        if (code == 39) return "§r";      // default foreground → reset
        if (code == 49) return "";         // default background → skip

        String color = FG_COLORS.get(code);
        if (color != null) return color;

        // Background colors (40-47, 100-107) — can't render in MC, skip
        if (code >= 40 && code <= 47) return "";
        if (code >= 100 && code <= 107) return "";
        return "";
    }
}
