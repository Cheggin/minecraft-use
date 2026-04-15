# Minecraft-Use -- New Ideas to Explore

Brainstormed 2026-04-11. Synthesized from three independent analysis passes (gameplay, technical, demo/viral).

---

## Tier 1: Quick Wins (Small effort, high impact)

### 1. Process Garden
**Concept**: `/listen` already binds processes to allays. Add a `--mob` flag so each service gets a distinct animal: your Next.js server is a bee, Vite is a fox, your API is a wolf. Your dev stack becomes a living ecosystem you walk through.

**What exists**: `/listen` already does 90% of this. Mob type is just hardcoded to `"allay"` in `ListenCommand.java:33`.

**Work**: Add a mob type argument to `/listen`. One parameter, one line change.

---

### 2. The War Room (Multi-Agent Debate)
**Concept**: Spawn 3+ agents as different mobs, each running Claude Code with a different system prompt ("security auditor", "perf optimizer", "UX advocate"). Ask them all the same question. Walk between villagers to "hear" different perspectives floating above their heads.

**What exists**: Multi-agent spawn already works. Arbitrary commands per agent already supported. Floating text renders per-agent.

**Work**: Zero code changes needed for a basic version. A `/team <preset>` convenience command would be medium effort.

---

### 3. Heartbeat Totems
**Concept**: Agent villagers reflect process health visually. Parse OutputPoller lines for error keywords (stack traces, FATAL, non-zero exits) and the villager takes damage, catches fire, or turns red. Healthy processes emit heart particles. Dead processes make the mob explode.

**Work**: Add a simple error classifier to OutputPoller's existing line-parsing loop. Small.

---

### 4. GitWeather
**Concept**: Attach a villager to a pane running `watch -n5 git status`. Uncommitted changes trigger rain. Merge conflicts spawn lightning. Clean working tree = clear sky. Your Minecraft weather reflects your repo state.

**Work**: Parse git status output in OutputPoller, call weather commands. Small-medium.

---

## Tier 2: Medium Effort, High Wow Factor

### 5. Zombie Horde CI
**Concept**: Spawn one zombie per test file: `/agent test-auth zombie "npm test -- auth.test.ts"`. Each zombie's floating text shows pass/fail. A horde with green/red text above their heads. Kill a zombie to re-run that test.

**Work**: Output coloring based on exit status + a `/test-suite` convenience command. Medium.

---

### 6. Kill to Deploy
**Concept**: Spawn a creeper named "deploy-staging." It follows you around, ticking. Kill it to trigger `git push origin staging` -- explosion animation plays. Spawn an iron golem for prod -- much harder to kill, which is the point.

**Work**: Death event handler that fires a shell command (currently despawn just discards). Medium.

---

### 7. VoiceCraft (Walk-up Voice Input)
**Concept**: Hold a keybind, speak, and your words are transcribed via Whisper and piped into whichever agent villager you're looking at. Walk up to any mob-process and talk to it.

**What exists**: Whisper transcription is already wired in the sidecar (`/transcribe/start-recording`, `/transcribe/stop-recording`).

**Work**: Keybind + raycasting to nearest agent + sidecar call + `bridge.type()`. Small-medium.

---

### 8. Villager Crosstalk (AI-to-AI Conversation)
**Concept**: `/crosstalk alice bob "review each other's code"` pipes alice's tmux output as input to bob's pane, and vice versa. Two villagers autonomously debate, visible as dueling floating text. Kill either to stop.

**Work**: Bi-directional OutputPoller piping + turn management + configurable max turns (to prevent infinite API burn). Medium.

---

### 9. Webhook Villagers (GitHub Events as Mobs)
**Concept**: Reuse the FastAPI sidecar to receive GitHub webhooks. Each push/PR/CI event spawns a temporary villager: failed CI = zombie, success = iron golem. It walks to you, shows commit message + status, then despawns after 60s. Your world becomes a physical CI dashboard.

**Work**: New sidecar endpoint + mod-side HTTP polling or push mechanism. Medium.

---

### 10. SSHPortal
**Concept**: Place a Nether portal, assign it a host: `/portal user@server`. Step through to open an SSH session in a new tmux pane with a villager showing remote shell output on the other side.

**Work**: Portal event interception + SSH pane management. Medium.

---

## Tier 3: Ambitious / Large Effort

### 11. Terminal Cartography
**Concept**: `/agent <name> --map` reads CLI output (file tree, git log --graph, docker ps) and renders it as physical Minecraft structures. A file tree becomes actual tree-shaped blocks. Git branches become colored wool paths.

**Work**: Parsing + dynamic schematic generation. Large.

---

### 12. Say It, Build It (Voice-to-Architecture Pipeline)
**Concept**: Hold key, speak "Build me a Japanese temple." Whisper transcribes -> Claude interprets -> Browser Use finds schematic -> downloads -> auto-places in world. Voice to physical structure in one shot.

**Blocker**: Litematica auto-placement API. Currently `/build` only downloads; placement is manual.

**Work**: Pipeline orchestration + Litematica integration. Large.

---

### 13. Schematic Roulette (AI Builds a Village)
**Concept**: Tell Claude "Build me a medieval village." It iteratively searches for schematics (blacksmith, house, well...) and places each one spatially relative to the last. Time-lapse for a 30-second clip.

**Blocker**: Same Litematica auto-placement gap + spatial reasoning for relative placement.

**Work**: Large.

---

## Open Questions to Resolve

| Question | Affects |
|----------|---------|
| Can Litematica be driven programmatically? | Ideas 12, 13 |
| Concurrent poller performance ceiling? (200ms x N agents) | Ideas 1, 2, 5, 6 |
| Is multiplayer support worth pursuing? | War Room, Pair Programming |
| Max floating text readability with 5+ agents nearby? | All multi-agent ideas |
| Death event hooks for programmatic mobs in Fabric? | Ideas 6, 9 |

---

## Recommended Exploration Order

```
Quick demos (today):
  1. Process Garden  -- one-line change to /listen
  2. War Room        -- zero code, just script 3 /agent commands

Next sprint:
  3. VoiceCraft      -- wire existing Whisper to agent targeting
  4. Heartbeat Totems -- error classifier in OutputPoller
  5. Kill to Deploy   -- death event handler

Demo video material:
  6. Zombie Horde CI  -- very shareable visual
  7. Webhook Villagers -- physical CI dashboard

Validate first:
  8. Litematica API feasibility -- gates the two biggest ideas
```
