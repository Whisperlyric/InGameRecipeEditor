package dev.whisperlyric.ingamerecipeeditor.serializer;

import com.google.gson.*;
import dev.whisperlyric.ingamerecipeeditor.schema.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方序列化器
 * 根据Schema定义将配方数据序列化为JSON或从JSON反序列化
 */
public class RecipeSerializer {
    
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private final SchemaRegistry schemaRegistry;
    
    public RecipeSerializer() {
        this.schemaRegistry = SchemaRegistry.getInstance();
    }
    
    /**
     * 序列化配方数据为JSON
     * 
     * @param recipeType 配方类型
     * @param slotData 槽位数据映射（槽位ID → 数据）
     * @param propertyData 属性数据映射（属性ID → 值）
     * @return 生成的JSON对象
     */
    public JsonObject serialize(String recipeType, Map<String, Object> slotData, Map<String, Object> propertyData) {
        RecipeSchema schema = schemaRegistry.get(recipeType);
        if (schema == null) {
            throw new IllegalArgumentException("未注册的配方类型: " + recipeType);
        }
        
        JsonObject json = new JsonObject();
        
        // 1. 设置配方类型
        json.addProperty("type", recipeType);
        
        // 2. 序列化槽位数据
        serializeSlots(json, schema, slotData);
        
        // 3. 序列化属性数据
        serializeProperties(json, schema, propertyData);
        
        return json;
    }
    
    /**
     * 序列化槽位数据
     */
    private void serializeSlots(JsonObject json, RecipeSchema schema, Map<String, Object> slotData) {
        for (SlotDefinition slot : schema.getJsonSlots()) {
            Object data = slotData.get(slot.getId());
            if (data == null) {
                if (slot.isRequired()) {
                    throw new IllegalArgumentException("缺少必需槽位数据: " + slot.getId());
                }
                continue;
            }
            
            String jsonField = slot.getJsonField();
            JsonElement element = serializeSlotData(data, slot.getType());
            json.add(jsonField, element);
        }
    }
    
    /**
     * 序列化单个槽位数据
     */
    private JsonElement serializeSlotData(Object data, SlotDefinition.IngredientType type) {
        switch (type) {
            case ITEM -> {
                if (data instanceof ItemStack stack) {
                    return serializeItemStack(stack);
                } else if (data instanceof Map) {
                    return GSON.toJsonTree(data);
                }
            }
            case FLUID, GAS, INFUSE_TYPE, PIGMENT, SLURRY, ANY -> {
                return GSON.toJsonTree(data);
            }
        }
        return GSON.toJsonTree(data);
    }
    
