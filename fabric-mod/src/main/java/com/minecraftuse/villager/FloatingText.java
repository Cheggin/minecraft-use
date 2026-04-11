package com.minecraftuse.villager;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class FloatingText {

    private static final int MAX_LINES = 4;
    private static final double LINE_SPACING = 0.3;
    private static final int MAX_LINE_LENGTH = 40;

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
        // Show last MAX_LINES lines
        int startIndex = Math.max(0, lines.size() - MAX_LINES);
        List<String> displayLines = lines.subList(startIndex, lines.size());

        for (int i = 0; i < stands.size(); i++) {
            ArmorStandEntity stand = stands.get(i);
            if (i < displayLines.size()) {
                String line = displayLines.get(i);
                if (line.length() > MAX_LINE_LENGTH) {
                    line = line.substring(0, MAX_LINE_LENGTH);
                }
                stand.setCustomName(Text.literal(line));
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
