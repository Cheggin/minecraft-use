package com.minecraftuse;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public class ModSounds {

    public static final Identifier AGENT_DEATH_ID = Identifier.of("minecraft-use", "agent_death");
    public static final SoundEvent AGENT_DEATH = SoundEvent.of(AGENT_DEATH_ID);

    private static final String[] SPAWN_NAMES = {
        "alex", "gregor", "gregor2", "aitor", "magnus", "shawn", "larsen", "reagan_whats_up_picka"
    };

    private static final Map<String, SoundEvent> SPAWN_SOUNDS = new HashMap<>();

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, AGENT_DEATH_ID, AGENT_DEATH);

        for (String name : SPAWN_NAMES) {
            Identifier id = Identifier.of("minecraft-use", name + "_spawn");
            SoundEvent event = SoundEvent.of(id);
            Registry.register(Registries.SOUND_EVENT, id, event);
            SPAWN_SOUNDS.put(name, event);
        }
        // Aliases — short names map to their full sound
        SPAWN_SOUNDS.put("reagan", SPAWN_SOUNDS.get("reagan_whats_up_picka"));
        SPAWN_SOUNDS.put("greg", SPAWN_SOUNDS.get("gregor"));
        SPAWN_SOUNDS.put("gregor2", SPAWN_SOUNDS.get("gregor2"));
    }

    /**
     * Get the spawn sound for an agent name. Returns null if no custom sound exists.
     */
    public static SoundEvent getSpawnSound(String agentName) {
        return SPAWN_SOUNDS.get(agentName.toLowerCase());
    }
}
