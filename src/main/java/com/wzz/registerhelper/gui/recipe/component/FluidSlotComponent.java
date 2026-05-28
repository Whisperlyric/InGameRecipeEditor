package com.wzz.registerhelper.gui.recipe.component;

public class FluidSlotComponent extends RecipeComponent {
    private final int slotIndex;
    private String fluidId;
    private long amount;
    private final long maxAmount;
    
    public FluidSlotComponent(int x, int y, String id, int slotIndex, String defaultFluid, long defaultAmount, long maxAmount) {
        super(x, y, 16, 58, id);
        this.slotIndex = slotIndex;
        this.fluidId = defaultFluid;
        this.amount = defaultAmount;
        this.maxAmount = maxAmount;
    }
    
    public FluidSlotComponent(int x, int y, String id, int slotIndex) {
        this(x, y, id, slotIndex, "minecraft:water", 1000, 10000);
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
}
