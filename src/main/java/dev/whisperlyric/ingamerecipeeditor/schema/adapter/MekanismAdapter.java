package dev.whisperlyric.ingamerecipeeditor.schema.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.schema.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mekanism模组配方适配器
 * 主要负责JSON数据解析，Schema定义由JSON文件提供
 */
public class MekanismAdapter implements ModRecipeAdapter {
    
    @Override
    public String getModId() {
        return "mekanism";
    }
    
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("mekanism");
    }
    
    @Override
    public void registerSchemas(SchemaRegistry registry) {
        // Schema定义现在由JSON文件提供，SchemaJsonLoader会自动加载
        // 此方法保留用于可能的动态注册需求
    }
    
    @Override
    public Map<String, Object> extractRecipeData(Recipe<?> recipe) {
        Map<String, Object> data = new HashMap<>();
        // 具体实现需要根据Mekanism的Recipe类进行反射或直接访问
        return data;
    }
    
    @Override
    public Map<String, Object> parseJsonData(JsonObject json) {
        Map<String, Object> data = new HashMap<>();
        String type = json.get("type").getAsString();
        RecipeSchema schema = SchemaRegistry.getInstance().get(type);
        
        if (schema == null) return data;
        
        // 解析所有槽位数据（使用路径解析）
        for (SlotDefinition slot : schema.getJsonSlots()) {
            JsonElement slotData = extractValueByPaths(json, slot);
            if (slotData != null) {
                data.put(slot.getId(), slotData);
                
                // 解析数量（如果有amount_path）
                if (slot.hasAmountPath()) {
                    JsonElement amountData = getValueByPath(json, slot.getAmountPath());
                    if (amountData != null) {
                        data.put(slot.getId() + "_amount", amountData);
                    }
                }
                
                // 解析化学品类型（如果有chemical_type_path）
                if (slot.hasChemicalTypePath()) {
                    JsonElement typeData = getValueByPath(json, slot.getChemicalTypePath());
                    if (typeData != null) {
                        data.put(slot.getId() + "_chemicalType", typeData);
                    }
                }
            }
        }
        
        // 解析所有属性数据
        for (PropertyDefinition prop : schema.getProperties()) {
            String jsonPath = prop.getJsonField();
            JsonElement propData = getValueByPath(json, jsonPath);
            if (propData != null) {
                data.put(prop.getId(), propData);
            } else {
                data.put(prop.getId(), prop.getDefaultValue());
            }
        }
        
        return data;
    }
    
    /**
     * 从JSON中根据槽位定义提取值
     * 支持单路径和多路径
     */
    private JsonElement extractValueByPaths(JsonObject json, SlotDefinition slot) {
        // 优先使用单路径
        if (slot.getJsonField() != null && !slot.getJsonField().isEmpty()) {
            return getValueByPath(json, slot.getJsonField());
        }
        
        // 使用多路径（尝试每个路径直到找到值）
        if (slot.getJsonPaths() != null) {
            for (String path : slot.getJsonPaths()) {
                JsonElement value = getValueByPath(json, path);
                if (value != null && !value.isJsonNull()) {
                    return value;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 根据路径从JSON中获取值
     * 路径格式如 "/input/ingredient/item" 或 "input.ingredient.item"
     */
    private JsonElement getValueByPath(JsonObject json, String path) {
        if (path == null || path.isEmpty()) return null;
        
        // 标准化路径（移除开头的斜杠）
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        
        // 支持两种分隔符：斜杠和点
        String[] parts = normalizedPath.split("[/\\.]");
        
        JsonElement current = json;
        for (String part : parts) {
            if (current == null || current.isJsonNull()) return null;
            
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (!obj.has(part)) return null;
                current = obj.get(part);
            } else if (current.isJsonArray()) {
                // 如果是数组，尝试将part解析为索引
                try {
                    int index = Integer.parseInt(part);
                    JsonArray arr = current.getAsJsonArray();
                    if (index < 0 || index >= arr.size()) return null;
                    current = arr.get(index);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    @Override
    public List<String> getSupportedRecipeTypes() {
        return List.of(
            "mekanism:crushing",
            "mekanism:enriching",
            "mekanism:smelting",
            "mekanism:combining",
            "mekanism:compressing",
            "mekanism:purifying",
            "mekanism:injecting",
            "mekanism:metallurgic_infusing",
            "mekanism:sawing",
            "mekanism:chemical_infusing",
            "mekanism:separating",
            "mekanism:oxidizing",
            "mekanism:dissolution",
            "mekanism:crystallizing",
            "mekanism:activating",
            "mekanism:centrifuging",
            "mekanism:gas_conversion",
            "mekanism:infusion_conversion",
            "mekanism:reaction",
            "mekanism:rotary",
            "mekanism:energy_conversion",
            "mekanism:evaporating",
            "mekanism:washing",
            "mekanism:painting",
            "mekanism:pigment_mixing",
            "mekanism:pigment_extracting",
            "mekanism:nucleosynthesizing"
        );
    }
    
    @Override
    public boolean supportsRecipeType(String recipeType) {
        return recipeType.startsWith("mekanism:");
    }
    
    @Override
    public String getDisplayName(String recipeType) {
        RecipeSchema schema = SchemaRegistry.getInstance().get(recipeType);
        return schema != null ? schema.getDisplayName() : recipeType;
    }
}