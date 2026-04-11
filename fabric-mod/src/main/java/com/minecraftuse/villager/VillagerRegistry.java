package com.minecraftuse.villager;

import net.minecraft.entity.passive.VillagerEntity;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class VillagerRegistry {

    public record AgentVillagerData(
        VillagerEntity villager,
        String paneName,
        FloatingText floatingText,
        OutputPoller outputPoller
    ) {}

    private static final VillagerRegistry INSTANCE = new VillagerRegistry();

    private final Map<String, AgentVillagerData> byName = new HashMap<>();

    private VillagerRegistry() {}

    public static VillagerRegistry getInstance() {
        return INSTANCE;
    }

    public void register(String name, AgentVillagerData data) {
        byName.put(name, data);
    }

    public void unregister(String name) {
        AgentVillagerData data = byName.remove(name);
        if (data != null) {
            data.outputPoller().stop();
            data.floatingText().remove();
            if (data.villager().isAlive()) {
                data.villager().discard();
            }
        }
    }

    public AgentVillagerData getByName(String name) {
        return byName.get(name);
    }

    public AgentVillagerData getByEntity(VillagerEntity entity) {
        for (AgentVillagerData data : byName.values()) {
            if (data.villager().equals(entity)) {
                return data;
            }
        }
        return null;
    }

    public String getNameByEntity(VillagerEntity entity) {
        for (Map.Entry<String, AgentVillagerData> entry : byName.entrySet()) {
            if (entry.getValue().villager().equals(entity)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public Collection<Map.Entry<String, AgentVillagerData>> getAll() {
        return Collections.unmodifiableCollection(byName.entrySet());
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    public void tickAll() {
        for (AgentVillagerData data : byName.values()) {
            if (data.villager().isAlive()) {
                data.floatingText().tick(data.villager().getPos().add(0, 2.2, 0));
            }
        }
    }

    public void clear() {
        for (String name : byName.keySet().toArray(new String[0])) {
            unregister(name);
        }
    }
}
