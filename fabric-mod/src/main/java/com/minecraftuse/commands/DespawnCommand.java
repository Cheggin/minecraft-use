package com.minecraftuse.commands;

import com.minecraftuse.villager.VillagerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;
import java.util.List;

public class DespawnCommand {

    private static final String HOMEBREW_BIN = "/opt/homebrew/bin";
    private static final int PROCESS_TIMEOUT_SECONDS = 10;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("despawn")
                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                    .executes(context -> executeDespawn(context.getSource(),
                        StringArgumentType.getString(context, "name"))))
        );
    }

    private static int executeDespawn(FabricClientCommandSource source, String name) {
        VillagerRegistry registry = VillagerRegistry.getInstance();
        VillagerRegistry.AgentVillagerData data = registry.getByName(name);

        if (data == null) {
            source.sendFeedback(Text.literal("§e[MCUse] §cNo agent named '" + name + "' found."));
            return 0;
        }

        String paneName = data.paneName();

        // Unregister: stops poller, removes floating text, discards villager entity
        registry.unregister(name);

        // Kill the tmux pane async to avoid blocking
        Thread despawnThread = new Thread(() -> {
            try {
                // Use tmux kill-pane by pane name target
                ProcessBuilder pb = new ProcessBuilder("/opt/homebrew/bin/tmux", "kill-pane", "-t", paneName);
                pb.redirectErrorStream(true);

                String currentPath = pb.environment().getOrDefault("PATH", "/usr/bin:/bin");
                if (!currentPath.contains(HOMEBREW_BIN)) {
                    pb.environment().put("PATH", HOMEBREW_BIN + ":" + currentPath);
                }

                Process process = pb.start();
                boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                }
            } catch (Exception e) {
                // Silently ignore pane kill failures — villager and poller are already cleaned up
            }
        }, "DespawnCommand-" + name);

        despawnThread.setDaemon(true);
        despawnThread.start();

        source.sendFeedback(Text.literal("§e[MCUse] §aDespawned agent: §f" + name));
        return 1;
    }
}
