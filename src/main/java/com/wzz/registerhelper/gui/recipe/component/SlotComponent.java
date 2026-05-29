package com.wzz.registerhelper.gui.recipe.component;

/**
 * 物品槽位组件
 */
public class SlotComponent extends RecipeComponent {
    private final int slotIndex;
    private String label;
    private double probability = 1.0;
    private boolean bulkSlot = false;
    
    public SlotComponent(int x, int y, String id, int slotIndex) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
        this.label = "";
    }
    
    public SlotComponent(int x, int y, String id, int slotIndex, String label) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
        this.label = label != null ? label : "";
    }
    
    public SlotComponent(int x, int y, String id, int slotIndex, String label, boolean bulkSlot) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
        this.label = label != null ? label : "";
        this.bulkSlot = bulkSlot;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.SLOT;
    }

    public int getSlotIndex() { return slotIndex; }
    
    public String getLabel() { return label; }
    
    public void setLabel(String label) { this.label = label != null ? label : ""; }
    
    public double getProbability() { return probability; }
    
    public void setProbability(double probability) { 
        this.probability = Math.max(0.0, Math.min(1.0, probability));
    }
    
    public boolean hasLabel() { return label != null && !label.isEmpty(); }
    
    public boolean isBulkSlot() { return bulkSlot; }
    
    public void setBulkSlot(boolean bulkSlot) { this.bulkSlot = bulkSlot; }
}