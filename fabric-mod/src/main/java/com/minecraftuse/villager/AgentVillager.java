package com.minecraftuse.villager;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.village.VillagerProfession;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerType;

import java.lang.reflect.Field;

public class AgentVillager {

    private static final double FOLLOW_SPEED = 0.6;

    private final VillagerEntity entity;
    private final String name;

    private AgentVillager(VillagerEntity entity, String name) {
        this.entity = entity;
        this.name = name;
    }

    public static AgentVillager spawn(ServerWorld world, Vec3d pos, String name) {
        VillagerEntity villager = new VillagerEntity(
            net.minecraft.entity.EntityType.VILLAGER,
            world
        );

        villager.setPosition(pos.x, pos.y, pos.z);
        villager.setCustomName(Text.literal(name));
        villager.setCustomNameVisible(true);

        villager.setVillagerData(
            new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1)
        );

        villager.setInvulnerable(true);
        villager.setNoGravity(false);

        // goalSelector is protected — access via reflection
        try {
            Field gsField = MobEntity.class.getDeclaredField("goalSelector");
            gsField.setAccessible(true);
            GoalSelector goalSelector = (GoalSelector) gsField.get(villager);
            goalSelector.clear(goal -> true);
            goalSelector.add(1, new FollowPlayerGoal(villager, FOLLOW_SPEED));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // If reflection fails, goalSelector field name may differ in this mapping
            // Villager will still spawn and follow via brain, just without custom goal
        }

        world.spawnEntity(villager);

        // Play spawn sound
        world.playSound(null, villager.getBlockPos(),
            SoundEvents.ENTITY_VILLAGER_CELEBRATE,
            SoundCategory.NEUTRAL, 1.0f, 1.0f);

        return new AgentVillager(villager, name);
    }

    public void despawn() {
        if (entity.isAlive()) {
            entity.discard();
        }
    }

    public VillagerEntity getEntity() {
        return entity;
    }

    public String getName() {
        return name;
    }
}
