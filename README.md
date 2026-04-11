# Minecraft-Use

An in-game AI agent and schematic catalog for Minecraft. Spawn AI villagers that browse the web, search for schematics, and place builds — all without leaving the game.

## What It Does

- **AI Villagers**: Spawn villagers powered by Claude that can browse the web, answer questions, and execute tasks via an in-game chat GUI
- **Schematic Catalog**: Browse, search, and download schematics from minecraft-schematics.com directly in-game
- **Auto-Build**: Place downloaded schematics in your world using Litematica integration
- **Web Frontend**: Minecraft-styled website for browsing your schematic collection (Convex-backed)

## Architecture

```
fabric-mod/          Fabric 1.21.1 mod (Java 21)
  commands/          /build, /catalog, /spawn, /claude, /shell, etc.
  villager/          AI villager entity with floating text + chat GUI
  bridge/            tmux bridge for sidecar communication
  catalog/           In-game schematic catalog GUI
  gui/               Villager chat screen, catalog screen

sidecar/             Python sidecar (FastAPI + Browser Use)
  server.py          FastAPI server on localhost:8765
  browser_repl.py    Browser Use REPL for AI agent browsing
  browser_search.py  Schematic search via Browser Use
  bulk_scraper.py    Bulk schematic downloader
  index_builder.py   Catalog index builder

frontend/            Web frontend (Vite + React + Convex)
  src/               Minecraft-styled landing page and catalog
  convex/            Convex backend (schematic storage, search)
```

## Tech Stack

- **Mod**: Fabric 1.21.1, Java 21, Fabric Loader 0.16.9
- **Sidecar**: Python 3.12, FastAPI, Browser Use, Playwright
- **Frontend**: Vite, React 19, Tailwind CSS v4, Convex
- **Schematic Placement**: Litematica
- **AI**: Claude API via Browser Use

## Getting Started

### Mod

```bash
cd fabric-mod
./gradlew runClient
```

### Sidecar

```bash
cd sidecar
source venv/bin/activate
python server.py
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## In-Game Commands

| Command | Description |
|---------|-------------|
| `/spawn` | Spawn an AI agent villager |
| `/despawn` | Remove an AI villager |
| `/claude <message>` | Send a message to Claude |
| `/build <name>` | Place a schematic by name |
| `/catalog` | Open the schematic catalog GUI |
| `/download <url>` | Download a schematic from URL |
| `/list` | List available schematics |
| `/undo` | Undo the last schematic placement |
| `/shell <cmd>` | Run a shell command via tmux |

## License

GNU General Public License v3.0 - see [LICENSE](LICENSE) for details.
