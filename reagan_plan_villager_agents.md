# Villager Agents — Plan

## Concept
`/spawn <name> [command]` creates a tmux pane AND a Minecraft villager. The villager follows you, displays pane output above its head as floating text, and you interact with it by right-clicking. When the villager dies, the tmux pane closes.

This is **additive** — the existing `/claude`, `/browser-use`, `/shell` commands are unchanged. Villager agents are a visual, in-world alternative.

---

## Commands

### `/spawn <name> [command]`
- Creates a new tmux pane via `tmux split-window`
- Names it with `tmux-bridge name <pane> <name>`
- Optionally runs `command` in the pane (e.g., `/spawn claude lfg`, `/spawn py python3`)
- Spawns a villager near the player named `<name>`
- Villager follows the player and displays pane output

### `/despawn <name>`
- Kills the villager entity
- Closes the tmux pane (`tmux kill-pane`)
- Cleans up floating text armor stands

### Villager death (creeper, lava, player, etc.)
- Triggers pane close automatically
- Cleans up armor stands
- Chat message: "[MCUse] claude was killed — pane closed"

---

## Architecture

```
/spawn claude lfg
       │
       ├──► tmux split-window -h
       │    tmux-bridge name %N claude
       │    tmux send-keys "lfg" Enter
       │
       └──► Spawn VillagerEntity "claude"
            ├── Custom AI: FollowPlayerGoal
            ├── Right-click: open chat input → TmuxBridge.type()
            ├── Floating text: 3-5 armor stands above head
            └── Output poller: TmuxBridge.read() every 2s → update armor stands
```

---

## Components

### 1. AgentVillager (entity management)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/AgentVillager.java`

Manages the lifecycle of a spawned agent villager:
- `spawn(world, playerPos, name)` — creates villager entity near player
  - `setCustomName(Text.literal(name))`
  - `setCustomNameVisible(true)`
  - `setAiDisabled(false)` — but with our custom goals only
  - Set villager profession to LIBRARIAN (fits the "knowledge" theme)
  - Make invulnerable to everything except direct player kill and void
- `despawn()` — removes villager + armor stands + pane
- `getVillager()` — returns the entity reference
- Track association: villager entity ID ↔ pane name

### 2. FollowPlayerGoal (AI)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/FollowPlayerGoal.java`

