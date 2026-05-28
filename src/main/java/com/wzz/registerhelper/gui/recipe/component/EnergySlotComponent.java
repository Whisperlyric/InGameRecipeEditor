package com.wzz.registerhelper.gui.recipe.component;

import java.awt.*;

public class EnergySlotComponent extends RecipeComponent {
    private final int slotIndex;
    private long energy;
    private long maxEnergy;
    
    public EnergySlotComponent(int x, int y, String id, int slotIndex, long defaultEnergy, long maxEnergy) {
        super(x, y, 18, 18, id);
        this.slotIndex = slotIndex;
        this.energy = defaultEnergy;
        this.maxEnergy = maxEnergy;
    }
    
    @Override
    public ComponentType getType() {
        return ComponentType.ENERGY_SLOT;
    }
    
    public int getSlotIndex() {
        return slotIndex;
    }
    
    public long getEnergy() {
        return energy;
    }
    
    public void setEnergy(long energy) {
        this.energy = Math.max(0, Math.min(maxEnergy, energy));
    }
    
    public long getMaxEnergy() {
        return maxEnergy;
    }
    
    public void setMaxEnergy(long maxEnergy) {
        this.maxEnergy = maxEnergy;
    }
    
    public double getEnergyRatio() {
        return maxEnergy > 0 ? (double) energy / maxEnergy : 0;
    }
    
    public long getEnergyInFE() {
        return (long)(energy * 0.4);
    }
    
    public long getMaxEnergyInFE() {
        return (long)(maxEnergy * 0.4);
    }
}
