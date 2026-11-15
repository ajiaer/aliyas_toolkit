package com.prism.aliyastoolkit.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * 蓝图箱的流体储罐（标准 mB 单位，1 桶 = 1000 mB）。
 * <p>
 * 同一时间可以存多种流体，总量不超过储罐容量；写入顺序即显示顺序，
 * 这样界面上同一批流体的分段位置保持不变。
 */
public final class FluidTank {
    /** 一个桶对应的流体量（mB）。 */
    public static final int BUCKET_VOLUME = 1000;
    private static final String TAG_FLUIDS = "Fluids";
    private static final String TAG_FLUID = "Fluid";
    private static final String TAG_AMOUNT = "Amount";

    private final int capacity;
    private final Map<Fluid, Integer> contents = new LinkedHashMap<>();

    public FluidTank(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    public static FluidTank empty(int capacity) {
        return new FluidTank(capacity);
    }

    public int capacity() {
        return this.capacity;
    }

    public boolean isEmpty() {
        return this.contents.isEmpty();
    }

    public Map<Fluid, Integer> contents() {
        return Collections.unmodifiableMap(this.contents);
    }

    public int getAmount(Fluid fluid) {
        return this.contents.getOrDefault(fluid, 0);
    }

    public int getTotalAmount() {
        int total = 0;
        for (int amount : this.contents.values()) {
            total += amount;
        }
        return total;
    }

    public int getFreeSpace() {
        return Math.max(0, this.capacity - this.getTotalAmount());
    }

    /** 可以接受多少（用于按容量截断）。 */
    public int getAcceptable(Fluid fluid, int amount) {
        return Math.min(Math.max(0, amount), this.getFreeSpace());
    }

    /** 存入流体，返回实际存入量。 */
    public int fill(Fluid fluid, int amount) {
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) {
            return 0;
        }
        int accepted = this.getAcceptable(fluid, amount);
        if (accepted > 0) {
            this.contents.merge(fluid, accepted, Integer::sum);
        }
        return accepted;
    }

    /** 取出流体，返回实际取出量。 */
    public int drain(Fluid fluid, int amount) {
        int stored = this.getAmount(fluid);
        int drained = Math.min(Math.max(0, amount), stored);
        if (drained <= 0) {
            return 0;
        }
        if (drained >= stored) {
            this.contents.remove(fluid);
        } else {
            this.contents.put(fluid, stored - drained);
        }
        return drained;
    }

    public void clear() {
        this.contents.clear();
    }

    /** 按容量截断超出部分（容量变小后读取旧数据时使用）。 */
    public void clampToCapacity() {
        int remaining = this.capacity;
        List<Fluid> order = new ArrayList<>(this.contents.keySet());
        for (Fluid fluid : order) {
            int amount = this.contents.get(fluid);
            int keep = Math.min(amount, remaining);
            remaining -= keep;
            if (keep <= 0) {
                this.contents.remove(fluid);
            } else {
                this.contents.put(fluid, keep);
            }
        }
    }

    /** 是否满足这些流体需求（每种流体都有足够的量）。 */
    public boolean containsAll(List<BlueprintData.FluidRequirement> requirements) {
        for (BlueprintData.FluidRequirement requirement : requirements) {
            if (this.getAmount(requirement.fluid()) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    /** 按需求扣除流体（调用前应先确认 {@link #containsAll}）。 */
    public void consume(List<BlueprintData.FluidRequirement> requirements) {
        for (BlueprintData.FluidRequirement requirement : requirements) {
            this.drain(requirement.fluid(), requirement.amount());
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<Fluid, Integer> entry : this.contents.entrySet()) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(entry.getKey());
            if (id == null || entry.getValue() <= 0) {
                continue;
            }
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString(TAG_FLUID, id.toString());
            fluidTag.putInt(TAG_AMOUNT, entry.getValue());
            list.add(fluidTag);
        }
        tag.put(TAG_FLUIDS, list);
        return tag;
    }

    public static FluidTank load(CompoundTag tag, int capacity) {
        FluidTank tank = new FluidTank(capacity);
        if (tag == null) {
            return tank;
        }
        ListTag list = tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag fluidTag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(fluidTag.getString(TAG_FLUID));
            if (id == null) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            if (fluid == null || fluid == Fluids.EMPTY) {
                continue;
            }
            tank.fill(fluid, fluidTag.getInt(TAG_AMOUNT));
        }
        tank.clampToCapacity();
        return tank;
    }
}
