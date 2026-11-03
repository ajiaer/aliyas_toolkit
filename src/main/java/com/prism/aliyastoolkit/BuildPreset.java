package com.prism.aliyastoolkit;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 一个“快速建造”预设：操作方式 + 维度 + 形状 + 填充方式。
 * <p>
 * 各选项之间有约束（见紧凑构造器）：
 * <ul>
 * <li>一维：形状只能是直线，填充只能是实心；</li>
 * <li>二维：形状是方形/圆形，填充只能是实心/空心；</li>
 * <li>三维：形状是立方（方形）/球（圆形）；球只能实心/空心，立方还可以是框架。</li>
 * </ul>
 * 本类是不可变对象，修改时用 {@code withXxx(...)} 生成新实例。
 */
public record BuildPreset(Operation operation, DimensionKind dimension, ShapeKind shape, Fill fill) {
    /** 操作方式。 */
    public enum Operation implements StringRepresentable {
        /** 点到点：两个点分别是范围的两个角。 */
        POINT_TO_POINT("point_to_point"),
        /** 中心到点：第一个点是中心，向两侧对称扩展。 */
        CENTER_TO_POINT("center_to_point");

        public static final Codec<Operation> CODEC = StringRepresentable.fromEnum(Operation::values);
        public static final StreamCodec<ByteBuf, Operation> STREAM_CODEC = ByteBufCodecs.VAR_INT
                .map(Operation::byId, Operation::ordinal);

        private final String name;

        Operation(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static Operation byId(int id) {
            for (Operation value : values()) {
                if (value.ordinal() == id) {
                    return value;
                }
            }
            return POINT_TO_POINT;
        }

        public String translationKey() {
            return "gui.aliyastoolkit.build.operation." + this.name;
        }
    }

    /** 维度：一维直线、二维平面、三维空间。 */
    public enum DimensionKind implements StringRepresentable {
        ONE("one"),
        TWO("two"),
        THREE("three");

        public static final Codec<DimensionKind> CODEC = StringRepresentable.fromEnum(DimensionKind::values);
        public static final StreamCodec<ByteBuf, DimensionKind> STREAM_CODEC = ByteBufCodecs.VAR_INT
                .map(DimensionKind::byId, DimensionKind::ordinal);

        private final String name;

        DimensionKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static DimensionKind byId(int id) {
            for (DimensionKind value : values()) {
                if (value.ordinal() == id) {
                    return value;
                }
            }
            return TWO;
        }

        public String translationKey() {
            return "gui.aliyastoolkit.build.dimension." + this.name;
        }

        /** 这个维度用到的坐标轴数量。 */
        public int axisCount() {
            return switch (this) {
                case ONE -> 1;
                case TWO -> 2;
                case THREE -> 3;
            };
        }
    }

    /** 形状：直线 / 方形（二维正方形、三维立方） / 圆形（二维圆、三维球）。 */
    public enum ShapeKind implements StringRepresentable {
        LINE("line"),
        SQUARE("square"),
        CIRCLE("circle");

        public static final Codec<ShapeKind> CODEC = StringRepresentable.fromEnum(ShapeKind::values);
        public static final StreamCodec<ByteBuf, ShapeKind> STREAM_CODEC = ByteBufCodecs.VAR_INT
                .map(ShapeKind::byId, ShapeKind::ordinal);

        private final String name;

        ShapeKind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static ShapeKind byId(int id) {
            for (ShapeKind value : values()) {
                if (value.ordinal() == id) {
                    return value;
                }
            }
            return SQUARE;
        }

        public String translationKey() {
            return "gui.aliyastoolkit.build.shape." + this.name;
        }
    }


    /** 填充方式：实心 / 空心 / 框架（只有边）。 */
    public enum Fill implements StringRepresentable {
        SOLID("solid"),
        HOLLOW("hollow"),
        FRAME("frame");

        public static final Codec<Fill> CODEC = StringRepresentable.fromEnum(Fill::values);
        public static final StreamCodec<ByteBuf, Fill> STREAM_CODEC = ByteBufCodecs.VAR_INT
                .map(Fill::byId, Fill::ordinal);

        private final String name;

        Fill(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static Fill byId(int id) {
            for (Fill value : values()) {
                if (value.ordinal() == id) {
                    return value;
                }
            }
            return SOLID;
        }

        public String translationKey() {
            return "gui.aliyastoolkit.build.fill." + this.name;
        }
    }

