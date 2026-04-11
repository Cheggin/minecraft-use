package com.minecraftuse.bridge;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

/**
 * Builds a Minecraft Text object from a string containing § formatting codes.
 * Unlike Text.literal(), this actually applies the formatting.
 */
public class FormattedText {

    private static final Map<Character, Formatting> FORMAT_MAP = Map.ofEntries(
        Map.entry('0', Formatting.BLACK),
        Map.entry('1', Formatting.DARK_BLUE),
        Map.entry('2', Formatting.DARK_GREEN),
        Map.entry('3', Formatting.DARK_AQUA),
        Map.entry('4', Formatting.DARK_RED),
        Map.entry('5', Formatting.DARK_PURPLE),
        Map.entry('6', Formatting.GOLD),
        Map.entry('7', Formatting.GRAY),
        Map.entry('8', Formatting.DARK_GRAY),
        Map.entry('9', Formatting.BLUE),
        Map.entry('a', Formatting.GREEN),
        Map.entry('b', Formatting.AQUA),
        Map.entry('c', Formatting.RED),
        Map.entry('d', Formatting.LIGHT_PURPLE),
        Map.entry('e', Formatting.YELLOW),
        Map.entry('f', Formatting.WHITE),
        Map.entry('l', Formatting.BOLD),
        Map.entry('m', Formatting.STRIKETHROUGH),
        Map.entry('n', Formatting.UNDERLINE),
        Map.entry('o', Formatting.ITALIC),
        Map.entry('r', Formatting.RESET)
    );

    /**
     * Parse a string with § codes into a properly formatted Text object.
     */
    public static Text parse(String input) {
        if (input == null || input.isEmpty()) return Text.empty();

        MutableText result = Text.empty();
        StringBuilder current = new StringBuilder();
        Formatting[] activeFormats = new Formatting[0];

        int i = 0;
        while (i < input.length()) {
            if (input.charAt(i) == '§' && i + 1 < input.length()) {
                // Flush current text with active formatting
                if (current.length() > 0) {
                    MutableText segment = Text.literal(current.toString());
                    for (Formatting f : activeFormats) {
                        segment = segment.formatted(f);
                    }
                    result.append(segment);
                    current.setLength(0);
                }

                char code = Character.toLowerCase(input.charAt(i + 1));
                Formatting fmt = FORMAT_MAP.get(code);
                if (fmt != null) {
                    if (fmt == Formatting.RESET) {
                        activeFormats = new Formatting[0];
                    } else {
                        // Add to active formats
                        Formatting[] newFormats = new Formatting[activeFormats.length + 1];
                        System.arraycopy(activeFormats, 0, newFormats, 0, activeFormats.length);
                        newFormats[activeFormats.length] = fmt;
                        activeFormats = newFormats;
                    }
                }
                i += 2;
            } else {
                current.append(input.charAt(i));
                i++;
            }
        }

        // Flush remaining text
        if (current.length() > 0) {
            MutableText segment = Text.literal(current.toString());
            for (Formatting f : activeFormats) {
                segment = segment.formatted(f);
            }
            result.append(segment);
        }

        return result;
    }
}
