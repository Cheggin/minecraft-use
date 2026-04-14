'use strict';

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const {
  JAVA_BIN,
  TMUX_BRIDGE_PATH,
  SIDECAR_DIR,
  MOD_DIR,
  MOD_JAR_PATH,
  MOD_JAR_NAME,
  MINECRAFT_MODS_DIR,
  TMUX_SESSION,
} = require('./constants');

const GREEN = '\x1b[32m';
const CYAN = '\x1b[36m';
const YELLOW = '\x1b[33m';
const BOLD = '\x1b[1m';
const RESET = '\x1b[0m';

function log(msg) {
  process.stdout.write(`${msg}\n`);
}

function step(msg) {
  process.stdout.write(`\n${CYAN}▸${RESET} ${BOLD}${msg}${RESET}\n`);
}

function ok(msg) {
  process.stdout.write(`  ${GREEN}✓${RESET} ${msg}\n`);
}

function run(cmd, opts) {
  execSync(cmd, { stdio: 'inherit', ...opts });
}

function buildAndInstall() {
  step('Building Fabric mod');
  const gradlew = path.join(MOD_DIR, 'gradlew');
  run(`PATH="${JAVA_BIN}:$PATH" "${gradlew}" build`, { cwd: MOD_DIR });
  ok('Mod built');

  step(`Installing mod JAR to ${MINECRAFT_MODS_DIR}`);
  if (!fs.existsSync(MINECRAFT_MODS_DIR)) {
    fs.mkdirSync(MINECRAFT_MODS_DIR, { recursive: true });
  }
  const dest = path.join(MINECRAFT_MODS_DIR, MOD_JAR_NAME);
  fs.copyFileSync(MOD_JAR_PATH, dest);
  ok(`${MOD_JAR_NAME} installed`);
}

function killExistingSession() {
  try {
    execSync(`tmux kill-session -t "${TMUX_SESSION}" 2>/dev/null`, { stdio: 'pipe' });
  } catch {
    // No existing session — that's fine
  }
}

function tmux(args) {
  return execSync(`tmux ${args}`, { encoding: 'utf8' }).trim();
}

function sendKeys(pane, keys) {
  execSync(`tmux send-keys -t "${TMUX_SESSION}:dev.${pane}" ${JSON.stringify(keys)} Enter`, {
    stdio: 'pipe',
  });
}

