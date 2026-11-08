package com.prism.aliyastoolkit;

/**
 * “蓝图建造”的共用常量（双端共用）。
 * <p>
 * 客户端用它限制一次能处理的方块数量、提示距离，服务端用同一套数值校验请求，
 * 保证客户端预览与实际操作不会差太多。
 */
public final class CopyPaste {
    /** 一次最多剪切/复制多少个方块。 */
    public static final int MAX_VOLUME = 4096;

    /** 操作位置离玩家的最大距离（与快速建造一致）。 */
    public static final double MAX_DISTANCE = 40.0D;

    /** 绕 Y 轴旋转的 4 个方向（0 = 不旋转，每 +1 顺时针 90°）。 */
    public static final int ROTATIONS = 4;

    private CopyPaste() {
    }
}
