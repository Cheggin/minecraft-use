package com.minecraftuse.commands;

import com.minecraftuse.villager.VillagerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DespawnCommand {

    private static final int PROCESS_TIMEOUT_SECONDS = 5;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("despawn")
                .then(ClientCommandManager.literal("--all")
                    .executes(context -> executeDespawnAll(context.getSource())))
                .then(ClientCommandManager.literal("--port")
                    .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                        .executes(context -> executeDespawnPort(context.getSource(),
                            IntegerArgumentType.getInteger(context, "port")))))
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

        registry.unregister(name);
        source.sendFeedback(Text.literal("§e[MCUse] §aDespawned agent: §f" + name));
        return 1;
    }

    private static int executeDespawnPort(FabricClientCommandSource source, int port) {
        // Also despawn any agent that's monitoring this port
        VillagerRegistry registry = VillagerRegistry.getInstance();
        List<String> matching = new ArrayList<>();
        for (Map.Entry<String, VillagerRegistry.AgentVillagerData> entry : registry.getAll()) {
            if (entry.getValue().monitoredPort() != null && entry.getValue().monitoredPort() == port) {
                matching.add(entry.getKey());
            }
        }
        for (String name : matching) {
            registry.unregister(name);
            source.sendFeedback(Text.literal("§e[MCUse] §aDespawned agent: §f" + name + " §7(port " + port + ")"));
        }

        // Kill all processes on the port
        Thread killThread = new Thread(() -> {
            try {
                ProcessBuilder lsofPb = new ProcessBuilder("lsof", "-ti", ":" + port);
                lsofPb.redirectErrorStream(true);
                Process lsofProc = lsofPb.start();
                String pidOutput = new String(lsofProc.getInputStream().readAllBytes()).trim();
                lsofProc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!pidOutput.isEmpty()) {
                    for (String pid : pidOutput.split("\\s+")) {
                        try {
                            new ProcessBuilder("kill", pid.trim()).start()
                                .waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }, "PortKill-" + port);
        killThread.setDaemon(true);
        killThread.start();

        source.sendFeedback(Text.literal("§e[MCUse] §aKilling all processes on port §f" + port));
        return 1;
    }

    private static int executeDespawnAll(FabricClientCommandSource source) {
        VillagerRegistry registry = VillagerRegistry.getInstance();

        // Collect names first to avoid concurrent modification
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, VillagerRegistry.AgentVillagerData> entry : registry.getAll()) {
            names.add(entry.getKey());
        }

        if (names.isEmpty()) {
            source.sendFeedback(Text.literal("§e[MCUse] §cNo agents to despawn."));
            return 0;
        }

        for (String name : names) {
            registry.unregister(name);
        }

        source.sendFeedback(Text.literal("§e[MCUse] §aDespawned all agents §7(" + names.size() + ")"));
        return 1;
    }
}
