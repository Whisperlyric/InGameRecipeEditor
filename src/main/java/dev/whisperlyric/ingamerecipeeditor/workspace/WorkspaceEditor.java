package dev.whisperlyric.ingamerecipeeditor.workspace;

import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.schema.*;
import dev.whisperlyric.ingamerecipeeditor.serializer.RecipeSerializer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 工作区编辑器
 * 提供配方可视化编辑界面，处理用户交互，收集数据并生成JSON
 */
public class WorkspaceEditor {
    
    private final RecipeSchema currentSchema;
    private final String recipeType;
    private final List<WorkspaceSlot> slots;
    private final Map<String, Object> slotData = new HashMap<>();
    private final Map<String, Object> propertyData = new HashMap<>();
    private final RecipeSerializer serializer;
    
    /**
     * 创建新配方编辑器
     * 如果配方类型未注册，schema将为null
     */
    public WorkspaceEditor(String recipeType) {
        this.recipeType = recipeType;
        this.currentSchema = SchemaRegistry.getInstance().get(recipeType);
        
        if (this.currentSchema == null) {
            InGameRecipeEditor.LOGGER.warn("未注册的配方类型: {}，将创建空编辑器", recipeType);
            this.slots = new ArrayList<>();
            this.serializer = null;
        } else {
            this.slots = createSlots();
            this.serializer = new RecipeSerializer();
            initializeDefaultProperties();
        }
    }
    
    /**
     * 检查配方类型是否已注册
     */
    public boolean isSchemaRegistered() {
        return currentSchema != null;
    }
    
    /**
     * 获取配方Schema（可能为null）
     */
    public Optional<RecipeSchema> getSchema() {
        return Optional.ofNullable(currentSchema);
    }
    
    /**
     * 获取配方类型
     */
    public String getRecipeType() {
        return recipeType;
    }
    
    /**
     * 获取未注册提示消息
     */
    public Component getUnregisteredMessage() {
        return Component.translatable("ingamerecipeeditor.error.unregistered_type", recipeType);
    }
    
    /**
     * 编辑现有配方
     */
    public WorkspaceEditor(String recipeType, JsonObject existingJson) {
        this(recipeType);
        
        // 从现有JSON填充数据
        ModRecipeAdapter adapter = findAdapter(recipeType);
        if (adapter != null) {
            Map<String, Object> data = adapter.parseJsonData(existingJson);
            fillData(data);
        }
    }
    
    /**
     * 创建槽位组件
     */
    private List<WorkspaceSlot> createSlots() {
        List<WorkspaceSlot> slotList = new ArrayList<>();
        
        // 创建输入槽位
        for (SlotDefinition slotDef : currentSchema.getInputSlots()) {
            slotList.add(new WorkspaceSlot(slotDef, this));
        }
        
        // 创建输出槽位
        for (SlotDefinition slotDef : currentSchema.getOutputSlots()) {
            slotList.add(new WorkspaceSlot(slotDef, this));
        }
        
        // 创建仅渲染槽位（不参与数据收集）
        for (SlotDefinition slotDef : currentSchema.getRenderOnlySlots()) {
            slotList.add(new WorkspaceSlot(slotDef, this, true));
        }
        
        return slotList;
    }
    
    /**
     * 初始化属性默认值
     */
    private void initializeDefaultProperties() {
        for (PropertyDefinition prop : currentSchema.getProperties()) {
            propertyData.put(prop.getId(), prop.getDefaultValue());
        }
    }
    
