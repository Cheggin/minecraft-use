# Minecraft Use — Project Plan

## Vision
**"I can build anything."** An in-game schematic catalog with thousands of builds, searchable and placeable without ever leaving Minecraft.

## What This Is
A Fabric mod with a full in-game GUI that gives players instant access to a massive schematic library. Browse, search, filter, preview, and place any schematic — all from inside the game. No alt-tabbing, no manual downloads, no file management.

## What This Is NOT
- Not an AI generation tool (models can't generate good builds today)
- Not a thin wrapper around alt-tab (the value is the catalog + in-game UX, not saving one download)

---

## Architecture

```
┌─────────────────────────────────────────┐
│         Fabric Mod (Java 21)            │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Catalog GUI (Screen)           │    │
│  │  - Search bar (TextFieldWidget) │    │
│  │  - Category filter buttons      │    │
│  │  - Scrollable list (EntryList)  │    │
│  │  - Thumbnail rendering          │    │
│  │  - Detail panel                 │    │
│  └──────────┬──────────────────────┘    │
│             │                           │
│  ┌──────────▼──────────────────────┐    │
│  │  Schematic Engine               │    │
│  │  - .schem NBT parser            │    │
│  │  - Block placer                 │    │
│  │  - Ghost overlay renderer       │    │
│  │  - Material swap                │    │
│  └──────────┬──────────────────────┘    │
│             │                           │
│  ┌──────────▼──────────────────────┐    │
│  │  Metadata Index (JSON)          │    │
│  │  - name, category, tags         │    │
│  │  - dimensions, block count      │    │
│  │  - thumbnail path               │    │
│  │  - source URL, author           │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│      Python Sidecar (FastAPI)           │
│                                         │
│  ┌─────────────────────────────────┐    │
│  │  Bulk Scraper (Browser Use)     │    │
│  │  - Crawl minecraft-schematics   │    │
│  │  - Download all .schem files    │    │
│  │  - Extract metadata per entry   │    │
│  │  - Save thumbnails              │    │
│  └──────────┬──────────────────────┘    │
│             │                           │
│  ┌──────────▼──────────────────────┐    │
│  │  Index Builder                  │    │
│  │  - Build JSON index from        │    │
│  │    scraped metadata             │    │
│  │  - Category/tag normalization   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
```

## Tech Stack
- **Mod**: Fabric 1.21.1, Java 21, Fabric Loader 0.16.9, Fabric API 0.107.0
- **Sidecar**: Python 3.11+, FastAPI, Browser Use 0.1.40+, langchain-anthropic
- **Schematic format**: .schem (Sponge v3) primary, .schematic/.litematic secondary
- **Metadata index**: JSON file (loaded into memory by mod at startup)
- **Communication**: HTTP REST on localhost:8765 (already working)

---

## Milestones

### M1: Bulk Scraper (Python Sidecar)
**Goal**: Download every schematic from minecraft-schematics.com with metadata.

**What exists**: `sidecar/browser_search.py` has a `SchematicSearcher` class that searches by query. Needs to become a bulk crawler.

#### Tasks
1. **Bulk crawler** — New class `BulkScraper` in `sidecar/bulk_scraper.py`
   - Navigate category pages on minecraft-schematics.com
   - Paginate through all results (the site has categories: houses, castles, towers, medieval, modern, etc.)
   - For each schematic page: extract name, author, category, tags, rating, dimensions, download count, description
   - Download the .schem/.schematic file to `sidecar/schema_cache/`
   - Download the thumbnail image to `sidecar/schema_cache/thumbnails/`
   - Save metadata to a JSON file per schematic

2. **Index builder** — `sidecar/index_builder.py`
   - Aggregate all per-schematic JSON into a single `catalog_index.json`
   - Normalize categories and tags (lowercase, deduplicate)
   - Include file path, thumbnail path, dimensions, block count
   - Output format:
     ```json
     {
       "schematics": [
         {
           "id": "medieval-watchtower-1234",
           "name": "Medieval Watchtower",
           "author": "builder123",
           "category": "castle",
           "tags": ["medieval", "tower", "stone"],
           "rating": 4.5,
           "downloads": 1200,
           "dimensions": { "width": 15, "height": 32, "length": 15 },
           "file": "medieval-watchtower-1234.schem",
           "thumbnail": "thumbnails/medieval-watchtower-1234.png",
           "source_url": "https://www.minecraft-schematics.com/schematic/1234/"
         }
       ]
     }
     ```

3. **Resume support** — Scraper tracks what's already downloaded and skips duplicates on re-run

4. **New endpoint**: `POST /bulk-scrape` to trigger scraping (or run as CLI script)

#### Acceptance Criteria
- [ ] Scraper downloads 100+ schematics from at least 3 categories as proof of concept
- [ ] Each schematic has a corresponding JSON metadata file and thumbnail
- [ ] `catalog_index.json` is valid JSON, loadable, and contains all scraped entries
- [ ] Re-running the scraper skips already-downloaded files
- [ ] Schematics are saved to `sidecar/schema_cache/` with consistent naming

---

### M2: Schematic Engine (Java Mod)
**Goal**: Parse .schem files and place blocks in the world.

**What exists**: `SchematicParser.java` and `SchematicPlacer.java` are stubs with TODOs in `fabric-mod/src/main/java/com/minecraftuse/schematic/`.

#### Tasks
1. **NBT Parser** — Implement `SchematicParser.parse(File)`
   - Read .schem file (GZip compressed NBT)
   - Extract from NBT root compound:
     - `Version` (int) — schema version
     - `Width`, `Height`, `Length` (short) — dimensions
     - `Palette` (compound) — block state string → int index mapping
     - `BlockData` (byte array) — varint-encoded block indices
     - `Offset` (int array, optional) — placement offset
   - Decode varint block data into 3D block array using palette
   - Return `Schematic` record with all parsed data
   - Use Minecraft's built-in NBT classes (`NbtCompound`, `NbtIo`)

2. **Block Placer** — Implement `SchematicPlacer.place(world, pos, schematic)`
   - Iterate through schematic block data
   - Map palette entries to Minecraft `BlockState` objects via `Registries.BLOCK`
   - Place blocks at player position + offset
   - Skip air blocks (palette entry "minecraft:air")
   - Batch placement to avoid lag (place N blocks per tick using scheduled task)

3. **Material Swap** — `SchematicPlacer.place(world, pos, schematic, swapMap)`
   - Accept a `Map<String, String>` of material replacements
   - Apply swaps during block placement by modifying palette lookups
   - e.g., `--swap stone=deepslate` replaces all stone with deepslate

4. **Metadata loader** — Load `catalog_index.json` at mod init
   - Copy `catalog_index.json` from sidecar cache to mod's config directory (or read directly)
   - Parse into `List<SchematicEntry>` with all metadata fields
   - Provide fuzzy search method over name + tags

#### Acceptance Criteria
- [ ] `SchematicParser.parse()` correctly reads a .schem file and returns valid `Schematic` record
- [ ] `SchematicPlacer.place()` places a schematic in the world at the player's position
- [ ] Air blocks are skipped during placement
- [ ] Batched placement prevents lag spikes (configurable blocks-per-tick, default 1000)
- [ ] Material swap works: `--swap stone=deepslate` replaces all stone blocks
- [ ] At least 3 different .schem files from the scraper parse and place correctly

---

### M3: In-Game Catalog GUI
**Goal**: Full browsable schematic catalog accessible via keybind.

#### Tasks
1. **Keybind registration** — Register `K` key (configurable) to open catalog
   - `KeyBindingHelper.registerKeyBinding()` in mod initializer
   - Check for key press in client tick event, open `CatalogScreen`

2. **CatalogScreen** — New class extending `Screen`
   - Layout:
     ```
     ┌───────────────────────────────────────────────┐
     │  Schematic Catalog                        [X] │
     │  [Search: ___________________________] [🔍]   │
     │  [All] [Castle] [House] [Tower] [Modern] ...  │
     │───────────────────────────────────────────────│
     │  [img] Medieval Watchtower                    │
     │        Castle • ★★★★½ • 15x32x15             │
     │  [img] Dark Fantasy Tower                     │
     │        Tower • ★★★★ • 12x45x12               │
     │  [img] Modern Villa                           │
     │        House • ★★★★★ • 30x12x25              │
     │  [img] Stone Keep                             │
     │        Castle • ★★★★ • 20x18x20              │
     │        ... (scrollable)                       │
     │───────────────────────────────────────────────│
     │  Selected: Medieval Watchtower                │
     │  By: builder123 • 1,200 downloads             │
     │  [Place] [Ghost Preview] [Swap Materials]     │
     └───────────────────────────────────────────────┘
     ```

3. **SchematicListWidget** — Extends `EntryListWidget<SchematicListWidget.Entry>`
   - Each entry renders: thumbnail (32x32), name, category, rating stars, dimensions
   - Clicking an entry selects it and populates the detail panel
   - Scrollable with mouse wheel

4. **Search and filter**
   - `TextFieldWidget` for search — fuzzy match against name + tags
   - Category buttons filter the list (toggle on/off)
   - Sort options: name, rating, downloads, dimensions

5. **Thumbnail rendering**
   - Load PNG thumbnails from disk as `NativeImageBackedTexture`
   - Register with `MinecraftClient.getTextureManager()`
   - Render in list entries at 32x32 pixels
   - Lazy-load thumbnails (don't load all at once — load visible + buffer)

6. **Detail panel** — Bottom section showing:
   - Full name, author, download count
   - Action buttons: Place, Ghost Preview, Swap Materials
   - "Place" closes GUI and places schematic at player position
   - "Ghost Preview" closes GUI and enters ghost overlay mode

#### Acceptance Criteria
- [ ] Pressing K opens the catalog GUI
- [ ] Catalog displays all indexed schematics with name, category, rating, dimensions
- [ ] Search filters results in real-time as user types (fuzzy matching)
- [ ] Category buttons filter the list correctly
- [ ] Scrolling works smoothly with 100+ entries
- [ ] Thumbnails render correctly from disk PNGs
- [ ] Clicking "Place" places the selected schematic at the player's position
- [ ] GUI closes cleanly with Escape or X button

---

### M4: Ghost Overlay Preview
**Goal**: Render a transparent preview of the schematic in-world before placing.

#### Tasks
1. **Ghost renderer** — New class `GhostRenderer`
   - Hook into Fabric's `WorldRenderEvents.AFTER_TRANSLUCENT` event
   - When active, render schematic blocks as transparent overlays at target position
   - Use `BufferBuilder` with translucent render layer (alpha ~0.4)
   - Render block outlines or tinted transparent blocks
   - Player can walk around and see the preview from all angles

2. **Position control**
   - Ghost preview starts at player's look position (raycast to ground)
   - Scroll wheel or arrow keys to nudge position (1 block increments)
   - Hold shift + scroll to rotate 90° increments
   - Right-click to confirm placement, Escape to cancel

3. **Color coding**
   - Green tint for blocks that can be placed (air at target)
   - Red tint for blocks that conflict with existing blocks
   - This tells the player if the schematic fits before committing

4. **Performance**
   - Only render blocks within render distance
   - Cache the render data (don't rebuild every frame)
   - Rebuild only when position/rotation changes

#### Acceptance Criteria
- [ ] Ghost preview renders transparent blocks at the target location
- [ ] Player can see the preview from all angles by walking around
- [ ] Position is adjustable with scroll wheel / arrow keys
- [ ] Right-click confirms placement, Escape cancels
- [ ] Preview doesn't cause significant FPS drops with schematics under 50k blocks
- [ ] Green/red tinting correctly indicates placement conflicts

---

### M5: Polish and UX
**Goal**: Make the full loop feel good.

#### Tasks
1. **Chat feedback** — Progress messages during placement ("Placing... 45% complete")
2. **Undo** — `/undo` command to revert last placement (store original blocks before placing)
3. **Replay mode** — `/build name --replay` places blocks one-by-one with delay (timelapse effect)
4. **Scaling** — `/build name --scale 2` doubles dimensions (each block becomes 2x2x2)
5. **Improved /list** — Fuzzy search with results showing metadata from index
6. **Config file** — Keybind customization, blocks-per-tick, default ghost mode on/off
7. **Error handling** — Graceful failures when files are missing/corrupt, user-friendly chat messages

#### Acceptance Criteria
- [ ] `/undo` reverts the last schematic placement
- [ ] `--replay` visually places blocks over time
- [ ] `--scale 2` correctly doubles schematic size
- [ ] All errors show helpful chat messages (not stack traces)

---

## Implementation Order

```
M1 (Bulk Scraper) ──► M2 (Schematic Engine) ──► M3 (Catalog GUI) ──► M4 (Ghost Preview) ──► M5 (Polish)
     │                       │                        │
     │                       │                        └─ Depends on M2 for Place action
     │                       └─ Depends on M1 for test schematics
     └─ Independent, can start immediately
```

**Critical path**: M1 → M2 → M3. M4 and M5 are enhancements.

The mod is usable after M3 — that's the "I can build anything" experience. M4 and M5 make it polished.

---

## File Structure (Target)

```
fabric-mod/src/main/java/com/minecraftuse/
├── MinecraftUseMod.java              (exists — entry point, keybind registration)
├── command/
│   ├── BuildCommand.java             (exists — needs flag parsing)
│   ├── DownloadCommand.java          (exists — working)
│   ├── ListCommand.java              (exists — needs fuzzy search)
│   └── UndoCommand.java              (new — M5)
├── gui/
│   ├── CatalogScreen.java            (new — M3, main catalog GUI)
│   ├── SchematicListWidget.java      (new — M3, scrollable list)
│   └── SchematicDetailPanel.java     (new — M3, bottom detail/actions)
├── catalog/
│   ├── SchematicEntry.java           (new — metadata record)
│   ├── CatalogIndex.java             (new — loads/searches index JSON)
│   └── ThumbnailManager.java         (new — M3, lazy thumbnail loading)
├── schematic/
│   ├── SchematicParser.java          (exists as stub — implement in M2)
│   └── SchematicPlacer.java          (exists as stub — implement in M2)
├── render/
│   └── GhostRenderer.java            (new — M4, transparent overlay)
└── network/
    └── SidecarClient.java            (exists — working)

sidecar/
├── server.py                          (exists — working)
├── browser_search.py                  (exists — refactor for bulk)
├── bulk_scraper.py                    (new — M1)
├── index_builder.py                   (new — M1)
├── requirements.txt                   (exists)
└── schema_cache/
    ├── *.schem                        (downloaded schematics)
    ├── *.json                         (per-schematic metadata)
    ├── catalog_index.json             (aggregated index)
    └── thumbnails/
        └── *.png                      (schematic thumbnails)
```

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| minecraft-schematics.com rate-limits or blocks scraping | Can't build catalog | Add delays between requests (2-5s), rotate user agents, respect robots.txt. Fallback: scrape multiple sites. |
| .schem files have inconsistent formats across sources | Parser breaks on some files | Test with 20+ files from different uploaders. Handle both Sponge v2 and v3. Log and skip unparseable files. |
| Large schematics (100k+ blocks) cause placement lag | Bad UX, potential crash | Batched placement (1000 blocks/tick default). Warn user in GUI for large schematics. |
| Thumbnail loading for 1000+ entries causes memory pressure | GUI stutters or OOM | Lazy loading — only load thumbnails for visible entries + small buffer. Unload off-screen thumbnails. |
| Ghost rendering at scale is GPU-intensive | FPS drops during preview | Limit ghost render to schematics under 50k blocks. Use LOD — far blocks render as outlines only. |
| Site HTML changes break the scraper | Future scrapes fail | Keep scraper logic isolated in `bulk_scraper.py`. When it breaks, update selectors — existing downloaded catalog still works. |

---

## Open Questions
1. **Catalog distribution**: Should the pre-scraped catalog be bundled with the mod (large download) or downloaded on first run?
2. **Updates**: How often to re-scrape for new schematics? Manual trigger vs. periodic?
3. **Frontend app**: The existing `frontend/` is a React+Convex app that's disconnected. Kill it, repurpose it as a web companion, or ignore for now?

---

## Already Working
- Java 21 (OpenJDK 21.0.10 via Homebrew) at `/opt/homebrew/opt/openjdk@21/bin`
- Fabric mod compiles and runs (`./gradlew runClient`)
- Python sidecar (FastAPI + Browser Use) on localhost:8765
- HTTP communication: Mod ↔ Sidecar tested via `/ping`
- 3 commands registered: `/build`, `/download`, `/list`
- `SidecarClient.java` handles async HTTP with GSON
- `browser_search.py` searches Planet Minecraft + Minecraft Schematics

## Installed
- Java 21 (OpenJDK 21.0.10 via Homebrew) — needs sudo symlink for global access
- Python 3.12 (via Homebrew)
- Gradle 8.12 with Fabric Loom 1.9.2
- Fabric Loader 0.16.9 for MC 1.21.1
- browser-use 0.12.6, FastAPI, Playwright + Chromium

## Paths
- Minecraft: `~/Library/Application Support/minecraft/`
- Mod jar: `fabric-mod/build/libs/fabric-mod-1.0.0.jar`
- Mods folder: `~/Library/Application Support/minecraft/mods/`
- Sidecar venv: `sidecar/venv/`
