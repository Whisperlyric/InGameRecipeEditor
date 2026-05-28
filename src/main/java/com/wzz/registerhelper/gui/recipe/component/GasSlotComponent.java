package com.wzz.registerhelper.gui.recipe.component;

public class GasSlotComponent extends RecipeComponent {
    private final int slotIndex;
    private String gasId;
    private int amount;
    private final boolean gasOnly;
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount) {
        this(x, y, id, slotIndex, defaultGas, defaultAmount, false);
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex, String defaultGas, int defaultAmount, boolean gasOnly) {
        super(x, y, 16, 58, id);
        this.slotIndex = slotIndex;
        this.gasId = defaultGas;
        this.amount = defaultAmount;
        this.gasOnly = gasOnly;
    }
    
    public GasSlotComponent(int x, int y, String id, int slotIndex) {
        this(x, y, id, slotIndex, null, 0, false);
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.GAS_SLOT;
    }
    
    public int getSlotIndex() { return slotIndex; }
    public String getGasId() { return gasId; }
    public void setGasId(String gasId) { this.gasId = gasId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public boolean isGasOnly() { return gasOnly; }
}
