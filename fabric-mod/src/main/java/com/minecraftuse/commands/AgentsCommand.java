package com.minecraftuse.commands;

import com.minecraftuse.gui.AgentDashboardScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public class AgentsCommand {

    private static String pendingOpen = null;
    private static int pendingTicks = 0;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("agents")
                .executes(context -> {
                    pendingOpen = "dashboard";
                    pendingTicks = 3;
                    return 1;
                })
        );
    }

    public static void tick(MinecraftClient client) {
        if (pendingOpen != null && pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks == 0) {
                client.setScreen(new AgentDashboardScreen());
                pendingOpen = null;
            }
        }
    }
}
