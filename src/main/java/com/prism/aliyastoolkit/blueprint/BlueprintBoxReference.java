package com.prism.aliyastoolkit.blueprint;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 蓝图箱界面/操作的网络定位：
 * <ul>
 * <li>{@code pos} 不为 null：世界里的蓝图箱方块实体；</li>
 * <li>{@code pos} 为 null：玩家物品栏里的蓝图箱物品（slot 为主物品栏槽位）。</li>
 * </ul>
 */
public record BlueprintBoxReference(@Nullable BlockPos pos, int slot) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintBoxReference> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, reference) -> {
                        buffer.writeBoolean(reference.isItem());
                        if (reference.isItem()) {
                            buffer.writeVarInt(reference.slot());
                        } else {
                            BlockPos.STREAM_CODEC.encode(buffer, reference.pos());
                        }
                    },
                    buffer -> buffer.readBoolean()
                            ? item(buffer.readVarInt())
                            : block(BlockPos.STREAM_CODEC.decode(buffer)));

    public static BlueprintBoxReference block(BlockPos pos) {
        return new BlueprintBoxReference(pos, -1);
    }

    public static BlueprintBoxReference item(int slot) {
        return new BlueprintBoxReference(null, slot);
    }

    public boolean isItem() {
        return this.pos == null;
    }
}
