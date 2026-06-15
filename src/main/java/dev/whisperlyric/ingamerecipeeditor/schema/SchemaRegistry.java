package dev.whisperlyric.ingamerecipeeditor.schema;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 契约注册表
 * 管理所有配方类型的Schema定义，提供Schema的注册、查询和布局生成接口
 */
public class SchemaRegistry {
    
    private static SchemaRegistry instance;
    
    private final Map<String, RecipeSchema> schemas = new HashMap<>();
    private final Map<String, ModRecipeAdapter> adapters = new HashMap<>();
    
    /**
     * 获取单例实例
     */
    public static SchemaRegistry getInstance() {
        if (instance == null) {
            instance = new SchemaRegistry();
            instance.registerDefaultSchemas();
        }
        return instance;
    }
    
    /**
     * 私有构造方法
     */
    private SchemaRegistry() {}
    
    // ========== Schema注册方法 ==========
    
    /**
     * 注册Schema
     */
    public void register(RecipeSchema schema) {
        schemas.put(schema.getRecipeType(), schema);
    }
    
    /**
     * 批量注册Schema
     */
    public void registerAll(List<RecipeSchema> schemaList) {
        for (RecipeSchema schema : schemaList) {
            register(schema);
        }
    }
    
    /**
     * 注册模组适配器
     */
    public void registerAdapter(ModRecipeAdapter adapter) {
        adapters.put(adapter.getModId(), adapter);
        adapter.registerSchemas(this);
    }
    
    // ========== Schema查询方法 ==========
    
    /**
     * 根据配方类型获取Schema
     */
    public RecipeSchema get(String recipeType) {
        return schemas.get(recipeType);
    }
    
    /**
     * 检查是否存在Schema
     */
    public boolean hasSchema(String recipeType) {
        return schemas.containsKey(recipeType);
    }
    
    /**
     * 获取所有已注册的Schema
     */
    public Collection<RecipeSchema> getAllSchemas() {
        return schemas.values();
    }
    
    /**
     * 获取所有已注册的配方类型
     */
    public Collection<String> getAllRecipeTypes() {
        return schemas.keySet();
    }
    
    /**
     * 获取模组适配器
     */
    public ModRecipeAdapter getAdapter(String modId) {
        return adapters.get(modId);
    }
    
    /**
     * 获取所有模组适配器
     */
    public Collection<ModRecipeAdapter> getAllAdapters() {
        return adapters.values();
    }
    
    // ========== 默认Schema注册 ==========
    
    /**
     * 注册默认Schema（原版配方）
     */
    private void registerDefaultSchemas() {
        registerMinecraftSchemas();
    }
    
    /**
     * 注册原版Minecraft配方Schema
     */
    private void registerMinecraftSchemas() {
        // 熔炉配方
        register(RecipeSchema.builder()
            .recipeType("minecraft:smelting")
            .displayName("熔炉配方")
            .size(150, 60)
            .addInputSlot("input_0", 0, "ingredient", 34, 6)
            .addOutputSlot("output_0", 0, "result", 97, 15)
            .addFloatProperty("experience", "experience", 0.7f, false)
            .addIntProperty("cookingtime", "cookingtime", 200, false)
            .build()
        );
        
        // 高炉配方
        register(RecipeSchema.builder()
            .recipeType("minecraft:blasting")
            .displayName("高炉配方")
            .size(150, 60)
            .addInputSlot("input_0", 0, "ingredient", 34, 6)
            .addOutputSlot("output_0", 0, "result", 97, 15)
            .addFloatProperty("experience", "experience", 0.7f, false)
            .addIntProperty("cookingtime", "cookingtime", 100, false)
            .build()
        );
        
        // 烟熏炉配方
        register(RecipeSchema.builder()
            .recipeType("minecraft:smoking")
            .displayName("烟熏炉配方")
            .size(150, 60)
            .addInputSlot("input_0", 0, "ingredient", 34, 6)
            .addOutputSlot("output_0", 0, "result", 97, 15)
            .addFloatProperty("experience", "experience", 0.7f, false)
            .addIntProperty("cookingtime", "cookingtime", 100, false)
            .build()
        );
        
        // 有序合成配方（需要特殊处理pattern）
        register(RecipeSchema.builder()
            .recipeType("minecraft:crafting_shaped")
            .displayName("有序合成配方")
            .size(150, 60)
            // 合成配方的槽位需要动态生成（3x3网格）
            .addOutputSlot("output_0", 0, "result", 94, 19)
            .build()
        );
        
        // 无序合成配方
        register(RecipeSchema.builder()
            .recipeType("minecraft:crafting_shapeless")
            .displayName("无序合成配方")
            .size(150, 60)
            // 无序配方的槽位需要动态生成
            .addOutputSlot("output_0", 0, "result", 94, 19)
            .build()
        );
        
        // 石切机配方
        register(RecipeSchema.builder()
            .recipeType("minecraft:stonecutting")
            .displayName("石切机配方")
            .size(150, 60)
            .addInputSlot("input_0", 0, "ingredient", 34, 6)
            .addOutputSlot("output_0", 0, "result", 97, 15)
            .addIntProperty("count", "count", 1, false)
            .build()
        );
    }
    
    /**
     * 重置注册表（用于测试或重新加载）
     */
    public void reset() {
        schemas.clear();
        adapters.clear();
        registerDefaultSchemas();
    }
}