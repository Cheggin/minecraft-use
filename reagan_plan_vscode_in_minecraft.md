# VS Code in Minecraft — Plan

## Vision
Place a block in the Minecraft world that renders a full VS Code editor (via `vscode.dev` or `code-server`). Players walk up to it, interact, and code — with the editor rendered on in-game blocks. Integrates with our existing agent villager system.

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Minecraft World                                 │
│                                                  │
│  ┌──────────────────────┐                        │
│  │  Code Screen (blocks)│  ← MCEF renders here   │
│  │  ┌──────────────────┐│                        │
│  │  │  vscode.dev      ││                        │
│  │  │  or code-server  ││                        │
│  │  │                  ││                        │
│  │  │  Full editor:    ││                        │
│  │  │  - Syntax HL     ││                        │
│  │  │  - File tree     ││                        │
│  │  │  - Terminal      ││                        │
│  │  └──────────────────┘│                        │
│  └──────────────────────┘                        │
│        ↕ keyboard/mouse                          │
│  [alex] villager watching the screen             │
└─────────────────────────────────────────────────┘
         │
         │ MCEF (Chromium Embedded Framework)
         ↓
┌─────────────────────────────────────────────────┐
│  Chromium (off-screen rendering)                 │
│  → vscode.dev / localhost:8080 (code-server)    │
│  → Renders to OpenGL texture                     │
│  → Keyboard/mouse passthrough from Minecraft     │
└─────────────────────────────────────────────────┘
```

## Approach

### Phase 1: MCEF + WebDisplays Setup
1. Add MCEF as a dependency to the Fabric mod
2. Add WebDisplays as a companion mod (or build our own screen renderer)
3. Verify Chromium renders inside Minecraft on macOS Apple Silicon

### Phase 2: Code Screen Command
New command: `/code <url>` or `/code` (defaults to vscode.dev)
- Spawns a 3x2 block screen in front of the player
- Loads the URL in MCEF's embedded browser
- Player can interact with keyboard/mouse when looking at the screen
- Escape exits the screen interaction

### Phase 3: code-server Integration
Instead of vscode.dev (which requires Microsoft auth), use code-server:
- `code-server` runs locally on port 8080
- Full VS Code with extensions, terminal, git
- No auth required for localhost
- The sidecar could start code-server automatically

### Phase 4: Agent Integration
- `/agent alex --screen` — villager spawns next to a code screen
- The villager's tmux pane output is also visible on the screen
- Right-clicking the screen opens the editor, right-clicking the villager opens chat

## Dependencies

### MCEF (Minecraft Chromium Embedded Framework)
- Maven: `com.cinemamod:mcef-fabric:2.x.x` (check latest)
- Downloads CEF binaries on first launch (~200MB)
- Renders web pages to OpenGL textures
- Handles keyboard/mouse input passthrough

### code-server (VS Code in the browser)
- Install: `brew install code-server` or `npm install -g code-server`
- Run: `code-server --port 8080 --auth none --bind-addr 0.0.0.0:8080`
- Provides full VS Code at http://localhost:8080

### Alternative: WebDisplays mod
- Already built on MCEF
- Provides block-based screens out of the box
- Could use as-is instead of building custom renderer

## Implementation Plan

### Step 1: Add MCEF dependency to build.gradle
```gradle
repositories {
    maven { url "https://repo.cinemamod.com/releases" }
}
dependencies {
    modImplementation "com.cinemamod:mcef-fabric:VERSION"
}
```

### Step 2: Create CodeScreenBlock + BlockEntity
- Custom block that renders a web page
- BlockEntity stores the URL and screen dimensions
- BlockEntityRenderer uses MCEF to render the page as a texture

### Step 3: Create CodeScreenRenderer (BlockEntityRenderer)
- Initialize MCEF browser pointed at URL
- On each frame: get the rendered texture from MCEF
- Draw the texture on the block face
- Handle mouse/keyboard when player is "focused" on the screen

### Step 4: Create /code command
- `/code` — spawns screen at player position, loads vscode.dev
- `/code <url>` — spawns screen with custom URL
- `/code server` — starts code-server and loads it
- `/code close` — removes the screen

### Step 5: Input handling
- Right-click the screen block → enter "focus mode"
- All keyboard input goes to the MCEF browser
- Mouse movement maps to screen coordinates
- Press Escape to exit focus mode

### Step 6: code-server auto-start
- Add to Taskfile: `task code-server` starts code-server
- Or add to `minecraft-code start` to auto-launch
- Add code-server pane to tmux session

## File Structure

```
fabric-mod/src/main/java/com/minecraftuse/
├── screen/
│   ├── CodeScreenBlock.java          — custom block
│   ├── CodeScreenBlockEntity.java    — stores URL, manages browser
│   ├── CodeScreenRenderer.java       — renders MCEF texture on block
│   ├── CodeScreenInteraction.java    — keyboard/mouse handling
│   └── CodeScreenRegistry.java      — block/item registration
├── commands/
│   └── CodeCommand.java              — /code command
└── ...
```

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| MCEF not compatible with our Fabric version (1.21.1) | Check MCEF version compatibility first. Fall back to WebDisplays mod if needed. |
| CEF download is 200MB on first launch | Warn user, show progress. Only happens once. |
| Performance impact of Chromium in Minecraft | Limit to one screen at a time. Use offscreen rendering. |
| macOS Apple Silicon support | MCEF claims support. Test early. |
| code-server requires separate install | Add to `minecraft-code init`. Or use vscode.dev as fallback. |
| Keyboard conflicts (MC hotkeys vs editor) | Full keyboard capture when screen is focused. Escape to exit. |

## Testing Strategy

### Unit Tests (Java)
1. CodeScreenBlock registration test
2. CodeCommand argument parsing test
3. URL validation test
4. Screen dimension calculation test

### Integration Tests
1. MCEF loads without crashing
2. Browser navigates to URL
3. Texture is rendered (non-null, correct dimensions)
4. Keyboard events pass through to browser
5. Screen spawns at correct position relative to player

### Manual Tests
1. `/code` spawns a screen and loads vscode.dev
2. Can type in the editor
3. Can open files, use terminal
4. Escape exits focus mode
5. `/code close` removes the screen
6. Performance: >30 FPS with screen active

## Open Questions
1. Use WebDisplays mod directly (faster) or build custom renderer (more control)?
2. Should the screen be a block in the world or a full-screen GUI overlay?
3. How to handle multiple screens (one per agent)?
4. Should code-server workspace be shared with the project directory?
