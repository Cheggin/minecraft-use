package com.minecraftuse.commands;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraftuse.MinecraftUseMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class BuildCommand {

    private static final String CONVEX_URL = "https://sincere-bandicoot-65.convex.cloud";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("build")
                .then(ClientCommandManager.literal("list")
                    .executes(context -> {
                        listSchematics(context.getSource());
                        return 1;
                    }))
                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                    .executes(context -> {
                        String name = StringArgumentType.getString(context, "name");
                        executeBuild(context.getSource(), name);
                        return 1;
                    }))
        );
    }

    private static void listSchematics(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("§e[MCUse] §7Fetching schematics from Convex..."));

        Thread thread = new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("path", "schematics:listSchematics");
                body.add("args", new JsonObject());

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CONVEX_URL + "/api/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    sendFeedback(source, "§e[MCUse] §cFailed to fetch from Convex: " + response.statusCode());
                    return;
                }

                JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                JsonElement value = result.get("value");

                if (value == null || !value.isJsonArray()) {
                    sendFeedback(source, "§e[MCUse] §7No schematics found.");
                    return;
                }

                JsonArray schematics = value.getAsJsonArray();
                sendFeedback(source, "§e[MCUse] §f" + schematics.size() + " schematic(s) available:");

                for (JsonElement el : schematics) {
                    JsonObject obj = el.getAsJsonObject();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "unknown";
                    String fileName = obj.has("fileName") ? obj.get("fileName").getAsString() : "";
                    String category = obj.has("category") ? obj.get("category").getAsString() : "";
                    int fileSize = obj.has("fileSize") ? obj.get("fileSize").getAsInt() : 0;

                    String label = fileName.replace(".schem", "").replace(".schematic", "");
                    sendFeedback(source, "§a  " + label + " §7- " + name + " §8(" + category + ", " + fileSize + " bytes)");
                }

                sendFeedback(source, "§7Use: /build <filename> to download for Litematica");

            } catch (Exception e) {
                sendFeedback(source, "§e[MCUse] §cError: " + e.getMessage());
            }
        }, "BuildCommand-list");
        thread.setDaemon(true);
        thread.start();
    }

    private static void executeBuild(FabricClientCommandSource source, String name) {
        source.sendFeedback(Text.literal("§e[MCUse] §fDownloading schematic: §a" + name + "§f..."));

        Thread thread = new Thread(() -> {
            try {
                String fileName = name.endsWith(".schem") ? name : name + ".schem";

                // Query Convex for the schematic
                JsonObject body = new JsonObject();
                body.addProperty("path", "schematics:getSchematicByFileName");
                JsonObject queryArgs = new JsonObject();
                queryArgs.addProperty("fileName", fileName);
                body.add("args", queryArgs);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CONVEX_URL + "/api/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    sendFeedback(source, "§e[MCUse] §cFailed to query Convex: " + response.statusCode());
                    return;
                }

                JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                JsonElement value = result.get("value");

                if (value == null || value.isJsonNull()) {
                    sendFeedback(source, "§e[MCUse] §cSchematic not found in database: " + fileName);
                    return;
                }

                JsonObject schematic = value.getAsJsonObject();
                String fileUrl = schematic.has("fileUrl") && !schematic.get("fileUrl").isJsonNull()
                    ? schematic.get("fileUrl").getAsString() : null;
                String schematicName = schematic.has("name") ? schematic.get("name").getAsString() : fileName;

                if (fileUrl == null || fileUrl.isEmpty()) {
                    sendFeedback(source, "§e[MCUse] §cNo download URL for schematic: " + fileName);
                    return;
                }

                // Download the .schem file
                sendFeedback(source, "§e[MCUse] §7Downloading from Convex...");
                HttpRequest dlRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fileUrl))
                    .GET()
                    .build();

                HttpResponse<byte[]> dlResponse = HTTP.send(dlRequest, HttpResponse.BodyHandlers.ofByteArray());
                byte[] fileBytes = dlResponse.body();

                // Save to Litematica's schematics folder
                MinecraftClient client = MinecraftClient.getInstance();
                Path schematicsDir = client.runDirectory.toPath().resolve("schematics");
                Files.createDirectories(schematicsDir);
                Path localFile = schematicsDir.resolve(fileName);
                Files.write(localFile, fileBytes);

                sendFeedback(source, "§e[MCUse] §aDownloaded: §f" + schematicName + " §7(" + fileBytes.length + " bytes)");
                sendFeedback(source, "§e[MCUse] §7Saved to: §fschematics/" + fileName);
                sendFeedback(source, "§e[MCUse] §7Open Litematica (§fM key§7) → §fSchematic Browser §7→ load and place");

            } catch (Exception e) {
                sendFeedback(source, "§e[MCUse] §cBuild failed: " + e.getMessage());
                MinecraftUseMod.LOGGER.error("[BuildCommand] Error: {}", e.getMessage(), e);
            }
        }, "BuildCommand-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void sendFeedback(FabricClientCommandSource source, String message) {
        MinecraftClient.getInstance().execute(() ->
            source.sendFeedback(Text.literal(message))
        );
    }
}
