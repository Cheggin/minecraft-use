'use strict';

const { execSync } = require('child_process');
const fs = require('fs');
const {
  JAVA_BIN,
  TMUX_BRIDGE_PATH,
  SIDECAR_VENV,
  MOD_JAR_PATH,
  REQUIRED_JAVA_MAJOR,
  REQUIRED_PYTHON_MAJOR,
  REQUIRED_PYTHON_MINOR,
} = require('./constants');

const GREEN = '\x1b[32m';
const RED = '\x1b[31m';
const YELLOW = '\x1b[33m';
const RESET = '\x1b[0m';
const BOLD = '\x1b[1m';

function pass(label, detail) {
  process.stdout.write(`  ${GREEN}✓${RESET} ${label}${detail ? `  ${YELLOW}(${detail})${RESET}` : ''}\n`);
}

function fail(label, hint) {
  process.stdout.write(`  ${RED}✗${RESET} ${label}${hint ? `\n    ${YELLOW}→ ${hint}${RESET}` : ''}\n`);
}

function checkJava() {
  try {
    const javaExe = `${JAVA_BIN}/java`;
    const out = execSync(`"${javaExe}" -version 2>&1`, { encoding: 'utf8' });
    const match = out.match(/version "(\d+)/);
    if (match) {
      const major = parseInt(match[1], 10);
      if (major >= REQUIRED_JAVA_MAJOR) {
        pass(`Java ${major}`, javaExe);
        return true;
      }
      fail(`Java ${REQUIRED_JAVA_MAJOR}+ required (found ${major})`, `brew install openjdk@21`);
      return false;
    }
    fail('Java version unreadable', `brew install openjdk@21`);
    return false;
  } catch {
    fail(`Java 21 not found at ${JAVA_BIN}`, `brew install openjdk@21`);
    return false;
  }
}

function checkTmux() {
  try {
    const out = execSync('tmux -V', { encoding: 'utf8' }).trim();
    pass('tmux', out);
    return true;
  } catch {
    fail('tmux not found', 'brew install tmux  (macOS) or  apt install tmux  (Linux)');
    return false;
  }
}

function checkTmuxBridge() {
  if (fs.existsSync(TMUX_BRIDGE_PATH)) {
    pass('tmux-bridge (smux)', TMUX_BRIDGE_PATH);
    return true;
  }
  fail(
    'tmux-bridge not found',
    'curl -fsSL https://raw.githubusercontent.com/ShawnPana/smux/main/install.sh | bash'
  );
  return false;
}

function checkPython() {
  const candidates = ['/opt/homebrew/bin/python3', '/opt/homebrew/bin/python3.12', 'python3.12', 'python3', 'python'];
  for (const cmd of candidates) {
    try {
      const out = execSync(`${cmd} --version 2>&1`, { encoding: 'utf8' }).trim();
      const match = out.match(/Python (\d+)\.(\d+)/);
      if (match) {
        const major = parseInt(match[1], 10);
        const minor = parseInt(match[2], 10);
        if (
          major > REQUIRED_PYTHON_MAJOR ||
          (major === REQUIRED_PYTHON_MAJOR && minor >= REQUIRED_PYTHON_MINOR)
        ) {
          pass(`Python ${major}.${minor}`, cmd);
          return true;
        }
        fail(
          `Python ${REQUIRED_PYTHON_MAJOR}.${REQUIRED_PYTHON_MINOR}+ required (found ${major}.${minor})`,
          'brew install python@3.12'
        );
        return false;
      }
    } catch {
      // try next candidate
    }
  }
  fail(`Python ${REQUIRED_PYTHON_MAJOR}.${REQUIRED_PYTHON_MINOR}+ not found`, 'brew install python@3.12');
  return false;
}

function checkNode() {
  try {
    const out = execSync('node --version', { encoding: 'utf8' }).trim();
    pass('Node.js', out);
    return true;
  } catch {
    fail('Node.js not found', 'https://nodejs.org');
    return false;
  }
}

function checkSidecarVenv() {
  if (fs.existsSync(SIDECAR_VENV)) {
    pass('Python venv', SIDECAR_VENV);
    return true;
  }
  fail('Python venv missing', 'run: minecraft-code init');
  return false;
}

function checkModJar() {
  if (fs.existsSync(MOD_JAR_PATH)) {
    pass('Fabric mod JAR compiled', MOD_JAR_PATH);
    return true;
  }
  fail('Fabric mod JAR not built', 'run: minecraft-code init');
  return false;
}

function runDoctor() {
  process.stdout.write(`\n${BOLD}minecraft-code doctor${RESET}\n`);
  process.stdout.write('─'.repeat(40) + '\n\n');

  process.stdout.write(`${BOLD}Prerequisites${RESET}\n`);
  const javaOk = checkJava();
  const tmuxOk = checkTmux();
  const bridgeOk = checkTmuxBridge();
  const pythonOk = checkPython();
  const nodeOk = checkNode();

  process.stdout.write(`\n${BOLD}Setup Status${RESET}\n`);
  const venvOk = checkSidecarVenv();
  const jarOk = checkModJar();

  const allOk = javaOk && tmuxOk && bridgeOk && pythonOk && nodeOk && venvOk && jarOk;

  process.stdout.write('\n');
  if (allOk) {
    process.stdout.write(`${GREEN}${BOLD}All checks passed.${RESET} Run ${BOLD}minecraft-code start${RESET} to launch.\n\n`);
  } else {
    process.stdout.write(`${RED}${BOLD}Some checks failed.${RESET} Fix the issues above, then run ${BOLD}minecraft-code init${RESET}.\n\n`);
  }

  return allOk;
}

module.exports = { runDoctor };
