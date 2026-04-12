# Minecraft-Use

Claude Code, inside Minecraft.

Spawn a villager, talk to it in chat, and it runs Claude Code for you — browsing the web, finding schematics, and placing builds in your world.

## Features

- **In-game Claude Code** — chat with a villager that runs Claude via tmux + Browser Use
- **Schematic search** — villager finds and downloads .schem files from the web
- **Auto-build** — place schematics with Litematica
- **Web catalog** — Minecraft-styled frontend for browsing saved schematics (Convex DB)

## Architecture

```
fabric-mod/          Fabric 1.21.1 mod (Java 21)
  commands/          /spawn, /build, /claude, /catalog, /shell, etc.
  villager/          AI villager with floating text + chat GUI
  bridge/            tmux bridge to sidecar

sidecar/             Python (FastAPI + Browser Use)
  server.py          localhost:8765
  browser_repl.py    Browser Use REPL

frontend/            Vite + React + Convex
  src/               Minecraft-styled UI
  convex/            Schematic storage + search
```

## Getting Started

```bash
# Mod
cd fabric-mod && ./gradlew runClient

# Sidecar
cd sidecar && source venv/bin/activate && python server.py

# Frontend
cd frontend && npm install && npm run dev
```

## Commands

| Command | What it does |
|---------|-------------|
| `/spawn` | Spawn a Claude villager |
| `/despawn` | Remove it |
| `/claude <msg>` | Send a message to Claude |
| `/build <name>` | Place a schematic |
| `/catalog` | Open schematic browser |
| `/download <url>` | Download a .schem file |
| `/list` | List saved schematics |
| `/undo` | Undo last placement |
| `/shell <cmd>` | Run a shell command |

## Stack

Fabric 1.21.1 / Java 21 / Python 3.12 / FastAPI / Browser Use / Playwright / Vite / React 19 / Tailwind v4 / Convex / Litematica

## License

GPL v3 — see [LICENSE](LICENSE).
