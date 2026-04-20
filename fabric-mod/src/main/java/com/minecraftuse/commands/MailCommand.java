package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.gui.MailScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public class MailCommand {

    private static boolean pendingOpen = false;
    private static int pendingTicks = 0;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("mail")
                .executes(context -> {
                    pendingOpen = true;
                    pendingTicks = 3;
                    return 1;
                })
        );
    }

    public static void tick(MinecraftClient client) {
        if (pendingOpen && pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks == 0) {
                client.setScreen(new MailScreen(MinecraftUseMod.MAIL));
                pendingOpen = false;
            }
        }
    }
}
