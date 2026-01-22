package com.builderbot.render;

import com.builderbot.builder.BuildManager;
import com.builderbot.placement.SchematicPlacement;
import com.builderbot.schematic.BlockEntry;
import com.builderbot.schematic.BuildLayer;
import com.builderbot.schematic.TutorialSchematic;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Renders schematic preview (ghost blocks) and bounding box.
 * Hides during building to avoid visual clutter.
 */
public class SchematicRenderer {

    private static final float PREVIEW_ALPHA = 0.35f;
    private static final float CONFIRMED_ALPHA = 0.5f;
    private static final float BOUNDING_BOX_ALPHA = 0.8f;

    /**
     * Main render method - called every frame.
     */
    public static void render(WorldRenderContext context) {
        BuildManager manager = BuildManager.getInstance();
        SchematicPlacement placement = manager.getPlacementController().getPlacement();

        if (placement == null) {
            return;
        }

        // HIDE during building - less clutter
        if (manager.getBuildExecutor().isBuilding()) {
            return;
        }

        TutorialSchematic schematic = placement.getSchematic();
        if (schematic == null) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Vec3d cameraPos = context.camera().getPos();

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        float alpha = placement.isConfirmed() ? CONFIRMED_ALPHA : PREVIEW_ALPHA;

        // Render layer blocks
        renderLayers(matrices, schematic, placement, alpha);

        // Render bounding box
        renderBoundingBox(matrices, placement);

        matrices.pop();
    }

    /**
     * Renders all layer blocks as colored outlines.
     */
    private static void renderLayers(MatrixStack matrices, TutorialSchematic schematic,
                                     SchematicPlacement placement, float alpha) {

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (BuildLayer layer : schematic.getLayers()) {
            float[] color = layer.getColorComponents();

            for (BlockEntry entry : layer.getBlocks()) {
                BlockPos worldPos = placement.toWorldPos(entry);
                renderBlockFace(buffer, matrices, worldPos,
                        color[0], color[1], color[2], alpha);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * Renders a block as colored faces.
     */
    private static void renderBlockFace(BufferBuilder buffer, MatrixStack matrices,
                                        BlockPos pos, float r, float g, float b, float a) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float x1 = pos.getX();
        float y1 = pos.getY();
        float z1 = pos.getZ();
        float x2 = x1 + 1;
        float y2 = y1 + 1;
        float z2 = z1 + 1;

        // Small offset to prevent z-fighting
        float offset = 0.001f;
        x1 += offset; y1 += offset; z1 += offset;
        x2 -= offset; y2 -= offset; z2 -= offset;

        // Bottom face
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        // Top face
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // North face
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);

        // South face
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

        // West face
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);

        // East face
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
    }

    /**
     * Renders the bounding box outline.
     */
    private static void renderBoundingBox(MatrixStack matrices, SchematicPlacement placement) {
        BlockBox box = placement.getWorldBoundingBox();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.lineWidth(2.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        float x1 = box.getMinX();
        float y1 = box.getMinY();
        float z1 = box.getMinZ();
        float x2 = box.getMaxX() + 1;
        float y2 = box.getMaxY() + 1;
        float z2 = box.getMaxZ() + 1;

        float r = 1.0f, g = 1.0f, b = 0.0f, a = BOUNDING_BOX_ALPHA;

        // Bottom edges
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);

        // Top edges
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

        // Vertical edges
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
        buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(1.0f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}