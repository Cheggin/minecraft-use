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
                    openScreen(DEFAULT_URL);
                    return 1;
                })
                .then(ClientCommandManager.literal("repo")
                    .executes(context -> {
                        openScreen(REPO_URL);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("login")
                    .executes(context -> {
                        openScreen("https://github.com/login");
                        return 1;
                    }))
                .then(ClientCommandManager.literal("server")
                    .executes(context -> {
                        openScreen(SERVER_URL);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("close")
                    .executes(context -> {
                        MinecraftClient.getInstance().execute(() -> {
                            MinecraftClient mc = MinecraftClient.getInstance();
                            if (mc.currentScreen instanceof CodeScreenGUI) {
                                mc.currentScreen.close();
                            }
                        });
                        return 1;
                    }))
                .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                    .executes(context -> {
                        String url = StringArgumentType.getString(context, "url");
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
            if (pendingTicks == 0) {
                com.minecraftuse.MinecraftUseMod.LOGGER.info("[CodeCommand] Opening screen after delay: {}", pendingUrl);
                client.setScreen(new CodeScreenGUI(pendingUrl));
                pendingUrl = null;
            }
        }
    }

    private static void openScreen(String url) {
        pendingUrl = url;
        pendingTicks = 3;
    }
}
