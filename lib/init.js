'use strict';

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const {
  JAVA_BIN,
  TMUX_BRIDGE_PATH,
  SMUX_INSTALL_URL,
  SIDECAR_DIR,
  SIDECAR_VENV,
  SIDECAR_REQUIREMENTS,
  MOD_DIR,
  MOD_JAR_PATH,
  MOD_JAR_NAME,
  MINECRAFT_MODS_DIR,
  PACKAGE_ROOT,
} = require('./constants');
const { runDoctor } = require('./doctor');

const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const CYAN = '\x1b[36m';
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

function installSmux() {
  if (fs.existsSync(TMUX_BRIDGE_PATH)) {
    ok('smux / tmux-bridge already installed');
    return;
  }
  step('Installing smux (tmux-bridge)');
  run(`curl -fsSL ${SMUX_INSTALL_URL} | bash`);
  ok('smux installed');
}

function createVenv() {
  if (fs.existsSync(SIDECAR_VENV)) {
    ok('Python venv already exists');
    return;
  }
  step('Creating Python venv');
  // Prefer homebrew Python 3.12+ over system Python
  const pythonCandidates = ['/opt/homebrew/bin/python3.12', '/opt/homebrew/bin/python3', 'python3.12', 'python3'];
  let pythonBin = 'python3';
  for (const candidate of pythonCandidates) {
    try {
      const ver = execSync(`${candidate} --version`, { encoding: 'utf8' }).trim();
      const match = ver.match(/Python (\d+)\.(\d+)/);
      if (match && (parseInt(match[1]) > 3 || (parseInt(match[1]) === 3 && parseInt(match[2]) >= 12))) {
        pythonBin = candidate;
        break;
      }
    } catch {}
  }
  run(`${pythonBin} -m venv "${SIDECAR_VENV}"`);
  ok('Venv created');
}

function installPythonDeps() {
  step('Installing Python dependencies');
  const pip = path.join(SIDECAR_VENV, 'bin', 'pip');
  run(`"${pip}" install -r "${SIDECAR_REQUIREMENTS}"`);
  ok('Python dependencies installed');
}

function buildMod() {
  step('Building Fabric mod');
  const gradlew = path.join(MOD_DIR, 'gradlew');
  run(`PATH="${JAVA_BIN}:$PATH" "${gradlew}" build`, { cwd: MOD_DIR });
  ok('Fabric mod built');
}

function installModJar() {
  step(`Installing mod JAR to ${MINECRAFT_MODS_DIR}`);
  if (!fs.existsSync(MINECRAFT_MODS_DIR)) {
    fs.mkdirSync(MINECRAFT_MODS_DIR, { recursive: true });
    ok(`Created mods directory: ${MINECRAFT_MODS_DIR}`);
  }
  const dest = path.join(MINECRAFT_MODS_DIR, MOD_JAR_NAME);
  fs.copyFileSync(MOD_JAR_PATH, dest);
  ok(`Copied ${MOD_JAR_NAME} → ${MINECRAFT_MODS_DIR}`);
}

function createEnvTemplate() {
  const envFile = path.join(PACKAGE_ROOT, '.env');
  const envExample = path.join(PACKAGE_ROOT, '.env.example');

  const template = `# minecraft-code environment config

# Browser Use Cloud API key (for schematic downloading)
# Get one at: https://cloud.browser-use.com
BROWSER_USE_API_KEY=

# OpenAI API key (for Whisper transcription, optional)
OPENAI_API_KEY=
`;

  if (!fs.existsSync(envExample)) {
    fs.writeFileSync(envExample, template, 'utf8');
    ok('.env.example created');
  } else {
    ok('.env.example already exists');
  }

  if (!fs.existsSync(envFile)) {
    fs.writeFileSync(envFile, template, 'utf8');
    ok('.env created — fill in your API keys');
  } else {
    ok('.env already exists');
  }
}

function runInit() {
  log(`\n${BOLD}minecraft-code init${RESET}`);
  log('─'.repeat(40));

  step('Running doctor checks');
  // Run lightweight prerequisite checks only (skip venv/jar which don't exist yet)
  const { execSync: exec2 } = require('child_process');
  let prereqsFailed = false;

  ['tmux'].forEach((cmd) => {
    try {
      exec2(`command -v ${cmd}`, { stdio: 'pipe' });
    } catch {
      process.stdout.write(`\x1b[31m✗\x1b[0m ${cmd} not found — install it first\n`);
      prereqsFailed = true;
    }
  });

  if (prereqsFailed) {
    process.stdout.write('\nFix missing prerequisites and re-run init.\n');
    process.exit(1);
  }
  ok('Prerequisites look good');

  installSmux();
  createVenv();
  installPythonDeps();
  buildMod();
  installModJar();
  createEnvTemplate();

  log(`\n${GREEN}${BOLD}Init complete!${RESET}`);
  log(`\nNext steps:`);
  log(`  1. Fill in your API keys in ${YELLOW}.env${RESET}`);
  log(`  2. Run ${BOLD}minecraft-code start${RESET} to launch the dev environment`);
  log(`  3. Attach with: ${BOLD}tmux attach -t minecraft-code${RESET}\n`);
}

module.exports = { runInit };
