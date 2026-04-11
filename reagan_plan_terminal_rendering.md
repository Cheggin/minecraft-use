# Terminal Rendering — Medium Tier Plan

## Goal
Parse ANSI escape codes from tmux pane output and render them as colored text in Minecraft's GUI. This makes the VillagerChatScreen and `/claude` output look like a real terminal with syntax highlighting.

## How ANSI Colors Map to Minecraft

ANSI uses `\e[XXm` codes. Minecraft uses `§X` codes.

| ANSI Code | Color | Minecraft § |
|-----------|-------|------------|
| 30 | Black | §0 |
| 31 | Red | §c |
| 32 | Green | §a |
| 33 | Yellow | §e |
| 34 | Blue | §9 |
| 35 | Magenta | §d |
| 36 | Cyan | §b |
| 37 | White | §f |
| 1 | Bold | §l |
| 0 | Reset | §r |
| 90-97 | Bright colors | mapped similarly |

## Implementation

### 1. AnsiToMinecraft.java (new utility)
Convert ANSI escape sequences to Minecraft `§` formatting codes.

```java
public class AnsiToMinecraft {
    public static String convert(String input) {
        // Parse \e[XXm sequences
        // Map to § codes
        // Handle multiple params like \e[1;32m (bold green)
        // Return formatted string
    }
}
```

### 2. Update OutputPoller
Instead of stripping ANSI, convert to Minecraft colors.

### 3. Update VillagerChatScreen
Render the converted colored text.

### 4. Update ClaudeCommand
Show colored output in chat.

## Files to Create/Modify
- NEW: `bridge/AnsiToMinecraft.java`
- MODIFY: `villager/OutputPoller.java` — use convert instead of strip
- MODIFY: `gui/VillagerChatScreen.java` — render colored text
- MODIFY: `commands/ClaudeCommand.java` — show colored output
