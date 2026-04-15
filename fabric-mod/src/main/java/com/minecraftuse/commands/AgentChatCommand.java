package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.bridge.AnsiStripper;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * /agent-chat <agent1> <agent2> [rounds] — start a conversation between two agents.
 * /agent-chat stop — stop all active conversations.
 *
 * Agent1 sends the initial prompt, agent2 responds, then agent1 responds to that, etc.
 * Each response is typed into the other agent's tmux pane.
 */
public class AgentChatCommand {

    private static final int DEFAULT_ROUNDS = 5;
    private static final int POLL_INTERVAL_MS = 500;
    private static final int MAX_WAIT_MS = 60000;

    private static final AtomicBoolean conversationActive = new AtomicBoolean(false);
    private static Thread conversationThread;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("agent-chat")
                .then(ClientCommandManager.literal("stop")
                    .executes(context -> {
                        stopConversation(context.getSource());
                        return 1;
                    }))
                .then(ClientCommandManager.argument("agent1", StringArgumentType.string())
                    .then(ClientCommandManager.argument("agent2", StringArgumentType.string())
                        .executes(context -> {
                            String agent1 = StringArgumentType.getString(context, "agent1");
                            String agent2 = StringArgumentType.getString(context, "agent2");
                            startConversation(context.getSource(), agent1, agent2, null, DEFAULT_ROUNDS);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("prompt", StringArgumentType.greedyString())
                            .executes(context -> {
                                String agent1 = StringArgumentType.getString(context, "agent1");
                                String agent2 = StringArgumentType.getString(context, "agent2");
                                String prompt = StringArgumentType.getString(context, "prompt");
                                startConversation(context.getSource(), agent1, agent2, prompt, DEFAULT_ROUNDS);
                                return 1;
                            }))))
        );
    }

    private static void stopConversation(FabricClientCommandSource source) {
        if (conversationActive.getAndSet(false)) {
            if (conversationThread != null) {
                conversationThread.interrupt();
                conversationThread = null;
            }
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent conversation stopped."));
        } else {
            source.sendFeedback(Text.literal("§e[MCUse] §7No active conversation."));
        }
    }

    private static void startConversation(FabricClientCommandSource source, String agent1, String agent2, String prompt, int rounds) {
        VillagerRegistry registry = VillagerRegistry.getInstance();

        if (!registry.contains(agent1)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent '" + agent1 + "' not found. Spawn it with /agent " + agent1));
            return;
        }
        if (!registry.contains(agent2)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cAgent '" + agent2 + "' not found. Spawn it with /agent " + agent2));
            return;
        }

        if (conversationActive.getAndSet(true)) {
            source.sendFeedback(Text.literal("§e[MCUse] §cA conversation is already active. Use /agent-chat stop first."));
            return;
        }

        String initialPrompt = prompt != null ? prompt
            : "Say hello to " + agent2 + " and introduce yourself. Keep it brief (1-2 sentences).";

        source.sendFeedback(Text.literal("§e[MCUse] §aStarting conversation: §f" + agent1 + " §7↔ §f" + agent2));
        source.sendFeedback(Text.literal("§e[MCUse] §7" + rounds + " rounds. Use /agent-chat stop to end early."));

        PaneConfig config = PaneConfig.load(new File("."));
        TmuxBridge bridge = new TmuxBridge(config.getTmuxSocket());

        conversationThread = new Thread(() -> {
            try {
                // Send initial prompt to agent1
                MinecraftUseMod.LOGGER.info("[AgentChat] Sending initial prompt to {}: {}", agent1, initialPrompt);
                sendToAgent(bridge, agent1, initialPrompt);
                sendActionBar("§e[MCUse] §7" + agent1 + " is thinking...");

                String lastResponse = waitForResponse(bridge, agent1);
                MinecraftUseMod.LOGGER.info("[AgentChat] Response from {}: {}", agent1, lastResponse != null ? truncate(lastResponse, 80) : "NULL");
                if (lastResponse == null || !conversationActive.get()) {
                    sendFeedback(source, "§e[MCUse] §cNo response from " + agent1 + " — conversation ended.");
                    return;
                }

                sendFeedback(source, "§e[MCUse] §a" + agent1 + ": §f" + truncate(lastResponse, 100));

                for (int round = 0; round < rounds && conversationActive.get(); round++) {
                    // Send agent1's response to agent2
                    String toAgent2 = agent1 + " says: " + lastResponse;
                    sendToAgent(bridge, agent2, toAgent2);
                    sendActionBar("§e[MCUse] §7" + agent2 + " is thinking... (round " + (round + 1) + "/" + rounds + ")");

                    lastResponse = waitForResponse(bridge, agent2);
                    if (lastResponse == null || !conversationActive.get()) break;

                    sendFeedback(source, "§e[MCUse] §b" + agent2 + ": §f" + truncate(lastResponse, 100));

                    if (round + 1 >= rounds) break;

                    // Send agent2's response back to agent1
                    String toAgent1 = agent2 + " says: " + lastResponse;
                    sendToAgent(bridge, agent1, toAgent1);
                    sendActionBar("§e[MCUse] §7" + agent1 + " is thinking... (round " + (round + 1) + "/" + rounds + ")");

                    lastResponse = waitForResponse(bridge, agent1);
                    if (lastResponse == null || !conversationActive.get()) break;

                    sendFeedback(source, "§e[MCUse] §a" + agent1 + ": §f" + truncate(lastResponse, 100));
                }

                if (conversationActive.get()) {
                    sendFeedback(source, "§e[MCUse] §7Conversation finished after " + rounds + " rounds.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                sendFeedback(source, "§e[MCUse] §cConversation error: " + e.getMessage());
            } finally {
                conversationActive.set(false);
                conversationThread = null;
            }
        }, "AgentChat-" + agent1 + "-" + agent2);
        conversationThread.setDaemon(true);
        conversationThread.start();
    }

    private static void sendToAgent(TmuxBridge bridge, String agentName, String message) throws Exception {
        bridge.read(agentName).get();
        bridge.type(agentName, message).get();
        bridge.read(agentName).get();
        bridge.keys(agentName, "Enter").get();
    }

    /**
     * Wait for the agent to produce a new response.
     * Polls the pane, looking for the last line starting with ● or ⏺ that changes.
     */
    private static String waitForResponse(TmuxBridge bridge, String agentName) throws Exception {
        // Snapshot current state
        String beforeOutput = AnsiStripper.strip(bridge.read(agentName).get());

        long startTime = System.currentTimeMillis();
        String lastOutput = "";
        int stableCount = 0;

        while (System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
            if (!conversationActive.get()) return null;

            Thread.sleep(POLL_INTERVAL_MS);
            String current = AnsiStripper.strip(bridge.read(agentName).get());

            if (current.equals(lastOutput) && !current.equals(beforeOutput)) {
                stableCount++;
                if (stableCount >= 3) {
                    return extractLastResponse(current);
                }
            } else {
                stableCount = 0;
            }
            lastOutput = current;
        }

        return extractLastResponse(lastOutput);
    }

    /** Extract the last ● or ⏺ response from pane output */
    private static String extractLastResponse(String output) {
        String[] lines = output.split("\n");
        StringBuilder response = new StringBuilder();
        int lastAgentStart = -1;

        // Search ALL lines for agent markers, not just from the bottom
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            char firstChar = l.isEmpty() ? 0 : l.charAt(0);
            // Check by Unicode codepoint: ● = U+25CF, ⏺ = U+23FA
            if (firstChar == '\u25CF' || firstChar == '\u23FA') {
                lastAgentStart = i;
                break;
            }
        }

        if (lastAgentStart >= 0) {
            for (int i = lastAgentStart; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.isEmpty()) continue;
                char fc = l.charAt(0);
                // Stop at user prompt or status bar
                if (fc == '\u276F') break; // ❯
                if (l.startsWith("> ")) break;
                if (l.startsWith("[OMC")) break;
                if (l.startsWith("\u23F5")) break; // ⏵ bypass
                if (l.chars().filter(c -> c >= 0x2500 && c <= 0x257F).count() > l.length() / 2) break; // ─── lines
                if (response.length() > 0) response.append(" ");
                response.append(l);
            }
        }

        String result = response.toString().trim();
        // Remove the bullet character
        if (result.startsWith("●") || result.startsWith("⏺")) {
            result = result.substring(1).trim();
        }
        return result.isEmpty() ? null : result;
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    private static void sendFeedback(FabricClientCommandSource source, String message) {
        MinecraftClient.getInstance().execute(() ->
            source.sendFeedback(Text.literal(message))
        );
    }

    private static void sendActionBar(String message) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    Text.literal(message), true);
            }
        });
    }
}
