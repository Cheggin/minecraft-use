'use strict';

const os = require('os');
const path = require('path');

const PLATFORM = process.platform;
const IS_MAC = PLATFORM === 'darwin';
const IS_LINUX = PLATFORM === 'linux';

// Java 21 paths per OS
const JAVA_HOME_MAC = '/opt/homebrew/opt/openjdk@21';
const JAVA_HOME_LINUX = '/usr/lib/jvm/java-21-openjdk-amd64';
const JAVA_HOME = IS_MAC ? JAVA_HOME_MAC : JAVA_HOME_LINUX;
const JAVA_BIN = path.join(JAVA_HOME, 'bin');

// smux / tmux-bridge
const SMUX_DIR = path.join(os.homedir(), '.smux');
const TMUX_BRIDGE_PATH = path.join(SMUX_DIR, 'bin', 'tmux-bridge');
const SMUX_INSTALL_URL = 'https://raw.githubusercontent.com/ShawnPana/smux/main/install.sh';

// Package root (one level up from lib/)
const PACKAGE_ROOT = path.resolve(__dirname, '..');

// Fabric mod
const MOD_DIR = path.join(PACKAGE_ROOT, 'fabric-mod');
const MOD_JAR_NAME = 'fabric-mod-1.0.0.jar';
const MOD_JAR_PATH = path.join(MOD_DIR, 'build', 'libs', MOD_JAR_NAME);

// Sidecar
const SIDECAR_DIR = path.join(PACKAGE_ROOT, 'sidecar');
const SIDECAR_VENV = path.join(SIDECAR_DIR, 'venv');
const SIDECAR_REQUIREMENTS = path.join(SIDECAR_DIR, 'requirements.txt');

// Minecraft mods folder per OS
const MINECRAFT_MODS_MAC = path.join(os.homedir(), 'Library', 'Application Support', 'minecraft', 'mods');
const MINECRAFT_MODS_LINUX = path.join(os.homedir(), '.minecraft', 'mods');
const MINECRAFT_MODS_DIR = IS_MAC ? MINECRAFT_MODS_MAC : MINECRAFT_MODS_LINUX;

// tmux session
const TMUX_SESSION = 'minecraft-use';

// Required versions
const REQUIRED_JAVA_MAJOR = 21;
const REQUIRED_PYTHON_MAJOR = 3;
const REQUIRED_PYTHON_MINOR = 12;

module.exports = {
  PLATFORM,
  IS_MAC,
  IS_LINUX,
  JAVA_HOME,
  JAVA_BIN,
  SMUX_DIR,
  TMUX_BRIDGE_PATH,
  SMUX_INSTALL_URL,
  PACKAGE_ROOT,
  MOD_DIR,
  MOD_JAR_NAME,
  MOD_JAR_PATH,
  SIDECAR_DIR,
  SIDECAR_VENV,
  SIDECAR_REQUIREMENTS,
  MINECRAFT_MODS_DIR,
  TMUX_SESSION,
  REQUIRED_JAVA_MAJOR,
  REQUIRED_PYTHON_MAJOR,
  REQUIRED_PYTHON_MINOR,
};
