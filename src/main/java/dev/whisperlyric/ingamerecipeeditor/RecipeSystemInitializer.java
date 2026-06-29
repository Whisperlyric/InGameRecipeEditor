package dev.whisperlyric.ingamerecipeeditor;

import dev.whisperlyric.ingamerecipeeditor.schema.ModRecipeAdapter;
import dev.whisperlyric.ingamerecipeeditor.schema.SchemaRegistry;
import dev.whisperlyric.ingamerecipeeditor.schema.adapter.ImmersiveEngineeringAdapter;
import dev.whisperlyric.ingamerecipeeditor.schema.adapter.MekanismAdapter;

/**
 * 配方系统初始化类
 * 注册所有模组适配器和Schema
 */
public class RecipeSystemInitializer {
    
    private static boolean initialized;
    
    /**
     * 初始化配方系统
     */
    public static void initialize() {
        if (initialized) return;
        
        SchemaRegistry registry = SchemaRegistry.getInstance();
        
        // 注册模组适配器
        registerAdapters(registry);
        
        initialized = true;
    }
    
    /**
     * 注册所有模组适配器
     */
    private static void registerAdapters(SchemaRegistry registry) {
        // 注册Mekanism适配器
        ModRecipeAdapter mekanismAdapter = new MekanismAdapter();
        if (mekanismAdapter.isModLoaded()) {
            registry.registerAdapter(mekanismAdapter);
        }
        
        // 注册ImmersiveEngineering适配器
        ModRecipeAdapter ieAdapter = new ImmersiveEngineeringAdapter();
        if (ieAdapter.isModLoaded()) {
            registry.registerAdapter(ieAdapter);
        }
        
        // 可以继续添加其他模组适配器
        // registry.registerAdapter(new ThermalExpansionAdapter());
        // registry.registerAdapter(new CreateAdapter());
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