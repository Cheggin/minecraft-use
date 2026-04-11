package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BrowserUseCommand {

    private static final String BROWSER_PANE = "browser";
    private static final String DONE_SENTINEL = "DONE";
    private static final int MAX_OUTPUT_LINES = 10;
    private static final int POLL_INTERVAL_MS = 500;
    private static final int MAX_WAIT_MS = 120000;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("browser-use")
                .then(ClientCommandManager.literal("get-schematics")
                    .then(ClientCommandManager.argument("query", StringArgumentType.greedyString())
                        .executes(context -> {
                            String query = StringArgumentType.getString(context, "query");
                            FabricClientCommandSource source = context.getSource();
                            executeTask(source, "get-schematics " + query);
                            return 1;
                        })))
                .then(ClientCommandManager.argument("task", StringArgumentType.greedyString())
                    .executes(context -> {
                        String task = StringArgumentType.getString(context, "task");
                        FabricClientCommandSource source = context.getSource();
                        executeTask(source, task);
                        return 1;
                    }))
        );
    }

    private static void executeTask(FabricClientCommandSource source, String task) {
        PaneConfig config = PaneConfig.load(new File("."));
        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

        if (!bridge.isAvailable()) {
            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
            return;
        }

        String resolvedPane = config.getPane(BROWSER_PANE) != null
            ? config.getPane(BROWSER_PANE)
            : BROWSER_PANE;

        source.sendFeedback(Text.literal("§e[MCUse] §7Running browser task: §f" + task));

        // Run everything on a background thread
        Thread thread = new Thread(() -> {
            try {
                // Send the command
                bridge.read(resolvedPane).get();
                bridge.type(resolvedPane, task).get();
                bridge.read(resolvedPane).get();
                bridge.keys(resolvedPane, "Enter").get();

                // Poll for TASK_STARTED line (should appear within a few seconds)
                String liveUrl = null;
                for (int i = 0; i < 20; i++) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    String output = bridge.read(resolvedPane).get();
                    String[] lines = output.split("\n");
                    for (String line : lines) {
                        String l = line.trim();
                        if (l.startsWith("TASK_STARTED:")) {
                            liveUrl = l.substring("TASK_STARTED:".length()).trim();
                            break;
                        }
                    }
                    if (liveUrl != null) break;
                    sendActionBar("§e[MCUse] §7Starting browser task...");
                }

                if (liveUrl != null) {
                    sendFeedback(source, "§e[MCUse] §7Live view: §9§n" + liveUrl);
                }

                // Now poll for DONE sentinel (task completion)
                long startTime = System.currentTimeMillis();
                String finalOutput = null;

                while (System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
                    Thread.sleep(POLL_INTERVAL_MS * 2);
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    sendActionBar("§e[MCUse] §7Browser task running... §f" + elapsed + "s");
                    String output = bridge.read(resolvedPane).get();

                    // Check if DONE appeared after the last >>> prompt
                    String[] lines = output.split("\n");
                    int lastPrompt = -1;
                    for (int i = lines.length - 1; i >= 0; i--) {
                        if (lines[i].trim().startsWith(">>>")) {
                            lastPrompt = i;
                            break;
                        }
                    }

                    if (lastPrompt >= 0) {
                        boolean hasDone = false;
                        for (int i = lastPrompt; i < lines.length; i++) {
                            if (lines[i].trim().equals(DONE_SENTINEL)) {
                                hasDone = true;
                                break;
                            }
                        }
                        if (hasDone) {
                            // Extract content between prompt and DONE
                            List<String> content = new ArrayList<>();
                            for (int i = lastPrompt + 1; i < lines.length; i++) {
                                String l = lines[i].trim();
                                if (l.equals(DONE_SENTINEL)) break;
                                if (!l.isEmpty() && !l.startsWith("TASK_STARTED:") && !l.startsWith(">>>")) {
                                    content.add(l);
                                }
                            }
                            finalOutput = String.join("\n", content);
                            break;
                        }
                    }
                }

                if (finalOutput != null && !finalOutput.isEmpty()) {
                    String[] resultLines = finalOutput.split("\n");
                    int shown = Math.min(resultLines.length, MAX_OUTPUT_LINES);
                    sendFeedback(source, "§e[MCUse] §aTask finished:");
                    for (int i = 0; i < shown; i++) {
                        sendFeedback(source, "§f  " + resultLines[i]);
                    }
                    if (resultLines.length > MAX_OUTPUT_LINES) {
                        sendFeedback(source, "§7  ... " + (resultLines.length - MAX_OUTPUT_LINES) + " more lines");
                    }
                } else {
                    sendFeedback(source, "§e[MCUse] §7Task timed out — use /tmux-read browser to check");
                }

            } catch (Exception e) {
                sendFeedback(source, "§e[MCUse] §cError: " + e.getMessage());
            }
        }, "BrowserUseCommand");
        thread.setDaemon(true);
        thread.start();
    }

    private static void sendFeedback(FabricClientCommandSource source, String message) {
        MinecraftClient.getInstance().execute(() ->
            source.sendFeedback(Text.literal(message))
        );
    }

    /** Show a message on the action bar (above hotbar) — doesn't flood chat */
    private static void sendActionBar(String message) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal(message), true);
            }
        });
    }
}
