package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

public class DownloadCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("download")
                .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                    .executes(context -> {
                        String query = StringArgumentType.getString(context, "query");
                        FabricClientCommandSource source = context.getSource();

                        source.sendFeedback(Text.literal("§e[MCUse] §fSearching for: " + query + "..."));

                        MinecraftUseMod.SIDECAR.download(query)
                            .thenAccept(result -> {
                                String filename = result.get("filename").getAsString();
                                String siteName = result.has("source") ? result.get("source").getAsString() : "unknown";
                                source.sendFeedback(Text.literal(
                                    "§e[MCUse] §aDownloaded: §f" + filename + " §7(from " + siteName + ")"
                                ));
                            })
                            .exceptionally(error -> {
                                source.sendFeedback(Text.literal(
                                    "§e[MCUse] §cDownload failed: " + error.getMessage()
                                ));
                                return null;
                            });

                        return 1;
                    }))
        );
    }
}
