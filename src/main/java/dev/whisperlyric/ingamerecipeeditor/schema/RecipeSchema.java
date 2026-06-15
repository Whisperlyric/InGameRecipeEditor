package dev.whisperlyric.ingamerecipeeditor.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配方契约对象
 * 定义特定配方类型的数据结构契约，规定槽位数量、类型、位置及与JSON字段的映射关系
 */
public class RecipeSchema {
    
    private final String recipeType;                  // 配方类型（如"mekanism:crushing"）
    private final String displayName;                 // 显示名称
    private final int width;                          // 配方界面宽度
    private final int height;                         // 配方界面高度
    private final List<SlotDefinition> inputSlots;    // 输入槽位定义列表
    private final List<SlotDefinition> outputSlots;   // 输出槽位定义列表
    private final List<SlotDefinition> renderOnlySlots; // 仅渲染槽位列表（不在JSON中）
    private final List<PropertyDefinition> properties; // 属性定义列表
    
    /**
     * 构造方法
     */
    public RecipeSchema(String recipeType, String displayName, int width, int height,
                         List<SlotDefinition> inputSlots, List<SlotDefinition> outputSlots,
                         List<SlotDefinition> renderOnlySlots, List<PropertyDefinition> properties) {
        this.recipeType = recipeType;
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.inputSlots = inputSlots != null ? new ArrayList<>(inputSlots) : new ArrayList<>();
        this.outputSlots = outputSlots != null ? new ArrayList<>(outputSlots) : new ArrayList<>();
        this.renderOnlySlots = renderOnlySlots != null ? new ArrayList<>(renderOnlySlots) : new ArrayList<>();
        this.properties = properties != null ? new ArrayList<>(properties) : new ArrayList<>();
    }
    
    // ========== Getter方法 ==========
    
