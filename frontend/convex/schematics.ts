import { v } from "convex/values";
import { mutation, query, action } from "./_generated/server";
import { api } from "./_generated/api";

export const generateUploadUrl = mutation({
  args: {},
  handler: async (ctx) => {
    return await ctx.storage.generateUploadUrl();
  },
});

export const storeSchematic = mutation({
  args: {
    name: v.string(),
    fileName: v.string(),
    fileId: v.id("_storage"),
    fileSize: v.number(),
    category: v.optional(v.string()),
    tags: v.optional(v.array(v.string())),
    author: v.optional(v.string()),
    sourceUrl: v.optional(v.string()),
    dimensions: v.optional(
      v.object({
        width: v.number(),
        height: v.number(),
        length: v.number(),
      })
    ),
    thumbnailUrl: v.optional(v.string()),
    rating: v.optional(v.number()),
    downloads: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const id = await ctx.db.insert("schematics", args);
    return id;
  },
});

export const listSchematics = query({
  args: {
    count: v.optional(v.number()),
  },
  handler: async (ctx, args) => {
    const limit = args.count ?? 50;
    const schematics = await ctx.db.query("schematics").order("desc").take(limit);
    return await Promise.all(
      schematics.map(async (s) => {
        const url = await ctx.storage.getUrl(s.fileId);
        return { ...s, fileUrl: url };
      })
    );
  },
});

export const searchSchematics = query({
  args: {
    query: v.string(),
    category: v.optional(v.string()),
  },
  handler: async (ctx, args) => {
    let search = ctx.db
      .query("schematics")
      .withSearchIndex("search_name", (q) => {
        let s = q.search("name", args.query);
        if (args.category) {
          s = s.eq("category", args.category);
        }
        return s;
      });
    return await search.take(20);
  },
});

export const getSchematicFile = query({
  args: {
    id: v.id("schematics"),
  },
  handler: async (ctx, args) => {
    const schematic = await ctx.db.get(args.id);
    if (!schematic) return null;
    const url = await ctx.storage.getUrl(schematic.fileId);
    return { ...schematic, fileUrl: url };
  },
});

export const listByCategory = query({
  args: {
    category: v.string(),
  },
  handler: async (ctx, args) => {
    return await ctx.db
      .query("schematics")
      .withIndex("by_category", (q) => q.eq("category", args.category))
      .take(50);
  },
});

export const getCategories = query({
  args: {},
  handler: async (ctx) => {
    const all = await ctx.db.query("schematics").take(200);
    const categories = new Set<string>();
    for (const s of all) {
      if (s.category) categories.add(s.category);
    }
    return Array.from(categories).sort();
  },
});

export const getSchematicByName = query({
  args: {
    name: v.string(),
  },
  handler: async (ctx, args) => {
    const schematics = await ctx.db.query("schematics").collect();
    // Fuzzy match: case-insensitive, partial match on name or fileName
    const query = args.name.toLowerCase();
    const match = schematics.find(
      (s) =>
        s.name.toLowerCase().includes(query) ||
        s.fileName.toLowerCase().includes(query) ||
        s.fileName.toLowerCase().replace(".schem", "").replace(".schematic", "") === query
    );
    if (!match) return null;
    const url = await ctx.storage.getUrl(match.fileId);
    return { ...match, fileUrl: url };
  },
});

export const getSchematicByFileName = query({
  args: {
    fileName: v.string(),
  },
  handler: async (ctx, args) => {
    const schematics = await ctx.db.query("schematics").collect();
    const match = schematics.find((s) => s.fileName === args.fileName);
    if (!match) return null;
    const url = await ctx.storage.getUrl(match.fileId);
    return { ...match, fileUrl: url };
  },
});
