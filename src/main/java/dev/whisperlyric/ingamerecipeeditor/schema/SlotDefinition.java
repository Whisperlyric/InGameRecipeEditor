package dev.whisperlyric.ingamerecipeeditor.schema;

/**
 * 槽位定义对象
 * 定义单个槽位的完整信息，包括槽位ID、类型、位置、JSON映射等
 */
public class SlotDefinition {
    
    /**
     * 槽位角色类型
     */
    public enum SlotRole {
        INPUT,          // 输入槽位（对应JSON字段）
        OUTPUT,         // 输出槽位（对应JSON字段）
        CATALYST,       // 催化剂槽位（可选，可能不在JSON中）
        RENDER_ONLY,    // 仅渲染槽位（不在JSON中，如能量槽）
        EXTRA           // 额外槽位（可选输入）
    }
    
    /**
     * 成分类型
     */
    public enum IngredientType {
        ITEM,           // 物品
        FLUID,          // 流体
        GAS,            // 气体（Mekanism）
        INFUSE_TYPE,    // 灌注类型（Mekanism）
        PIGMENT,        // 颜料（Mekanism）
        SLURRY,         // 污泥（Mekanism）
        ENERGY,         // 能量
        ANY             // 任意化学品（Mekanism）
    }
    
    private final String id;               // 槽位ID（如"input_0", "fluid_input_1"）
    private final int index;               // 槽位索引（在配方中的位置）
    private final SlotRole role;           // 槽位角色
    private final IngredientType type;     // 成分类型
    private final boolean required;        // 是否必需
    private final String jsonField;        // JSON字段路径（如"/input/ingredient"）
    private final String[] jsonPaths;      // 多个JSON字段路径（用于支持item/tag两种格式）
    private final String amountPath;       // 数量路径（如"/input/count"）
    private final String chemicalTypePath; // 化学品类型路径（用于merged_chemical类型）
    private final int x;                   // 可视化位置X
    private final int y;                   // 可视化位置Y
    private final int width;               // 槽位宽度
    private final int height;              // 槽位高度
    private final int amountScale;         // 显示数量与JSON数量之间的比例（默认1）
    
    /**
     * 构造方法
     */
    public SlotDefinition(String id, int index, SlotRole role, IngredientType type, 
                          boolean required, String jsonField, String[] jsonPaths,
                          String amountPath, String chemicalTypePath,
                          int x, int y, int width, int height, int amountScale) {
        this.id = id;
        this.index = index;
        this.role = role;
        this.type = type;
        this.required = required;
        this.jsonField = jsonField;
        this.jsonPaths = jsonPaths;
        this.amountPath = amountPath;
        this.chemicalTypePath = chemicalTypePath;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.amountScale = Math.max(1, amountScale);
    }
    
    /**
     * 简化构造方法（默认槽位大小18x18）
     */
    public SlotDefinition(String id, int index, SlotRole role, IngredientType type,
                          boolean required, String jsonField, int x, int y) {
        this(id, index, role, type, required, jsonField, null, null, null, x, y, 18, 18, 1);
    }
    
    // ========== Getter方法 ==========
    
