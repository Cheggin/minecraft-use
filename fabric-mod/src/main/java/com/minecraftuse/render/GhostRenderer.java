package com.minecraftuse.render;

import com.minecraftuse.MinecraftUseMod;
import com.minecraftuse.schematic.SchematicParser;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a transparent ghost overlay of a schematic at a target position.
 * Uses WorldRenderEvents.AFTER_TRANSLUCENT to hook into the render pipeline.
 * Green tint = can place, red tint = blocked by existing blocks.
 */
public class GhostRenderer {

    private static final float GHOST_ALPHA = 0.4f;
    private static final int COLOR_GREEN = 0x6600FF00;
    private static final int COLOR_RED   = 0x66FF0000;

    // Singleton active ghost state
    private static GhostRenderer activeGhost = null;

    private final SchematicParser.Schematic schematic;
    private BlockPos origin;
    private boolean registered = false;

    // Pre-computed block positions relative to origin
    private final List<GhostBlock> ghostBlocks = new ArrayList<>();

    private record GhostBlock(BlockPos relPos, BlockState state) {}

    public GhostRenderer(SchematicParser.Schematic schematic, BlockPos origin) {
        this.schematic = schematic;
        this.origin = origin;
        buildGhostBlocks();
    }

    private void buildGhostBlocks() {
        ghostBlocks.clear();
        String[] indexToState = buildIndexToState(schematic.palette());
        int[] blockData = schematic.blockData();
        int width = schematic.width();
        int length = schematic.length();
        int[] offset = schematic.offset();

        for (int i = 0; i < blockData.length; i++) {
            int idx = blockData[i];
            if (idx < 0 || idx >= indexToState.length) continue;
            String stateStr = indexToState[idx];
            if (stateStr == null) continue;
            if (stateStr.contains(":air")) continue;

            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);

            BlockPos rel = new BlockPos(x + offset[0], y + offset[1], z + offset[2]);
            BlockState state = parseBlockState(stateStr);
            if (state != null) {
                ghostBlocks.add(new GhostBlock(rel, state));
            }
        }
    }

    private static String[] buildIndexToState(Map<String, Integer> palette) {
        int max = 0;
        for (int v : palette.values()) if (v > max) max = v;
        String[] arr = new String[max + 1];
        for (Map.Entry<String, Integer> e : palette.entrySet()) arr[e.getValue()] = e.getKey();
        return arr;
    }

    private static BlockState parseBlockState(String blockStateStr) {
        String blockId = blockStateStr;
        int bracket = blockStateStr.indexOf('[');
        if (bracket != -1) blockId = blockStateStr.substring(0, bracket);
        Identifier id = Identifier.tryParse(blockId);
        if (id == null) return null;
        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR && !blockId.equals("minecraft:air")) return null;
        return block.getDefaultState();
    }

    /**
     * Activate this ghost renderer, replacing any currently active one.
     */
    public void activate() {
        if (activeGhost != null) {
            activeGhost.deactivate();
        }
        activeGhost = this;
        if (!registered) {
            WorldRenderEvents.AFTER_TRANSLUCENT.register(this::onAfterTranslucent);
            registered = true;
        }
        MinecraftUseMod.LOGGER.info("[GhostRenderer] Activated ghost preview at {}", origin);
    }

    public void deactivate() {
        if (activeGhost == this) {
            activeGhost = null;
        }
        MinecraftUseMod.LOGGER.info("[GhostRenderer] Deactivated ghost preview");
    }

    public static void deactivateActive() {
        if (activeGhost != null) {
            activeGhost.deactivate();
        }
    }

    public static boolean hasActiveGhost() {
        return activeGhost != null;
    }

    /**
     * Move the ghost origin by a delta.
     */
    public void move(int dx, int dy, int dz) {
        origin = origin.add(dx, dy, dz);
    }

    public BlockPos getOrigin() {
        return origin;
    }

    private void onAfterTranslucent(WorldRenderContext context) {
        if (ghostBlocks.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d camPos = context.camera().getPos();

        matrices.push();
        matrices.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer consumer = consumers.getBuffer(RenderLayer.getTranslucent());

        for (GhostBlock ghost : ghostBlocks) {
            BlockPos worldPos = origin.add(ghost.relPos());
            boolean blocked = !mc.world.getBlockState(worldPos).isAir();

            float r = blocked ? 1.0f : 0.0f;
            float g = blocked ? 0.0f : 1.0f;
            float b = 0.0f;
            float a = GHOST_ALPHA;

            renderBlockOutline(matrices, consumer, worldPos, r, g, b, a);
        }

        matrices.pop();
    }

    private void renderBlockOutline(MatrixStack matrices, VertexConsumer consumer,
                                     BlockPos pos, float r, float g, float b, float a) {
        Matrix4f mat = matrices.peek().getPositionMatrix();

        float x0 = pos.getX();
        float y0 = pos.getY();
        float z0 = pos.getZ();
        float x1 = x0 + 1.0f;
        float y1 = y0 + 1.0f;
        float z1 = z0 + 1.0f;

        int ri = (int)(r * 255);
        int gi = (int)(g * 255);
        int bi = (int)(b * 255);
        int ai = (int)(a * 255);

        // Bottom face
        renderQuad(consumer, mat, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, ri, gi, bi, ai);
        // Top face
        renderQuad(consumer, mat, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, ri, gi, bi, ai);
        // North face (z=z0)
        renderQuad(consumer, mat, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, ri, gi, bi, ai);
        // South face (z=z1)
        renderQuad(consumer, mat, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, ri, gi, bi, ai);
        // West face (x=x0)
        renderQuad(consumer, mat, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, ri, gi, bi, ai);
        // East face (x=x1)
        renderQuad(consumer, mat, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, ri, gi, bi, ai);
    }

    private void renderQuad(VertexConsumer consumer, Matrix4f mat,
                              float x0, float y0, float z0,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float x3, float y3, float z3,
                              int r, int g, int b, int a) {
        consumer.vertex(mat, x0, y0, z0).color(r, g, b, a);
        consumer.vertex(mat, x1, y1, z1).color(r, g, b, a);
        consumer.vertex(mat, x2, y2, z2).color(r, g, b, a);
        consumer.vertex(mat, x3, y3, z3).color(r, g, b, a);
    }
}
