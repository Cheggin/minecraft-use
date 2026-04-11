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
import java.util.stream.Collectors;

public class TmuxReadCommand {

    private static final int MAX_LINES = 20;
    private static final int MAX_OUTPUT_LENGTH = 500;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("tmux-read")
                .then(ClientCommandManager.argument("pane", StringArgumentType.word())
                    .executes(context -> {
                        String pane = StringArgumentType.getString(context, "pane");
                        FabricClientCommandSource source = context.getSource();

                        source.sendFeedback(Text.literal("§e[MCUse] §fReading from pane §b" + pane + "§f..."));

                        PaneConfig config = PaneConfig.load(new File("."));
                        String socket = config.getTmuxSocket();
                        TmuxBridge bridge = new TmuxBridge(socket);

                        if (!bridge.isAvailable()) {
                            source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
                            return 0;
                        }

                        String resolvedPane = config.getPane(pane) != null ? config.getPane(pane) : pane;

                        bridge.read(resolvedPane)
                            .thenAccept(output -> {
                                String[] lines = output.split("\n");
                                int startIdx = Math.max(0, lines.length - MAX_LINES);
                                String trimmed = Arrays.stream(lines, startIdx, lines.length)
                                    .collect(Collectors.joining("\n"))
                                    .trim();
                                trimmed = truncate(trimmed);
                                if (trimmed.isEmpty()) {
                                    source.sendFeedback(Text.literal("§e[MCUse] §7(no output)"));
                                } else {
                                    source.sendFeedback(Text.literal("§e[MCUse] §f" + trimmed));
                                }
                            })
                            .exceptionally(err -> {
                                source.sendFeedback(Text.literal("§e[MCUse] §cError: " + err.getMessage()));
                                return null;
                            });

                        return 1;
                    }))
        );
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_OUTPUT_LENGTH) return s;
        return s.substring(0, MAX_OUTPUT_LENGTH) + "§7... (truncated)";
    }
}
