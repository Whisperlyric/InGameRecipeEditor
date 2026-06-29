package dev.whisperlyric.ingamerecipeeditor.mixin;

import com.google.gson.JsonElement;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.RecipeJsonCache;
import dev.whisperlyric.ingamerecipeeditor.customrecipes.CustomRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import dev.whisperlyric.ingamerecipeeditor.generated.GeneratedRecipesManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * RecipeManager的Mixin - 在配方加载时注入自定义配方并移除禁用配方
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    /**
     * 在配方加载时注入，加载自定义配方并移除被禁用的配方
     */
    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD")
    )
    private void ingamerecipeeditor$applyCustomRecipesAndDeletions(
            Map<ResourceLocation, JsonElement> originalRecipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        try {
            // 初始化管理器
            DisabledRecipesManager.serverInit();
            GeneratedRecipesManager.serverInit();
            
            // 缓存所有配方JSON（与JEIRecipeManager参考项目一致）
            registerhelper$cacheAllRecipeJson(originalRecipes);
            
            // 1. 从 config 目录加载自定义配方并注入
            registerhelper$loadAndInjectCustomRecipes(originalRecipes);
            
            // 2. 移除被禁用的配方
            registerhelper$applyRecipeDeletions(originalRecipes);
            
            // 3. 处理待删除的生成配方
            registerhelper$applyPendingGeneratedRecipeDeletes(originalRecipes);
            
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("应用配方规则失败", e);
        }
    }

    /**
     * 缓存所有配方JSON到RecipeJsonCache
     * 与JEIRecipeManager参考项目一致，在RecipeManager.apply()时缓存所有配方原始JSON
     * 这样编辑任意配方时都能获取到原始JSON
     */
    @Unique
    private void registerhelper$cacheAllRecipeJson(Map<ResourceLocation, JsonElement> originalRecipes) {
        Map<String, String> allRecipeJsonMap = new HashMap<>();
        for (var entry : originalRecipes.entrySet()) {
            allRecipeJsonMap.put(entry.getKey().toString(), entry.getValue().toString());
        }
        RecipeJsonCache.serverCacheAllRecipeJson(allRecipeJsonMap);
    }

    /**
     * 从 config 目录加载自定义配方并注入到配方映射中
     */
    @Unique
    private void registerhelper$loadAndInjectCustomRecipes(Map<ResourceLocation, JsonElement> originalRecipes) {
        Map<ResourceLocation, JsonElement> customRecipes = CustomRecipesManager.loadCustomRecipes();
        
        if (customRecipes.isEmpty()) {
            return;
        }
        
        int injectedCount = 0;
        int overriddenCount = 0;
        
        for (Map.Entry<ResourceLocation, JsonElement> entry : customRecipes.entrySet()) {
            ResourceLocation recipeId = entry.getKey();
            JsonElement recipeJson = entry.getValue();
            
            // 检查是否在禁用列表中
            if (DisabledRecipesManager.isRecipeDisabled(recipeId.toString())) {
                InGameRecipeEditor.LOGGER.debug("跳过禁用的自定义配方: {}", recipeId);
                continue;
            }
            
            // 检查是否已存在（覆盖模式）
            if (originalRecipes.containsKey(recipeId)) {
                originalRecipes.put(recipeId, recipeJson);
                overriddenCount++;
                InGameRecipeEditor.LOGGER.debug("覆盖配方: {}", recipeId);
            } else {
                originalRecipes.put(recipeId, recipeJson);
                injectedCount++;
                InGameRecipeEditor.LOGGER.debug("注入新配方: {}", recipeId);
            }
        }
        
        if (injectedCount > 0 || overriddenCount > 0) {
            InGameRecipeEditor.LOGGER.info("注入 {} 个新配方，覆盖 {} 个已有配方", injectedCount, overriddenCount);
        }
    }

    /**
     * 移除被禁用的配方
     */
    @Unique
    private void registerhelper$applyRecipeDeletions(Map<ResourceLocation, JsonElement> originalRecipes) {
        Set<String> disabledRecipeIds = DisabledRecipesManager.getDisabledRecipes();
        
        if (disabledRecipeIds.isEmpty()) {
            return;
        }

        // 清除旧的服务器端JSON缓存（配置可能已变化）
        DisabledRecipesManager.serverClearRecipeJsonCache();
        
        int removedCount = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator = originalRecipes.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = iterator.next();
            ResourceLocation recipeId = entry.getKey();
            String recipeIdStr = recipeId.toString();
            
            if (disabledRecipeIds.contains(recipeIdStr)) {
                // 在移除前缓存原始JSON，供客户端预览使用
                DisabledRecipesManager.serverCacheRecipeJson(recipeIdStr, entry.getValue().toString());
                iterator.remove();
                removedCount++;
                InGameRecipeEditor.LOGGER.debug("移除禁用配方: {}", recipeId);
            }
        }
        
        if (removedCount > 0) {
            InGameRecipeEditor.LOGGER.info("已移除 {} 个禁用配方", removedCount);
        }
    }

    /**
     * 处理待删除的生成配方
     */
    @Unique
    private void registerhelper$applyPendingGeneratedRecipeDeletes(Map<ResourceLocation, JsonElement> originalRecipes) {
        Set<String> pendingDeletes = GeneratedRecipesManager.getPendingGeneratedRecipeDeletes();
        
        if (pendingDeletes.isEmpty()) {
            return;
        }
        
        int deletedCount = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator = originalRecipes.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = iterator.next();
            ResourceLocation recipeId = entry.getKey();
            String recipeIdStr = recipeId.toString();
            
            if (pendingDeletes.contains(recipeIdStr)) {
                iterator.remove();
                deletedCount++;
                InGameRecipeEditor.LOGGER.debug("删除生成配方: {}", recipeId);
            }
        }
        
        if (deletedCount > 0) {
            InGameRecipeEditor.LOGGER.info("已删除 {} 个生成配方", deletedCount);
        }
    }
}