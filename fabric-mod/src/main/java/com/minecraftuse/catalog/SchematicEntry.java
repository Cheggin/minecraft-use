package com.minecraftuse.catalog;

import java.util.List;

/**
 * Represents a single entry in the schematic catalog index.
 */
public record SchematicEntry(
    String id,
    String name,
    String author,
    String category,
    List<String> tags,
    double rating,
    int downloads,
    int[] dimensions,
    String file,
    String thumbnail,
    String sourceUrl
) {}