Custom goal that replaces default villager AI:
- Every tick: pathfind toward nearest player
- Stay within 3-6 blocks (don't crowd the player)
- If player moves >16 blocks away, teleport to player (prevent losing the villager)
- Walk speed: slightly faster than player walk speed
- Don't enter water, don't path off cliffs

### 3. FloatingText (hologram display)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/FloatingText.java`

Stack of invisible armor stands above the villager:
- 3-5 armor stands, each 0.3 blocks apart vertically
- `setInvisible(true)`, `setNoGravity(true)`, `setMarker(true)` (no hitbox)
- Each armor stand has `setCustomName()` with one line of text
- `update(List<String> lines)` — set text on each armor stand
- `tick()` — reposition above villager every tick (match villager coords + Y offset)
- `remove()` — despawn all armor stands

Display rules:
- Show last 3-5 lines of pane output
- Truncate lines to 40 chars (readability at distance)
- Update every 2 seconds (poll TmuxBridge.read())
- When output hasn't changed, don't flicker/re-render

### 4. VillagerInteractionHandler (right-click chat)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/VillagerInteractionHandler.java`

When player right-clicks an agent villager:
- Intercept via `UseEntityCallback.EVENT`
- Check if the entity is one of our tracked agent villagers
- Open a simple text input screen (like an anvil rename but simpler)
- Player types a message → send to the villager's tmux pane via TmuxBridge
- Show "Sent to <name>..." in chat

### 5. SpawnCommand + DespawnCommand
**File**: `fabric-mod/src/main/java/com/minecraftuse/commands/SpawnCommand.java`
**File**: `fabric-mod/src/main/java/com/minecraftuse/commands/DespawnCommand.java`

`/spawn <name> [command]`:
1. Check if name is already taken (existing villager or pane)
2. Create tmux pane: `tmux split-window -h` via ProcessBuilder
3. Get new pane ID, name it: `tmux-bridge name <id> <name>`
4. If command provided: `tmux-bridge type <name> <command>` + `keys Enter`
5. Spawn AgentVillager at player position
6. Start output poller (async, every 2 seconds)
7. Register in VillagerRegistry

`/despawn <name>`:
1. Look up in VillagerRegistry
2. Kill villager entity
3. Close tmux pane
4. Clean up floating text
5. Unregister

### 6. VillagerRegistry (tracking)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/VillagerRegistry.java`

Singleton that tracks all active agent villagers:
- `Map<String, AgentVillagerData>` — name → (villager entity, pane ID, floating text, poller)
- `register(name, villager, paneId)`
- `unregister(name)` — full cleanup
- `getByEntity(entity)` — reverse lookup for interaction handler
- `getByName(name)` — lookup for /despawn
- `tickAll()` — called every tick, updates floating text positions
- `onEntityDeath(entity)` — if it's one of ours, close pane + cleanup

### 7. OutputPoller (async pane reader)
**File**: `fabric-mod/src/main/java/com/minecraftuse/villager/OutputPoller.java`

Runs async, reads pane output and updates floating text:
- Poll `TmuxBridge.read(paneName, 5)` every 2 seconds
- Compare with last read — only update floating text if changed
- Strip ANSI codes, truncate lines to 40 chars
- Pass lines to FloatingText.update()
- Runs on a separate thread, pushes updates to main thread via `MinecraftClient.execute()`

---

## File Structure

```
fabric-mod/src/main/java/com/minecraftuse/
├── villager/
│   ├── AgentVillager.java          — entity lifecycle
│   ├── FollowPlayerGoal.java       — custom AI goal
│   ├── FloatingText.java           — armor stand hologram
│   ├── VillagerInteractionHandler.java — right-click handling
│   ├── VillagerRegistry.java       — tracks all agent villagers
│   └── OutputPoller.java           — async pane output reader
├── commands/
│   ├── SpawnCommand.java           — /spawn <name> [command]
│   └── DespawnCommand.java         — /despawn <name>
└── ... (existing files unchanged)
```

---

## Registration in MinecraftUseMod.java

```java
// In onInitializeClient():
SpawnCommand.register(dispatcher);
DespawnCommand.register(dispatcher);

// In tick events:
ClientTickEvents.END_CLIENT_TICK.register(client -> {
    VillagerRegistry.getInstance().tickAll();
});

// Entity death listener:
// Register callback to detect agent villager deaths
```

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Armor stands cause entity lag with many villagers | Limit to 5 active agent villagers max. Use marker armor stands (no hitbox). |
| Villager pathfinding is janky | Teleport if >16 blocks away. Keep follow distance at 3-6 blocks. |
| Right-click opens villager trade screen instead of our GUI | Cancel the default interaction event and open our screen instead. |
| Floating text unreadable at distance | Truncate to 40 chars. Only show 3-5 lines. Use white text. |
| Pane creation fails (tmux not running) | Check tmux availability before spawning. Show error in chat. |
| Output polling causes performance issues | Poll every 2s, not every tick. Run on separate thread. |

---

## Example Usage

```
/spawn claude lfg
  → Creates tmux pane "claude" running Claude Code
  → Spawns villager "claude" that follows you
  → Claude's responses float above the villager's head

/spawn browser python browser_repl.py
  → Creates tmux pane "browser" running the REPL
  → Spawns villager "browser"

*right-click claude villager*
  → Opens text input
  → Type "what is a creeper?"
  → Sent to claude pane, response appears above villager

*creeper blows up the browser villager*
  → "[MCUse] browser was killed — pane closed"
  → tmux pane closes

/despawn claude
  → Villager removed, pane closed, floating text cleaned up
```

---

## Open Questions
1. Should villagers be invulnerable by default, or is the "death = pane close" mechanic the fun part?
2. Should right-click open a GUI or use Minecraft's built-in chat (prefix messages with villager name)?
3. Max concurrent villager agents? (Suggest 5 to limit entity/armor stand count)
