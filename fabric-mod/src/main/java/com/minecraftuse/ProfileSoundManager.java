package com.minecraftuse;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Profile-based sound system.
 *
 * Structure:
 *   config/minecraft-code/profiles/<user-profile>/
 *     <agentname>_spawn.ogg    — plays when /agent <agentname> is run
 *     death.ogg                — plays when any agent dies (for this profile)
 *
 * Example:
 *   profiles/reagan/
 *     alex_spawn.ogg
 *     shawn_spawn.ogg
 *     death.ogg
 *   profiles/default/
 *     (empty — uses vanilla sounds)
 *
 * Active profile is set in config/minecraft-code/profile.txt
 */
public class ProfileSoundManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft-use/ProfileSoundManager");

    private static final String PROFILES_SUBPATH = "config/minecraft-code/profiles";
    private static final String PROFILE_CONFIG = "config/minecraft-code/profile.txt";
    private static final String RESOURCE_PACK_NAME = "minecraft-code-sounds";
    private static final String MOD_NAMESPACE = "minecraft-use";

    private static String activeProfile = "default";
    /** agent name → SoundEvent for spawn */
    private static final Map<String, SoundEvent> spawnSounds = new HashMap<>();
    /** Profile-level death sound */
    private static SoundEvent deathSound = null;

    private ProfileSoundManager() {}

    public static void init() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            LOGGER.warn("MinecraftClient not available during ProfileSoundManager.init()");
            return;
        }

        Path runDir = client.runDirectory.toPath();
        Path profilesDir = runDir.resolve(PROFILES_SUBPATH);
        Path profileConfig = runDir.resolve(PROFILE_CONFIG);

        // Create profiles directory if missing
        if (!Files.exists(profilesDir)) {
            try {
                Files.createDirectories(profilesDir.resolve("default"));
                writeReadme(profilesDir);
                LOGGER.info("Created profiles directory: {}", profilesDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create profiles directory", e);
            }
        }

        // Read active profile from config
        if (Files.exists(profileConfig)) {
            try {
                activeProfile = Files.readString(profileConfig).trim();
            } catch (IOException e) {
                LOGGER.warn("Failed to read profile.txt, using default");
            }
        } else {
            // Create default profile.txt
            try {
                Files.writeString(profileConfig, "default\n");
            } catch (IOException e) {
                LOGGER.warn("Failed to create profile.txt");
            }
        }

        LOGGER.info("Active sound profile: {}", activeProfile);

        // Load the active profile
        Path activeDir = profilesDir.resolve(activeProfile);
        if (!Files.exists(activeDir) || !Files.isDirectory(activeDir)) {
            LOGGER.info("Profile '{}' not found, no custom sounds loaded", activeProfile);
            return;
        }

        // Prepare resource pack
        Path packRoot = runDir.resolve("resourcepacks").resolve(RESOURCE_PACK_NAME);
        Path soundsDir = packRoot.resolve("assets").resolve(MOD_NAMESPACE).resolve("sounds");
        try {
            Files.createDirectories(soundsDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create resource pack directory", e);
            return;
        }

        // Scan active profile for *_spawn.ogg and death.ogg
        try (var stream = Files.list(activeDir)) {
            stream.filter(p -> p.toString().endsWith(".ogg")).forEach(oggFile -> {
                String fileName = oggFile.getFileName().toString();

                if (fileName.equals("death.ogg")) {
                    // Profile death sound
                    String soundId = activeProfile + "_death";
                    SoundEvent event = copyAndRegister(soundId, oggFile, soundsDir);
                    if (event != null) {
                        deathSound = event;
                        LOGGER.info("Registered death sound for profile '{}'", activeProfile);
                    }
                } else if (fileName.endsWith("_spawn.ogg")) {
                    // Agent-specific spawn sound
                    String agentName = fileName.replace("_spawn.ogg", "");
                    String soundId = agentName + "_spawn";
                    SoundEvent event = copyAndRegister(soundId, oggFile, soundsDir);
                    if (event != null) {
                        spawnSounds.put(agentName, event);
                        LOGGER.info("Registered spawn sound for agent '{}' in profile '{}'", agentName, activeProfile);
                    }
                }
            });
        } catch (IOException e) {
            LOGGER.error("Failed to scan profile directory: {}", activeDir, e);
            return;
        }

        if (!spawnSounds.isEmpty() || deathSound != null) {
            writeSoundsJson(packRoot);
            writePackMcmeta(packRoot);
            LOGGER.info("ProfileSoundManager: {} spawn sounds, death={}", spawnSounds.size(), deathSound != null);
        }
    }

    private static SoundEvent copyAndRegister(String soundId, Path oggFile, Path soundsDir) {
        Identifier id = Identifier.of(MOD_NAMESPACE, soundId);
        Path destFile = soundsDir.resolve(soundId + ".ogg");
        try {
            Files.copy(oggFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to copy sound: {}", oggFile, e);
            return null;
        }

        SoundEvent event = SoundEvent.of(id);
        try {
            Registry.register(Registries.SOUND_EVENT, id, event);
        } catch (Exception e) {
            SoundEvent existing = Registries.SOUND_EVENT.get(id);
            if (existing != null) return existing;
        }
        return event;
    }

    private static void writeSoundsJson(Path packRoot) {
        Path soundsJson = packRoot.resolve("assets").resolve(MOD_NAMESPACE).resolve("sounds.json");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(soundsJson))) {
            pw.println("{");
            int total = spawnSounds.size() + (deathSound != null ? 1 : 0);
            int written = 0;
            for (var entry : spawnSounds.entrySet()) {
                String eventName = entry.getKey() + "_spawn";
                String comma = (++written < total) ? "," : "";
                pw.printf("  \"%s\": { \"sounds\": [\"%s:%s\"] }%s%n",
                    eventName, MOD_NAMESPACE, eventName, comma);
            }
            if (deathSound != null) {
                String eventName = activeProfile + "_death";
                String comma = (++written < total) ? "," : "";
                pw.printf("  \"%s\": { \"sounds\": [\"%s:%s\"] }%s%n",
                    eventName, MOD_NAMESPACE, eventName, comma);
            }
            pw.println("}");
        } catch (IOException e) {
            LOGGER.error("Failed to write sounds.json", e);
        }
    }

    private static void writePackMcmeta(Path packRoot) {
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) return;
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(mcmeta))) {
            pw.println("{");
            pw.println("  \"pack\": {");
            pw.println("    \"pack_format\": 15,");
            pw.println("    \"description\": \"minecraft-code profile sounds\"");
            pw.println("  }");
            pw.println("}");
        } catch (IOException e) {
            LOGGER.error("Failed to write pack.mcmeta", e);
        }
    }

    private static void writeReadme(Path profilesDir) throws IOException {
        Path readme = profilesDir.resolve("README.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(readme))) {
            pw.println("minecraft-code Sound Profiles");
            pw.println("==============================");
            pw.println();
            pw.println("Each subdirectory is a user profile containing custom sounds.");
            pw.println("Set your active profile in config/minecraft-code/profile.txt");
            pw.println();
            pw.println("File naming:");
            pw.println("  <agentname>_spawn.ogg  — plays when /agent <agentname> is run");
            pw.println("  death.ogg              — plays when any agent dies");
            pw.println();
            pw.println("Example (profiles/reagan/):");
            pw.println("  alex_spawn.ogg");
            pw.println("  shawn_spawn.ogg");
            pw.println("  death.ogg");
            pw.println();
            pw.println("The 'default' profile uses vanilla Minecraft sounds.");
            pw.println("Restart Minecraft after changing sounds or profile.");
        }
    }

    // ── Public API ──

    public static String getActiveProfile() {
        return activeProfile;
    }

    public static void playSpawnSound(String agentName, net.minecraft.server.world.ServerWorld world,
                                       net.minecraft.util.math.BlockPos pos) {
        String key = agentName.toLowerCase();
        SoundEvent sound = spawnSounds.get(key);
        if (sound != null) {
            world.playSound(null, pos, sound, SoundCategory.NEUTRAL, 5.0f, 1.0f);
        } else {
            world.playSound(null, pos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.NEUTRAL, 1.0f, 1.0f);
        }
    }

    public static void playDeathSound(String agentName, PlayerEntity player) {
        SoundEvent sound = deathSound != null ? deathSound : ModSounds.AGENT_DEATH;
        player.playSound(sound, 5.0f, 1.0f);
    }
}
