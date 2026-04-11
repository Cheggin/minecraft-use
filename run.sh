#!/bin/bash
# Minecraft Use — Dev Launcher
# Creates a tmux session with named panes for Minecraft + AI agents

set -e

SESSION="minecraft-use"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_PATH="/opt/homebrew/opt/openjdk@21/bin"
TMUX_BRIDGE="$HOME/.smux/bin/tmux-bridge"

echo "=== Minecraft Use Dev Environment ==="
echo ""

# Preflight checks
if ! command -v tmux &>/dev/null; then
    echo "[ERROR] tmux not installed. Install with: brew install tmux"
    exit 1
fi

if [ ! -f "$TMUX_BRIDGE" ]; then
    echo "[ERROR] tmux-bridge not found at $TMUX_BRIDGE"
    echo "Install smux: curl -fsSL https://raw.githubusercontent.com/ShawnPana/smux/main/install.sh | bash"
    exit 1
fi

# Kill existing session if present
tmux kill-session -t "$SESSION" 2>/dev/null || true

# Create 4-pane layout:
#  ┌──────────────┬──────────────┐
#  │  sidecar     │  game        │
#  │  (FastAPI)   │  (Minecraft) │
#  ├──────────────┼──────────────┤
#  │  claude      │  browser     │
#  │  (Claude CLI)│  (REPL)      │
#  └──────────────┴──────────────┘

# Create session — starts with one pane (will become top-left)
tmux new-session -d -s "$SESSION" -n "dev" -x 220 -y 50

# Split into 2x2 grid using tiled layout
# First split: left | right
tmux split-window -h -t "$SESSION:dev"
# Second split: top-left / bottom-left
tmux split-window -v -t "$SESSION:dev.0"
# Third split: top-right / bottom-right
tmux split-window -v -t "$SESSION:dev.2"

# Apply even tiled layout for clean 2x2 grid
tmux select-layout -t "$SESSION:dev" tiled

# Now we have 4 panes in tiled layout:
#   %N pane 0 = top-left     (sidecar)
#   %N pane 1 = bottom-left  (claude)
#   %N pane 2 = top-right    (game / Minecraft)
#   %N pane 3 = bottom-right (browser)

# Start services in each pane
# Pane 0: sidecar (FastAPI)
tmux send-keys -t "$SESSION:dev.0" \
    "cd '$SCRIPT_DIR/sidecar' && source venv/bin/activate && uvicorn server:app --host 127.0.0.1 --port 8765 --reload" Enter

# Pane 1: Claude Code
tmux send-keys -t "$SESSION:dev.1" \
    "echo '=== Claude pane === Type: claude'" Enter

# Pane 2: Shell (run Minecraft manually or use for /shell commands)
tmux send-keys -t "$SESSION:dev.2" \
    "export PATH=$JAVA_PATH:\$PATH && cd '$SCRIPT_DIR' && echo '=== Shell pane === Run Minecraft with: cd fabric-mod && ./gradlew runClient'" Enter

# Pane 3: Browser Use REPL
tmux send-keys -t "$SESSION:dev.3" \
    "cd '$SCRIPT_DIR/sidecar' && source venv/bin/activate && echo '=== Browser pane === Type: python browser_repl.py'" Enter

# Get actual pane IDs and name them with tmux-bridge
# (pane indices may not match %N IDs, so we use session:window.pane targeting)
PANE0=$(tmux display-message -t "$SESSION:dev.0" -p '#{pane_id}')
PANE1=$(tmux display-message -t "$SESSION:dev.1" -p '#{pane_id}')
PANE2=$(tmux display-message -t "$SESSION:dev.2" -p '#{pane_id}')
PANE3=$(tmux display-message -t "$SESSION:dev.3" -p '#{pane_id}')

"$TMUX_BRIDGE" name "$PANE0" sidecar
"$TMUX_BRIDGE" name "$PANE1" claude
"$TMUX_BRIDGE" name "$PANE2" shell
"$TMUX_BRIDGE" name "$PANE3" browser

# Set pane border titles
tmux select-pane -t "$SESSION:dev.0" -T "sidecar"
tmux select-pane -t "$SESSION:dev.1" -T "claude"
tmux select-pane -t "$SESSION:dev.2" -T "shell"
tmux select-pane -t "$SESSION:dev.3" -T "browser"

# Focus the game pane
tmux select-pane -t "$SESSION:dev.2"

echo "tmux session '$SESSION' started."
echo ""
echo "Attach with:  tmux attach -t $SESSION"
echo ""
echo "Pane layout:"
echo "  [sidecar]  top-left     — Python sidecar (port 8765)"
echo "  [game]     top-right    — Minecraft (./gradlew runClient)"
echo "  [claude]   bottom-left  — Claude Code (run 'claude' to start)"
echo "  [browser]  bottom-right — Browser Use (run 'python browser_repl.py' to start)"
echo ""
echo "In Minecraft:"
echo "  /claude <msg>              — talk to Claude Code"
echo "  /browser-use <task>        — run Browser Use tasks"
echo "  /shell <cmd>               — run shell commands (add a 'shell' pane)"
echo "  /tmux-send <pane> <text>   — send to any named pane"
echo "  Press K                    — open schematic catalog"
echo ""

# Attach to session
tmux attach -t "$SESSION"
