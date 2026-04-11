package com.minecraftuse.commands;

import com.minecraftuse.MinecraftUseMod;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * /undo command: reverts the last schematic placement by restoring the original block states.
 * Each placement snapshot is stored as a list of (pos, originalState) pairs.
 */
public class UndoCommand {

    // Stack of placement snapshots; each entry is one /build operation
    private static final Deque<List<BlockSnapshot>> UNDO_STACK = new ArrayDeque<>();

    private static final int MAX_UNDO_LEVELS = 10;

    public record BlockSnapshot(BlockPos pos, BlockState originalState) {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            ClientCommandManager.literal("undo")
                .executes(context -> {
                    FabricClientCommandSource source = context.getSource();
                    return executeUndo(source);
                })
        );
    }

    private static int executeUndo(FabricClientCommandSource source) {
        if (UNDO_STACK.isEmpty()) {
            source.sendFeedback(Text.literal("§e[MCUse] §7Nothing to undo."));
            return 0;
        }

        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            source.sendFeedback(Text.literal("§e[MCUse] §cNo world available."));
            return 0;
        }

        List<BlockSnapshot> snapshot = UNDO_STACK.pop();
        int count = 0;
        for (BlockSnapshot entry : snapshot) {
            world.setBlockState(entry.pos(), entry.originalState(), net.minecraft.block.Block.NOTIFY_ALL);
            count++;
        }

        MinecraftUseMod.LOGGER.info("[UndoCommand] Reverted {} blocks", count);
        source.sendFeedback(Text.literal("§e[MCUse] §aUndone: §f" + count + " blocks restored."));
        return 1;
    }

    /**
     * Call this before placing a schematic to record original block states for undo.
     * Pass the list of positions that will be overwritten.
     */
    public static void recordSnapshot(List<BlockPos> positions) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        List<BlockSnapshot> snapshot = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            BlockState original = world.getBlockState(pos);
            snapshot.add(new BlockSnapshot(pos.toImmutable(), original));
        }

        UNDO_STACK.push(snapshot);

        // Trim stack to max size
        while (UNDO_STACK.size() > MAX_UNDO_LEVELS) {
            UNDO_STACK.removeLast();
        }

        MinecraftUseMod.LOGGER.debug("[UndoCommand] Snapshot recorded: {} blocks", snapshot.size());
    }

    public static boolean hasUndo() {
        return !UNDO_STACK.isEmpty();
    }

    public static void clearHistory() {
        UNDO_STACK.clear();
    }
}
