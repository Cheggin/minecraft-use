package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.minecraftuse.villager.AgentVillager;
import com.minecraftuse.villager.FloatingText;
import com.minecraftuse.villager.LogOutputPoller;
import com.minecraftuse.villager.VillagerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ListenCommand {

    private static final String TMUX_BRIDGE_PATH = System.getProperty("user.home") + "/.smux/bin/tmux-bridge";
    private static final String TMUX_PATH = "/opt/homebrew/bin/tmux";
    private static final String HOMEBREW_BIN = "/opt/homebrew/bin";
    private static final int PROCESS_TIMEOUT_SECONDS = 10;
    private static final String TMUX_SESSION = "minecraft-use";
    private static final String AGENTS_WINDOW = "agents";
    private static final String MOB_TYPE = "allay";

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("listen")
                .then(ClientCommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                    .executes(context -> executeListen(context.getSource(),
                        IntegerArgumentType.getInteger(context, "port"))))
        );
    }

    private static int executeListen(FabricClientCommandSource source, int port) {
        String name = "listen-" + port;
        VillagerRegistry registry = VillagerRegistry.getInstance();

        if (registry.contains(name)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAlready listening on port " + port + ". Use /despawn " + name + " first."));
            return 0;
        }

        if (!new File(TMUX_BRIDGE_PATH).canExecute()) {
            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
            return 0;
        }

        source.sendFeedback(Text.literal("§e[MCUse] §7Resolving process on port " + port + "..."));

        Thread listenThread = new Thread(() -> {
            try {
                // Resolve PID on the port
                String pidOutput = runProcess(List.of("lsof", "-ti", ":" + port)).trim();
                if (pidOutput.isEmpty()) {
                    sendFeedbackOnMain(source, "§e[MCUse] §cNo process found on port " + port);
                    return;
                }

                String primaryPid = pidOutput.split("\\s+")[0].trim();

                // Get process name
                String processName = runProcess(List.of("ps", "-p", primaryPid, "-o", "comm=")).trim();
                if (processName.contains("/")) {
                    processName = processName.substring(processName.lastIndexOf('/') + 1);
                }

                // Try to find an existing tmux pane running this process
                String existingPaneId = findTmuxPaneForPid(primaryPid);
                String paneLabelToUse;

                if (existingPaneId != null) {
                    // Process is already in a tmux pane — attach to it directly
                    runProcess(List.of(TMUX_BRIDGE_PATH, "name", existingPaneId, name));
                    paneLabelToUse = name;
                    sendFeedbackOnMain(source, "§e[MCUse] §7Found process in tmux pane, attaching...");
                } else {
                    // Process is NOT in tmux — create a pane with `ports logs <port> -f`
                    paneLabelToUse = name;

                    String existingWindows = runProcess(List.of(
                        TMUX_PATH, "list-windows", "-t", TMUX_SESSION, "-F", "#{window_name}"
                    ));
                    boolean agentsWindowExists = existingWindows.lines()
                        .anyMatch(line -> line.trim().equals(AGENTS_WINDOW));

                    if (!agentsWindowExists) {
                        runProcess(List.of(TMUX_PATH, "new-window", "-t", TMUX_SESSION, "-n", AGENTS_WINDOW));
                    }

                    runProcess(List.of(TMUX_PATH, "split-window", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW));
                    runProcess(List.of(TMUX_PATH, "select-layout", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW, "tiled"));

                    String paneId = runProcess(List.of(
                        TMUX_PATH, "display-message", "-t", TMUX_SESSION + ":" + AGENTS_WINDOW, "-p", "#{pane_id}"
                    )).trim();
                    if (paneId.isEmpty()) {
                        sendFeedbackOnMain(source, "§e[MCUse] §cFailed to get tmux pane ID.");
                        return;
                    }

                    runProcess(List.of(TMUX_BRIDGE_PATH, "name", paneId, name));

                    PaneConfig config2 = PaneConfig.load(new File("."));
                    TmuxBridge tmpBridge = new TmuxBridge(config2.getTmuxSocket());

                    // Find a .vite-requests.log via the process's working directory
                    String tailCommand = "ports logs " + port + " -f";
                    try {
                        String cwdOut = runProcess(List.of("lsof", "-p", primaryPid, "-d", "cwd", "-Fn")).trim();
                        for (String cwdLine : cwdOut.split("\n")) {
                            if (cwdLine.startsWith("n") && cwdLine.length() > 1) {
                                String dir = cwdLine.substring(1);
                                // Check cwd, parent dirs, and frontend/ subdirs
                                File cur = new File(dir);
                                for (int d = 0; d < 5 && cur != null; d++) {
                                    for (String rel : new String[]{".vite-requests.log", "frontend/.vite-requests.log"}) {
                                        File f = new File(cur, rel);
                                        if (f.exists()) {
                                            tailCommand = "tail -f " + f.getAbsolutePath();
                                            d = 999; // break outer
                                            break;
                                        }
                                    }
                                    cur = cur.getParentFile();
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}

                    tmpBridge.read(name).get();
                    tmpBridge.type(name, tailCommand).get();
                    tmpBridge.read(name).get();
                    tmpBridge.keys(name, "Enter").get();

                    sendFeedbackOnMain(source, "§e[MCUse] §7Streaming: " + tailCommand);
                }

                PaneConfig config = PaneConfig.load(new File("."));
                TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

                // Spawn the villager on the main thread
                MinecraftClient client = MinecraftClient.getInstance();
                final String displayName = processName.isEmpty() ? "port-" + port : processName;
                final String finalPaneName = paneLabelToUse;
                final boolean attachedToExisting = existingPaneId != null;
                client.execute(() -> {
                    if (client.player == null || client.world == null) {
                        sendFeedbackOnMain(source, "§e[MCUse] §cNot in a world.");
                        return;
                    }

                    ServerWorld serverWorld = client.getServer() != null
                        ? client.getServer().getWorld(client.world.getRegistryKey())
                        : null;

                    if (serverWorld == null) {
                        sendFeedbackOnMain(source, "§e[MCUse] §cCannot spawn: not in an integrated server world.");
                        return;
                    }

                    Vec3d spawnPos = client.player.getPos().add(2, 0, 0);

                    FloatingText floatingText = new FloatingText(serverWorld);
                    floatingText.spawn(spawnPos.add(0, 1.2, 0));

                    // Spawn as allay, NOT invulnerable (so killing it kills the process)
                    AgentVillager agent = AgentVillager.spawn(serverWorld, spawnPos, name, MOB_TYPE);
                    agent.getEntity().setInvulnerable(false);

                    LogOutputPoller poller = new LogOutputPoller(bridge, finalPaneName, floatingText);
                    poller.start();

                    VillagerRegistry.AgentVillagerData data = new VillagerRegistry.AgentVillagerData(
                        agent.getEntity(),
                        finalPaneName,
                        floatingText,
                        poller,
                        port
                    );
                    registry.register(name, data);

                    String mode = attachedToExisting ? "§aattached to tmux pane" : "§astreaming via port-whisperer";
                    source.sendFeedback(Text.literal(
                        "§e[MCUse] §aListening on port §f" + port
                        + " §7(" + displayName + " PID " + primaryPid + ")"
                        + "\n§e[MCUse] " + mode
                        + "\n§e[MCUse] §7Kill the " + MOB_TYPE + " to stop the process."
                    ));
                });

            } catch (Exception e) {
                sendFeedbackOnMain(source, "§e[MCUse] §cListen failed: " + e.getMessage());
            }
        }, "ListenCommand-" + port);

        listenThread.setDaemon(true);
        listenThread.start();

        return 1;
    }

    /**
     * Find a tmux pane whose shell PID is an ancestor of the given PID.
     * Detects when a process is running inside an existing tmux pane.
     *
     * @return the pane ID (e.g. %5) if found, null otherwise
     */
    private static String findTmuxPaneForPid(String targetPid) {
        try {
            String paneList = runProcess(List.of(
                TMUX_PATH, "list-panes", "-a", "-F", "#{pane_pid} #{pane_id}"
            ));

            if (paneList.isBlank()) return null;

            // Walk up the process tree from targetPid to find a matching pane PID
            java.util.Set<String> ancestors = new java.util.HashSet<>();
            String currentPid = targetPid;
            for (int i = 0; i < 20; i++) {
                ancestors.add(currentPid);
                String ppid = runProcess(List.of("ps", "-o", "ppid=", "-p", currentPid)).trim();
                if (ppid.isEmpty() || ppid.equals("0") || ppid.equals("1") || ppid.equals(currentPid)) {
                    break;
                }
                currentPid = ppid;
            }

            for (String line : paneList.split("\n")) {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 2 && ancestors.contains(parts[0])) {
                    return parts[1];
                }
            }
        } catch (Exception ignored) {}
        return null;
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

        boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
