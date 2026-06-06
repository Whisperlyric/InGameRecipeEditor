package com.wzz.registerhelper.gui.recipe.component;

public class FluidSlotComponent extends RecipeComponent {
    private final int slotIndex;
    private String fluidId;
    private long amount;
    private final long maxAmount;
    private final boolean useSmallSlot;  // 是否使用小槽（16x28）
    
    // 小槽尺寸常量
    public static final int SMALL_WIDTH = 16;
    public static final int SMALL_HEIGHT = 28;
    // 标准槽尺寸常量
    public static final int STANDARD_WIDTH = 16;
    public static final int STANDARD_HEIGHT = 58;
    
    public FluidSlotComponent(int x, int y, String id, int slotIndex, String defaultFluid, long defaultAmount, long maxAmount, boolean useSmallSlot) {
        super(x, y, useSmallSlot ? SMALL_WIDTH : STANDARD_WIDTH, useSmallSlot ? SMALL_HEIGHT : STANDARD_HEIGHT, id);
        this.slotIndex = slotIndex;
        this.fluidId = defaultFluid;
        this.amount = defaultAmount;
        this.maxAmount = maxAmount;
        this.useSmallSlot = useSmallSlot;
    }
    
    public FluidSlotComponent(int x, int y, String id, int slotIndex, String defaultFluid, long defaultAmount, long maxAmount) {
        this(x, y, id, slotIndex, defaultFluid, defaultAmount, maxAmount, false);
    }
    
    public FluidSlotComponent(int x, int y, String id, int slotIndex) {
        this(x, y, id, slotIndex, "minecraft:water", 1000, 10000, false);
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.FLUID_SLOT;
    }
    
    public int getSlotIndex() { return slotIndex; }
    public String getFluidId() { return fluidId; }
    public void setFluidId(String fluidId) { this.fluidId = fluidId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = Math.max(0, Math.min(maxAmount, amount)); }
    public long getMaxAmount() { return maxAmount; }
    public boolean isEmpty() { return fluidId.isEmpty() || amount <= 0; }
    public boolean isUseSmallSlot() { return useSmallSlot; }
}
