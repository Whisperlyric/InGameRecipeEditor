package com.wzz.registerhelper.gui.recipe.component;

public class ChemicalSlotComponent extends RecipeComponent {
    public enum ChemicalType {
        GAS("gas", "气体"),
        SLURRY("slurry", "污泥"),
        PIGMENT("pigment", "颜料"),
        INFUSE_TYPE("infuse_type", "灌注类型");
        
        private final String id;
        private final String displayName;
        
        ChemicalType(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        
        public static ChemicalType fromId(String id) {
            for (ChemicalType type : values()) {
                if (type.id.equals(id)) return type;
            }
            return GAS;
        }
    }
    
    private final int slotIndex;
    private final ChemicalType chemicalType;
    private String chemicalId;
    private long amount;
    private final long maxAmount;
    
    public ChemicalSlotComponent(int x, int y, String id, int slotIndex, ChemicalType chemicalType, String defaultChemical, long defaultAmount, long maxAmount) {
        super(x, y, 16, 58, id);
        this.slotIndex = slotIndex;
        this.chemicalType = chemicalType;
        this.chemicalId = defaultChemical;
        this.amount = defaultAmount;
        this.maxAmount = maxAmount;
    }
    
    public ChemicalSlotComponent(int x, int y, String id, int slotIndex, ChemicalType chemicalType) {
        this(x, y, id, slotIndex, chemicalType, "mekanism:hydrogen", 100, 10000);
    }
    
    @Override
    public ComponentType getType() {
        return switch (chemicalType) {
            case GAS -> ComponentType.GAS_SLOT;
            case SLURRY -> ComponentType.SLURRY_SLOT;
            case PIGMENT -> ComponentType.PIGMENT_SLOT;
            case INFUSE_TYPE -> ComponentType.INFUSE_TYPE_SLOT;
        };
    }
    
    public int getSlotIndex() { return slotIndex; }
    public ChemicalType getChemicalType() { return chemicalType; }
    public String getChemicalId() { return chemicalId; }
    public void setChemicalId(String chemicalId) { this.chemicalId = chemicalId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = Math.max(0, Math.min(maxAmount, amount)); }
    public long getMaxAmount() { return maxAmount; }
    public boolean isEmpty() { return chemicalId.isEmpty() || amount <= 0; }
}
