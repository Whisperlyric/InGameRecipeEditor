package com.wzz.registerhelper.gui.recipe.component;

public class GasSlotComponent extends RecipeComponent {
    private final int slotIndex;
    private String gasId;
    private int amount;
    private final int maxAmount;
    private final boolean gasOnly;
    private final int displayDivisor;
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount) {
        this(x, y, id, slotIndex, defaultGas, defaultAmount, 10000, false, 1);
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount, boolean gasOnly) {
        this(x, y, id, slotIndex, defaultGas, defaultAmount, 10000, gasOnly, 1);
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount, int maxAmount, boolean gasOnly) {
        this(x, y, id, slotIndex, defaultGas, defaultAmount, maxAmount, gasOnly, 1);
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount, int maxAmount, boolean gasOnly, int displayDivisor) {
        super(x, y, 16, 58, id);
        this.slotIndex = slotIndex;
        this.gasId = defaultGas;
        this.amount = defaultAmount;
        this.maxAmount = maxAmount;
        this.gasOnly = gasOnly;
        this.displayDivisor = displayDivisor;
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex) {
        this(x, y, id, slotIndex, null, 0, 10000, false, 1);
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.GAS_SLOT;
    }
    
    public int getSlotIndex() { return slotIndex; }
    public String getGasId() { return gasId; }
    public void setGasId(String gasId) { this.gasId = gasId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = Math.max(0, Math.min(maxAmount, amount)); }
    public int getMaxAmount() { return maxAmount; }
    public boolean isGasOnly() { return gasOnly; }
    public int getDisplayDivisor() { return displayDivisor; }
    public int getDisplayAmount() { return amount / displayDivisor; }
}
