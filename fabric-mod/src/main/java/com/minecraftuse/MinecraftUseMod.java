package com.minecraftuse;

import com.minecraftuse.commands.BuildCommand;
import com.minecraftuse.commands.DownloadCommand;
import com.minecraftuse.commands.ListCommand;
import com.minecraftuse.network.SidecarClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftUseMod implements ClientModInitializer {

    public static final String MOD_ID = "minecraft-use";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final SidecarClient SIDECAR = new SidecarClient("http://localhost:8765");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Minecraft Use mod initializing...");

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            BuildCommand.register(dispatcher);
            DownloadCommand.register(dispatcher);
            ListCommand.register(dispatcher);
        });

        LOGGER.info("Minecraft Use mod initialized — commands registered");
    }
}
