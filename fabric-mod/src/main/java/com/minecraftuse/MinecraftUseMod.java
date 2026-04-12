package com.minecraftuse;

import com.minecraftuse.commands.AgentChatCommand;
import com.minecraftuse.commands.AgentTellCommand;
import com.minecraftuse.commands.BrowserUseCommand;
import com.minecraftuse.commands.BuildCommand;
import com.minecraftuse.commands.CatalogCommand;
import com.minecraftuse.commands.ClaudeCommand;
import com.minecraftuse.commands.DespawnCommand;
import com.minecraftuse.commands.DownloadCommand;
import com.minecraftuse.commands.ListCommand;
import com.minecraftuse.commands.ListenCommand;
import com.minecraftuse.commands.ShellCommand;
import com.minecraftuse.commands.SpawnCommand;
import com.minecraftuse.commands.TmuxReadCommand;
import com.minecraftuse.commands.TmuxSendCommand;
import com.minecraftuse.commands.UndoCommand;
import com.minecraftuse.gui.CatalogScreen;
import com.minecraftuse.network.SidecarClient;
import com.minecraftuse.villager.VillagerInteractionHandler;
import com.minecraftuse.villager.VillagerRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecraftUseMod implements ClientModInitializer {

    public static final String MOD_ID = "minecraft-use";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final SidecarClient SIDECAR = new SidecarClient("http://localhost:8765");

    private static KeyBinding openCatalogKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Minecraft Use mod initializing...");
        ModSounds.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            AgentChatCommand.register(dispatcher);
            AgentTellCommand.register(dispatcher);
            BrowserUseCommand.register(dispatcher);
            BuildCommand.register(dispatcher);
            CatalogCommand.register(dispatcher);
            ClaudeCommand.register(dispatcher);
            DespawnCommand.register(dispatcher);
            DownloadCommand.register(dispatcher);
            ListCommand.register(dispatcher);
            ListenCommand.register(dispatcher);
            ShellCommand.register(dispatcher);
            SpawnCommand.register(dispatcher);
            TmuxSendCommand.register(dispatcher);
            TmuxReadCommand.register(dispatcher);
            UndoCommand.register(dispatcher);
        });

        VillagerInteractionHandler.init();

        // Register K keybind to open the catalog screen
        openCatalogKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.minecraft-use.open_catalog",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "category.minecraft-use"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openCatalogKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CatalogScreen());
                }
            }
            VillagerRegistry.getInstance().tickAll();
        });

        LOGGER.info("Minecraft Use mod initialized — commands and keybinds registered");
    }
}
