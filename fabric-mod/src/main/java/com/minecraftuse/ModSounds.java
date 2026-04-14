package com.minecraftuse;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    public static final Identifier AGENT_DEATH_ID = Identifier.of("minecraft-use", "agent_death");
    public static final SoundEvent AGENT_DEATH = SoundEvent.of(AGENT_DEATH_ID);

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, AGENT_DEATH_ID, AGENT_DEATH);
    }
}
