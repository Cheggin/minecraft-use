package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.minecraftuse.villager.AgentVillager;
import com.minecraftuse.villager.FloatingText;
import com.minecraftuse.villager.OutputPoller;
import com.minecraftuse.villager.VillagerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class SpawnCommand {

    private static final String TMUX_BRIDGE_PATH = System.getProperty("user.home") + "/.smux/bin/tmux-bridge";
    private static final String TMUX_PATH = "/opt/homebrew/bin/tmux";
    private static final String HOMEBREW_BIN = "/opt/homebrew/bin";
    private static final int PROCESS_TIMEOUT_SECONDS = 10;
    private static final String TMUX_SESSION = "minecraft-use";
    private static final String AGENTS_WINDOW = "agents";

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("agent")
                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                    .executes(context -> executeSpawn(context.getSource(),
                        StringArgumentType.getString(context, "name"), "villager", null))
                    .then(ClientCommandManager.argument("mob_type", StringArgumentType.string())
                        .executes(context -> executeSpawn(context.getSource(),
                            StringArgumentType.getString(context, "name"),
                            StringArgumentType.getString(context, "mob_type"), null))
                        .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                            .executes(context -> executeSpawn(context.getSource(),
                                StringArgumentType.getString(context, "name"),
                                StringArgumentType.getString(context, "mob_type"),
                                StringArgumentType.getString(context, "command"))))))
        );
    }

    private static final String DEFAULT_COMMAND = "lfg";

    private static int executeSpawn(FabricClientCommandSource source, String name, String mobType, String command) {
        // Default to lfg (Claude Code) if no command specified
        final String finalCommand = (command == null || command.isBlank()) ? DEFAULT_COMMAND : command;
        final String finalMobType = (mobType == null || mobType.isBlank()) ? "villager" : mobType.toLowerCase();
        VillagerRegistry registry = VillagerRegistry.getInstance();

        if (registry.contains(name)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent '" + name + "' already exists. Use /despawn first."));
            return 0;
        }

        // Check tmux-bridge availability
        if (!new File(TMUX_BRIDGE_PATH).canExecute()) {
            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
            return 0;
        }

        // Run the rest async to avoid blocking the game thread
        Thread spawnThread = new Thread(() -> {
            try {
                // Ensure the agents window exists in the minecraft-use session
                String existingWindows = runProcess(List.of(
                    TMUX_PATH, "list-windows", "-t", TMUX_SESSION, "-F", "#{window_name}"
                ));
                boolean agentsWindowExists = existingWindows.lines()
                    .anyMatch(line -> line.trim().equals(AGENTS_WINDOW));

                if (!agentsWindowExists) {
                    runProcess(List.of(TMUX_PATH, "new-window", "-t", TMUX_SESSION, "-n", AGENTS_WINDOW));
                }

                // Create a new pane in the agents window and apply tiled layout
                runProcess(List.of(TMUX_PATH, "split-window", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW));
                runProcess(List.of(TMUX_PATH, "select-layout", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW, "tiled"));

                // Capture new pane ID from the agents window
                String paneId = runProcess(List.of(
                    TMUX_PATH, "display-message", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW, "-p", "#{pane_id}"
                )).trim();
                if (paneId.isEmpty()) {
                    sendFeedbackOnMain(source, "§e[MCUse] §cFailed to get tmux pane ID.");
                    return;
                }

                // Name the pane via tmux-bridge
                runProcess(List.of(TMUX_BRIDGE_PATH, "name", paneId, name));

                PaneConfig config = PaneConfig.load(new File("."));
                TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

                // Run command in the pane
                bridge.read(name).get();
                bridge.type(name, finalCommand).get();
                bridge.read(name).get();
                bridge.keys(name, "Enter").get();

                // Spawn the villager on the main thread
                MinecraftClient client = MinecraftClient.getInstance();
                final String finalPaneId = paneId;
                client.execute(() -> {
                    if (client.player == null || client.world == null) {
                        sendFeedbackOnMain(source, "§e[MCUse] §cNot in a world.");
                        return;
                    }

                    ServerWorld serverWorld = client.getServer() != null
                        ? client.getServer().getWorld(client.world.getRegistryKey())
                        : null;

                    if (serverWorld == null) {
                        sendFeedbackOnMain(source, "§e[MCUse] §cCannot spawn villager: not in an integrated server world.");
                        return;
                    }

                    Vec3d spawnPos = client.player.getPos().add(2, 0, 0);

                    FloatingText floatingText = new FloatingText(serverWorld);
                    floatingText.spawn(spawnPos.add(0, 0.5, 0));

                    AgentVillager agent = AgentVillager.spawn(serverWorld, spawnPos, name, finalMobType);

                    OutputPoller poller = new OutputPoller(bridge, name, floatingText);
                    poller.start();

                    VillagerRegistry.AgentVillagerData data = new VillagerRegistry.AgentVillagerData(
                        agent.getEntity(),
                        name,
                        floatingText,
                        poller
                    );
                    registry.register(name, data);

                    source.sendFeedback(Text.literal("§e[MCUse] §aSpawned agent: §f" + name));
                });

            } catch (Exception e) {
                sendFeedbackOnMain(source, "§e[MCUse] §cSpawn failed: " + e.getMessage());
            }
        }, "SpawnCommand-" + name);

        spawnThread.setDaemon(true);
        spawnThread.start();

        return 1;
    }

    private static String runProcess(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        String currentPath = pb.environment().getOrDefault("PATH", "/usr/bin:/bin");
        if (!currentPath.contains(HOMEBREW_BIN)) {
            pb.environment().put("PATH", HOMEBREW_BIN + ":" + currentPath);
        }

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Process timed out: " + cmd);
        }

        return output.toString();
    }

    private static void sendFeedbackOnMain(FabricClientCommandSource source, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> source.sendFeedback(Text.literal(message)));
    }
}
