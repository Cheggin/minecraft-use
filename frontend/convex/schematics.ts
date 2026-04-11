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
    return await ctx.db.query("schematics").order("desc").take(limit);
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
