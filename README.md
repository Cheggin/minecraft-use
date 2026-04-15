# minecraft-code

[![npm](https://img.shields.io/npm/v/@reaganhsu/minecraft-code)](https://www.npmjs.com/package/@reaganhsu/minecraft-code)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-support-orange)](https://buymeacoffee.com/reaganhsu1b)

Turn Minecraft into a terminal interface for AI agents. Spawn Claude Code, Browser Use, or any CLI tool as Minecraft villagers that follow you around, display output above their heads, and respond to your messages.

```
/agent alex              → spawn Claude villager
/agent rex wolf          → spawn wolf agent
/claude hello            → talk to Claude Code
/browser-use get-schematics castle → download builds
/build 27283             → place schematic via Litematica
/agent-tell alex shawn "review this" → agents talk to each other
```

## Quick Start

```bash
# Install
npm install -g @reaganhsu/minecraft-code

# Check prerequisites
minecraft-code doctor

# Set up everything (venv, mod build, dependencies)
minecraft-code init

# Launch (tmux + sidecar + Claude Code + Minecraft)
minecraft-code start
```

## How It Works

The mod connects to tmux terminal panes via [smux/tmux-bridge](https://github.com/ShawnPana/smux). Each command you type in Minecraft sends text to a named tmux pane and reads the output back. The mod doesn't know or care what's running in each pane.

```
Minecraft Chat  →  TmuxBridge.java  →  tmux-bridge CLI  →  tmux pane
     ↓                                                         ↓
  /claude hello     types "hello" into        Claude Code responds
     ↓              the "claude" pane              ↓
  Shows response    reads output back         "Hello! How can I help?"
  in Minecraft chat
```

## Architecture

```
tmux session "minecraft-code"
┌──────────────┬──────────────┐
│  sidecar     │  claude      │
│  (FastAPI)   │  (lfg)       │
├──────────────┼──────────────┤
│  minecraft   │  browser     │
│  (gradlew)   │  (repl)      │
├──────────────┤              │
│  shell       │              │
│  (bash)      │              │
└──────────────┴──────────────┘

+ "agents" window (created by /agent command)
┌──────────────┬──────────────┐
│  alex        │  shawn       │
│  (lfg)       │  (lfg)       │
└──────────────┴──────────────┘
```

## Commands

### Terminal Commands
| Command | Description |
|---------|-------------|
| `/claude <message>` | Send a message to Claude Code |
| `/browser-use <task>` | Run a Browser Use task |
| `/browser-use get-schematics <query>` | Search and download schematics |
| `/shell <command>` | Run a shell command |
| `/tmux-send <pane> <text>` | Send text to any tmux pane |
| `/tmux-read <pane>` | Read from any tmux pane |

### Agent Villagers
| Command | Description |
|---------|-------------|
| `/agent <name>` | Spawn a villager agent (runs Claude Code) |
| `/agent <name> <mob>` | Spawn as specific mob (wolf, pig, creeper...) |
| `/agent <name> --attach <pane>` | Attach villager to existing tmux pane |
| `/despawn <name>` | Remove agent and close its tmux pane |
| `/agent-tell <from> <to> <msg>` | One agent sends a message to another |
| `/agent-chat <a1> <a2> <prompt>` | Multi-round conversation between agents |

### Code Editor (VS Code in Minecraft)
| Command | Description |
|---------|-------------|
| `/code` | Open VS Code (vscode.dev) in-game |
| `/code repo` | Open the project repo in github.dev |
| `/code server` | Open code-server at localhost:8080 |
| `/code login` | Sign in to GitHub |
| `/code <url>` | Open any URL in the browser |

### Dashboard
| Command | Description |
|---------|-------------|
| `/agents` | Open agent dashboard showing all active agents |

### Schematics
| Command | Description |
|---------|-------------|
| `/build list` | List schematics from Convex database |
| `/build <name>` | Download schematic for Litematica placement |

## Agent Villagers

Spawn AI agents as Minecraft mobs that follow you around:

```
/agent alex              → librarian villager named "alex"
/agent rex wolf          → wolf named "rex"
/agent scout creeper     → creeper named "scout"
```

Each agent:
- **Follows you** (3-6 block distance, teleports if >16 blocks away)
- **Shows output** as floating text above its head (with ANSI color rendering)
- **Right-click** to open a scrollable chat GUI
- **Dies** -> tmux pane closes + custom death sound
- **Custom spawn sounds** per name (alex, shawn, magnus, aitor, etc.)

Agents can talk to each other:
```
/agent-tell alex shawn "tell shawn to review the code"
/agent-chat alex shawn "debate rust vs python"
```

## Schematics Pipeline

Download schematics from the web and place them in Minecraft:

```
/browser-use get-schematics castle     → Browser Use finds and downloads
                                       → Saves to Convex database
/build list                            → Shows available schematics
/build castle                          → Downloads from Convex
                                       → Load in Litematica (M+C) to place
```

Supports:
- minecraft-schematics.com (default, skips non-free)
- planetminecraft.com (`get-schematics castle --site planet`)

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21+ | `brew install openjdk@21` |
| tmux | 3.x+ | `brew install tmux` |
| Python | 3.12+ | `brew install python@3.12` |
| Node.js | 18+ | `brew install node` |
| smux | latest | auto-installed by `minecraft-code init` |

Run `minecraft-code doctor` to check everything.

## Setup Guide

### 1. Browser Use API Key (required for schematic downloading)

1. Go to [cloud.browser-use.com](https://cloud.browser-use.com)
2. Sign up and create an API key
3. (Optional) Create a browser profile for persistent sessions — save the profile ID

### 2. Convex Database (required for schematic storage)

1. Install Convex: `npm install convex`
2. Set up a new project: `npx convex dev` in the `frontend/` directory
3. This creates the schematic storage tables and functions
4. Note your Convex deployment URL (e.g., `https://your-project.convex.cloud`)

### 3. Environment Variables

Create a `.env` file in the project root:

```env
# Required for schematic downloading
BROWSER_USE_API_KEY=bu_your_key_here

# Optional — for voice transcription
OPENAI_API_KEY=sk_your_key_here
```

### 4. Convex Configuration

If you're using your own Convex deployment, update the `CONVEX_URL` in:
- `sidecar/browser_repl.py`
- `fabric-mod/src/main/java/com/minecraftuse/commands/BuildCommand.java`
- `fabric-mod/src/main/java/com/minecraftuse/catalog/CatalogIndex.java`

## CLI

```bash
minecraft-code doctor    # Check prerequisites
minecraft-code init      # Set up everything
minecraft-code start     # Launch tmux environment + Minecraft
minecraft-code stop      # Kill tmux session
```

## Development

```bash
task build          # Build the Fabric mod
task test           # Run all tests (Java + Python)
task test:python    # Python tests only (50 tests)
task lint           # Run ruff linter
task mc:install     # Copy mod JAR to mods folder
task up             # Start tmux dev environment
task down           # Kill tmux session
```

## Tech Stack

- **Minecraft Mod**: Fabric 1.21.1, Java 21, Fabric API 0.107.0
- **Terminal Bridge**: smux/tmux-bridge
- **Sidecar**: Python 3.12, FastAPI, Browser Use Cloud SDK
- **Database**: Convex (schematic storage)
- **Schematic Placement**: Litematica
- **Frontend**: React 19, Vite, Convex

## Supported Mob Types

villager, wolf, cat, pig, cow, sheep, chicken, fox, parrot, rabbit, horse, donkey, llama, goat, bee, axolotl, frog, camel, sniffer, allay, iron_golem, snow_golem, zombie, skeleton, creeper, enderman

## License

MIT
