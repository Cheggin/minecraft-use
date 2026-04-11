package com.minecraftuse.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.minecraftuse.MinecraftUseMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class ListCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("list")
                .executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    source.sendFeedback(Text.literal("§e[MCUse] §fListing cached schematics..."));

                    MinecraftUseMod.SIDECAR.list()
                        .thenAccept(schematics -> {
                            if (schematics.isEmpty()) {
                                source.sendFeedback(Text.literal("§e[MCUse] §7No schematics cached. Use /download <query> to get some."));
                                return;
                            }

                            source.sendFeedback(Text.literal("§e[MCUse] §f--- Cached Schematics ---"));
                            for (JsonElement elem : schematics) {
                                String name = elem.getAsJsonObject().get("name").getAsString();
                                String size = elem.getAsJsonObject().has("size")
                                    ? elem.getAsJsonObject().get("size").getAsString()
                                    : "unknown";

                                MutableText entry = Text.literal("  §a▸ §f" + name + " §7(" + size + ")")
                                    .styled(style -> style
                                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/build " + name))
                                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to build")))
                                    );
                                source.sendFeedback(entry);
                            }
                        })
                        .exceptionally(error -> {
                            source.sendFeedback(Text.literal(
                                "§e[MCUse] §cFailed to list schematics. Is the sidecar running?"
                            ));
                            return null;
                        });

                    return 1;
                })
                .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(context -> {
                        String query = StringArgumentType.getString(context, "query");
                        FabricClientCommandSource source = context.getSource();
                        source.sendFeedback(Text.literal("§e[MCUse] §fSearching cached schematics for: " + query));
                        // TODO: fuzzy search implementation
                        return 1;
                    }))
        );
    }
}
