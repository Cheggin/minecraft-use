package com.minecraftuse.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraftuse.MinecraftUseMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches schematic catalog from Convex database dynamically.
 */
public class CatalogIndex {

    private static final String CONVEX_URL = "https://sincere-bandicoot-65.convex.cloud";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private volatile List<SchematicEntry> entries = Collections.emptyList();

    public CatalogIndex() {}

    /**
     * Fetch all schematics from Convex. Can be called from any thread.
     */
    public CompletableFuture<List<SchematicEntry>> refresh() {
        return CompletableFuture.supplyAsync(() -> {
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
                    MinecraftUseMod.LOGGER.warn("[CatalogIndex] Convex query failed: {} {}", response.statusCode(), response.body());
                    return entries;
                }

                JsonObject result = GSON.fromJson(response.body(), JsonObject.class);
                JsonElement value = result.get("value");

                if (value == null || !value.isJsonArray()) {
                    MinecraftUseMod.LOGGER.warn("[CatalogIndex] Unexpected response: {}", response.body());
                    return entries;
                }

                List<SchematicEntry> newEntries = new ArrayList<>();
                for (JsonElement element : value.getAsJsonArray()) {
                    JsonObject obj = element.getAsJsonObject();
                    newEntries.add(parseEntry(obj));
                }

                entries = Collections.unmodifiableList(newEntries);
                MinecraftUseMod.LOGGER.info("[CatalogIndex] Loaded {} schematics from Convex", entries.size());
                return entries;

            } catch (Exception e) {
                MinecraftUseMod.LOGGER.error("[CatalogIndex] Failed to fetch from Convex: {}", e.getMessage());
                return entries;
            }
        });
    }

    private static SchematicEntry parseEntry(JsonObject obj) {
        String id = getStringOrEmpty(obj, "_id");
        String name = getStringOrEmpty(obj, "name");
        String author = getStringOrEmpty(obj, "author");
        String category = getStringOrEmpty(obj, "category");
        String file = getStringOrEmpty(obj, "fileName");
        String thumbnail = "";
        String sourceUrl = getStringOrEmpty(obj, "sourceUrl");
        String fileUrl = getStringOrEmpty(obj, "fileUrl");
        double rating = obj.has("rating") ? obj.get("rating").getAsDouble() : 0.0;
        int downloads = obj.has("downloads") ? obj.get("downloads").getAsInt() : 0;
        int fileSize = obj.has("fileSize") ? obj.get("fileSize").getAsInt() : 0;

        List<String> tags = new ArrayList<>();
        if (obj.has("tags") && obj.get("tags").isJsonArray()) {
            for (JsonElement tag : obj.getAsJsonArray("tags")) {
                tags.add(tag.getAsString());
            }
        }

        int[] dimensions = new int[]{0, 0, 0};

        return new SchematicEntry(id, name, author, category, tags, rating, downloads, dimensions, file, thumbnail, sourceUrl);
    }

    private static String getStringOrEmpty(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : "";
    }

    public List<SchematicEntry> getAll() {
        return entries;
    }

    /**
     * Fuzzy search over name, author, category, and tags.
     */
    public List<SchematicEntry> search(String query) {
        if (query == null || query.isBlank()) {
            return entries;
        }

        String[] tokens = query.toLowerCase().trim().split("\\s+");

        record ScoredEntry(SchematicEntry entry, int score) {}

        List<ScoredEntry> scored = new ArrayList<>();
        for (SchematicEntry entry : entries) {
            int score = computeScore(entry, tokens);
            if (score > 0) {
                scored.add(new ScoredEntry(entry, score));
            }
        }

        scored.sort(Comparator.comparingInt(ScoredEntry::score).reversed());

        List<SchematicEntry> results = new ArrayList<>(scored.size());
        for (ScoredEntry se : scored) {
            results.add(se.entry());
        }
        return results;
    }

    private int computeScore(SchematicEntry entry, String[] tokens) {
        int score = 0;
        String nameLower = entry.name().toLowerCase();
        String authorLower = entry.author().toLowerCase();
        String categoryLower = entry.category().toLowerCase();

        for (String token : tokens) {
            if (nameLower.contains(token)) score += 3;
            if (categoryLower.contains(token)) score += 2;
            if (authorLower.contains(token)) score += 1;
            for (String tag : entry.tags()) {
                if (tag.toLowerCase().contains(token)) {
                    score += 2;
                    break;
                }
            }
        }
        return score;
    }
}