    public String getRecipeType() { return recipeType; }
    public String getDisplayName() { return displayName; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    
    public List<SlotDefinition> getInputSlots() { return Collections.unmodifiableList(inputSlots); }
    public List<SlotDefinition> getOutputSlots() { return Collections.unmodifiableList(outputSlots); }
    public List<SlotDefinition> getRenderOnlySlots() { return Collections.unmodifiableList(renderOnlySlots); }
    public List<PropertyDefinition> getProperties() { return Collections.unmodifiableList(properties); }
    
    /**
     * 获取所有槽位（包括输入、输出、仅渲染）
     */
    public List<SlotDefinition> getAllSlots() {
        List<SlotDefinition> allSlots = new ArrayList<>();
        allSlots.addAll(inputSlots);
        allSlots.addAll(outputSlots);
        allSlots.addAll(renderOnlySlots);
        return allSlots;
    }
    
    /**
     * 获取所有JSON槽位（不包括仅渲染槽位）
     */
    public List<SlotDefinition> getJsonSlots() {
        List<SlotDefinition> jsonSlots = new ArrayList<>();
        for (SlotDefinition slot : getAllSlots()) {
            if (slot.isInJson()) {
                jsonSlots.add(slot);
            }
        }
        return jsonSlots;
    }
    
    // ========== 数量统计方法 ==========
    
    public int getInputSlotCount() { return inputSlots.size(); }
    public int getOutputSlotCount() { return outputSlots.size(); }
    public int getRenderOnlySlotCount() { return renderOnlySlots.size(); }
    public int getTotalSlotCount() { return getAllSlots().size(); }
    public int getPropertyCount() { return properties.size(); }
    
    // ========== 查找方法 ==========
    
    /**
     * 根据ID查找槽位
     */
    public SlotDefinition getSlotById(String id) {
        for (SlotDefinition slot : getAllSlots()) {
            if (slot.getId().equals(id)) {
                return slot;
            }
        }
        return null;
    }
    
    /**
     * 根据JSON字段名查找槽位
     */
    public SlotDefinition getSlotByJsonField(String jsonField) {
        for (SlotDefinition slot : getAllSlots()) {
            if (jsonField != null && jsonField.equals(slot.getJsonField())) {
                return slot;
            }
        }
        return null;
    }
    
    /**
     * 根据索引查找输入槽位
     */
    public SlotDefinition getInputSlotByIndex(int index) {
        if (index >= 0 && index < inputSlots.size()) {
            return inputSlots.get(index);
        }
        return null;
    }
    
    /**
     * 根据索引查找输出槽位
     */
    public SlotDefinition getOutputSlotByIndex(int index) {
        if (index >= 0 && index < outputSlots.size()) {
            return outputSlots.get(index);
        }
        return null;
    }
    
    /**
     * 根据ID查找属性
     */
    public PropertyDefinition getPropertyById(String id) {
        for (PropertyDefinition prop : properties) {
            if (prop.getId().equals(id)) {
                return prop;
            }
        }
        return null;
    }
    
    /**
     * 根据JSON字段名查找属性
     */
    public PropertyDefinition getPropertyByJsonField(String jsonField) {
        for (PropertyDefinition prop : properties) {
            if (jsonField != null && jsonField.equals(prop.getJsonField())) {
                return prop;
            }
        }
        return null;
    }
    
    // ========== Builder模式 ==========
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String recipeType;
        private String displayName;
        private int width = 150;
        private int height = 60;
        private final List<SlotDefinition> inputSlots = new ArrayList<>();
        private final List<SlotDefinition> outputSlots = new ArrayList<>();
        private final List<SlotDefinition> renderOnlySlots = new ArrayList<>();
        private final List<PropertyDefinition> properties = new ArrayList<>();
        
        public Builder recipeType(String recipeType) { this.recipeType = recipeType; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        
        /**
         * 添加输入槽位
         */
        public Builder addInputSlot(SlotDefinition slot) {
            inputSlots.add(slot);
            return this;
        }
        
        /**
         * 快速添加物品输入槽位
         */
        public Builder addInputSlot(String id, int index, String jsonField, int x, int y) {
            inputSlots.add(SlotDefinition.builder()
                .inputSlot(id, index, SlotDefinition.IngredientType.ITEM, jsonField, x, y)
                .build());
            return this;
        }
        
        /**
         * 快速添加化学品输入槽位
         */
        public Builder addChemicalInputSlot(String id, int index, SlotDefinition.IngredientType type, 
                                             String jsonField, int x, int y) {
            inputSlots.add(SlotDefinition.builder()
                .inputSlot(id, index, type, jsonField, x, y)
                .build());
            return this;
        }
        
        /**
         * 添加输出槽位
         */
        public Builder addOutputSlot(SlotDefinition slot) {
            outputSlots.add(slot);
            return this;
        }
        
        /**
         * 快速添加物品输出槽位
         */
        public Builder addOutputSlot(String id, int index, String jsonField, int x, int y) {
            outputSlots.add(SlotDefinition.builder()
                .outputSlot(id, index, SlotDefinition.IngredientType.ITEM, jsonField, x, y)
                .build());
            return this;
        }
        
        /**
         * 快速添加化学品输出槽位
         */
        public Builder addChemicalOutputSlot(String id, int index, SlotDefinition.IngredientType type,
                                              String jsonField, int x, int y) {
            outputSlots.add(SlotDefinition.builder()
                .outputSlot(id, index, type, jsonField, x, y)
                .build());
            return this;
        }
        
        /**
         * 添加仅渲染槽位（不在JSON中）
         */
        public Builder addRenderOnlySlot(SlotDefinition slot) {
            renderOnlySlots.add(slot);
            return this;
        }
        
        /**
         * 快速添加能量槽位（仅渲染）
         */
        public Builder addEnergySlot(String id, int x, int y) {
            renderOnlySlots.add(SlotDefinition.builder()
                .renderOnlySlot(id, x, y)
                .type(SlotDefinition.IngredientType.ENERGY)
                .build());
            return this;
        }
        
        /**
         * 添加属性
         */
        public Builder addProperty(PropertyDefinition property) {
            properties.add(property);
            return this;
        }
        
        /**
         * 快速添加整数属性
         */
        public Builder addIntProperty(String id, String jsonField, int defaultValue, boolean required) {
            properties.add(PropertyDefinition.builder()
                .intProperty(id, jsonField, defaultValue, required)
                .build());
            return this;
        }
        
        /**
         * 快速添加浮点属性
         */
        public Builder addFloatProperty(String id, String jsonField, float defaultValue, boolean required) {
            properties.add(PropertyDefinition.builder()
                .floatProperty(id, jsonField, defaultValue, required)
                .build());
            return this;
        }
        
        public RecipeSchema build() {
            return new RecipeSchema(recipeType, displayName, width, height, 
                                    inputSlots, outputSlots, renderOnlySlots, properties);
        }
    }
}