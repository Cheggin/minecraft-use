package com.minecraftuse.villager;

import com.minecraftuse.gui.VillagerChatScreen;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

public class VillagerInteractionHandler {

    public static void init() {
        UseEntityCallback.EVENT.register(VillagerInteractionHandler::onUseEntity);
    }

    private static ActionResult onUseEntity(
            PlayerEntity player,
            World world,
            Hand hand,
            Entity entity,
            EntityHitResult hitResult) {

        if (world.isClient() && entity instanceof VillagerEntity villager) {
            VillagerRegistry.AgentVillagerData data = VillagerRegistry.getInstance().getByEntity(villager);
            if (data != null) {
                String name = VillagerRegistry.getInstance().getNameByEntity(villager);
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> client.setScreen(new VillagerChatScreen(name, data)));
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }
}
