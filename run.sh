#!/bin/bash
# Minecraft Use — Dev Launcher
# Starts both the Fabric mod dev client and the Python sidecar

set -e

export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

echo "=== Minecraft Use Dev Environment ==="
echo ""

# Start Python sidecar in background
echo "[1/2] Starting Python sidecar on :8765..."
cd sidecar
source venv/bin/activate
uvicorn server:app --host 127.0.0.1 --port 8765 --reload &
SIDECAR_PID=$!
cd ..

echo "[2/2] Starting Minecraft with Fabric mod..."
cd fabric-mod
./gradlew runClient &
MC_PID=$!
cd ..

echo ""
echo "Sidecar PID: $SIDECAR_PID"
echo "Minecraft PID: $MC_PID"
echo ""
echo "Press Ctrl+C to stop both"

# Cleanup on exit
trap "kill $SIDECAR_PID $MC_PID 2>/dev/null; exit" INT TERM
wait
