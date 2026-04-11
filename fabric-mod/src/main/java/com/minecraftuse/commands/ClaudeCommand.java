package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ClaudeCommand {

    private static final String CLAUDE_PANE = "claude";
    private static final int MAX_DISPLAY_LINES = 10;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("claude")
                .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        FabricClientCommandSource source = context.getSource();

                        PaneConfig config = PaneConfig.load(new File("."));
                        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

                        if (!bridge.isAvailable()) {
                            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
                            return 0;
                        }

                        String resolvedPane = config.getPane(CLAUDE_PANE) != null
                            ? config.getPane(CLAUDE_PANE)
                            : CLAUDE_PANE;

                        source.sendFeedback(Text.literal("§e[MCUse] §7Asking Claude..."));

                        bridge.sendAndWait(resolvedPane, message)
                            .thenAccept(output -> {
                                String trimmed = output.trim();
                                if (trimmed.isEmpty()) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §7(no response from Claude)"));
                                    return;
                                }
                                displayResponse(source, trimmed);
                            })
                            .exceptionally(err -> {
                                String msg = err.getMessage();
                                if (msg != null && msg.contains("timed out")) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cClaude timed out — is the 'claude' pane running?"));
                                } else if (msg != null && msg.contains("tmux-bridge error")) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cPane 'claude' not found — check tmux session"));
                                } else {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cError: " + msg));
                                }
                                return null;
                            });

                        return 1;
                    }))
        );
    }

    private static void displayResponse(FabricClientCommandSource source, String response) {
        String[] lines = response.split("\n");

        // Filter out Claude Code chrome (same as OutputPoller)
        List<String> filtered = Arrays.stream(lines)
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.matches("^[\\u2500-\\u257F\\-]{3,}$"))
            .filter(line -> !line.startsWith("[OMC#"))
            .filter(line -> !line.contains("bypass permissions"))
            .filter(line -> !line.contains("shift+tab to cycle"))
            .filter(line -> !line.contains("Claude Code v"))
            .filter(line -> !line.contains("Claude Max"))
            .filter(line -> !line.contains("/remote-control"))
            .filter(line -> !line.contains("session:"))
            .filter(line -> !line.contains("context)"))
            .filter(line -> !line.contains("claude.ai/code/"))
            .filter(line -> !line.contains("Code in CLI"))
            .filter(line -> !line.contains("active ·"))
            .filter(line -> !line.contains("MCP server"))
            .filter(line -> !line.contains("/mcp"))
            .filter(line -> !line.startsWith("~/"))
            .filter(line -> !line.equals(">"))
            .filter(line -> !line.equals("❯"))
            .filter(line -> !line.equals(")"))
            .filter(line -> line.chars().filter(c -> c > 0x2500).count() < line.length() / 2)
            .collect(java.util.stream.Collectors.toList());

        // Extract only the last agent response (lines starting with ● or ⏺)
        List<String> agentLines = new java.util.ArrayList<>();
        int lastAgentStart = -1;
        for (int i = filtered.size() - 1; i >= 0; i--) {
            String l = filtered.get(i);
            if (l.startsWith("●") || l.startsWith("⏺")) {
                lastAgentStart = i;
                break;
            }
            if (l.startsWith("❯") || l.startsWith("> ")) break;
        }
        if (lastAgentStart >= 0) {
            for (int i = lastAgentStart; i < filtered.size(); i++) {
                String l = filtered.get(i);
                if (i > lastAgentStart && (l.startsWith("❯") || l.startsWith("> "))) break;
                agentLines.add(l);
            }
        } else {
            agentLines = filtered;
        }

        if (agentLines.isEmpty()) {
            source.sendFeedback(Text.literal("§e[MCUse] §7(no response from Claude)"));
            return;
        }

        int total = agentLines.size();
        int shown = Math.min(total, MAX_DISPLAY_LINES);

        source.sendFeedback(Text.literal("§e[MCUse] §bClaude:"));
        for (int i = 0; i < shown; i++) {
            source.sendFeedback(Text.literal("§f  " + agentLines.get(i)));
        }

        if (total > MAX_DISPLAY_LINES) {
            int remaining = total - MAX_DISPLAY_LINES;
            source.sendFeedback(Text.literal("§7  ... " + remaining + " more line" + (remaining == 1 ? "" : "s")));
        }
    }
}
