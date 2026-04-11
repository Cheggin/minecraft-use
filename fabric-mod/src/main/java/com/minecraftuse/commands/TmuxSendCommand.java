package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.io.File;

public class TmuxSendCommand {

    private static final int MAX_OUTPUT_LENGTH = 500;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("tmux-send")
                .then(ClientCommandManager.argument("pane", StringArgumentType.word())
                    .then(ClientCommandManager.argument("text", StringArgumentType.greedyString())
                        .executes(context -> {
                            String pane = StringArgumentType.getString(context, "pane");
                            String text = StringArgumentType.getString(context, "text");
                            FabricClientCommandSource source = context.getSource();

                            source.sendFeedback(Text.literal("§e[MCUse] §fSending to pane §b" + pane + "§f: " + text));

                            PaneConfig config = PaneConfig.load(new File("."));
                            String socket = config.getTmuxSocket();
                            TmuxBridge bridge = new TmuxBridge(socket);

                            if (!bridge.isAvailable()) {
                                source.sendFeedback(Text.literal("§e[MCUse] §ctmux-bridge not found at ~/.smux/bin/tmux-bridge"));
                                return 0;
                            }

                            String resolvedPane = config.getPane(pane) != null ? config.getPane(pane) : pane;

                            bridge.sendAndWait(resolvedPane, text)
                                .thenAccept(output -> {
                                    String trimmed = truncate(output.trim());
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
                        })))
        );
    }

    private static String truncate(String s) {
        if (s.length() <= MAX_OUTPUT_LENGTH) return s;
        return s.substring(0, MAX_OUTPUT_LENGTH) + "§7... (truncated)";
    }
}
