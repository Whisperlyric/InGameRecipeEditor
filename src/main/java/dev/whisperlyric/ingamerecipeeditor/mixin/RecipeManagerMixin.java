package dev.whisperlyric.ingamerecipeeditor.mixin;

import com.google.gson.JsonElement;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * RecipeManager的Mixin - 在配方加载时移除黑名单配方
 */
@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    /**
     * 在配方加载时注入，移除被禁用的配方
     */
    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD")
    )
    private void ingamerecipeeditor$applyRecipeDeletions(
            Map<ResourceLocation, JsonElement> originalRecipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci) {
        try {
            // 初始化禁用配方管理器
            DisabledRecipesManager.serverInit();
            
            // 获取禁用配方列表
            Set<String> disabledRecipeIds = DisabledRecipesManager.getDisabledRecipes();
            
            if (disabledRecipeIds.isEmpty()) {
                return;
            }
            
            int removedCount = 0;
            Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator = originalRecipes.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<ResourceLocation, JsonElement> entry = iterator.next();
                ResourceLocation recipeId = entry.getKey();
                String recipeIdStr = recipeId.toString();
                
                if (disabledRecipeIds.contains(recipeIdStr)) {
                    iterator.remove();
                    removedCount++;
                    InGameRecipeEditor.LOGGER.debug("移除禁用配方: {}", recipeId);
                }
            }
            
            if (removedCount > 0) {
                InGameRecipeEditor.LOGGER.info("已移除 {} 个禁用配方", removedCount);
            }
            
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.error("应用配方删除规则失败", e);
        }
    }
}