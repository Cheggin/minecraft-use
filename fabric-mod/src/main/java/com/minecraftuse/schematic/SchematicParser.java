package com.minecraftuse.schematic;

import com.minecraftuse.MinecraftUseMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses .schem (Sponge v3) schematic files into an in-memory representation.
 */
public class SchematicParser {

    // Sponge schematic NBT keys
    private static final String KEY_SCHEMATIC = "Schematic";
    private static final String KEY_VERSION = "Version";
    private static final String KEY_WIDTH = "Width";
    private static final String KEY_HEIGHT = "Height";
    private static final String KEY_LENGTH = "Length";
    private static final String KEY_BLOCKS = "Blocks";
    private static final String KEY_PALETTE = "Palette";
    private static final String KEY_DATA = "Data";
    private static final String KEY_OFFSET = "Offset";

    // Supported Sponge schematic version
    private static final int SUPPORTED_VERSION = 3;

    // Varint constants
    private static final int VARINT_SEGMENT_BITS = 0x7F;
    private static final int VARINT_CONTINUE_BIT = 0x80;

    public record Schematic(
        int version,
        int width,
        int height,
        int length,
        Map<String, Integer> palette,
        int[] blockData,
        int[] offset
    ) {}

    public static Schematic parse(File schemFile) throws IOException {
        NbtCompound root = NbtIo.readCompressed(schemFile.toPath(), NbtSizeTracker.of(100 * 1024 * 1024L));

        // Sponge v3: root contains "Schematic" compound, v2 is the root itself
        NbtCompound schematic;
        if (root.contains(KEY_SCHEMATIC)) {
            schematic = root.getCompound(KEY_SCHEMATIC);
        } else {
            schematic = root;
        }

        int version = schematic.getInt(KEY_VERSION);
        MinecraftUseMod.LOGGER.info("[SchematicParser] Parsing schematic version {} from {}", version, schemFile.getName());

        int width  = schematic.getShort(KEY_WIDTH)  & 0xFFFF;
        int height = schematic.getShort(KEY_HEIGHT) & 0xFFFF;
        int length = schematic.getShort(KEY_LENGTH) & 0xFFFF;

        // Offset: int[3] = [x, y, z], default to [0, 0, 0]
        int[] offset = new int[]{0, 0, 0};
        if (schematic.contains(KEY_OFFSET)) {
            int[] rawOffset = schematic.getIntArray(KEY_OFFSET);
            if (rawOffset.length >= 3) {
                offset[0] = rawOffset[0];
                offset[1] = rawOffset[1];
                offset[2] = rawOffset[2];
            }
        }

        // Parse palette: Palette lives under Blocks.Palette (v3) or directly under Palette (v2)
        NbtCompound paletteCompound;
        NbtCompound blocksSection = null;
        if (schematic.contains(KEY_BLOCKS)) {
            blocksSection = schematic.getCompound(KEY_BLOCKS);
            paletteCompound = blocksSection.getCompound(KEY_PALETTE);
        } else {
            paletteCompound = schematic.getCompound(KEY_PALETTE);
        }

        Map<String, Integer> palette = new HashMap<>();
        for (String key : paletteCompound.getKeys()) {
            palette.put(key, paletteCompound.getInt(key));
        }

        // Parse block data byte array — varint encoded
        byte[] rawData;
        if (blocksSection != null && blocksSection.contains(KEY_DATA)) {
            rawData = blocksSection.getByteArray(KEY_DATA);
        } else {
            rawData = schematic.getByteArray(KEY_DATA);
        }

        int[] blockData = decodeVarints(rawData);

        MinecraftUseMod.LOGGER.info("[SchematicParser] Parsed schematic: {}x{}x{}, palette size={}, blocks={}",
            width, height, length, palette.size(), blockData.length);

        return new Schematic(version, width, height, length, palette, blockData, offset);
    }

    /**
     * Decodes a byte array of varint-encoded integers into an int array.
     * Each varint uses the standard 7-bit encoding: the high bit of each byte
     * signals that more bytes follow.
     */
    public static int[] decodeVarints(byte[] data) {
        int[] result = new int[data.length]; // upper bound; actual count may be smaller
        int resultIndex = 0;
        int byteIndex = 0;

        while (byteIndex < data.length) {
            int value = 0;
            int shift = 0;

            while (true) {
                if (byteIndex >= data.length) {
                    break;
                }
                int b = data[byteIndex++] & 0xFF;
                value |= (b & VARINT_SEGMENT_BITS) << shift;
                shift += 7;
                if ((b & VARINT_CONTINUE_BIT) == 0) {
                    break;
                }
                if (shift >= 32) {
                    throw new IllegalArgumentException("Varint too large at byte index " + byteIndex);
                }
            }

            result[resultIndex++] = value;
        }

        // Trim to actual size
        int[] trimmed = new int[resultIndex];
        System.arraycopy(result, 0, trimmed, 0, resultIndex);
        return trimmed;
    }
}
