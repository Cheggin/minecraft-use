package com.minecraftuse.catalog;

import com.minecraftuse.MinecraftUseMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Lazy-loads PNG thumbnails from disk as NativeImageBackedTexture instances.
 * Caches loaded textures by file path to avoid redundant loads.
 */
public class ThumbnailManager {

    private static final String TEXTURE_PREFIX = "minecraft-use/thumbnail/";
    private static final Identifier MISSING_TEXTURE = Identifier.of(MinecraftUseMod.MOD_ID, "textures/missing.png");

    private final Map<String, Identifier> cache = new HashMap<>();
    private final File thumbnailBaseDir;

    public ThumbnailManager(File thumbnailBaseDir) {
        this.thumbnailBaseDir = thumbnailBaseDir;
    }

    /**
     * Returns the texture Identifier for a thumbnail path, loading it lazily.
     * Returns MISSING_TEXTURE if the file doesn't exist or fails to load.
     */
    public Identifier getTexture(String thumbnailPath) {
        if (thumbnailPath == null || thumbnailPath.isBlank()) {
            return MISSING_TEXTURE;
        }

        if (cache.containsKey(thumbnailPath)) {
            return cache.get(thumbnailPath);
        }

        File file = new File(thumbnailBaseDir, thumbnailPath);
        if (!file.exists()) {
            MinecraftUseMod.LOGGER.warn("[ThumbnailManager] Thumbnail not found: {}", file.getAbsolutePath());
            cache.put(thumbnailPath, MISSING_TEXTURE);
            return MISSING_TEXTURE;
        }

        try {
            Identifier textureId = loadTexture(thumbnailPath, file);
            cache.put(thumbnailPath, textureId);
            return textureId;
        } catch (IOException e) {
            MinecraftUseMod.LOGGER.warn("[ThumbnailManager] Failed to load thumbnail {}: {}", thumbnailPath, e.getMessage());
            cache.put(thumbnailPath, MISSING_TEXTURE);
            return MISSING_TEXTURE;
        }
    }

    private Identifier loadTexture(String thumbnailPath, File file) throws IOException {
        // Build a stable texture ID from the path
        String sanitized = thumbnailPath.toLowerCase()
            .replaceAll("[^a-z0-9_/.-]", "_");
        Identifier textureId = Identifier.of(MinecraftUseMod.MOD_ID, TEXTURE_PREFIX + sanitized);

        try (InputStream stream = Files.newInputStream(file.toPath())) {
            net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
            textureManager.registerTexture(textureId, texture);
            MinecraftUseMod.LOGGER.debug("[ThumbnailManager] Loaded thumbnail: {}", textureId);
        }

        return textureId;
    }

    /**
     * Releases all cached textures and clears the cache.
     */
    public void dispose() {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : cache.values()) {
            if (!id.equals(MISSING_TEXTURE)) {
                textureManager.destroyTexture(id);
            }
        }
        cache.clear();
    }
}
