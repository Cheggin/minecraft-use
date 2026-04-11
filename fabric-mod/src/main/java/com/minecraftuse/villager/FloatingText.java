package com.minecraftuse.villager;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class FloatingText {

    private static final int MAX_LINES = 6;
    private static final double LINE_SPACING = 0.3;
    private static final int MAX_LINE_LENGTH = 60;

    private final ServerWorld world;
    private final List<ArmorStandEntity> stands = new ArrayList<>();

    public FloatingText(ServerWorld world) {
        this.world = world;
    }

    public void spawn(Vec3d pos) {
        remove();
        for (int i = 0; i < MAX_LINES; i++) {
            ArmorStandEntity stand = new ArmorStandEntity(
                world,
                pos.x,
                pos.y + i * LINE_SPACING,
                pos.z
            );
            stand.setInvisible(true);
            stand.setNoGravity(true);
            // setMarker() is private — set MARKER_FLAG via data tracker directly
            byte currentFlags = stand.getDataTracker().get(ArmorStandEntity.ARMOR_STAND_FLAGS);
            stand.getDataTracker().set(ArmorStandEntity.ARMOR_STAND_FLAGS,
                (byte) (currentFlags | ArmorStandEntity.MARKER_FLAG));
            stand.setCustomNameVisible(true);
            stand.setCustomName(Text.literal(""));
            world.spawnEntity(stand);
            stands.add(stand);
        }
    }

    public void update(List<String> lines) {
        // Wrap long lines across multiple stands
        List<String> wrappedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.length() <= MAX_LINE_LENGTH) {
                wrappedLines.add(line);
            } else {
                // Word-wrap at MAX_LINE_LENGTH
                while (line.length() > MAX_LINE_LENGTH) {
                    int cutIndex = MAX_LINE_LENGTH;
                    int spaceIndex = line.lastIndexOf(' ', cutIndex);
                    if (spaceIndex > MAX_LINE_LENGTH / 2) {
                        cutIndex = spaceIndex;
                    }
                    wrappedLines.add(line.substring(0, cutIndex));
                    line = line.substring(cutIndex).trim();
                }
                if (!line.isEmpty()) {
                    wrappedLines.add(line);
                }
            }
        }

        // Show last MAX_LINES wrapped lines
        int startIndex = Math.max(0, wrappedLines.size() - MAX_LINES);
        List<String> displayLines = wrappedLines.subList(startIndex, wrappedLines.size());

        // Assign lines in reverse: first line of text goes to highest stand
        for (int i = 0; i < stands.size(); i++) {
            ArmorStandEntity stand = stands.get(stands.size() - 1 - i);
            if (i < displayLines.size()) {
                stand.setCustomName(Text.literal(displayLines.get(i)));
                stand.setCustomNameVisible(true);
            } else {
                stand.setCustomName(Text.literal(""));
                stand.setCustomNameVisible(false);
            }
        }
    }

    public void tick(Vec3d villagerPos) {
        for (int i = 0; i < stands.size(); i++) {
            ArmorStandEntity stand = stands.get(i);
            if (!stand.isRemoved()) {
                stand.refreshPositionAndAngles(
                    villagerPos.x,
                    villagerPos.y + i * LINE_SPACING,
                    villagerPos.z,
                    stand.getYaw(),
                    stand.getPitch()
                );
            }
        }
    }

    public void remove() {
        for (ArmorStandEntity stand : stands) {
            if (!stand.isRemoved()) {
                stand.discard();
            }
        }
        stands.clear();
    }
}
