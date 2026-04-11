package com.minecraftuse.schematic;

import com.minecraftuse.MinecraftUseMod;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles placing schematic blocks into the world.
 * Batches placement to avoid frame drops: 1000 blocks per tick.
 */
public class SchematicPlacer {

    private static final int BLOCKS_PER_TICK = 1000;

    // Cache directory relative to game directory
    private static final String CACHE_DIR = "minecraftuse/cache";

    public static void placeFromCacheWithSwap(String name, Map<String, String> materialSwap, FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        File gameDir = client.runDirectory;
        File cacheDir = new File(gameDir, CACHE_DIR);
        File schemFile = new File(cacheDir, name.endsWith(".schem") ? name : name + ".schem");

        if (!schemFile.exists()) {
            source.sendFeedback(Text.literal("§e[MCUse] §cSchematic not found in cache: " + schemFile.getName()));
            return;
        }

        SchematicParser.Schematic schematic;
        try {
            schematic = SchematicParser.parse(schemFile);
        } catch (IOException e) {
            MinecraftUseMod.LOGGER.error("[SchematicPlacer] Failed to parse schematic: {}", e.getMessage());
            source.sendFeedback(Text.literal("§e[MCUse] §cFailed to parse schematic: " + e.getMessage()));
            return;
        }

        ClientPlayerEntity player = source.getPlayer();
        BlockPos origin = player.getBlockPos();

        source.sendFeedback(Text.literal(
            "§e[MCUse] §fPlacing §a" + schematic.blockData().length + "§f blocks at " +
            origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + " §7(with material swap)"
        ));

        placeWithSwap(schematic, origin, materialSwap, source);
    }

    public static void placeFromCache(String name, FabricClientCommandSource source) {
        MinecraftClient client = MinecraftClient.getInstance();
        File gameDir = client.runDirectory;
        File cacheDir = new File(gameDir, CACHE_DIR);
        File schemFile = new File(cacheDir, name.endsWith(".schem") ? name : name + ".schem");

        if (!schemFile.exists()) {
            source.sendFeedback(Text.literal("§e[MCUse] §cSchematic not found in cache: " + schemFile.getName()));
            return;
        }

        SchematicParser.Schematic schematic;
        try {
            schematic = SchematicParser.parse(schemFile);
        } catch (IOException e) {
            MinecraftUseMod.LOGGER.error("[SchematicPlacer] Failed to parse schematic: {}", e.getMessage());
            source.sendFeedback(Text.literal("§e[MCUse] §cFailed to parse schematic: " + e.getMessage()));
            return;
        }

        ClientPlayerEntity player = source.getPlayer();
        BlockPos origin = player.getBlockPos();

        source.sendFeedback(Text.literal(
            "§e[MCUse] §fPlacing §a" + schematic.blockData().length + "§f blocks at " +
            origin.getX() + ", " + origin.getY() + ", " + origin.getZ()
        ));

        place(schematic, origin, null, source);
    }

