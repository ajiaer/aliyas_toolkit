package com.prism.aliyastoolkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * “快速建造”的几何计算（双端共用）。
 * <p>
 * 客户端用它画预览和辅助线，服务端用同一套逻辑重新计算真正要放置的方块，
 * 保证预览与实际建造完全一致。
 * <p>
 * 吸附规则（尽量贴近“最近的 正位置 / 正斜位置”）：
 * <ul>
 * <li>一维：取偏移量最大的坐标轴，得到一条沿轴直线；如果恰好有两个分量相等且都是最大值，
 * 则取 45° 正斜线。例如第一个点 (0,0,0)、第二个点 (0,2,3) → 规划到 (0,0,3)。</li>
 * <li>二维/三维：按维度选取偏移量最大的若干坐标轴，形成一个正方体/正方形的范围
 * （边长取这些轴上偏移量的最大值，圆的直径也取这个值）。</li>
 * </ul>
 */
public final class QuickBuild {
    /** 一次最多建造多少个方块。 */
    public static final int MAX_VOLUME = 4096;
    /** 建造点离玩家的最大距离。 */
    public static final double MAX_DISTANCE = 40.0D;

    private QuickBuild() {
    }

    /**
     * 把原始第二个点吸附成“正位置 / 正斜位置”。
     * <p>
     * 圆形/球形取“正方向位置”（只沿一个轴），只用来确定半径；方形取对角位置。
     *
     * @param look 玩家视线方向，用于距离相同（对称）时决定选哪一边
     */
    public static BlockPos snap(BlockPos first, BlockPos raw, BuildPreset preset, Vec3 look) {
        int[] delta = {raw.getX() - first.getX(), raw.getY() - first.getY(), raw.getZ() - first.getZ()};

        if (preset.dimension() == BuildPreset.DimensionKind.ONE) {
            int[] ext = snapLineExtents(delta, look);
            return first.offset(ext[0], ext[1], ext[2]);
        }

        int axisCount = preset.dimension().axisCount();
        int[] axes = chooseAxes(delta, axisCount, look);
        int[] ext = new int[3];

        if (preset.shape() == BuildPreset.ShapeKind.CIRCLE) {
            // 圆形/球形：第二个点吸附到“正方向”（只沿一个轴），半径取最大的偏移
            int axis = dominantAxis(delta, look);
            boolean inAxes = false;
            for (int candidate : axes) {
                if (candidate == axis) {
                    inAxes = true;
                    break;
                }
            }
            if (!inAxes) {
                axis = axes[0];
            }
            int size = Math.max(1, Math.abs(delta[axis]));
            int sign = Integer.signum(delta[axis]);
            ext[axis] = (sign == 0 ? 1 : sign) * size;
        } else {
            // 方形：允许长方形/长方体，两个点就是对角的两个位置
            for (int axis : axes) {
                ext[axis] = delta[axis];
            }
        }
        return first.offset(ext[0], ext[1], ext[2]);
    }

    /**
     * 用来计算范围的第二个点。
     * <p>
     * 方形/直线与 {@link #snap} 相同；圆形/球形会补齐成正方形/立方体（这样内切圆/球才是正的），
     * 同时保留选中的平面（轴向由原始偏移 + 视线决定），避免服务端丢失平面信息。
     */
    public static BlockPos snapBox(BlockPos first, BlockPos raw, BuildPreset preset, Vec3 look) {
        if (preset.dimension() == BuildPreset.DimensionKind.ONE
                || preset.shape() != BuildPreset.ShapeKind.CIRCLE) {
            return snap(first, raw, preset, look);
        }

        int[] delta = {raw.getX() - first.getX(), raw.getY() - first.getY(), raw.getZ() - first.getZ()};
        int[] axes = chooseAxes(delta, preset.dimension().axisCount(), look);
        int size = 0;
        for (int axis : axes) {
            size = Math.max(size, Math.abs(delta[axis]));
        }
        if (size <= 0) {
            size = 1;
        }
        int[] ext = new int[3];
        for (int axis : axes) {
            int sign = Integer.signum(delta[axis]);
            ext[axis] = (sign == 0 ? 1 : sign) * size;
        }
        return first.offset(ext[0], ext[1], ext[2]);
    }

