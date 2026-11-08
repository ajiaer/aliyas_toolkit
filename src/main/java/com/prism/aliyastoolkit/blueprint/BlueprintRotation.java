package com.prism.aliyastoolkit.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 蓝图旋转工具：在蓝图整体绕 Y 轴旋转时，同时旋转方块状态里的朝向。
 * <p>
 * 先调用原版 {@link BlockState#rotate(Rotation)}，让楼梯、原木、铁轨等方块执行自己的旋转逻辑；
 * 再对原版没有处理的常见属性做兜底：
 * <ul>
 * <li>{@link DirectionProperty}：例如箱子/熔炉/漏斗/告示牌的朝向；</li>
 * <li>{@link Direction.Axis}：例如原木、锁链等柱状方块的 X/Z 轴；</li>
 * <li>名为 {@code rotation} 的 0-15 整数属性：例如告示牌、旗帜、头颅；</li>
 * </ul>
 * 只对“原版旋转后没有变化”的属性做兜底，避免重复旋转。
 */
public final class BlueprintRotation {
    private BlueprintRotation() {
    }

    public static BlockState rotate(BlockState state, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        if (turns == 0 || state.isAir()) {
            return state;
        }

        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };

        BlockState rotated = state.rotate(rotation);

        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction before = state.getValue(directionProperty);
                if (rotated.getValue(directionProperty) == before) {
                    Direction after = rotation.rotate(before);
                    if (after != before) {
                        rotated = rotated.setValue(directionProperty, after);
                    }
                }
            } else if (property instanceof EnumProperty<?> enumProperty
                    && enumProperty.getValueClass() == Direction.Axis.class) {
                @SuppressWarnings("unchecked")
                EnumProperty<Direction.Axis> axisProperty = (EnumProperty<Direction.Axis>) enumProperty;
                Direction.Axis before = state.getValue(axisProperty);
                if (rotated.getValue(axisProperty) == before && turns % 2 == 1) {
                    Direction.Axis after = before == Direction.Axis.X ? Direction.Axis.Z
                            : before == Direction.Axis.Z ? Direction.Axis.X : before;
                    if (after != before) {
                        rotated = rotated.setValue(axisProperty, after);
                    }
                }
            } else if (property instanceof IntegerProperty integerProperty
                    && "rotation".equals(integerProperty.getName())
                    && integerProperty.getPossibleValues().size() == 16) {
                int before = state.getValue(integerProperty);
                if (rotated.getValue(integerProperty) == before) {
                    int after = Math.floorMod(before + turns * 4, 16);
                    if (after != before) {
                        rotated = rotated.setValue(integerProperty, after);
                    }
                }
            }
        }

        return rotated;
    }
}
