# Minecraft Use — Project Plan

## Vision
**Minecraft as an AI agent interface.** Type commands in Minecraft chat, they execute in terminal sessions running Claude Code, Browser Use, or anything else. Schematics are the killer demo — browse, search, and place thousands of builds without leaving the game.

## What This Is
A Fabric mod that connects to tmux terminal panes via `tmux-bridge` (from [smux](https://github.com/ShawnPana/smux)). The mod sends text to named panes and reads output back. Whatever is running in those panes — Claude Code, a Browser Use script, a plain shell — the mod doesn't care. It's a terminal client inside Minecraft.

On top of this, a schematic module provides an in-game catalog GUI for browsing, searching, and placing downloaded schematics.

## What This Is NOT
- Not a custom agent framework — the mod is a thin terminal bridge
- Not a WebSocket/HTTP server architecture — communication goes through tmux's Unix socket
- Not limited to specific tools — any CLI tool in a tmux pane is accessible

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        tmux session                             │
│                                                                 │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │  game pane   │  │ claude pane  │  │  browser pane          │ │
│  │  (Minecraft) │  │ (Claude Code)│  │  (Browser Use REPL)    │ │
│  │              │  │              │  │                        │ │
│  └──────┬───── ┘  └──────▲───── ┘  └──────▲─────────────────┘ │
│         │                │                 │                    │
│         │    tmux-bridge read/type/keys    │                    │
│         └────────────────┴─────────────────┘                    │
└─────────────────────────────────────────────────────────────────┘

How it flows:
1. Player types: /claude what is a creeper?
2. Mod calls: tmux-bridge type claude "what is a creeper?"
3. Mod polls: tmux-bridge read claude 50  (until output stabilizes)
4. Mod displays Claude's response in Minecraft chat
```

### Communication Layer

```
Minecraft Mod (Java 21)
       │
       │  ProcessBuilder("bash", "-c", "tmux-bridge read/type/keys ...")
       │
       ▼
tmux-bridge CLI (bash script at ~/.smux/bin/tmux-bridge)
       │
       │  tmux send-keys / capture-pane via Unix socket
       │
       ▼
tmux server (local, already running)
       │
       │  pane buffer I/O
       │
       ▼
Target pane (Claude Code, Browser Use, bash, anything)
```

No HTTP. No WebSocket. No sidecar server needed for the bridge. Just subprocess calls to a bash script that talks to tmux.

---

## Tech Stack
- **Mod**: Fabric 1.21.1, Java 21, Fabric Loader 0.16.9, Fabric API 0.107.0
- **Terminal bridge**: smux / tmux-bridge (bash), tmux 3.6a
- **Browser Use**: Python 3.12, browser-use 0.1.40+, langchain-anthropic (runs in a tmux pane)
- **Claude Code**: `claude` CLI (runs in a tmux pane)
- **Schematic format**: .schem (Sponge v3) primary
- **Metadata index**: JSON file (loaded by mod at startup)

---

## Milestones

### M1: tmux-bridge Integration (Core)
**Goal**: The mod can send commands to and read output from named tmux panes.

#### Prerequisites
- Install smux: `curl -fsSL https://raw.githubusercontent.com/ShawnPana/smux/main/install.sh | bash`
- tmux 3.6a already installed at `/opt/homebrew/bin/tmux`

#### Tasks

1. **TmuxBridge.java** — New class in `fabric-mod/src/main/java/com/minecraftuse/bridge/`
   - Wraps all tmux-bridge subprocess calls
   - Methods:
     ```java
     public class TmuxBridge {
         private static final String BRIDGE = System.getProperty("user.home")
             + "/.smux/bin/tmux-bridge";

         // Read last N lines from a named pane
         public static String read(String pane, int lines);

         // Type text into a named pane (handles read-guard automatically)
         public static void type(String pane, String text);

         // Send a keypress (Enter, Escape, C-c, etc.)
         public static void keys(String pane, String key);

         // Send command + Enter, poll until response stabilizes
         public static CompletableFuture<String> sendAndWait(
             String pane, String command, int timeoutMs);

         // Check if a pane exists and is reachable
         public static boolean isAvailable(String pane);
     }
     ```
   - `sendAndWait` implementation:
     1. Call `read(pane, 1)` to satisfy read guard
     2. Call `type(pane, command)` + `keys(pane, "Enter")`
     3. Poll `read(pane, 50)` every 500ms
     4. When two consecutive reads are identical, response is complete
     5. Return the new lines (diff from pre-command snapshot)
     6. Timeout after configurable ms (default 30000)
   - All subprocess calls run off the main thread (async via `CompletableFuture.supplyAsync`)
   - Pass `TMUX_BRIDGE_SOCKET` env var explicitly since JVM won't have `$TMUX`

2. **PaneConfig** — Mod config for pane names and tmux socket path
   - Config file: `config/minecraft-use.json`
     ```json
     {
       "tmux_socket": "/private/tmp/tmux-501/default",
       "panes": {
         "claude": "claude",
         "browser": "browser"
       },
       "response_timeout_ms": 30000,
       "poll_interval_ms": 500
     }
     ```
   - Auto-detect socket path from `/private/tmp/tmux-{uid}/default` on macOS

3. **Basic commands** — Register two commands to test the bridge:
   - `/tmux-send <pane> <text>` — sends text to a pane, reads response, shows in chat
   - `/tmux-read <pane>` — reads last 20 lines from a pane, shows in chat

4. **Chat display** — Utility to format terminal output for Minecraft chat
   - Strip ANSI escape codes
   - Truncate long output (show first 10 lines + "... N more lines")
   - Clickable "show more" that opens a longer view

#### Acceptance Criteria
- [ ] `TmuxBridge.read("claude", 20)` returns the last 20 lines from a tmux pane named "claude"
- [ ] `TmuxBridge.type("claude", "hello")` types "hello" into the claude pane
- [ ] `TmuxBridge.sendAndWait("claude", "echo hi", 5000)` returns "hi" within 5 seconds
- [ ] `/tmux-send claude "echo hello"` displays "hello" in Minecraft chat
- [ ] All tmux-bridge calls run off the main server thread (no tick lag)
- [ ] Graceful error when tmux-bridge is not installed or pane doesn't exist
- [ ] Works when Minecraft is launched via `./gradlew runClient` from run.sh

#### How to Test
```bash
# Terminal 1: Start tmux and name panes
tmux new-session -s minecraft -n main
# Split panes, then name them:
~/.smux/bin/tmux-bridge name %0 game
~/.smux/bin/tmux-bridge name %1 claude

# Terminal 2 (in the "claude" pane): Just run bash for testing
# (later this will be `claude` CLI)

# Terminal 3 (in the "game" pane): Start Minecraft
cd /Users/reagan/Documents/GitHub/minecraft-use && bash run.sh

# In Minecraft: test the commands
/tmux-send claude "echo hello world"
# Should see "hello world" in chat
```

---

### M2: Agent Commands
**Goal**: Dedicated commands for Claude Code and Browser Use.

**Depends on**: M1 (tmux-bridge integration)

#### Tasks

1. **`/claude <message>`** — Talk to Claude Code from in-game
   - Sends `message` to the "claude" pane via `TmuxBridge.sendAndWait`
   - Displays response in chat
   - Claude Code must be running in the pane (`claude` CLI in interactive mode)
   - Response detection: poll until Claude's `>` prompt reappears
   - Long responses: show first 10 lines in chat, full response in a GUI screen

2. **`/browser-use <task>`** — Run a Browser Use task from in-game
   - Sends task to the "browser" pane
   - The browser pane runs a Python REPL that accepts natural language tasks:
     ```python
     # sidecar/browser_repl.py — simple REPL for Browser Use
     while True:
         task = input(">>> ")
         result = asyncio.run(agent.run(task))
         print(result.final_result())
         print("DONE")  # sentinel for response detection
     ```
   - Response detection: poll until "DONE" sentinel appears
   - Displays result in chat

3. **`/browser-use get-schematics <query>`** — Specialized schematic search
   - Sends to browser pane: `search and download schematics matching: <query>`
   - Browser Use navigates minecraft-schematics.com, downloads files
   - Returns list of downloaded filenames
   - Downloaded .schem files go to `sidecar/schema_cache/`

4. **`/shell <command>`** — Run any shell command (power user)
   - Sends to a "shell" pane (just bash)
   - Returns stdout
   - Useful for file management, git, etc.

5. **Chat feedback with progress** — For long-running tasks:
   - Show "Working..." in chat with animated dots
   - Periodic updates from polling intermediate output
   - Final result when complete

#### Acceptance Criteria
- [ ] `/claude what is a creeper?` sends to Claude Code and shows the response in chat
- [ ] `/browser-use "go to google.com and search for minecraft builds"` executes in Browser Use
- [ ] `/browser-use get-schematics medieval castle` downloads .schem files to cache
- [ ] `/shell ls -la` shows directory listing in chat
- [ ] Long responses are truncated in chat with option to view full output
- [ ] "Working..." feedback appears immediately, doesn't block the game
- [ ] Errors (pane not found, timeout) show helpful messages

#### How to Test
```bash
# Start tmux with all panes
tmux new-session -s minecraft -n main
tmux split-window -h
tmux split-window -v

~/.smux/bin/tmux-bridge name %0 game
~/.smux/bin/tmux-bridge name %1 claude
~/.smux/bin/tmux-bridge name %2 browser

# In claude pane: start Claude Code
claude

# In browser pane: start the Browser Use REPL
cd sidecar && source venv/bin/activate && python browser_repl.py

# In game pane: start Minecraft
cd /Users/reagan/Documents/GitHub/minecraft-use && bash run.sh

# In Minecraft: test commands
/claude what is a creeper?
/browser-use get-schematics medieval castle
/shell echo hello
```

---

### M3: Bulk Scraper + Index
**Goal**: Download a large schematic library with metadata for the in-game catalog.

**Depends on**: M2 (Browser Use integration, for on-demand downloads) — but can be built in parallel as a standalone script.

#### Tasks

1. **Bulk scraper** — `sidecar/bulk_scraper.py`
   - Uses Browser Use to crawl minecraft-schematics.com category pages
   - For each schematic: download file, thumbnail, and metadata (name, author, category, tags, rating, dimensions)
   - Save to `sidecar/schema_cache/` with per-schematic JSON metadata files
   - Resume support: skip already-downloaded files on re-run
   - Rate limiting: 2-5 second delay between page loads

2. **Index builder** — `sidecar/index_builder.py`
   - Aggregate all per-schematic JSON into `catalog_index.json`
   - Normalize categories/tags
   - Format:
     ```json
     {
       "schematics": [{
         "id": "medieval-watchtower-1234",
         "name": "Medieval Watchtower",
         "author": "builder123",
         "category": "castle",
         "tags": ["medieval", "tower", "stone"],
         "rating": 4.5,
         "downloads": 1200,
         "dimensions": {"width": 15, "height": 32, "length": 15},
         "file": "medieval-watchtower-1234.schem",
         "thumbnail": "thumbnails/medieval-watchtower-1234.png",
         "source_url": "https://www.minecraft-schematics.com/schematic/1234/"
       }]
     }
     ```

3. **Can also be triggered from in-game**: `/browser-use get-schematics --bulk` runs the scraper in the browser pane

#### Acceptance Criteria
- [ ] Scraper downloads 100+ schematics from at least 3 categories
- [ ] Each has metadata JSON + thumbnail
- [ ] `catalog_index.json` is valid and contains all entries
- [ ] Re-running skips already-downloaded files
- [ ] Index builder runs independently: `python index_builder.py`

---

### M4: Schematic Engine
**Goal**: Parse .schem files and place blocks in the world.

**Depends on**: M3 (for test schematic files)

**What exists**: `SchematicParser.java` and `SchematicPlacer.java` are stubs.

#### Tasks

1. **NBT Parser** — Implement `SchematicParser.parse(File)`
   - Read GZip-compressed NBT .schem file
   - Extract: Version, Width/Height/Length, Palette (block state → index), BlockData (varint-encoded)
   - Decode varint block data into 3D array using palette
   - Use Minecraft's built-in `NbtCompound`, `NbtIo`

2. **Block Placer** — Implement `SchematicPlacer.place(world, pos, schematic)`
   - Map palette entries to `BlockState` via `Registries.BLOCK`
   - Place blocks at player position + offset
   - Skip air blocks
   - Batch placement: 1000 blocks/tick to prevent lag

3. **Material Swap** — `--swap stone=deepslate` modifies palette during placement

4. **Catalog index loader** — Load `catalog_index.json` at mod startup
   - Parse into `List<SchematicEntry>`
   - Fuzzy search over name + tags

#### Acceptance Criteria
- [ ] Parser reads .schem files and returns valid `Schematic` record
- [ ] Placer puts blocks in-world at player position
- [ ] Air blocks skipped, batched placement prevents lag
- [ ] Material swap works
- [ ] 3+ different .schem files parse and place correctly

---

### M5: In-Game Catalog GUI
**Goal**: Full browsable schematic catalog accessible via keybind.

**Depends on**: M4 (schematic engine for Place action), M3 (index for catalog data)

#### Tasks

1. **Keybind** — `K` key opens `CatalogScreen`

2. **CatalogScreen** — Extends `Screen`
   ```
   ┌───────────────────────────────────────────────┐
   │  Schematic Catalog                        [X] │
   │  [Search: ___________________________]        │
   │  [All] [Castle] [House] [Tower] [Modern] ...  │
   │───────────────────────────────────────────────│
   │  [img] Medieval Watchtower                    │
   │        Castle • ★★★★½ • 15x32x15             │
   │  [img] Dark Fantasy Tower                     │
   │        Tower • ★★★★ • 12x45x12               │
   │  [img] Modern Villa                           │
   │        House • ★★★★★ • 30x12x25              │
   │        ... (scrollable)                       │
   │───────────────────────────────────────────────│
   │  Selected: Medieval Watchtower                │
   │  By: builder123 • 1,200 downloads             │
   │  [Place] [Ghost Preview] [Swap Materials]     │
   └───────────────────────────────────────────────┘
   ```

3. **SchematicListWidget** — `EntryListWidget` with thumbnail, name, category, rating, dimensions per row

4. **Search + filter** — `TextFieldWidget` with fuzzy matching, category toggle buttons

5. **Thumbnails** — Load PNGs as `NativeImageBackedTexture`, lazy-load visible entries only

6. **Actions** — Place (close GUI, place at player pos), Ghost Preview (enter overlay mode)

#### Acceptance Criteria
- [ ] K opens catalog, Escape closes it
- [ ] All indexed schematics displayed with metadata
- [ ] Search filters in real-time (fuzzy)
- [ ] Category buttons filter correctly
- [ ] Smooth scrolling with 100+ entries
- [ ] Thumbnails render from disk PNGs
- [ ] "Place" places selected schematic at player position

---

### M6: Ghost Overlay + Polish
**Goal**: Transparent preview and UX polish.

**Depends on**: M5 (catalog GUI triggers ghost mode)

#### Tasks

1. **Ghost renderer** — Hook `WorldRenderEvents.AFTER_TRANSLUCENT`
   - Render schematic blocks as transparent overlays (alpha ~0.4)
   - Green tint = can place (air), red tint = conflict
   - Scroll wheel to nudge position, shift+scroll to rotate 90°
   - Right-click to confirm, Escape to cancel

2. **Undo** — `/undo` reverts last placement (store original blocks)

3. **Replay mode** — `--replay` places blocks one-by-one over time

4. **Chat feedback** — Progress during placement ("Placing... 45%")

5. **run.sh update** — Start tmux session with named panes automatically
   ```bash
   # Updated run.sh creates the full tmux environment:
   tmux new-session -d -s minecraft -n main
   tmux send-keys -t minecraft:main "cd $(pwd) && bash run_game.sh" Enter
   tmux split-window -h -t minecraft:main
   tmux send-keys -t minecraft:main.1 "claude" Enter
   tmux split-window -v -t minecraft:main.1
   tmux send-keys -t minecraft:main.2 "cd sidecar && source venv/bin/activate && python browser_repl.py" Enter
   # Name panes
   ~/.smux/bin/tmux-bridge name %0 game
   ~/.smux/bin/tmux-bridge name %1 claude
   ~/.smux/bin/tmux-bridge name %2 browser
   tmux attach -t minecraft
   ```

#### Acceptance Criteria
- [ ] Ghost preview renders transparent blocks at target location
- [ ] Position adjustable, right-click confirms, Escape cancels
- [ ] `/undo` reverts last placement
- [ ] `run.sh` sets up entire tmux environment automatically

---

## Implementation Order

```
M1 (tmux-bridge) ──► M2 (Agent Commands) ──► M5 (Catalog GUI) ──► M6 (Ghost + Polish)
                                │                    ▲
                                ▼                    │
                     M3 (Bulk Scraper) ──► M4 (Schematic Engine)
```

**M1 is the foundation** — everything depends on the terminal bridge working.
**M3 + M4 can be built in parallel with M2** — the scraper and parser don't need agent commands.
**The mod is usable after M2** — you can talk to Claude and Browser Use from Minecraft.
**The full experience lands at M5** — catalog GUI + placement.

---

## File Structure (Target)

```
fabric-mod/src/main/java/com/minecraftuse/
├── MinecraftUseMod.java              (entry point, keybind registration)
├── bridge/
│   ├── TmuxBridge.java               (new — M1, core tmux-bridge wrapper)
│   ├── PaneConfig.java               (new — M1, pane names + socket config)
│   ├── ResponsePoller.java           (new — M1, async poll-until-stable)
│   └── AnsiStripper.java             (new — M1, strip terminal escape codes)
├── command/
│   ├── TmuxSendCommand.java          (new — M1, /tmux-send for testing)
│   ├── ClaudeCommand.java            (new — M2, /claude)
│   ├── BrowserUseCommand.java        (new — M2, /browser-use)
│   ├── ShellCommand.java             (new — M2, /shell)
│   ├── BuildCommand.java             (exists — wire to schematic engine)
│   ├── DownloadCommand.java          (exists — wire to browser-use)
│   ├── ListCommand.java              (exists — wire to catalog index)
│   └── UndoCommand.java              (new — M6)
├── gui/
│   ├── CatalogScreen.java            (new — M5)
│   ├── SchematicListWidget.java      (new — M5)
│   ├── SchematicDetailPanel.java     (new — M5)
│   └── TerminalOutputScreen.java     (new — M2, full output viewer)
├── catalog/
│   ├── SchematicEntry.java           (new — M4)
│   ├── CatalogIndex.java             (new — M4)
│   └── ThumbnailManager.java         (new — M5)
├── schematic/
│   ├── SchematicParser.java          (exists as stub — M4)
│   └── SchematicPlacer.java          (exists as stub — M4)
├── render/
│   └── GhostRenderer.java            (new — M6)
└── network/
    └── SidecarClient.java            (exists — may phase out in favor of tmux-bridge)

sidecar/
├── browser_repl.py                    (new — M2, REPL for Browser Use tasks)
├── bulk_scraper.py                    (new — M3, crawls minecraft-schematics.com)
├── index_builder.py                   (new — M3, builds catalog_index.json)
├── browser_search.py                  (exists — refactor into browser_repl.py)
├── server.py                          (exists — may phase out)
├── requirements.txt                   (exists)
└── schema_cache/
    ├── *.schem
    ├── *.json
    ├── catalog_index.json
    └── thumbnails/*.png
```

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Response detection fails (output never "stabilizes") | Commands hang | Configurable timeout (default 30s). For Claude, detect `>` prompt. For browser_repl, use "DONE" sentinel. Worst case: timeout and show partial output. |
| tmux-bridge read guard causes race conditions | Missed output or failed writes | Always call read before type in a single atomic bash command (`read && type && keys`). |
| ProcessBuilder subprocess overhead per call | Slight latency | Each call is ~10-50ms (just bash + tmux IPC). Acceptable for chat commands. Not suitable for per-frame rendering. |
| ANSI escape codes in terminal output | Garbled Minecraft chat | Strip all ANSI codes in `AnsiStripper.java` before displaying. Regex: `\x1b\[[0-9;]*m` |
| Claude Code interactive mode has complex output | Hard to parse where response starts/ends | Snapshot output before sending command. After response, diff with snapshot. Only show new lines. |
| User must set up tmux panes manually | Friction for first-time setup | Updated `run.sh` creates the entire tmux environment automatically (M6). |
| minecraft-schematics.com blocks scraping | Can't build catalog | Rate limiting, respect robots.txt. Existing downloaded catalog still works. |
| Large schematics cause placement lag | Bad UX | Batched placement at 1000 blocks/tick. Warn in GUI for large schematics. |

---

## Open Questions
1. **Frontend app**: The existing `frontend/` (React+Convex) is disconnected. Kill it, repurpose as a web companion for the catalog, or ignore?
2. **Multi-pane switching**: Should there be a `/pane <name>` command to switch which pane receives commands by default?
3. **Persistent sessions**: Should the mod remember chat history with Claude across game restarts?

---

## Already Working
- Fabric mod compiles and runs (`./gradlew runClient` via `run.sh`)
- Java 21 at `/opt/homebrew/opt/openjdk@21/bin`
- tmux 3.6a at `/opt/homebrew/bin/tmux`
- 3 commands registered: `/build`, `/download`, `/list`
- `SidecarClient.java` handles async HTTP (may phase out)
- `browser_search.py` searches schematic sites (to be refactored into REPL)
- Python sidecar (FastAPI) on localhost:8765 (may phase out in favor of tmux-bridge)

## Installed
- Java 21 (OpenJDK 21.0.10 via Homebrew)
- Python 3.12 (via Homebrew)
- tmux 3.6a (via Homebrew)
- Gradle 8.12 with Fabric Loom 1.9.2
- Fabric Loader 0.16.9 for MC 1.21.1
- browser-use 0.12.6, FastAPI, Playwright + Chromium

## Needs Install
- smux: `curl -fsSL https://raw.githubusercontent.com/ShawnPana/smux/main/install.sh | bash`

## Paths
- Minecraft: `~/Library/Application Support/minecraft/`
- Mod jar: `fabric-mod/build/libs/fabric-mod-1.0.0.jar`
- Mods folder: `~/Library/Application Support/minecraft/mods/`
- Sidecar venv: `sidecar/venv/`
- tmux-bridge (after install): `~/.smux/bin/tmux-bridge`
- tmux socket: `/private/tmp/tmux-501/default`
