# Packaging Plan — Make minecraft-use installable

## Goal
A user should be able to run a single command to set up minecraft-use in any project and start using it.

## Target Experience
```bash
# Install globally
npm install -g minecraft-use

# Or use npx
npx minecraft-use init

# Then start everything
minecraft-use start
# or
npx minecraft-use start
```

## What the CLI needs to do

### `minecraft-use init`
1. Check prerequisites (Java 21, tmux, Python 3.12+, smux)
2. Install missing prerequisites (guide the user)
3. Clone/copy the fabric mod and sidecar into the current project
4. Set up Python venv and install dependencies
5. Build the Fabric mod
6. Install Litematica + MaLiLib
7. Create config files (.env template)
8. Create Taskfile.yml in the project

### `minecraft-use start`
1. Build the mod if needed
2. Install JAR to mods folder
3. Start tmux session with all panes (sidecar, claude, minecraft, shell, browser)
4. Name all panes via tmux-bridge

### `minecraft-use stop`
1. Kill tmux session
2. Clean up

### `minecraft-use doctor`
1. Check all prerequisites
2. Check if tmux-bridge is installed
3. Check if Java 21 is available
4. Check if Python venv exists
5. Report status

## Package Structure
```
minecraft-use/
├── package.json          (npm package with bin entry)
├── bin/
│   └── minecraft-use.js  (CLI entry point)
├── lib/
│   ├── init.js           (init command)
│   ├── start.js          (start command)
│   ├── stop.js           (stop command)
│   ├── doctor.js         (doctor command)
│   └── constants.js      (paths, versions)
├── fabric-mod/           (Fabric mod source)
├── sidecar/              (Python sidecar)
├── Taskfile.yml
└── README.md
```

## Steps
1. Create package.json with bin entry
2. Create CLI entry point (bin/minecraft-use.js)
3. Create init/start/stop/doctor commands
4. Write proper README with installation instructions
5. Test the full flow
6. Prepare for npm publish (user will do this)