    /**
     * 一维直线：在 6 个轴向和 12 个 45° 正斜方向里，选和原始偏移最接近的方向。
     * <p>
     * 之前要求“两个分量完全相等”才算正斜，很难点出来；现在按方向夹角判断，
     * 正斜的容差大得多，更容易匹配。
     */
    private static int[] snapLineExtents(int[] delta, Vec3 look) {
        int[] bestExt = new int[3];
        double length = Math.sqrt((double) delta[0] * delta[0] + (double) delta[1] * delta[1]
                + (double) delta[2] * delta[2]);
        if (length < 1.0E-6D) {
            return bestExt;
        }

        double bestScore = -2.0D;
        double[] bestDir = null;

        // 轴向
        for (int axis = 0; axis < 3; axis++) {
            if (delta[axis] == 0) {
                continue;
            }
            double score = Math.abs(delta[axis]) / length;
            double[] dir = new double[3];
            dir[axis] = Integer.signum(delta[axis]);
            if (isBetterDirection(score, dir, bestScore, bestDir, look)) {
                bestScore = score;
                bestDir = dir;
                bestExt = new int[3];
                bestExt[axis] = delta[axis];
            }
        }

        // 45° 正斜（两个轴等长）
        for (int i = 0; i < 3; i++) {
            for (int j = i + 1; j < 3; j++) {
                if (delta[i] == 0 || delta[j] == 0) {
                    continue;
                }
                double score = (Math.abs(delta[i]) + Math.abs(delta[j])) / (length * Math.sqrt(2.0D));
                int step = Math.max(1, (int) Math.round((Math.abs(delta[i]) + Math.abs(delta[j])) / 2.0D));
                double[] dir = new double[3];
                dir[i] = Integer.signum(delta[i]);
                dir[j] = Integer.signum(delta[j]);
                if (isBetterDirection(score, dir, bestScore, bestDir, look)) {
                    bestScore = score;
                    bestDir = dir;
                    bestExt = new int[3];
                    bestExt[i] = Integer.signum(delta[i]) * step;
                    bestExt[j] = Integer.signum(delta[j]) * step;
                }
            }
        }
        return bestExt;
    }

    private static boolean isBetterDirection(double score, double[] dir, double bestScore, double[] bestDir, Vec3 look) {
        if (bestDir == null) {
            return true;
        }
        if (score > bestScore + 1.0E-9D) {
            return true;
        }
        if (score < bestScore - 1.0E-9D) {
            return false;
        }
        if (look == null) {
            return false;
        }
        return dotWithLook(dir, look) > dotWithLook(bestDir, look);
    }

    private static double dotWithLook(double[] dir, Vec3 look) {
        double length = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
        if (length < 1.0E-6D) {
            return 0.0D;
        }
        return (dir[0] * look.x + dir[1] * look.y + dir[2] * look.z) / length;
    }

    /** 偏移量最大的坐标轴；一样大时看视线更偏向哪一边。 */
    private static int dominantAxis(int[] delta, Vec3 look) {
        int best = 0;
        double bestAbs = -1.0D;
        double bestLook = -1.0D;
        for (int i = 0; i < 3; i++) {
            double abs = Math.abs(delta[i]);
            double lookAbs = look == null ? 0.0D : Math.abs(component(look, i));
            if (abs > bestAbs || (abs == bestAbs && lookAbs > bestLook)) {
                best = i;
                bestAbs = abs;
                bestLook = lookAbs;
            }
        }
        return best;
    }

    /** 按偏移量从大到小选若干个坐标轴；一样大时看视线更偏向哪一边。 */
    private static int[] chooseAxes(int[] delta, int count, Vec3 look) {
        Integer[] order = {0, 1, 2};
        Arrays.sort(order, (a, b) -> {
            int compare = Integer.compare(Math.abs(delta[b]), Math.abs(delta[a]));
            if (compare != 0) {
                return compare;
            }
            double lookA = look == null ? 0.0D : Math.abs(component(look, a));
            double lookB = look == null ? 0.0D : Math.abs(component(look, b));
            compare = Double.compare(lookB, lookA);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(a, b);
        });
        int[] axes = new int[Math.min(count, 3)];
        for (int i = 0; i < axes.length; i++) {
            axes[i] = order[i];
        }
        return axes;
    }

    private static double component(Vec3 vec, int axis) {
        return switch (axis) {
            case 0 -> vec.x;
            case 1 -> vec.y;
            default -> vec.z;
        };
    }