function startTmuxSession() {
  step('Starting tmux session');

  killExistingSession();

  // Create session with one initial pane
  tmux(`new-session -d -s "${TMUX_SESSION}" -n dev -x 220 -y 50`);

  // Build 2x2 grid + 1 extra shell pane (5 total)
  //   .0 top-left     → sidecar
  //   .1 top-right    → claude
  //   .2 bottom-left  → minecraft
  //   .3 bottom-right → browser
  // Then split .2 to get a 5th shell pane → .3 shifts to shell, .4 = browser
  tmux(`split-window -h -t "${TMUX_SESSION}:dev"`);
  tmux(`split-window -v -t "${TMUX_SESSION}:dev.0"`);
  tmux(`split-window -v -t "${TMUX_SESSION}:dev.2"`);
  tmux(`select-layout -t "${TMUX_SESSION}:dev" tiled`);

  // Start services
  sendKeys(
    0,
    `cd '${SIDECAR_DIR}' && source venv/bin/activate && uvicorn server:app --host 127.0.0.1 --port 8765 --reload`
  );
  sendKeys(1, `lfg`);
  sendKeys(
    2,
    `export PATH="${JAVA_BIN}:$PATH" && cd '${MOD_DIR}' && ./gradlew runClient`
  );
  sendKeys(
    3,
    `cd '${SIDECAR_DIR}' && source venv/bin/activate && python browser_repl.py`
  );

  // Add 5th shell pane by splitting the minecraft pane
  tmux(`split-window -v -t "${TMUX_SESSION}:dev.2"`);
  sendKeys(3, `export PATH="${JAVA_BIN}:$PATH" && echo '=== Shell pane ready ==='`);

  // Re-apply tiled layout
  tmux(`select-layout -t "${TMUX_SESSION}:dev" tiled`);

  // Capture pane IDs for tmux-bridge naming
  const pane0 = tmux(`display-message -t "${TMUX_SESSION}:dev.0" -p '#{pane_id}'`);
  const pane1 = tmux(`display-message -t "${TMUX_SESSION}:dev.1" -p '#{pane_id}'`);
  const pane2 = tmux(`display-message -t "${TMUX_SESSION}:dev.2" -p '#{pane_id}'`);
  const pane3 = tmux(`display-message -t "${TMUX_SESSION}:dev.3" -p '#{pane_id}'`);
  const pane4 = tmux(`display-message -t "${TMUX_SESSION}:dev.4" -p '#{pane_id}'`);

  // Name panes with tmux-bridge
  if (fs.existsSync(TMUX_BRIDGE_PATH)) {
    execSync(`"${TMUX_BRIDGE_PATH}" name ${pane0} sidecar`, { stdio: 'pipe' });
    execSync(`"${TMUX_BRIDGE_PATH}" name ${pane1} claude`, { stdio: 'pipe' });
    execSync(`"${TMUX_BRIDGE_PATH}" name ${pane2} minecraft`, { stdio: 'pipe' });
    execSync(`"${TMUX_BRIDGE_PATH}" name ${pane3} shell`, { stdio: 'pipe' });
    execSync(`"${TMUX_BRIDGE_PATH}" name ${pane4} browser`, { stdio: 'pipe' });
  }

  // Set pane border titles
  tmux(`select-pane -t "${TMUX_SESSION}:dev.0" -T sidecar`);
  tmux(`select-pane -t "${TMUX_SESSION}:dev.1" -T claude`);
  tmux(`select-pane -t "${TMUX_SESSION}:dev.2" -T minecraft`);
  tmux(`select-pane -t "${TMUX_SESSION}:dev.3" -T shell`);
  tmux(`select-pane -t "${TMUX_SESSION}:dev.4" -T browser`);

  // Focus sidecar pane
  tmux(`select-pane -t "${TMUX_SESSION}:dev.0"`);

  ok('tmux session started');
}

function printLayout() {
  log(`\n${GREEN}${BOLD}Session '${TMUX_SESSION}' is ready!${RESET}`);
  log('');
  log(`  Attach:  ${BOLD}tmux attach -t ${TMUX_SESSION}${RESET}`);
  log('');
  log(`  ${BOLD}Layout:${RESET}`);
  log(`    ${YELLOW}[sidecar]${RESET}   top-left      — FastAPI sidecar on :8765`);
  log(`    ${YELLOW}[claude]${RESET}    top-right     — Claude Code (type 'lfg' or 'claude')`);
  log(`    ${YELLOW}[minecraft]${RESET} middle-left   — Minecraft (./gradlew runClient)`);
  log(`    ${YELLOW}[shell]${RESET}     middle-right  — General shell`);
  log(`    ${YELLOW}[browser]${RESET}   bottom        — Browser Use REPL`);
  log('');
  log(`  ${BOLD}In-game commands:${RESET}`);
  log(`    /claude <msg>              — send message to Claude Code`);
  log(`    /browser-use <task>        — run a Browser Use task`);
  log(`    /shell <cmd>               — run a shell command`);
  log(`    /tmux-send <pane> <text>   — send text to any named pane`);
  log(`    /agent <task>              — spawn an agent villager`);
  log(`    /build <schematic>         — fetch and build a schematic`);
  log(`    Press K                    — open schematic catalog`);
  log('');
}

function runStart() {
  log(`\n${BOLD}minecraft-use start${RESET}`);
  log('─'.repeat(40));

  buildAndInstall();
  startTmuxSession();
  printLayout();
}

module.exports = { runStart };
