package com.minecraftuse.schematic;

import com.minecraftuse.MinecraftUseMod;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * Handles placing schematic blocks into the world.
 * Supports instant, ghost overlay, and replay (timelapse) modes.
 */
public class SchematicPlacer {

    public static void placeFromCache(String name, FabricClientCommandSource source) {
        // TODO M3: Implementation
        // 1. Look up .schem file in sidecar cache directory
        // 2. Parse NBT data via SchematicParser
        // 3. Get player position as origin
        // 4. Iterate block data and place blocks relative to origin
        // 5. Handle flags: --ghost (render overlay), --replay (timed placement)

        source.sendFeedback(Text.literal(
            "§e[MCUse] §7Block placement not yet implemented. Schematic loaded: " + name
        ));
    }
}