    /**
     * 计算要放置的方块位置。
     *
     * @param first  第一个点（点到点时是一个角；中心到点时是中心）
     * @param second 第二个点（已经吸附过；相对 first 的偏移量决定范围大小）
     * @return 位置列表；范围过大或没有位置时返回空列表
     */
    public static List<BlockPos> positions(BlockPos first, BlockPos second, BuildPreset preset) {
        if (preset.dimension() == BuildPreset.DimensionKind.ONE) {
            List<BlockPos> line = new ArrayList<>();
            BlockPos from = first;
            BlockPos to = second;
            if (preset.operation() == BuildPreset.Operation.CENTER_TO_POINT) {
                from = first.offset(first.getX() - second.getX(), first.getY() - second.getY(),
                        first.getZ() - second.getZ());
            }
            addLine(line, from, to);
            return line.size() > MAX_VOLUME ? List.of() : line;
        }

        int[] delta = {second.getX() - first.getX(), second.getY() - first.getY(), second.getZ() - first.getZ()};
        int axisCount = preset.dimension().axisCount();
        int[] axes = new int[axisCount];
        int found = 0;
        for (int i = 0; i < 3 && found < axisCount; i++) {
            if (delta[i] != 0) {
                axes[found++] = i;
            }
        }
        for (int i = 0; i < 3 && found < axisCount; i++) {
            boolean contains = false;
            for (int j = 0; j < found; j++) {
                if (axes[j] == i) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                axes[found++] = i;
            }
        }

        int[] firstCoord = {first.getX(), first.getY(), first.getZ()};
        int[] lo = firstCoord.clone();
        int[] hi = firstCoord.clone();

        if (preset.shape() == BuildPreset.ShapeKind.CIRCLE) {
            // 圆形/球形：把范围补齐成正方形/立方体，内切圆/球才是正的
            int size = 0;
            for (int axis : axes) {
                size = Math.max(size, Math.abs(delta[axis]));
            }
            if (size <= 0) {
                size = 1;
            }
            for (int axis : axes) {
                int sign = Integer.signum(delta[axis]);
                if (sign == 0) {
                    sign = 1;
                }
                if (preset.operation() == BuildPreset.Operation.CENTER_TO_POINT) {
                    lo[axis] = firstCoord[axis] - size;
                    hi[axis] = firstCoord[axis] + size;
                } else {
                    int other = firstCoord[axis] + sign * size;
                    lo[axis] = Math.min(firstCoord[axis], other);
                    hi[axis] = Math.max(firstCoord[axis], other);
                }
            }
        } else {
            // 方形：允许长方形/长方体，直接用两个点在各个轴上的偏移
            for (int axis : axes) {
                int offset = delta[axis];
                if (preset.operation() == BuildPreset.Operation.CENTER_TO_POINT) {
                    lo[axis] = firstCoord[axis] - Math.abs(offset);
                    hi[axis] = firstCoord[axis] + Math.abs(offset);
                } else {
                    int other = firstCoord[axis] + offset;
                    lo[axis] = Math.min(firstCoord[axis], other);
                    hi[axis] = Math.max(firstCoord[axis], other);
                }
            }
        }

        long volume = 1L;
        for (int i = 0; i < 3; i++) {
            volume *= (long) (hi[i] - lo[i] + 1);
            if (volume > MAX_VOLUME) {
                return List.of();
            }
        }

        Set<BlockPos> solid = new LinkedHashSet<>();
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int y = lo[1]; y <= hi[1]; y++) {
                for (int z = lo[2]; z <= hi[2]; z++) {
                    if (preset.shape() == BuildPreset.ShapeKind.CIRCLE
                            && !insideCircle(new int[]{x, y, z}, lo, hi, axes)) {
                        continue;
                    }
                    solid.add(new BlockPos(x, y, z));
                }
            }
        }

        if (preset.fill() == BuildPreset.Fill.SOLID) {
            return new ArrayList<>(solid);
        }
        if (preset.fill() == BuildPreset.Fill.FRAME && axes.length >= 3) {
            return frame(solid, lo, hi, axes);
        }
        return boundary(solid, axes);
    }


    /** 一条直线上的所有格子（支持 45° 正斜线）。 */
    private static void addLine(List<BlockPos> out, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps == 0) {
            out.add(from);
            return;
        }
        if (steps > MAX_VOLUME) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            out.add(new BlockPos(
                    (int) Math.round(from.getX() + dx * t),
                    (int) Math.round(from.getY() + dy * t),
                    (int) Math.round(from.getZ() + dz * t)));
        }
    }

    /** 圆形/球形：以范围中心为圆心，半径取范围的一半。 */
    private static boolean insideCircle(int[] pos, int[] lo, int[] hi, int[] axes) {
        double norm = 0.0D;
        for (int axis : axes) {
            double center = (lo[axis] + hi[axis] + 1) / 2.0D;
            double radius = (hi[axis] - lo[axis] + 1) / 2.0D;
            double offset = pos[axis] + 0.5D - center;
            if (radius <= 0.0D) {
                if (Math.abs(offset) > 1.0E-6D) {
                    return false;
                }
                continue;
            }
            norm += (offset / radius) * (offset / radius);
        }
        return norm <= 1.0D + 1.0E-6D;
    }

    /** 空心：只保留“有一个相邻格不在形状里”的表面格子。 */
    private static List<BlockPos> boundary(Set<BlockPos> solid, int[] axes) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : solid) {
            boolean edge = false;
            for (int axis : axes) {
                if (!solid.contains(offset(pos, axis, 1)) || !solid.contains(offset(pos, axis, -1))) {
                    edge = true;
                    break;
                }
            }
            if (edge) {
                out.add(pos);
            }
        }
        return out;
    }

    /** 框架：只保留立方体的 12 条棱（至少两个坐标在范围的边界上）。 */
    private static List<BlockPos> frame(Set<BlockPos> solid, int[] lo, int[] hi, int[] axes) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos pos : solid) {
            int atEdge = 0;
            for (int axis : axes) {
                int value = switch (axis) {
                    case 0 -> pos.getX();
                    case 1 -> pos.getY();
                    default -> pos.getZ();
                };
                if (value == lo[axis] || value == hi[axis]) {
                    atEdge++;
                }
            }
            if (atEdge >= 2) {
                out.add(pos);
            }
        }
        return out;
    }

    private static BlockPos offset(BlockPos pos, int axis, int amount) {
        return switch (axis) {
            case 0 -> pos.offset(amount, 0, 0);
            case 1 -> pos.offset(0, amount, 0);
            default -> pos.offset(0, 0, amount);
        };
    }
}

