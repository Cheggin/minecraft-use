# /listen [port] Command — Implementation Plan

## Overview
Add a `/listen <port>` command that spawns a monitor villager showing live process logs above its head. When the villager dies or is despawned, it kills both the tmux pane and the process on that port.

## Key Design Decisions

### 1. What runs in the tmux pane?
**Decision**: A bash monitoring script that:
1. Resolves PID via `lsof -ti :<port>`
2. Gets process name via `ps -p <pid> -o comm=`
3. Checks for open log files via `lsof -p <pid> 2>/dev/null | grep -iE '\.log|\.out|\.err'`
4. If log files found → `tail -f <logfiles>`
5. If no log files → falls back to `log stream --process <pid> --level info` (macOS unified logging)
6. Shows "Process exited" when the PID dies

**Why**: On macOS you cannot attach to an existing process's stdout. This approach auto-discovers log files when available, falls back to system-level logging, and always works without requiring the user to start the process in tmux first.

### 2. Invulnerability
**Decision**: Listen-villagers are NOT invulnerable (unlike agent villagers). This makes "kill villager → kill process" a natural game mechanic. Additionally, both `/despawn` and villager death trigger process cleanup.

### 3. Naming
**Decision**: Auto-generated as `listen-<port>` (e.g., `listen-3000`). Prevents collisions since port is unique. Name is used as the tmux pane label.

### 4. Multiple PIDs on same port
**Decision**: Kill ALL PIDs found via `lsof -ti :<port>` on cleanup. On spawn, use the first PID for process name display.

### 5. Data model
**Decision**: Add optional `Integer monitoredPort` field to `AgentVillagerData` record. On cleanup, if port is non-null, re-resolve PIDs via `lsof -ti :<port>` and kill them. Re-resolving at kill time avoids PID reuse race conditions.

### 6. Output display
**Decision**: Create `LogOutputPoller` — a simplified variant of `OutputPoller` without Claude Code chrome filtering or agent response detection. Just shows the last N lines of raw tmux pane output as floating text. This is cleaner than trying to reuse OutputPoller's agent-specific logic.

### 7. Mob type
**Decision**: Default to `allay` for monitor villagers to visually distinguish from agent villagers (librarian).

## Command Syntax
```
/listen <port>
```

## Files to Create
1. `ListenCommand.java` — command registration and spawn logic
2. `LogOutputPoller.java` — simplified poller for raw log output

## Files to Modify
1. `VillagerRegistry.java` — add `monitoredPort` field to record, add port-kill logic in cleanup
2. `MinecraftUseMod.java` — register ListenCommand
3. `SpawnCommand.java` — pass `null` for new port field in existing AgentVillagerData construction

## Implementation Steps

### Step 1: Extend AgentVillagerData
- Add `Integer monitoredPort` field (nullable — null for agents, set for listeners)
- Update `tickAll()` death cleanup: if `monitoredPort != null`, run `lsof -ti :<port>` and kill all PIDs
- Update `unregister()` with same port-kill logic
- Update `SpawnCommand.java` to pass `null` for the new field

### Step 2: Create LogOutputPoller
- Same polling structure as OutputPoller (200ms interval, daemon thread)
- Reads tmux pane via `bridge.readWithColor(paneName)`
- Converts ANSI via `AnsiToMinecraft.convert()`
- Shows last 6 non-empty lines as floating text (no chrome filtering)
- No sound effects, no agent response detection

### Step 3: Create ListenCommand
- Register as `/listen <port>` client command
- Validate port is 1-65535
- Check no existing `listen-<port>` in registry
- Run async: resolve PID, create tmux pane with monitoring script, spawn villager + FloatingText + LogOutputPoller
- Villager spawned as `allay` mob type, NOT invulnerable

### Step 4: Register and Build
- Add `ListenCommand.register(dispatcher)` in MinecraftUseMod
- Gradle build to verify compilation

## Error Cases
- Port not in use → "No process found on port <port>"
- Already listening → "Already listening on port <port>"
- Invalid port → Brigadier integer validation handles this
- Process dies on its own → tmux pane shows "Process exited", villager stays until despawned
- Kill fails (permissions) → logged, not fatal
