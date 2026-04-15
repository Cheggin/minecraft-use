package com.minecraftuse.commands;

import com.minecraftuse.screen.CodeScreenGUI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public class CodeCommand {

    private static final String DEFAULT_URL = "https://vscode.dev";
    private static final String REPO_URL = "https://github.dev/Cheggin/minecraft-use";
    private static final String SERVER_URL = "http://localhost:8080";

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("code")
                .executes(context -> {
                    com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code invoked");
                    openScreen(DEFAULT_URL);
                    return 1;
                })
                .then(ClientCommandManager.literal("repo")
                    .executes(context -> {
                        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code repo invoked");
                        openScreen(REPO_URL);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("login")
                    .executes(context -> {
                        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code login invoked");
                        openScreen("https://github.com/login");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("server")
                    .executes(context -> {
                        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code server invoked");
                        openScreen(SERVER_URL);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("close")
                    .executes(context -> {
                        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code close invoked");
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.currentScreen instanceof CodeScreenGUI) {
                                com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] Closing active CodeScreenGUI");
                                mc.currentScreen.close();
                            } else {
                                com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] No CodeScreenGUI active; close ignored");
                            }
                        });
                        return 1;
                    }))
                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                    .executes(context -> {
                        String url = StringArgumentType.getString(context, "url");
                        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] /code <url> invoked with {}", url);
                        openScreen(url);
                        return 1;
                    }))
        );
    }

    private static String pendingUrl = null;
    private static int pendingTicks = 0;

    /** Call from ClientTickEvents to handle delayed screen opening */
    public static void tick(MinecraftClient client) {
        if (pendingUrl != null && pendingTicks > 0) {
            pendingTicks--;
            com.minecraftuse.MinecraftUseMod.LOGGER.info(
                "[CodeCommand] Pending screen countdown for {} now at {} ticks",
                pendingUrl,
                pendingTicks
            );
            if (pendingTicks == 0) {
                com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] Opening screen after delay: {}", pendingUrl);
                client.setScreen(new CodeScreenGUI(pendingUrl));
                pendingUrl = null;
            }
        }
    }

    private static void openScreen(String url) {
        com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] Scheduling screen open for URL: {}", url);
        pendingUrl = url;
        pendingTicks = 3;
    }
}
