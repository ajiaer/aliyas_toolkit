package com.prism.aliyastoolkit.client;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * 只用于蓝图虚影的轻量级世界视图：预览位置返回蓝图里的方块状态，
 * 其它位置回退到真实客户端世界。这样 BlockModelRenderer 的 checkSides
 * 可以把两个相邻虚影方块之间的面剔除掉。
 */
final class GhostPreviewLevel implements BlockAndTintGetter {
    private final ClientLevel level;
    private final Map<BlockPos, BlockState> previewStates;
    private final boolean fullBright;

    GhostPreviewLevel(ClientLevel level, Map<BlockPos, BlockState> previewStates) {
        this(level, previewStates, false);
    }

    GhostPreviewLevel(ClientLevel level, Map<BlockPos, BlockState> previewStates, boolean fullBright) {
        this.level = level;
        this.previewStates = previewStates;
        this.fullBright = fullBright;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState preview = this.previewStates.get(pos);
        return preview != null ? preview : this.level.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.level.getFluidState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.level.getBlockEntity(pos);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return this.fullBright ? 1.0F : this.level.getShade(direction, shade);
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    /** 可选满亮度模式；蓝图范围预览当前保持真实世界光照。 */
    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return this.fullBright ? 15 : this.level.getBrightness(layer, pos);
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return this.fullBright ? 15 : this.level.getRawBrightness(pos, amount);
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return this.level.getBlockTint(pos, colorResolver);
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }
}
