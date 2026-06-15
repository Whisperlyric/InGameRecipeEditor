package dev.whisperlyric.ingamerecipeeditor.schema;

import com.google.gson.JsonObject;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Map;

/**
 * 模组配方适配器接口
 * 为每个模组定义JSON格式映射和配方数据处理
 */
public interface ModRecipeAdapter {
    
    /**
     * 获取模组ID
     */
    String getModId();
    
    /**
     * 注册该模组的所有配方Schema
     */
    void registerSchemas(SchemaRegistry registry);
    
    /**
     * 从Recipe对象提取完整数据
     * 解决Recipe对象数据暴露不完整的问题
     * 
     * @param recipe Recipe对象
     * @return 提取的数据映射（槽位ID → 数据）
     */
    Map<String, Object> extractRecipeData(Recipe<?> recipe);
    
    /**
     * 从JSON解析完整数据
     * 直接解析JSON，不依赖Recipe对象
     * 
     * @param json JSON对象
     * @return 解析的数据映射（槽位ID → 数据）
     */
    Map<String, Object> parseJsonData(JsonObject json);
    
    /**
     * 获取该模组支持的配方类型列表
     */
    List<String> getSupportedRecipeTypes();
    
    /**
     * 判断是否支持该配方类型
     */
    boolean supportsRecipeType(String recipeType);
    
    /**
     * 获取配方类型的显示名称
     */
    String getDisplayName(String recipeType);
    
    /**
     * 判断模组是否已加载
     */
    boolean isModLoaded();
}