    /**
     * Place schematic blocks at origin. Skips air blocks.
     * Batches 1000 blocks per tick via scheduled tasks.
     * source may be null when called from GUI context.
     */
    public static void place(
        SchematicParser.Schematic schematic,
        BlockPos origin,
        Map<String, String> materialSwap,
        FabricClientCommandSource source
    ) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            if (source != null) source.sendFeedback(Text.literal("§e[MCUse] §cNo world available."));
            return;
        }

        // Build reverse palette: index -> blockstate string
        String[] indexToBlockState = buildIndexToBlockState(schematic.palette());

        // Collect non-air block positions and states
        List<PlacementEntry> placements = buildPlacementList(schematic, origin, indexToBlockState, materialSwap);

        if (placements.isEmpty()) {
            if (source != null) source.sendFeedback(Text.literal("§e[MCUse] §7No blocks to place."));
            return;
        }

        // Schedule batched placement
        schedulePlacementBatches(placements, world, source);
    }

    /**
     * Place with material substitution map: original blockstate -> replacement blockstate.
     */
    public static void placeWithSwap(
        SchematicParser.Schematic schematic,
        BlockPos origin,
        Map<String, String> materialSwap,
        FabricClientCommandSource source
    ) {
        place(schematic, origin, materialSwap, source);
    }

    private static String[] buildIndexToBlockState(Map<String, Integer> palette) {
        int maxIndex = 0;
        for (int idx : palette.values()) {
            if (idx > maxIndex) maxIndex = idx;
        }
        String[] indexToBlockState = new String[maxIndex + 1];
        for (Map.Entry<String, Integer> entry : palette.entrySet()) {
            indexToBlockState[entry.getValue()] = entry.getKey();
        }
        return indexToBlockState;
    }

    private static List<PlacementEntry> buildPlacementList(
        SchematicParser.Schematic schematic,
        BlockPos origin,
        String[] indexToBlockState,
        Map<String, String> materialSwap
    ) {
        int width = schematic.width();
        int height = schematic.height();
        int length = schematic.length();
        int[] blockData = schematic.blockData();
        int[] offset = schematic.offset();

        List<PlacementEntry> placements = new ArrayList<>();

        for (int i = 0; i < blockData.length; i++) {
            int paletteIndex = blockData[i];
            if (paletteIndex < 0 || paletteIndex >= indexToBlockState.length) continue;

            String blockStateStr = indexToBlockState[paletteIndex];
            if (blockStateStr == null) continue;

            // Apply material swap if provided
            if (materialSwap != null && materialSwap.containsKey(blockStateStr)) {
                blockStateStr = materialSwap.get(blockStateStr);
            }

            // Skip air
            if (blockStateStr.contains("minecraft:air") || blockStateStr.equals("minecraft:void_air") || blockStateStr.equals("minecraft:cave_air")) {
                continue;
            }

            // Compute XYZ from linear index (Sponge order: x + z*width + y*width*length)
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);

            BlockPos pos = origin.add(
                x + offset[0],
                y + offset[1],
                z + offset[2]
            );

            BlockState state = parseBlockState(blockStateStr);
            if (state != null) {
                placements.add(new PlacementEntry(pos, state));
            }
        }

        return placements;
    }

    private static void schedulePlacementBatches(
        List<PlacementEntry> placements,
        ClientWorld world,
        FabricClientCommandSource source
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        int total = placements.size();

        // Use a holder array to track progress across ticks
        int[] cursor = {0};

        // Schedule first batch immediately; subsequent batches via tick event
        Runnable batchRunner = new Runnable() {
            @Override
            public void run() {
                int end = Math.min(cursor[0] + BLOCKS_PER_TICK, total);
                for (int i = cursor[0]; i < end; i++) {
                    PlacementEntry entry = placements.get(i);
                    world.setBlockState(entry.pos(), entry.state(), Block.NOTIFY_ALL);
                }
                cursor[0] = end;

                if (cursor[0] < total) {
                    // Schedule next batch on next tick
                    Runnable self = this;
                    client.execute(self);
                } else {
                    Text msg = Text.literal("§e[MCUse] §aPlacement complete: " + total + " blocks placed.");
                    if (source != null) {
                        source.sendFeedback(msg);
                    } else if (client.player != null) {
                        client.player.sendMessage(msg, false);
                    }
                }
            }
        };

        client.execute(batchRunner);
    }

    /**
     * Parses a blockstate string like "minecraft:oak_log[axis=y]" into a BlockState.
     * Falls back to the base block's default state if properties can't be applied.
     */
    private static BlockState parseBlockState(String blockStateStr) {
        // Strip properties for now — parse just the block ID
        String blockId = blockStateStr;
        int bracketIndex = blockStateStr.indexOf('[');
        if (bracketIndex != -1) {
            blockId = blockStateStr.substring(0, bracketIndex);
        }

        Identifier id = Identifier.tryParse(blockId);
        if (id == null) {
            MinecraftUseMod.LOGGER.warn("[SchematicPlacer] Invalid block id: {}", blockId);
            return null;
        }

        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR && !blockId.equals("minecraft:air")) {
            MinecraftUseMod.LOGGER.warn("[SchematicPlacer] Unknown block: {}", blockId);
            return null;
        }

        return block.getDefaultState();
    }

    private record PlacementEntry(BlockPos pos, BlockState state) {}
}
