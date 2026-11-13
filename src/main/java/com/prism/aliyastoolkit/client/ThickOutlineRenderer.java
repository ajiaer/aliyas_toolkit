package com.prism.aliyastoolkit.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Create 强力胶风格的粗线渲染。
 *
 * <p>不使用 GL 的 lineWidth（驱动通常限制为 1px），而是把每条线生成为
 * 一个有实际截面的 cuboid，并通过 QUADS 绘制。这样线宽由几何尺寸决定，
 * 不会出现多层平行栅格线。</p>
 */
final class ThickOutlineRenderer {
    /** 目标方块框向外扩一点，避免和方块表面完全重合。 */
    static final double BOX_INFLATE = 0.002D;

    private static final RenderType THICK_OUTLINE = RenderType.create(
            "aliyastoolkit_thick_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private ThickOutlineRenderer() {
    }

    static RenderType type() {
        return THICK_OUTLINE;
    }

    /** 画一个有实际宽度的方块边条框。 */
    static void renderBox(PoseStack poseStack, VertexConsumer consumer, AABB box, float width,
            float red, float green, float blue, float alpha) {
        float half = width / 2.0F;
        renderBeam(poseStack, consumer, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                half, red, green, blue, alpha);

        renderBeam(poseStack, consumer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                half, red, green, blue, alpha);

        renderBeam(poseStack, consumer, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ,
                half, red, green, blue, alpha);
        renderBeam(poseStack, consumer, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ,
                half, red, green, blue, alpha);
    }

    /** 画一条从 start 起步、沿 axis 走 1 格的有厚度线段。 */
    static void renderEdge(PoseStack poseStack, VertexConsumer consumer,
            BlockPos start, Direction.Axis axis, float width,
            float red, float green, float blue, float alpha) {
        double x1 = start.getX();
        double y1 = start.getY();
        double z1 = start.getZ();
        double x2 = x1;
        double y2 = y1;
        double z2 = z1;
        switch (axis) {
            case X -> x2 += 1.0D;
            case Y -> y2 += 1.0D;
            case Z -> z2 += 1.0D;
        }
        renderBeam(poseStack, consumer, x1, y1, z1, x2, y2, z2,
                width / 2.0F, red, green, blue, alpha);
    }

    /** 画一根沿 X/Y/Z 轴的方形截面边条；两端各延长半个线宽以闭合转角。 */
    private static void renderBeam(PoseStack poseStack, VertexConsumer consumer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float half, float red, float green, float blue, float alpha) {
        double x0;
        double x3;
        double y0;
        double y3;
        double z0;
        double z3;
        if (x1 != x2) {
            x0 = Math.min(x1, x2) - half;
            x3 = Math.max(x1, x2) + half;
            y0 = y1 - half;
            y3 = y1 + half;
            z0 = z1 - half;
            z3 = z1 + half;
        } else if (y1 != y2) {
            x0 = x1 - half;
            x3 = x1 + half;
            y0 = Math.min(y1, y2) - half;
            y3 = Math.max(y1, y2) + half;
            z0 = z1 - half;
            z3 = z1 + half;
        } else {
            x0 = x1 - half;
            x3 = x1 + half;
            y0 = y1 - half;
            y3 = y1 + half;
            z0 = Math.min(z1, z2) - half;
            z3 = Math.max(z1, z2) + half;
        }
        renderCuboid(poseStack, consumer, x0, y0, z0, x3, y3, z3, red, green, blue, alpha);
    }

    /** 画一个轴对齐立方体的六个面；边条端盖也会一起生成。 */
    private static void renderCuboid(PoseStack poseStack, VertexConsumer consumer,
            double x0, double y0, double z0, double x1, double y1, double z1,
            float red, float green, float blue, float alpha) {
        PoseStack.Pose pose = poseStack.last();
        addQuad(pose, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                0.0F, -1.0F, 0.0F, red, green, blue, alpha);
        addQuad(pose, consumer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                0.0F, 1.0F, 0.0F, red, green, blue, alpha);
        addQuad(pose, consumer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                0.0F, 0.0F, -1.0F, red, green, blue, alpha);
        addQuad(pose, consumer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                0.0F, 0.0F, 1.0F, red, green, blue, alpha);
        addQuad(pose, consumer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                -1.0F, 0.0F, 0.0F, red, green, blue, alpha);
        addQuad(pose, consumer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                1.0F, 0.0F, 0.0F, red, green, blue, alpha);
    }

    private static void addQuad(PoseStack.Pose pose, VertexConsumer consumer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4,
            float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha) {
        addVertex(pose, consumer, x1, y1, z1, normalX, normalY, normalZ, red, green, blue, alpha);
        addVertex(pose, consumer, x2, y2, z2, normalX, normalY, normalZ, red, green, blue, alpha);
        addVertex(pose, consumer, x3, y3, z3, normalX, normalY, normalZ, red, green, blue, alpha);
        addVertex(pose, consumer, x4, y4, z4, normalX, normalY, normalZ, red, green, blue, alpha);
    }

    private static void addVertex(PoseStack.Pose pose, VertexConsumer consumer,
            double x, double y, double z, float normalX, float normalY, float normalZ,
            float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
