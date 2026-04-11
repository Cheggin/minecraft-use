package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.schematic.SchematicPlacer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

public class BuildCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("build")
                .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        FabricClientCommandSource source = context.getSource();

                        source.sendFeedback(Text.literal("§e[MCUse] §fBuilding schematic: " + name + "..."));

                        // TODO: Parse flags (--scale, --swap, --ghost, --replay)
                        // TODO: Load schematic from cache
                        // TODO: Place blocks via SchematicPlacer

                        SchematicPlacer.placeFromCache(name, source);

                        return 1;
                    }))
        );
    }
}
