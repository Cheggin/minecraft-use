package com.minecraftuse.villager;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;

import java.lang.reflect.Field;
import java.util.Map;

public class AgentVillager {

    private static final double FOLLOW_SPEED = 0.6;

    private static final Map<String, EntityType<?>> MOB_TYPES = Map.ofEntries(
        Map.entry("villager", EntityType.VILLAGER),
        Map.entry("wolf", EntityType.WOLF),
        Map.entry("cat", EntityType.CAT),
        Map.entry("pig", EntityType.PIG),
        Map.entry("cow", EntityType.COW),
        Map.entry("sheep", EntityType.SHEEP),
        Map.entry("chicken", EntityType.CHICKEN),
        Map.entry("fox", EntityType.FOX),
        Map.entry("parrot", EntityType.PARROT),
        Map.entry("rabbit", EntityType.RABBIT),
        Map.entry("horse", EntityType.HORSE),
        Map.entry("donkey", EntityType.DONKEY),
        Map.entry("llama", EntityType.LLAMA),
        Map.entry("goat", EntityType.GOAT),
        Map.entry("bee", EntityType.BEE),
        Map.entry("axolotl", EntityType.AXOLOTL),
        Map.entry("frog", EntityType.FROG),
        Map.entry("camel", EntityType.CAMEL),
        Map.entry("sniffer", EntityType.SNIFFER),
        Map.entry("allay", EntityType.ALLAY),
        Map.entry("iron_golem", EntityType.IRON_GOLEM),
        Map.entry("snow_golem", EntityType.SNOW_GOLEM),
        Map.entry("zombie", EntityType.ZOMBIE),
        Map.entry("skeleton", EntityType.SKELETON),
        Map.entry("creeper", EntityType.CREEPER),
        Map.entry("enderman", EntityType.ENDERMAN)
    );

    private final MobEntity entity;
    private final String name;

    private AgentVillager(MobEntity entity, String name) {
        this.entity = entity;
        this.name = name;
    }

    public static AgentVillager spawn(ServerWorld world, Vec3d pos, String name, String mobType) {
        EntityType<?> type = MOB_TYPES.getOrDefault(mobType, EntityType.VILLAGER);

        MobEntity mob = (MobEntity) type.create(world);
        if (mob == null) {
            mob = (MobEntity) EntityType.VILLAGER.create(world);
        }

        mob.setPosition(pos.x, pos.y, pos.z);
        mob.setCustomName(Text.literal(name));
        mob.setCustomNameVisible(true);
        mob.setInvulnerable(true);
        mob.setNoGravity(false);

        // Set villager profession if it's a villager
        if (mob instanceof VillagerEntity villager) {
            villager.setVillagerData(
                new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, 1)
            );
        }

        // Replace AI goals with follow player
        try {
            Field gsField = MobEntity.class.getDeclaredField("goalSelector");
            gsField.setAccessible(true);
            GoalSelector goalSelector = (GoalSelector) gsField.get(mob);
            goalSelector.clear(goal -> true);
            goalSelector.add(1, new FollowPlayerGoal(mob, FOLLOW_SPEED));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fallback: mob will use default AI but won't follow
        }

        world.spawnEntity(mob);

        // Play spawn sound
        world.playSound(null, mob.getBlockPos(),
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.NEUTRAL, 1.0f, 1.0f);

        return new AgentVillager(mob, name);
    }

    public static String[] getAvailableMobTypes() {
        return MOB_TYPES.keySet().toArray(new String[0]);
    }

    public void despawn() {
        if (entity.isAlive()) {
            entity.discard();
        }
    }

    public MobEntity getEntity() {
        return entity;
    }

    public String getName() {
        return name;
    }
}
