package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.bridge.PaneConfig;
import com.minecraftuse.bridge.TmuxBridge;
import com.minecraftuse.villager.VillagerRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.File;

/**
 * /agent-tell <from> <to> <message>
 *
 * One-shot: reads from's last response and sends it (with the message) to the target agent's pane.
 * Example: /agent-tell alex shawn "tell shawn he stinks"
 * This types the message into shawn's pane as if alex said it.
 */
public class AgentTellCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("agent-tell")
                .then(ClientCommandManager.argument("from", StringArgumentType.string())
                    .then(ClientCommandManager.argument("to", StringArgumentType.string())
                        .then(ClientCommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(context -> {
                                String from = StringArgumentType.getString(context, "from");
                                String to = StringArgumentType.getString(context, "to");
                                String message = StringArgumentType.getString(context, "message");
                                executeTell(context.getSource(), from, to, message);
                                return 1;
                            }))))
        );
    }

    private static void executeTell(FabricClientCommandSource source, String from, String to, String message) {
        VillagerRegistry registry = VillagerRegistry.getInstance();

        if (!registry.contains(from)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent '" + from + "' not found."));
            return;
        }
        if (!registry.contains(to)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent '" + to + "' not found."));
            return;
        }

        source.sendFeedback(Text.literal("§e[MCUse] §a" + from + " §7→ §b" + to + "§7: §f" + message));

        PaneConfig config = PaneConfig.load(new File("."));
        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

        Thread thread = new Thread(() -> {
            try {
                // Format the message as coming from the other agent
                String fullMessage = from + " says: " + message;

                // Send to the target agent's pane
                bridge.read(to).get();
                bridge.type(to, fullMessage).get();
                bridge.read(to).get();
                bridge.keys(to, "Enter").get();

                sendFeedback(source, "§e[MCUse] §7Message sent to §b" + to);

            } catch (Exception e) {
                sendFeedback(source, "§e[MCUse] §cFailed: " + e.getMessage());
            }
        }, "AgentTell-" + from + "-" + to);
        thread.setDaemon(true);
        thread.start();
    }

    private static void sendFeedback(FabricClientCommandSource source, String message) {
        MinecraftClient.getInstance().execute(() ->
            source.sendFeedback(Text.literal(message))
        );
    }
}
