package dev.whisperlyric.ingamerecipeeditor.schema.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.schema.*;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ImmersiveEngineering模组配方适配器
 * 定义IE所有配方类型的Schema和数据映射
 */
public class ImmersiveEngineeringAdapter implements ModRecipeAdapter {
    
    @Override
    public String getModId() {
        return "immersiveengineering";
    }
    
    @Override
    public boolean isModLoaded() {
        return ModList.get().isLoaded("immersiveengineering");
    }
    
    @Override
    public void registerSchemas(SchemaRegistry registry) {
        if (!isModLoaded()) return;
        
        // 碎石机配方（物品→物品+副输出）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:crusher")
            .displayName("碎石机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .addFloatProperty("secondaries_chance", "secondaries_chance", 0.0f, false)  // 副输出概率
            .build()
        );
        
        // 合金炉配方（两个物品→物品）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:alloy")
            .displayName("合金炉配方")
            .size(144, 54)
            .addInputSlot("input0", 0, "input0", 30, 20)
            .addInputSlot("input1", 1, "input1", 30, 40)
            .addOutputSlot("result", 0, "result", 110, 30)
            .addIntProperty("time", "time", 200, false)
            .build()
        );
        
        // 高炉配方（物品→物品+矿渣）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:blast_furnace")
            .displayName("高炉配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addOutputSlot("slag", 1, "slag", 110, 40)
            .addIntProperty("time", "time", 100, true)
            .build()
        );
        
        // 焦炉配方（物品→物品+流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:coke_oven")
            .displayName("焦炉配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("creosote", "creosote", 0, false)  // 输出流体数量
            .addIntProperty("time", "time", 300, true)
            .build()
        );
        
        // 压榨机配方（物品→物品+流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:squeezer")
            .displayName("压榨机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 发酵机配方（物品→物品+流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:fermenter")
            .displayName("发酵机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 精炼机配方（流体→两个流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:refinery")
            .displayName("精炼机配方")
            .size(144, 54)
            .addChemicalInputSlot("input", 0, SlotDefinition.IngredientType.FLUID, "input", 50, 35)
            .addChemicalOutputSlot("result0", 0, SlotDefinition.IngredientType.FLUID, "result0", 110, 15)
            .addChemicalOutputSlot("result1", 1, SlotDefinition.IngredientType.FLUID, "result1", 110, 33)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 混合器配方（物品+流体→流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:mixer")
            .displayName("混合器配方")
            .size(144, 54)
            .addChemicalInputSlot("fluidInput", 0, SlotDefinition.IngredientType.FLUID, "fluidInput", 30, 60)
            .addChemicalOutputSlot("fluidOutput", 0, SlotDefinition.IngredientType.FLUID, "fluidOutput", 110, 40)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 金属冲压机配方（物品+模具→物品）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:metal_press")
            .displayName("金属冲压机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 精密锯木机配方（物品→物品+副输出）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:sawmill")
            .displayName("精密锯木机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 瓶装机配方（物品+流体→物品）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:bottling_machine")
            .displayName("瓶装机配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addChemicalInputSlot("fluidInput", 1, SlotDefinition.IngredientType.FLUID, "fluidInput", 50, 40)
            .addOutputSlot("result", 0, "result", 110, 30)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 电弧炉配方（物品+添加剂→物品+副输出）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:arc_furnace")
            .displayName("电弧炉配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .addIntProperty("time", "time", 100, false)
            .build()
        );
        
        // 发电机燃料配方（流体→能量）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:generator_fuel")
            .displayName("发电机燃料配方")
            .size(144, 54)
            .addChemicalInputSlot("fluidInput", 0, SlotDefinition.IngredientType.FLUID, "fluidInput", 50, 35)
            .addIntProperty("energy", "energy", 0, true)  // 输出能量值
            .build()
        );
        
        // 矿物粉碎配方（物品→物品+副输出）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:ore_processing")
            .displayName("矿物粉碎配方")
            .size(144, 54)
            .addInputSlot("input_0", 0, "input", 50, 20)
            .addOutputSlot("result", 0, "result", 110, 20)
            .addIntProperty("energy", "energy", 8000, true)
            .build()
        );
        
        // 流体泵配方（流体→流体）
        registry.register(RecipeSchema.builder()
            .recipeType("immersiveengineering:fluid_pump")
            .displayName("流体泵配方")
            .size(144, 54)
            .addChemicalInputSlot("input", 0, SlotDefinition.IngredientType.FLUID, "input", 50, 35)
            .addChemicalOutputSlot("output", 0, SlotDefinition.IngredientType.FLUID, "output", 110, 35)
            .build()
        );
    }
    
    @Override
    public Map<String, Object> extractRecipeData(Recipe<?> recipe) {
        Map<String, Object> data = new HashMap<>();
        // 具体实现需要根据IE的Recipe类进行反射或直接访问
        return data;
    }
    
    @Override
    public Map<String, Object> parseJsonData(JsonObject json) {
        Map<String, Object> data = new HashMap<>();
        String type = json.get("type").getAsString();
        RecipeSchema schema = SchemaRegistry.getInstance().get(type);
        
        if (schema == null) return data;
        
        // 解析所有槽位数据
        for (SlotDefinition slot : schema.getJsonSlots()) {
            String jsonField = slot.getJsonField();
            if (jsonField != null && json.has(jsonField)) {
                data.put(slot.getId(), json.get(jsonField));
            }
        }
        
        // 解析副输出数组（IE特有）
        if (json.has("secondaries")) {
            JsonArray secondaries = json.getAsJsonArray("secondaries");
            List<Map<String, Object>> secondaryList = new ArrayList<>();
            for (JsonElement element : secondaries) {
                JsonObject secondaryJson = element.getAsJsonObject();
                Map<String, Object> secondary = new HashMap<>();
                if (secondaryJson.has("output")) {
                    secondary.put("output", secondaryJson.get("output"));
                }
                if (secondaryJson.has("chance")) {
                    secondary.put("chance", secondaryJson.get("chance").getAsFloat());
                }
                secondaryList.add(secondary);
            }
            data.put("secondaries", secondaryList);
        }
        
        // 解析所有属性数据
        for (PropertyDefinition prop : schema.getProperties()) {
            String jsonField = prop.getJsonField();
            if (jsonField != null && json.has(jsonField)) {
                data.put(prop.getId(), json.get(jsonField));
            } else {
                data.put(prop.getId(), prop.getDefaultValue());
            }
        }
        
        return data;
    }
    
    @Override
    public List<String> getSupportedRecipeTypes() {
        return List.of(
            "immersiveengineering:crusher",
            "immersiveengineering:alloy",
            "immersiveengineering:blast_furnace",
            "immersiveengineering:coke_oven",
            "immersiveengineering:squeezer",
            "immersiveengineering:fermenter",
            "immersiveengineering:refinery",
            "immersiveengineering:mixer",
            "immersiveengineering:metal_press",
            "immersiveengineering:sawmill",
            "immersiveengineering:bottling_machine",
            "immersiveengineering:arc_furnace",
            "immersiveengineering:generator_fuel",
            "immersiveengineering:ore_processing",
            "immersiveengineering:fluid_pump"
        );
    }
    
    @Override
    public boolean supportsRecipeType(String recipeType) {
        return recipeType.startsWith("immersiveengineering:");
    }
    
    @Override
    public String getDisplayName(String recipeType) {
        RecipeSchema schema = SchemaRegistry.getInstance().get(recipeType);
        return schema != null ? schema.getDisplayName() : recipeType;
    }
}