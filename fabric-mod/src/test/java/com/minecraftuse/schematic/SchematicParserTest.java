package com.minecraftuse.schematic;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SchematicParserTest {

    // --- Varint decoding tests ---

    @Test
    void decodeVarints_singleByte() {
        // Values 0-127 fit in one byte
        byte[] data = {0x00, 0x01, 0x7F};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{0, 1, 127}, result);
    }

    @Test
    void decodeVarints_twoByte() {
        // 128 = 0x80 0x01 in varint encoding
        byte[] data = {(byte) 0x80, 0x01};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{128}, result);
    }

    @Test
    void decodeVarints_threeByte() {
        // 16384 = 0x80 0x80 0x01 in varint encoding
        byte[] data = {(byte) 0x80, (byte) 0x80, 0x01};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{16384}, result);
    }

    @Test
    void decodeVarints_multipleValues() {
        // 1, 300, 2
        // 1   = 0x01
        // 300 = 0xAC 0x02  (300 = 0b1_0010_1100, varint: 0b1_0101100 = 0xAC, 0b0000010 = 0x02)
        // 2   = 0x02
        byte[] data = {0x01, (byte) 0xAC, 0x02, 0x02};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{1, 300, 2}, result);
    }

    @Test
    void decodeVarints_emptyInput() {
        byte[] data = {};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{}, result);
    }

    @Test
    void decodeVarints_sequentialPaletteIndices() {
        // Simulate a small palette-indexed block data: [0, 1, 2, 1, 0]
        byte[] data = {0x00, 0x01, 0x02, 0x01, 0x00};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{0, 1, 2, 1, 0}, result);
    }

    @Test
    void decodeVarints_largePaletteIndex() {
        // Index 255 = 0xFF 0x01 in varint encoding
        byte[] data = {(byte) 0xFF, 0x01};
        int[] result = SchematicParser.decodeVarints(data);
        assertArrayEquals(new int[]{255}, result);
    }

    // --- Schematic record tests ---

    @Test
    void schematicRecord_storesDimensions() {
        SchematicParser.Schematic schem = new SchematicParser.Schematic(
            3, 16, 8, 24,
            Map.of("minecraft:air", 0, "minecraft:stone", 1),
            new int[]{0, 1, 1, 0},
            new int[]{0, 0, 0}
        );
        assertEquals(3, schem.version());
        assertEquals(16, schem.width());
        assertEquals(8, schem.height());
        assertEquals(24, schem.length());
        assertEquals(2, schem.palette().size());
        assertEquals(4, schem.blockData().length);
    }

    @Test
    void schematicRecord_paletteMapping() {
        Map<String, Integer> palette = Map.of(
            "minecraft:air", 0,
            "minecraft:oak_planks", 1,
            "minecraft:stone_bricks", 2
        );
        SchematicParser.Schematic schem = new SchematicParser.Schematic(
            3, 4, 4, 4, palette, new int[16], new int[]{-2, 0, -2}
        );
        assertEquals(Integer.valueOf(0), schem.palette().get("minecraft:air"));
        assertEquals(Integer.valueOf(1), schem.palette().get("minecraft:oak_planks"));
        assertEquals(Integer.valueOf(2), schem.palette().get("minecraft:stone_bricks"));
    }

    @Test
    void schematicRecord_offsetValues() {
        SchematicParser.Schematic schem = new SchematicParser.Schematic(
            3, 10, 5, 10,
            Map.of("minecraft:air", 0),
            new int[500],
            new int[]{-5, 0, -5}
        );
        assertArrayEquals(new int[]{-5, 0, -5}, schem.offset());
    }
}
