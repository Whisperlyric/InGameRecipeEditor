package dev.whisperlyric.ingamerecipeeditor;

import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;

/**
 * 配方系统初始化类
 * Schema定义由JSON文件自动加载，通过SchemaJsonLoader实现
 */
public class RecipeSystemInitializer {
    
    private static boolean initialized;
    
    /**
     * 初始化配方系统
     */
    public static void initialize() {
        if (initialized) return;
        
        SchemaRegistry registry = SchemaRegistry.getInstance();
        
        initialized = true;
    }
    
    /**
     * 重置系统（用于测试或重新加载）
     */
    public static void reset() {
        initialized = false;
        SchemaRegistry.getInstance().reset();
    }
    
    /**
     * 检查是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
}