    public String getId() { return id; }
    public int getIndex() { return index; }
    public SlotRole getRole() { return role; }
    public IngredientType getType() { return type; }
    public boolean isRequired() { return required; }
    public String getJsonField() { return jsonField; }
    public String[] getJsonPaths() { return jsonPaths; }
    public String getAmountPath() { return amountPath; }
    public String getChemicalTypePath() { return chemicalTypePath; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getAmountScale() { return amountScale; }
    
    /**
     * 获取主要JSON路径（优先返回jsonField，否则返回jsonPaths的第一个）
     */
    public String getPrimaryJsonPath() {
        if (jsonField != null && !jsonField.isEmpty()) {
            return jsonField;
        }
        if (jsonPaths != null && jsonPaths.length > 0) {
            return jsonPaths[0];
        }
        return null;
    }
    
    /**
     * 是否有多个JSON路径
     */
    public boolean hasMultiplePaths() {
        return jsonPaths != null && jsonPaths.length > 1;
    }
    
    /**
     * 是否有数量路径
     */
    public boolean hasAmountPath() {
        return amountPath != null && !amountPath.isEmpty();
    }
    
    /**
     * 是否有化学品类型路径
     */
    public boolean hasChemicalTypePath() {
        return chemicalTypePath != null && !chemicalTypePath.isEmpty();
    }
    
    // ========== 类型判断方法 ==========
    
    public boolean isInputSlot() { return role == SlotRole.INPUT || role == SlotRole.EXTRA; }
    public boolean isOutputSlot() { return role == SlotRole.OUTPUT; }
    public boolean isRenderOnly() { return role == SlotRole.RENDER_ONLY; }
    public boolean isInJson() { 
        return (jsonField != null && !jsonField.isEmpty()) || 
               (jsonPaths != null && jsonPaths.length > 0); 
    }
    
    public boolean isItemSlot() { return type == IngredientType.ITEM; }
    public boolean isFluidSlot() { return type == IngredientType.FLUID; }
    public boolean isGasSlot() { return type == IngredientType.GAS; }
    public boolean isChemicalSlot() {
        return type == IngredientType.GAS || type == IngredientType.INFUSE_TYPE ||
               type == IngredientType.PIGMENT || type == IngredientType.SLURRY ||
               type == IngredientType.ANY;
    }
    
    // ========== Builder模式 ==========
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private int index;
        private SlotRole role;
        private IngredientType type;
        private boolean required = true;
        private String jsonField;
        private String[] jsonPaths;
        private String amountPath;
        private String chemicalTypePath;
        private int x;
        private int y;
        private int width = 18;
        private int height = 18;
        private int amountScale = 1;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder index(int index) { this.index = index; return this; }
        public Builder role(SlotRole role) { this.role = role; return this; }
        public Builder type(IngredientType type) { this.type = type; return this; }
        public Builder required(boolean required) { this.required = required; return this; }
        public Builder jsonField(String jsonField) { this.jsonField = jsonField; return this; }
        public Builder jsonPaths(String[] jsonPaths) { this.jsonPaths = jsonPaths; return this; }
        public Builder amountPath(String amountPath) { this.amountPath = amountPath; return this; }
        public Builder chemicalTypePath(String chemicalTypePath) { this.chemicalTypePath = chemicalTypePath; return this; }
        public Builder position(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        /**
         * 设置数量显示比例（显示值 -> JSON值的转换比例），默认为1
         */
        public Builder amountScale(int scale) { this.amountScale = Math.max(1, scale); return this; }
        
        /**
         * 创建输入槽位
         */
        public Builder inputSlot(String id, int index, IngredientType type, String jsonField, int x, int y) {
            this.id = id;
            this.index = index;
            this.role = SlotRole.INPUT;
            this.type = type;
            this.required = true;
            this.jsonField = jsonField;
            this.x = x;
            this.y = y;
            return this;
        }
        
        /**
         * 创建输出槽位
         */
        public Builder outputSlot(String id, int index, IngredientType type, String jsonField, int x, int y) {
            this.id = id;
            this.index = index;
            this.role = SlotRole.OUTPUT;
            this.type = type;
            this.required = true;
            this.jsonField = jsonField;
            this.x = x;
            this.y = y;
            return this;
        }
        
        /**
         * 创建仅渲染槽位（不在JSON中）
         */
        public Builder renderOnlySlot(String id, int x, int y) {
            this.id = id;
            this.role = SlotRole.RENDER_ONLY;
            this.jsonField = null;
            this.jsonPaths = null;
            this.required = false;
            this.x = x;
            this.y = y;
            return this;
        }
        
        public SlotDefinition build() {
            return new SlotDefinition(id, index, role, type, required, jsonField, jsonPaths,
                                      amountPath, chemicalTypePath, x, y, width, height, amountScale);
        }
    }
}