    /**
     * 序列化ItemStack
     */
    private JsonObject serializeItemStack(ItemStack stack) {
        JsonObject json = new JsonObject();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            json.addProperty("item", itemId.toString());
        }
        if (stack.getCount() > 1) {
            json.addProperty("count", stack.getCount());
        }
        if (stack.hasTag()) {
            if (stack.getTag() != null) {
                json.addProperty("nbt", stack.getTag().toString());
            }
        }
        return json;
    }
    
    /**
     * 序列化属性数据
     */
    private void serializeProperties(JsonObject json, RecipeSchema schema, Map<String, Object> propertyData) {
        for (PropertyDefinition prop : schema.getProperties()) {
            Object value = propertyData.get(prop.getId());
            
            // 如果值为null或等于默认值，跳过（可选属性）
            if (value == null) {
                if (prop.isRequired()) {
                    value = prop.getDefaultValue();
                } else {
                    continue;
                }
            }
            
            // 如果值等于默认值，跳过（减少JSON大小）
            if (!prop.isRequired() && value.equals(prop.getDefaultValue())) {
                continue;
            }
            
            String jsonField = prop.getJsonField();
            switch (prop.getType()) {
                case INTEGER -> json.addProperty(jsonField, (Integer) value);
                case FLOAT -> json.addProperty(jsonField, (Float) value);
                case STRING -> json.addProperty(jsonField, (String) value);
                case BOOLEAN -> json.addProperty(jsonField, (Boolean) value);
            }
        }
    }
    
    /**
     * 反序列化JSON为配方数据
     * 
     * @param json JSON对象
     * @return 解析的数据映射
     */
    public Map<String, Object> deserialize(JsonObject json) {
        String recipeType = json.get("type").getAsString();
        RecipeSchema schema = schemaRegistry.get(recipeType);
        if (schema == null) {
            throw new IllegalArgumentException("未注册的配方类型: " + recipeType);
        }
        
        Map<String, Object> data = new HashMap<>();
        
        // 1. 反序列化槽位数据
        deserializeSlots(json, schema, data);
        
        // 2. 反序列化属性数据
        deserializeProperties(json, schema, data);
        
        return data;
    }
    
    /**
     * 反序列化槽位数据
     */
    private void deserializeSlots(JsonObject json, RecipeSchema schema, Map<String, Object> data) {
        for (SlotDefinition slot : schema.getJsonSlots()) {
            String jsonField = slot.getJsonField();
            if (json.has(jsonField)) {
                JsonElement element = json.get(jsonField);
                Object slotData = deserializeSlotData(element, slot.getType());
                data.put(slot.getId(), slotData);
            } else if (slot.isRequired()) {
                throw new IllegalArgumentException("缺少必需JSON字段: " + jsonField);
            }
        }
    }
    
    /**
     * 反序列化单个槽位数据
     */
    private Object deserializeSlotData(JsonElement element, SlotDefinition.IngredientType type) {
        // 返回原始JSON元素，由具体模组适配器处理
        return element;
    }
    
    /**
     * 反序列化属性数据
     */
    private void deserializeProperties(JsonObject json, RecipeSchema schema, Map<String, Object> data) {
        for (PropertyDefinition prop : schema.getProperties()) {
            String jsonField = prop.getJsonField();
            if (json.has(jsonField)) {
                Object value = deserializeProperty(json.get(jsonField), prop.getType());
                data.put(prop.getId(), value);
            } else {
                // 使用默认值
                data.put(prop.getId(), prop.getDefaultValue());
            }
        }
    }
    
    /**
     * 反序列化属性值
     */
    private Object deserializeProperty(JsonElement element, PropertyDefinition.PropertyType type) {
        return switch (type) {
            case INTEGER -> element.getAsInt();
            case FLOAT -> element.getAsFloat();
            case STRING -> element.getAsString();
            case BOOLEAN -> element.getAsBoolean();
        };
    }
    
    /**
     * 验证配方数据完整性
     * 
     * @param recipeType 配方类型
     * @param slotData 槽位数据
     * @param propertyData 属性数据
     * @return 验证结果
     */
    public ValidationResult validate(String recipeType, Map<String, Object> slotData, Map<String, Object> propertyData) {
        ValidationResult result = new ValidationResult();
        RecipeSchema schema = schemaRegistry.get(recipeType);
        
        if (schema == null) {
            result.addError("未注册的配方类型: " + recipeType);
            return result;
        }
        
        // 验证必需槽位
        for (SlotDefinition slot : schema.getJsonSlots()) {
            if (slot.isRequired() && !slotData.containsKey(slot.getId())) {
                result.addError("缺少必需槽位数据: " + slot.getId());
            }
        }
        
        // 验证必需属性
        for (PropertyDefinition prop : schema.getProperties()) {
            if (prop.isRequired() && !propertyData.containsKey(prop.getId())) {
                result.addError("缺少必需属性数据: " + prop.getId());
            }
        }
        
        // 验证属性范围
        for (PropertyDefinition prop : schema.getProperties()) {
            Object value = propertyData.get(prop.getId());
            if (value instanceof Number num) {
                if (num.intValue() < prop.getMin() || num.intValue() > prop.getMax()) {
                    result.addWarning("属性 " + prop.getId() + " 超出范围: " + num.intValue() + 
                        " (范围: " + prop.getMin() + "-" + prop.getMax() + ")");
                }
            }
        }
        
        return result;
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) { errors.add(error); }
        public void addWarning(String warning) { warnings.add(warning); }
        
        public boolean isValid() { return errors.isEmpty(); }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("错误: ").append(errors.size()).append("\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("警告: ").append(warnings.size()).append("\n");
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append("\n");
                }
            }
            return sb.toString();
        }
    }
}