    /** 新预设的默认值：点到点、二维、方形、实心。 */
    public static final BuildPreset DEFAULT = new BuildPreset(Operation.POINT_TO_POINT, DimensionKind.TWO,
            ShapeKind.SQUARE, Fill.SOLID);

    public static final Codec<BuildPreset> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Operation.CODEC.optionalFieldOf("operation", DEFAULT.operation())
                            .forGetter(BuildPreset::operation),
                    DimensionKind.CODEC.optionalFieldOf("dimension", DEFAULT.dimension())
                            .forGetter(BuildPreset::dimension),
                    ShapeKind.CODEC.optionalFieldOf("shape", DEFAULT.shape()).forGetter(BuildPreset::shape),
                    Fill.CODEC.optionalFieldOf("fill", DEFAULT.fill()).forGetter(BuildPreset::fill))
            .apply(instance, BuildPreset::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuildPreset> STREAM_CODEC = StreamCodec.composite(
            Operation.STREAM_CODEC, BuildPreset::operation,
            DimensionKind.STREAM_CODEC, BuildPreset::dimension,
            ShapeKind.STREAM_CODEC, BuildPreset::shape,
            Fill.STREAM_CODEC, BuildPreset::fill,
            BuildPreset::new);

    public BuildPreset {
        if (operation == null) {
            operation = Operation.POINT_TO_POINT;
        }
        if (dimension == null) {
            dimension = DimensionKind.TWO;
        }
        if (shape == null) {
            shape = ShapeKind.SQUARE;
        }
        if (fill == null) {
            fill = Fill.SOLID;
        }

        // 约束：一维只能直线 + 实心；二维不能框架；三维圆形不能框架
        if (dimension == DimensionKind.ONE) {
            shape = ShapeKind.LINE;
            fill = Fill.SOLID;
        } else {
            if (shape == ShapeKind.LINE) {
                shape = ShapeKind.SQUARE;
            }
            if (dimension == DimensionKind.TWO && fill == Fill.FRAME) {
                fill = Fill.SOLID;
            }
            if (shape == ShapeKind.CIRCLE && fill == Fill.FRAME) {
                fill = Fill.SOLID;
            }
        }
    }

    public static List<Operation> operations() {
        return List.of(Operation.POINT_TO_POINT, Operation.CENTER_TO_POINT);
    }

    public static List<DimensionKind> dimensions() {
        return List.of(DimensionKind.ONE, DimensionKind.TWO, DimensionKind.THREE);
    }

    /** 某个维度下可选的形状。 */
    public static List<ShapeKind> shapesFor(DimensionKind dimension) {
        return dimension == DimensionKind.ONE
                ? List.of(ShapeKind.LINE)
                : List.of(ShapeKind.SQUARE, ShapeKind.CIRCLE);
    }

    /** 某个维度/形状下可选的填充方式。 */
    public static List<Fill> fillsFor(DimensionKind dimension, ShapeKind shape) {
        if (dimension == DimensionKind.ONE) {
            return List.of(Fill.SOLID);
        }
        if (dimension == DimensionKind.TWO || shape == ShapeKind.CIRCLE) {
            return List.of(Fill.SOLID, Fill.HOLLOW);
        }
        return List.of(Fill.SOLID, Fill.HOLLOW, Fill.FRAME);
    }

    /** 在一组可选值里前后移动（循环：到头会回到另一端）。 */
    public static <T> T step(List<T> values, T current, int direction) {
        if (values.isEmpty()) {
            return current;
        }
        int index = values.indexOf(current);
        if (index < 0) {
            return values.get(0);
        }
        int next = Math.floorMod(index + (direction >= 0 ? 1 : -1), values.size());
        return values.get(next);
    }

    public BuildPreset withOperation(Operation value) {
        return new BuildPreset(value, this.dimension, this.shape, this.fill);
    }

    public BuildPreset withDimension(DimensionKind value) {
        return new BuildPreset(this.operation, value, this.shape, this.fill);
    }

    public BuildPreset withShape(ShapeKind value) {
        return new BuildPreset(this.operation, this.dimension, value, this.fill);
    }

    public BuildPreset withFill(Fill value) {
        return new BuildPreset(this.operation, this.dimension, this.shape, value);
    }
}

