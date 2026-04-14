'use strict';

const { execSync } = require('child_process');
const { TMUX_SESSION } = require('./constants');

const GREEN = '\x1b[32m';
const YELLOW = '\x1b[33m';
const BOLD = '\x1b[1m';
const RESET = '\x1b[0m';

function runStop() {
  process.stdout.write(`\n${BOLD}minecraft-code stop${RESET}\n`);
  process.stdout.write('─'.repeat(40) + '\n\n');

  try {
    execSync(`tmux kill-session -t "${TMUX_SESSION}"`, { stdio: 'pipe' });
    process.stdout.write(`  ${GREEN}✓${RESET} tmux session '${TMUX_SESSION}' stopped\n\n`);
  } catch {
    process.stdout.write(`  ${YELLOW}—${RESET} No active session named '${TMUX_SESSION}'\n\n`);
  }
}

module.exports = { runStop };
