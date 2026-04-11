package com.minecraftuse.commands;

import com.minecraftuse.gui.CatalogScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class CatalogCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("catalog")
                .executes(context -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    client.execute(() -> client.setScreen(new CatalogScreen()));
                    context.getSource().sendFeedback(Text.literal("§e[MCUse] §7Opening catalog..."));
                    return 1;
                })
        );
    }
}
