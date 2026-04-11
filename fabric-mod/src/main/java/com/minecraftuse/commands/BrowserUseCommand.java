package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.io.File;

public class BrowserUseCommand {

    private static final String BROWSER_PANE = "browser";
    private static final String DONE_SENTINEL = "DONE";
    private static final int MAX_OUTPUT_LINES = 10;

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

        bridge.sendAndWait(resolvedPane, task)
            .thenAccept(output -> {
                String trimmed = output.trim();
                if (trimmed.isEmpty()) {
                    source.sendFeedback(Text.literal("§e[MCUse] §7(no output from browser)"));
                    return;
                }

                String[] lines = trimmed.split("\n");

                // Parse output — only look at lines after the last ">>> " prompt
                String liveUrl = null;
                java.util.List<String> contentLines = new java.util.ArrayList<>();
                boolean done = false;
                boolean foundPrompt = false;

                // Find the last ">>> " prompt and only process lines after it
                int lastPromptIndex = -1;
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].trim().startsWith(">>>")) {
                        lastPromptIndex = i;
                        break;
                    }
                }

                int startFrom = lastPromptIndex >= 0 ? lastPromptIndex + 1 : 0;
                for (int i = startFrom; i < lines.length; i++) {
                    String l = lines[i].trim();
                    if (l.startsWith("TASK_STARTED:")) {
                        liveUrl = l.substring("TASK_STARTED:".length()).trim();
                    } else if (l.equals(DONE_SENTINEL)) {
                        done = true;
                    } else if (!l.isEmpty() && !l.startsWith(">>>")) {
                        contentLines.add(l);
                    }
                }

                // Show live URL if found
                if (liveUrl != null) {
                    source.sendFeedback(Text.literal("§e[MCUse] §7Task started: §9§n" + liveUrl));
                }

                // Show output
                if (!contentLines.isEmpty()) {
                    int shown = Math.min(contentLines.size(), MAX_OUTPUT_LINES);
                    source.sendFeedback(Text.literal("§e[MCUse] §bBrowser:"));
                    for (int i = 0; i < shown; i++) {
                        source.sendFeedback(Text.literal("§f  " + contentLines.get(i)));
                    }
                    if (contentLines.size() > MAX_OUTPUT_LINES) {
                        int remaining = contentLines.size() - MAX_OUTPUT_LINES;
                        source.sendFeedback(Text.literal("§7  ... " + remaining + " more line" + (remaining == 1 ? "" : "s")));
                    }
                }

                if (done) {
                    source.sendFeedback(Text.literal("§e[MCUse] §aTask finished."));
                } else {
                    source.sendFeedback(Text.literal("§e[MCUse] §7(task still running — use /tmux-read browser to check)"));
                }
            })
            .exceptionally(err -> {
                String msg = err.getMessage();
                if (msg != null && msg.contains("timed out")) {
                    source.sendFeedback(Text.literal("§e[MCUse] §cBrowser task timed out — is the 'browser' pane running?"));
                } else if (msg != null && msg.contains("tmux-bridge error")) {
                    source.sendFeedback(Text.literal("§e[MCUse] §cPane 'browser' not found — check tmux session"));
                } else {
                    source.sendFeedback(Text.literal("§e[MCUse] §cError: " + msg));
                }
                return null;
            });
    }
}