    /**
     * 从数据映射填充槽位和属性
     */
    private void fillData(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            
            // 检查是否是槽位数据
            SlotDefinition slot = currentSchema.getSlotById(key);
            if (slot != null) {
                slotData.put(key, entry.getValue());
                // 更新槽位显示
                WorkspaceSlot wsSlot = findWorkspaceSlot(key);
                if (wsSlot != null) {
                    wsSlot.setData(entry.getValue());
                }
            }
            
            // 检查是否是属性数据
            PropertyDefinition prop = currentSchema.getPropertyById(key);
            if (prop != null) {
                propertyData.put(key, entry.getValue());
            }
        }
    }
    
    /**
     * 查找工作区槽位
     */
    private WorkspaceSlot findWorkspaceSlot(String slotId) {
        for (WorkspaceSlot slot : slots) {
            if (slot.getDefinition().getId().equals(slotId)) {
                return slot;
            }
        }
        return null;
    }
    
    /**
     * 查找适配器
     */
    private ModRecipeAdapter findAdapter(String recipeType) {
        for (ModRecipeAdapter adapter : SchemaRegistry.getInstance().getAllAdapters()) {
            if (adapter.supportsRecipeType(recipeType)) {
                return adapter;
            }
        }
        return null;
    }
    
    // ========== 用户交互处理 ==========
    
    /**
     * 设置槽位数据
     */
    public void setSlotData(String slotId, Object data) {
        slotData.put(slotId, data);
    }
    
    /**
     * 设置物品槽位数据
     */
    public void setItemSlotData(String slotId, ItemStack stack) {
        slotData.put(slotId, stack);
    }
    
    /**
     * 设置属性数据
     */
    public void setPropertyData(String propertyId, Object value) {
        PropertyDefinition prop = currentSchema.getPropertyById(propertyId);
        if (prop != null) {
            // 验证类型
            if (validatePropertyType(value, prop.getType())) {
                propertyData.put(propertyId, value);
            }
        }
    }
    
    /**
     * 验证属性类型
     */
    private boolean validatePropertyType(Object value, PropertyDefinition.PropertyType type) {
        return switch (type) {
            case INTEGER -> value instanceof Integer;
            case FLOAT -> value instanceof Float || value instanceof Double;
            case STRING -> value instanceof String;
            case BOOLEAN -> value instanceof Boolean;
        };
    }
    
    /**
     * 清除槽位数据
     */
    public void clearSlotData(String slotId) {
        slotData.remove(slotId);
        WorkspaceSlot slot = findWorkspaceSlot(slotId);
        if (slot != null) {
            slot.clearData();
        }
    }
    
    /**
     * 清除所有数据
     */
    public void clearAllData() {
        slotData.clear();
        propertyData.clear();
        initializeDefaultProperties();
        
        for (WorkspaceSlot slot : slots) {
            slot.clearData();
        }
    }
    
    // ========== 数据收集与生成 ==========
    
    /**
     * 收集所有数据并生成JSON
     */
    public JsonObject generateJson() {
        // 验证数据完整性
        RecipeSerializer.ValidationResult validation = serializer.validate(
            currentSchema.getRecipeType(), slotData, propertyData
        );
        
        if (!validation.isValid()) {
            throw new IllegalStateException("数据验证失败:\n" + validation.getSummary());
        }
        
        // 序列化为JSON
        return serializer.serialize(currentSchema.getRecipeType(), slotData, propertyData);
    }
    
    /**
     * 收集数据（不生成JSON）
     */
    public Map<String, Object> collectData() {
        Map<String, Object> allData = new HashMap<>();
        allData.putAll(slotData);
        allData.putAll(propertyData);
        return allData;
    }
    
    /**
     * 验证数据
     */
    public RecipeSerializer.ValidationResult validate() {
        return serializer.validate(currentSchema.getRecipeType(), slotData, propertyData);
    }
    
    // ========== Getter方法 ==========

    public List<WorkspaceSlot> getSlots() { return slots; }
    public Map<String, Object> getSlotData() { return slotData; }
    public Map<String, Object> getPropertyData() { return propertyData; }
    
    /**
     * 获取槽位数据
     */
    public Object getSlotData(String slotId) {
        return slotData.get(slotId);
    }
    
    /**
     * 获取属性数据
     */
    public Object getPropertyData(String propertyId) {
        return propertyData.get(propertyId);
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return currentSchema != null ? currentSchema.getDisplayName() : recipeType;
    }
    
    /**
     * 获取界面尺寸
     */
    public int getWidth() { return currentSchema != null ? currentSchema.getWidth() : 200; }
    public int getHeight() { return currentSchema != null ? currentSchema.getHeight() : 150; }
}