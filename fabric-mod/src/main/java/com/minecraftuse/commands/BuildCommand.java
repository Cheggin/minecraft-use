package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.schematic.SchematicPlacer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class BuildCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("build")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        String args = StringArgumentType.getString(context, "name");
                        FabricClientCommandSource source = context.getSource();

                        // Parse flags from args: --swap old=new
                        String name = args;
                        Map<String, String> swapMap = new HashMap<>();

                        String[] parts = args.split("\\s+");
                        StringBuilder nameBuilder = new StringBuilder();
                        for (int i = 0; i < parts.length; i++) {
                            if (parts[i].equals("--swap") && i + 1 < parts.length) {
                                String[] swapParts = parts[i + 1].split("=", 2);
                                if (swapParts.length == 2) {
                                    swapMap.put(swapParts[0], swapParts[1]);
                                }
                                i++; // skip next token
                            } else if (!parts[i].startsWith("--")) {
                                if (nameBuilder.length() > 0) nameBuilder.append(' ');
                                nameBuilder.append(parts[i]);
                            }
                        }
                        name = nameBuilder.length() > 0 ? nameBuilder.toString() : args;

                        MinecraftUseMod.LOGGER.info("[BuildCommand] Building schematic: {}", name);
                        source.sendFeedback(Text.literal("§e[MCUse] §fBuilding schematic: §a" + name + "§f..."));

                        if (!swapMap.isEmpty()) {
                            source.sendFeedback(Text.literal("§e[MCUse] §7Material swaps: " + swapMap));
                            SchematicPlacer.placeFromCacheWithSwap(name, swapMap, source);
                        } else {
                            SchematicPlacer.placeFromCache(name, source);
                        }

                        return 1;
                    }))
        );
    }
}
