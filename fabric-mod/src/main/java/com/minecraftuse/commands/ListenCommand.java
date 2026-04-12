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
                    // Process is already in a tmux pane — attach to it
                    runProcess(List.of(TMUX_BRIDGE_PATH, "name", existingPaneId, name));
                    paneLabelToUse = name;
                    sendFeedbackOnMain(source, "§e[MCUse] §7Found process in tmux pane " + existingPaneId + ", attaching...");
                } else {
                    // Process is NOT in tmux — create a new pane
                    paneLabelToUse = name;

                    // Try to find a log file for this process
                    // 1. Check the process's working directory
                    // 2. Search common project locations relative to the user's home
                    String logFile = null;
                    try {
                        String cwdOutput = runProcess(List.of("lsof", "-p", primaryPid, "-d", "cwd", "-Fn")).trim();
                        for (String line : cwdOutput.split("\n")) {
                            if (line.startsWith("n") && line.length() > 1) {
                                String dir = line.substring(1);
                                // Check the cwd itself and a frontend/ subdirectory
                                for (String candidate : new String[]{
                                    dir + "/.vite-requests.log",
                                    dir + "/frontend/.vite-requests.log"
                                }) {
                                    if (new File(candidate).exists()) {
                                        logFile = candidate;
                                        break;
                                    }
                                }
                                // Also walk up to find a project root with frontend/
                                if (logFile == null) {
                                    File current = new File(dir);
                                    for (int depth = 0; depth < 5 && current != null; depth++) {
                                        File check = new File(current, "frontend/.vite-requests.log");
                                        if (check.exists()) {
                                            logFile = check.getAbsolutePath();
                                            break;
                                        }
                                        check = new File(current, ".vite-requests.log");
                                        if (check.exists()) {
                                            logFile = check.getAbsolutePath();
                                            break;
                                        }
                                        current = current.getParentFile();
                                    }
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Last resort: check well-known location
                    if (logFile == null) {
                        String home = System.getProperty("user.home");
                        String[] searchPaths = {
                            home + "/Documents/GitHub/minecraft-use/frontend/.vite-requests.log"
                        };
                        for (String path : searchPaths) {
                            if (new File(path).exists()) {
                                logFile = path;
                                break;
                            }
                        }
                    }

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

                    // Use log file if found, otherwise fall back to ports watch
                    String command = logFile != null
                        ? "tail -f " + logFile
                        : "ports watch";
                    tmpBridge.read(name).get();
                    tmpBridge.type(name, command).get();
                    tmpBridge.read(name).get();
                    tmpBridge.keys(name, "Enter").get();

                    if (logFile != null) {
                        sendFeedbackOnMain(source, "§e[MCUse] §7Tailing log file: " + logFile);
                    } else {
                        sendFeedbackOnMain(source, "§e[MCUse] §7No log file found, using port-whisperer watch...");
                    }
                }

                PaneConfig config = PaneConfig.load(new File("."));
                TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

                // Determine if we have a log file for direct reading
                final File directLogFile;
                if (existingPaneId == null) {
                    // In the "not in tmux" path, check if we found a log file
                    String home = System.getProperty("user.home");
                    File candidate = new File(home + "/Documents/GitHub/minecraft-use/frontend/.vite-requests.log");
                    // Also try process cwd-based detection
                    File foundLog = null;
                    try {
                        String cwdOut = runProcess(List.of("lsof", "-p", primaryPid, "-d", "cwd", "-Fn")).trim();
                        for (String cwdLine : cwdOut.split("\n")) {
                            if (cwdLine.startsWith("n") && cwdLine.length() > 1) {
                                String dir = cwdLine.substring(1);
                                for (String p : new String[]{dir + "/.vite-requests.log", dir + "/frontend/.vite-requests.log"}) {
                                    if (new File(p).exists()) { foundLog = new File(p); break; }
                                }
                                if (foundLog == null) {
                                    File cur = new File(dir);
                                    for (int d = 0; d < 5 && cur != null; d++) {
                                        File c = new File(cur, "frontend/.vite-requests.log");
                                        if (c.exists()) { foundLog = c; break; }
                                        c = new File(cur, ".vite-requests.log");
                                        if (c.exists()) { foundLog = c; break; }
                                        cur = cur.getParentFile();
                                    }
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                    directLogFile = foundLog != null ? foundLog : (candidate.exists() ? candidate : null);
                } else {
                    directLogFile = null; // Attached to tmux pane, read from there
                }

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

                    LogOutputPoller poller = directLogFile != null
                        ? new LogOutputPoller(bridge, finalPaneName, floatingText, directLogFile)
                        : new LogOutputPoller(bridge, finalPaneName, floatingText);
                    poller.start();

                    VillagerRegistry.AgentVillagerData data = new VillagerRegistry.AgentVillagerData(
                        agent.getEntity(),
                        finalPaneName,
                        floatingText,
                        poller,
                        port
                    );
                    registry.register(name, data);

                    String mode = attachedToExisting ? "§aattached to tmux pane"
                        : directLogFile != null ? "§areading log file directly"
                        : "§7using ports watch";
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
     * This detects when a process (e.g. Vite dev server) is running inside
     * an existing tmux pane so we can attach to it directly.
     *
     * @return the pane ID (e.g. %5) if found, null otherwise
     */
    private static String findTmuxPaneForPid(String targetPid) {
        try {
            // Get all tmux pane PIDs and their IDs
            String paneList = runProcess(List.of(
                TMUX_PATH, "list-panes", "-a", "-F", "#{pane_pid} #{pane_id}"
            ));

            if (paneList.isBlank()) return null;

            // Walk up the process tree from targetPid to find a matching pane PID
            // Collect all ancestor PIDs first
            java.util.Set<String> ancestors = new java.util.HashSet<>();
            String currentPid = targetPid;
            for (int i = 0; i < 20; i++) { // max depth to avoid infinite loop
                ancestors.add(currentPid);
                String ppid = runProcess(List.of("ps", "-o", "ppid=", "-p", currentPid)).trim();
                if (ppid.isEmpty() || ppid.equals("0") || ppid.equals("1") || ppid.equals(currentPid)) {
                    break;
                }
                currentPid = ppid;
            }

            // Check each tmux pane to see if its PID is an ancestor of our target
            for (String line : paneList.split("\n")) {
                String[] parts = line.trim().split("\\s+", 2);
                if (parts.length == 2) {
                    String panePid = parts[0];
                    String paneId = parts[1];
                    if (ancestors.contains(panePid)) {
                        return paneId;
                    }
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
