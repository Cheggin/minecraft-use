package com.minecraftuse.commands;

import com.minecraftuse.screen.CodeScreenGUI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

public class CodeCommand {

    private static final String DEFAULT_URL = "https://vscode.dev";
    private static final String SERVER_URL = "http://localhost:8080";

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("code")
                .executes(context -> {
                    openScreen(DEFAULT_URL);
                    return 1;
                })
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

    private static void openScreen(String url) {
        MinecraftClient.getInstance().execute(() ->
            MinecraftClient.getInstance().setScreen(new CodeScreenGUI(url))
        );
    }
}
