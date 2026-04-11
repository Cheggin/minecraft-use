package com.minecraftuse.commands;

import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.io.File;

public class ShellCommand {

    private static final String SHELL_PANE = "shell";
    private static final int MAX_OUTPUT_LINES = 10;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("shell")
                .then(ClientCommandManager.argument("command", StringArgumentType.greedyString())
                    .executes(context -> {
                        String command = StringArgumentType.getString(context, "command");
                        FabricClientCommandSource source = context.getSource();

                        PaneConfig config = PaneConfig.load(new File("."));
                        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

                        if (!bridge.isAvailable()) {
                            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
                            return 0;
                        }

                        String resolvedPane = config.getPane(SHELL_PANE) != null
                            ? config.getPane(SHELL_PANE)
                            : SHELL_PANE;

                        source.sendFeedback(Text.literal("§e[MCUse] §7Running: §f" + command));

                        bridge.sendAndWait(resolvedPane, command)
                            .thenAccept(output -> {
                                String trimmed = output.trim();
                                if (trimmed.isEmpty()) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §7(no output)"));
                                    return;
                                }

                                String[] lines = trimmed.split("\n");
                                int shown = Math.min(lines.length, MAX_OUTPUT_LINES);

                                source.sendFeedback(Text.literal("§e[MCUse] §bShell output:"));
                                for (int i = 0; i < shown; i++) {
                                    String line = lines[i];
                                    if (!line.isBlank()) {
                                        source.sendFeedback(Text.literal("§f  " + line));
                                    }
                                }

                                if (lines.length > MAX_OUTPUT_LINES) {
                                    int remaining = lines.length - MAX_OUTPUT_LINES;
                                    source.sendFeedback(Text.literal("§7  ... " + remaining + " more line" + (remaining == 1 ? "" : "s")));
                                }
                            })
                            .exceptionally(err -> {
                                String msg = err.getMessage();
                                if (msg != null && msg.contains("timed out")) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cCommand timed out — is the 'shell' pane running?"));
                                } else if (msg != null && msg.contains("tmux-bridge error")) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cPane 'shell' not found — check tmux session"));
                                } else {
                                    source.sendFeedback(Text.literal("§e[MCUse] §cError: " + msg));
                                }
                                return null;
                            });

                        return 1;
                    }))
        );
    }
}
