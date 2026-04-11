package com.minecraftuse.catalog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecraftuse.MinecraftUseMod;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads and provides fuzzy search over the schematic catalog_index.json.
 */
public class CatalogIndex {

    private static final Gson GSON = new Gson();

    private final List<SchematicEntry> entries;

    public CatalogIndex(List<SchematicEntry> entries) {
        this.entries = Collections.unmodifiableList(entries);
    }

    public static CatalogIndex load(File catalogFile) throws IOException {
        try (FileReader reader = new FileReader(catalogFile)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            List<SchematicEntry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                entries.add(parseEntry(obj));
            }
            MinecraftUseMod.LOGGER.info("[CatalogIndex] Loaded {} entries from {}", entries.size(), catalogFile.getName());
            return new CatalogIndex(entries);
        }
    }

    private static SchematicEntry parseEntry(JsonObject obj) {
        String id = getStringOrEmpty(obj, "id");
        String name = getStringOrEmpty(obj, "name");
        String author = getStringOrEmpty(obj, "author");
        String category = getStringOrEmpty(obj, "category");
        String file = getStringOrEmpty(obj, "file");
        String thumbnail = getStringOrEmpty(obj, "thumbnail");
        String sourceUrl = getStringOrEmpty(obj, "sourceUrl");
        double rating = obj.has("rating") ? obj.get("rating").getAsDouble() : 0.0;
        int downloads = obj.has("downloads") ? obj.get("downloads").getAsInt() : 0;

        List<String> tags = new ArrayList<>();
        if (obj.has("tags") && obj.get("tags").isJsonArray()) {
            for (JsonElement tag : obj.getAsJsonArray("tags")) {
                tags.add(tag.getAsString());
            }
        }

        int[] dimensions = new int[]{0, 0, 0};
        if (obj.has("dimensions") && obj.get("dimensions").isJsonArray()) {
            JsonArray dims = obj.getAsJsonArray("dimensions");
            if (dims.size() >= 3) {
                dimensions[0] = dims.get(0).getAsInt();
                dimensions[1] = dims.get(1).getAsInt();
                dimensions[2] = dims.get(2).getAsInt();
            }
        }

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
     * Scores each entry by how many query tokens appear as substrings (case-insensitive).
     * Returns results sorted by descending score, filtered to score > 0.
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
            if (nameLower.contains(token)) {
                // Name matches are worth more
                score += 3;
            }
            if (categoryLower.contains(token)) {
                score += 2;
            }
            if (authorLower.contains(token)) {
                score += 1;
            }
            for (String tag : entry.tags()) {
                if (tag.toLowerCase().contains(token)) {
                    score += 2;
                    break;
                }
            }
        }
        return score;
    }

    /**
     * Filter entries by exact category match (case-insensitive).
     */
    public List<SchematicEntry> filterByCategory(String category) {
        if (category == null || category.isBlank()) {
            return entries;
        }
        String lower = category.toLowerCase();
        List<SchematicEntry> results = new ArrayList<>();
        for (SchematicEntry entry : entries) {
            if (entry.category().toLowerCase().equals(lower)) {
                results.add(entry);
            }
        }
        return results;
    }
}
