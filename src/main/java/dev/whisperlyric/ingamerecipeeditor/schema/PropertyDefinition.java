package dev.whisperlyric.ingamerecipeeditor.schema;

/**
 * 属性定义对象
 * 定义配方属性字段的信息，包括属性ID、JSON字段名、默认值等
 */
public class PropertyDefinition {
    
    /**
     * 属性类型
     */
    public enum PropertyType {
        INTEGER,        // 整数（如能量、时间）
        FLOAT,          // 浮点数（如经验值、概率）
        STRING,         // 字符串（如化学品类型）
        BOOLEAN         // 布尔值
    }
    
    private final String id;               // 属性ID（如"energy", "experience"）
    private final String jsonField;        // JSON字段名（如"energyRequired", "cookingtime"）
    private final PropertyType type;       // 属性类型
    private final Object defaultValue;     // 默认值
    private final boolean required;        // 是否必需
    private final String displayName;      // 显示名称（用于UI）
    private final int min;                 // 最小值（用于数值类型）
    private final int max;                 // 最大值（用于数值类型）
    
    /**
     * 构造方法
     */
    public PropertyDefinition(String id, String jsonField, PropertyType type,
                              Object defaultValue, boolean required, String displayName,
                              int min, int max) {
        this.id = id;
        this.jsonField = jsonField;
        this.type = type;
        this.defaultValue = defaultValue;
        this.required = required;
        this.displayName = displayName;
        this.min = min;
        this.max = max;
    }
    
    /**
     * 简化构造方法（整数类型，无范围限制）
     */
    public PropertyDefinition(String id, String jsonField, int defaultValue, boolean required) {
        this(id, jsonField, PropertyType.INTEGER, defaultValue, required, id, 0, Integer.MAX_VALUE);
    }
    
    /**
     * 简化构造方法（浮点类型）
     */
    public PropertyDefinition(String id, String jsonField, float defaultValue, boolean required) {
        this(id, jsonField, PropertyType.FLOAT, defaultValue, required, id, 0, Integer.MAX_VALUE);
    }
    
    // ========== Getter方法 ==========
    
    public String getId() { return id; }
    public String getJsonField() { return jsonField; }
    public PropertyType getType() { return type; }
    public Object getDefaultValue() { return defaultValue; }
    public boolean isRequired() { return required; }
    public String getDisplayName() { return displayName; }
    public int getMin() { return min; }
    public int getMax() { return max; }
    
    // ========== 类型判断方法 ==========
    
    public boolean isInteger() { return type == PropertyType.INTEGER; }
    public boolean isFloat() { return type == PropertyType.FLOAT; }
    public boolean isString() { return type == PropertyType.STRING; }
    public boolean isBoolean() { return type == PropertyType.BOOLEAN; }
    
    // ========== 获取默认值方法 ==========
    
    public int getDefaultInt() {
        if (defaultValue instanceof Number) {
            return ((Number) defaultValue).intValue();
        }
        return 0;
    }
    
    public float getDefaultFloat() {
        if (defaultValue instanceof Number) {
            return ((Number) defaultValue).floatValue();
        }
        return 0.0f;
    }
    
    public String getDefaultString() {
        if (defaultValue instanceof String) {
            return (String) defaultValue;
        }
        return "";
    }
    
    public boolean getDefaultBoolean() {
        if (defaultValue instanceof Boolean) {
            return (Boolean) defaultValue;
        }
        return false;
    }
    
    // ========== Builder模式 ==========
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String jsonField;
        private PropertyType type;
        private Object defaultValue;
        private boolean required = false;
        private String displayName;
        private int min = 0;
        private int max = Integer.MAX_VALUE;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder jsonField(String jsonField) { this.jsonField = jsonField; return this; }
        public Builder type(PropertyType type) { this.type = type; return this; }
        public Builder defaultValue(Object defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder required(boolean required) { this.required = required; return this; }
        public Builder displayName(String displayName) { this.displayName = displayName; return this; }
        public Builder range(int min, int max) { this.min = min; this.max = max; return this; }
        
        /**
         * 创建整数属性
         */
        public Builder intProperty(String id, String jsonField, int defaultValue, boolean required) {
            this.id = id;
            this.jsonField = jsonField;
            this.type = PropertyType.INTEGER;
            this.defaultValue = defaultValue;
            this.required = required;
            this.displayName = id;
            return this;
        }
        
        /**
         * 创建浮点属性
         */
        public Builder floatProperty(String id, String jsonField, float defaultValue, boolean required) {
            this.id = id;
            this.jsonField = jsonField;
            this.type = PropertyType.FLOAT;
            this.defaultValue = defaultValue;
            this.required = required;
            this.displayName = id;
            return this;
        }
        
        /**
         * 创建字符串属性
         */
        public Builder stringProperty(String id, String jsonField, String defaultValue, boolean required) {
            this.id = id;
            this.jsonField = jsonField;
            this.type = PropertyType.STRING;
            this.defaultValue = defaultValue;
            this.required = required;
            this.displayName = id;
            return this;
        }
        
        public PropertyDefinition build() {
            return new PropertyDefinition(id, jsonField, type, defaultValue, required, displayName, min, max);
        }
    }
}