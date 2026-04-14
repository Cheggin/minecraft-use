#!/usr/bin/env node
'use strict';

const USAGE = `
Usage: minecraft-use <command>

Commands:
  init      Set up prerequisites, build the mod, create venv
  start     Build mod, install JAR, launch tmux dev environment
  stop      Kill the tmux dev session
  doctor    Check all prerequisites and setup status

Examples:
  minecraft-use init
  minecraft-use start
  minecraft-use stop
  minecraft-use doctor
`;

const command = process.argv[2];

switch (command) {
  case 'init': {
    const { runInit } = require('../lib/init');
    runInit();
    break;
  }
  case 'start': {
    const { runStart } = require('../lib/start');
    runStart();
    break;
  }
  case 'stop': {
    const { runStop } = require('../lib/stop');
    runStop();
    break;
  }
  case 'doctor': {
    const { runDoctor } = require('../lib/doctor');
    const ok = runDoctor();
    process.exit(ok ? 0 : 1);
    break;
  }
  case undefined:
  case '--help':
  case '-h':
    process.stdout.write(USAGE);
    process.exit(0);
    break;
  default:
    process.stderr.write(`\nUnknown command: ${command}\n${USAGE}`);
    process.exit(1);
}
