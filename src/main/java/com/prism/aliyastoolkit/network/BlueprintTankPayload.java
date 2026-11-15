package com.prism.aliyastoolkit.network;

import com.prism.aliyastoolkit.AliyasToolkit;
import com.prism.aliyastoolkit.InventorySync;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxReference;
import com.prism.aliyastoolkit.blueprint.BlueprintBoxView;
import com.prism.aliyastoolkit.blueprint.BlueprintData;
import com.prism.aliyastoolkit.blueprint.FluidTank;
import com.prism.aliyastoolkit.menu.BlueprintBoxMenu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：蓝图箱界面的储罐与手上的桶交互。
 * <ul>
 * <li>{@link #EXTRACT}：手上拿着空桶，点储罐里某种流体 → 取出 1 桶（1000 mB），空桶变成对应液体桶；</li>
 * <li>{@link #INSERT}：手上拿着液体桶，点储罐任意位置 → 存入 1 桶（需要足够空间），变成空桶。</li>
 * </ul>
 */
public record BlueprintTankPayload(BlueprintBoxReference reference, int action, String fluidId)
        implements CustomPacketPayload {
    public static final int EXTRACT = 0;
    public static final int INSERT = 1;

    public static final Type<BlueprintTankPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AliyasToolkit.MODID, "blueprint_tank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintTankPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlueprintBoxReference.STREAM_CODEC, BlueprintTankPayload::reference,
                    ByteBufCodecs.VAR_INT, BlueprintTankPayload::action,
                    ByteBufCodecs.STRING_UTF8, BlueprintTankPayload::fluidId,
                    BlueprintTankPayload::new);

    @Override
    public Type<BlueprintTankPayload> type() {
        return TYPE;
    }

    public static void handle(BlueprintTankPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (!(player.containerMenu instanceof BlueprintBoxMenu menu)) {
                return;
            }
            BlueprintBoxView box = menu.getBox();
            if (!menu.getReference().equals(payload.reference()) || !box.stillValid(player)) {
                return;
            }
            int capacity = box.getTankCapacity();
            if (capacity <= 0) {
                return;
            }
            ItemStack carried = menu.getCarried();
            if (carried.isEmpty()) {
                return;
            }
            if (payload.action() == EXTRACT) {
                extract(player, menu, box, carried, payload.fluidId());
            } else if (payload.action() == INSERT) {
                insert(player, menu, box, carried);
            }
        });
    }

    /** 空桶取液：按客户端点到的那一段流体取 1 桶。 */
    private static void extract(ServerPlayer player, BlueprintBoxMenu menu, BlueprintBoxView box,
            ItemStack carried, String fluidId) {
        if (!carried.is(Items.BUCKET)) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(fluidId));
        if (fluid == null || fluid == Fluids.EMPTY) {
            return;
        }
        Item bucket = fluid.getBucket();
        if (bucket == Items.AIR) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.tank.no_bucket"), true);
            return;
        }
        FluidTank tank = box.getTank();
        if (tank.getAmount(fluid) < FluidTank.BUCKET_VOLUME) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.tank.empty"), true);
            return;
        }
        tank.drain(fluid, FluidTank.BUCKET_VOLUME);
        swapCarried(player, menu, carried, new ItemStack(bucket));
        box.setChanged();
        box.syncToClient();
        menu.broadcastChanges();
        InventorySync.sync(player);
        player.displayClientMessage(Component.translatable(
                "message.aliyastoolkit.blueprint.tank.extracted", FluidTank.BUCKET_VOLUME), true);
    }

    /** 液体桶存入：任意位置点击，存入 1 桶；不能超过蓝图对该流体的需求。 */
    private static void insert(ServerPlayer player, BlueprintBoxMenu menu, BlueprintBoxView box,
            ItemStack carried) {
        FluidStack contained = FluidUtil.getFluidContained(carried).orElse(FluidStack.EMPTY);
        if (contained.isEmpty() || contained.getAmount() < FluidTank.BUCKET_VOLUME) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.tank.no_fluid"), true);
            return;
        }
        Fluid fluid = contained.getFluid();
        if (fluid == Fluids.EMPTY) {
            return;
        }
        int required = requiredAmount(box, fluid);
        if (required <= 0) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.tank.not_needed"), true);
            return;
        }
        FluidTank tank = box.getTank();
        int remaining = required - tank.getAmount(fluid);
        if (remaining < FluidTank.BUCKET_VOLUME) {
            player.displayClientMessage(Component.translatable(
                    "message.aliyastoolkit.blueprint.tank.limit", required), true);
            return;
        }
        if (tank.getFreeSpace() < FluidTank.BUCKET_VOLUME) {
            player.displayClientMessage(
                    Component.translatable("message.aliyastoolkit.blueprint.tank.full"), true);
            return;
        }
        tank.fill(fluid, FluidTank.BUCKET_VOLUME);
        swapCarried(player, menu, carried, new ItemStack(Items.BUCKET));
        box.setChanged();
        box.syncToClient();
        menu.broadcastChanges();
        InventorySync.sync(player);
        player.displayClientMessage(Component.translatable(
                "message.aliyastoolkit.blueprint.tank.inserted", FluidTank.BUCKET_VOLUME), true);
    }

    /** 蓝图对某种流体的需求量（mB）；没有蓝图或不需要该流体时为 0。 */
    private static int requiredAmount(BlueprintBoxView box, Fluid fluid) {
        BlueprintData blueprint = box.getBlueprint();
        if (blueprint == null) {
            return 0;
        }
        int required = 0;
        for (BlueprintData.FluidRequirement requirement : blueprint.fluidRequirements()) {
            if (requirement.fluid() == fluid) {
                required += requirement.amount();
            }
        }
        return required;
    }

    /** 把手上的物品换成结果物品：多个时只换 1 个，结果放回背包。 */
    private static void swapCarried(ServerPlayer player, BlueprintBoxMenu menu, ItemStack carried,
            ItemStack result) {
        if (carried.getCount() > 1) {
            carried.shrink(1);
            menu.setCarried(carried);
            if (!player.getInventory().add(result)) {
                player.drop(result, false);
            }
        } else {
            menu.setCarried(result);
        }
        player.getInventory().setChanged();
    }
}
