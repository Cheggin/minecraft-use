package com.minecraftuse.commands;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.minecraftuse.MinecraftUseMod;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hold V to record voice, release to transcribe and put text in chat input.
 */
public class VoiceCommand {

    private static final String SIDECAR_URL = "http://localhost:8765";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final float DEFAULT_DURATION = 5.0f;

    private static KeyBinding voiceKey;
    private static final AtomicBoolean recording = new AtomicBoolean(false);
    private static long recordStartTime = 0;

    public static void register() {
        voiceKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.minecraft-use.voice",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "category.minecraft-use"
        ));
    }

    /**
     * Call this from ClientTickEvents to detect key press/release.
     */
    public static void tick(MinecraftClient client) {
        // Allow voice in VillagerChatScreen, block in other GUIs
        // Only allow voice recording when VillagerChatScreen is open
        if (!(client.currentScreen instanceof com.minecraftuse.gui.VillagerChatScreen)) return;

        if (voiceKey.isPressed() && !recording.get()) {
            // Key just pressed — start recording
            recording.set(true);
            recordStartTime = System.currentTimeMillis();

            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[MCUse] §7Recording... release V to stop"), true);
            }
        }

        if (!voiceKey.isPressed() && recording.get()) {
            // Key released — stop and transcribe
            recording.set(false);
            float duration = (System.currentTimeMillis() - recordStartTime) / 1000.0f;
            duration = Math.max(1.0f, Math.min(duration, 30.0f));

            if (client.player != null) {
                client.player.sendMessage(Text.literal("§e[MCUse] §7Transcribing..."), true);
            }

            final float finalDuration = duration;
            Thread thread = new Thread(() -> {
                try {
                    JsonObject body = new JsonObject();
                    body.addProperty("duration", finalDuration);

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SIDECAR_URL + "/transcribe/start"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                    JsonObject result = GSON.fromJson(response.body(), JsonObject.class);

                    if (result.has("text")) {
                        String text = result.get("text").getAsString().trim();
                        if (!text.isEmpty()) {
                            // Put transcribed text into VillagerChatScreen input field
                            client.execute(() -> {
                                if (client.currentScreen instanceof com.minecraftuse.gui.VillagerChatScreen chatScreen) {
                                    chatScreen.setInputText(text);
                                }
                            });
                        } else {
                            client.execute(() -> {
                                if (client.player != null) {
                                    client.player.sendMessage(Text.literal("§e[MCUse] §7(no speech detected)"), true);
                                }
                            });
                        }
                    } else if (result.has("error")) {
                        String error = result.get("error").getAsString();
                        MinecraftUseMod.LOGGER.error("[Voice] Transcribe error: {}", error);
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendMessage(Text.literal("§e[MCUse] §cVoice error: " + error), true);
                            }
                        });
                    }
                } catch (Exception e) {
                    MinecraftUseMod.LOGGER.error("[Voice] Failed: {}", e.getMessage());
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("§e[MCUse] §cVoice failed — is sidecar running?"), true);
                        }
                    });
                }
            }, "VoiceTranscribe");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
