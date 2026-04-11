package com.minecraftuse.villager;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.EnumSet;

public class FollowPlayerGoal extends Goal {

    private static final double MIN_FOLLOW_DISTANCE = 3.0;
    private static final double MAX_FOLLOW_DISTANCE = 6.0;
    private static final double TELEPORT_DISTANCE = 16.0;

    private final MobEntity villager;
    private final double followSpeed;
    private PlayerEntity targetPlayer;

    public FollowPlayerGoal(MobEntity villager, double followSpeed) {
        this.villager = villager;
        this.followSpeed = followSpeed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        targetPlayer = villager.getWorld().getClosestPlayer(villager, -1.0);
        return targetPlayer != null;
    }

    @Override
    public boolean shouldContinue() {
        targetPlayer = villager.getWorld().getClosestPlayer(villager, -1.0);
        return targetPlayer != null;
    }

    @Override
    public void tick() {
        if (targetPlayer == null) return;

        double distance = villager.distanceTo(targetPlayer);

        if (distance > TELEPORT_DISTANCE) {
            villager.teleport(targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), true);
            return;
        }

        if (distance > MAX_FOLLOW_DISTANCE) {
            villager.getNavigation().startMovingTo(
                targetPlayer.getX(),
                targetPlayer.getY(),
                targetPlayer.getZ(),
                followSpeed
            );
        } else if (distance < MIN_FOLLOW_DISTANCE) {
            villager.getNavigation().stop();
        }
    }

    @Override
    public void stop() {
        villager.getNavigation().stop();
        targetPlayer = null;
    }

    @Override
    public EnumSet<Control> getControls() {
        return EnumSet.of(Control.MOVE);
    }
}
