import { defineSchema, defineTable } from "convex/server";
import { v } from "convex/values";

export default defineSchema({
  schematics: defineTable({
    name: v.string(),
    fileName: v.string(),
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
    fileId: v.id("_storage"),
    fileSize: v.number(),
    thumbnailUrl: v.optional(v.string()),
    rating: v.optional(v.number()),
    downloads: v.optional(v.number()),
  })
    .index("by_name", ["name"])
    .index("by_category", ["category"])
    .searchIndex("search_name", {
      searchField: "name",
      filterFields: ["category"],
    }),
});
