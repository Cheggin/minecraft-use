# Spotify GUI for Minecraft

Custom in-game GUI for controlling local Spotify. Looks Minecraft-y (pixel font, dark slate panels, vanilla widgets, music-disc iconography), backed by AppleScript for playback + Spotify Web API for search.

## Architecture

```
Minecraft (/spotify)
  → SpotifyScreen (Java, vanilla MC GUI)
  → SpotifyClient (HTTP) ──┐
                           ↓
              FastAPI sidecar :8765
              ├── /spotify/now-playing   → osascript (AppleScript)
              ├── /spotify/playpause     → osascript
              ├── /spotify/play|pause|next|previous
              ├── /spotify/volume        → osascript
              ├── /spotify/seek          → osascript
              ├── /spotify/play-uri      → osascript "play track <uri>"
              └── /spotify/search        → Spotify Web API (Client Credentials)
```

**Why this split:** AppleScript handles all playback locally with zero auth and works on the free tier. Web API only fills the gap AppleScript can't fill — search.

## MVP scope (this PR)

1. `/spotify` command opens the GUI (deferred-tick pattern, mirrors `/agents`)
2. **Now Playing panel** — track name, artist, album, music-disc icon (chosen by hash of track id for variety), progress bar
3. **Transport controls** — play/pause, next, previous (vanilla `ButtonWidget`)
4. **Volume slider** — vanilla `SliderWidget`, 0–100
5. **Search box** — type query → fetch results → click row → AppleScript plays the URI
6. Polls `/spotify/now-playing` every ~1s while screen is open

## Out of scope (later)

- PKCE OAuth + personal playlists / Liked Songs
- Queue management
- Seek bar drag (display-only progress for v1)
- Lyrics
- Real album art rendering (using vanilla music disc items instead — more Minecraft-y anyway)

## Files to create

| File | Purpose |
|------|---------|
| `sidecar/spotify_bridge.py` | AppleScript wrappers + Web API search + token cache |
| `sidecar/server.py` | mount the new endpoints |
| `fabric-mod/.../commands/SpotifyCommand.java` | `/spotify` command |
| `fabric-mod/.../network/SpotifyClient.java` | HTTP client (mirrors `SidecarClient`) |
| `fabric-mod/.../gui/SpotifyScreen.java` | the GUI itself |
| `fabric-mod/.../MinecraftUseMod.java` | register command + tick hook |

## Aesthetic decisions

- Default Minecraft font (`textRenderer`) — already pixel-perfect
- Dark slate background (`0xCC101010`) matching `AgentDashboardScreen`
- Green accent for play state (`0xFF55FF55` — XP/note-block green)
- **Music disc as album art**: `context.drawItem(new ItemStack(Items.MUSIC_DISC_*), x, y)` picked from `[13, blocks, cat, chirp, far, mall, mellotin, stal, strad, ward, 11, wait, pigstep, otherside, 5, relic, creator, creator_music_box, precipice]` by `Math.abs(trackId.hashCode()) % discs.length`
- Section headers in `0xFFAAAA00` (gold), inline data in `0xFFCCCCCC`
- "JUKEBOX" title bar

## Verification

- `curl http://127.0.0.1:8765/spotify/now-playing` returns the current track JSON
- `curl -X POST http://127.0.0.1:8765/spotify/playpause` toggles playback
- `curl "http://127.0.0.1:8765/spotify/search?q=daft+punk"` returns track URIs
- Open `/spotify` in-game → see now-playing card → click pause → song pauses → click search → type → click result → song changes

## Risks

- **Spotify must be running locally** — if not, `/spotify` shows "Spotify not running" instead of crashing
- **AppleScript surface is Spotify-controlled** — they've removed/restored it before; if they break it, fall back to Web API + Premium requirement
- **macOS-only** — fine for now (the whole project assumes brew/macOS already)
