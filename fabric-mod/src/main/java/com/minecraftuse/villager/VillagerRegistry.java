package com.minecraftuse.villager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import com.minecraftuse.ProfileSoundManager;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillagerRegistry {

    public record AgentVillagerData(
        MobEntity villager,
        String paneName,
        FloatingText floatingText,
        OutputPoller outputPoller,
        Integer monitoredPort
    ) {
        /** Constructor for agent villagers (no port monitoring) */
        public AgentVillagerData(MobEntity villager, String paneName, FloatingText floatingText, OutputPoller outputPoller) {
            this(villager, paneName, floatingText, outputPoller, null);
        }
    }

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
            killMonitoredPort(data);
        }
    }

    public AgentVillagerData getByName(String name) {
        return byName.get(name);
    }

    public AgentVillagerData getByEntity(MobEntity entity) {
        for (AgentVillagerData data : byName.values()) {
            if (data.villager().equals(entity)) {
                return data;
            }
        }
        return null;
    }

    public String getNameByEntity(MobEntity entity) {
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
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, AgentVillagerData> entry : byName.entrySet()) {
            AgentVillagerData data = entry.getValue();
            if (data.villager().isAlive()) {
                data.floatingText().tick(data.villager().getPos().add(0, 1.2, 0));
            } else {
                dead.add(entry.getKey());
            }
        }
        // Clean up dead villagers
        for (String name : dead) {
            AgentVillagerData data = byName.remove(name);
            if (data != null) {
                data.outputPoller().stop();
                data.floatingText().remove();
                // Kill the tmux pane — resolve label to pane ID first via tmux-bridge
                try {
                    String tmuxBridge = System.getProperty("user.home") + "/.smux/bin/tmux-bridge";
                    ProcessBuilder resolvePb = new ProcessBuilder(tmuxBridge, "resolve", data.paneName());
                    resolvePb.environment().put("PATH", "/opt/homebrew/bin:" + resolvePb.environment().getOrDefault("PATH", "/usr/bin:/bin"));
                    resolvePb.redirectErrorStream(true);
                    Process resolveProc = resolvePb.start();
                    String paneId = new String(resolveProc.getInputStream().readAllBytes()).trim();
                    resolveProc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (!paneId.isEmpty()) {
                        ProcessBuilder killPb = new ProcessBuilder("/opt/homebrew/bin/tmux", "kill-pane", "-t", paneId);
                        killPb.redirectErrorStream(true);
                        killPb.start().waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    }
                } catch (Exception ignored) {}
                // Kill monitored port process if this was a listen-villager
                killMonitoredPort(data);
                // Play death sound and show message
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null && client.player != null) {
                    ProfileSoundManager.playDeathSound(name, client.player);
                    client.player.sendMessage(
                        Text.literal("§e[MCUse] §cDespawned agent: §f" + name + " §7(villager died)"),
                        false
                    );
                }
            }
        }
    }

    /** Kill all processes on the monitored port, if this was a listen-villager. */
    private void killMonitoredPort(AgentVillagerData data) {
        if (data.monitoredPort() == null) return;
        Thread killThread = new Thread(() -> {
            try {
                // Re-resolve PIDs at kill time to avoid stale PID race conditions
                ProcessBuilder lsofPb = new ProcessBuilder("lsof", "-ti", ":" + data.monitoredPort());
                lsofPb.redirectErrorStream(true);
                Process lsofProc = lsofPb.start();
                String pidOutput = new String(lsofProc.getInputStream().readAllBytes()).trim();
                lsofProc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!pidOutput.isEmpty()) {
                    for (String pid : pidOutput.split("\\s+")) {
                        try {
                            new ProcessBuilder("kill", pid.trim()).start()
                                .waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }, "PortKill-" + data.monitoredPort());
        killThread.setDaemon(true);
        killThread.start();
    }

    public void clear() {
        for (String name : byName.keySet().toArray(new String[0])) {
            unregister(name);
        }
    }
}
