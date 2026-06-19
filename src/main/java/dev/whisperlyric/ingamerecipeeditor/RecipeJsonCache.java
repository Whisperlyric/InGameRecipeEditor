package dev.whisperlyric.ingamerecipeeditor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方JSON缓存 - 在服务器端缓存所有配方的原始JSON
 * 与JEIRecipeManager参考项目一致，在RecipeManager.apply()时缓存所有配方JSON
 * 在单人游戏中，客户端可直接访问服务器缓存（共享JVM）
 */
public class RecipeJsonCache {
    // 服务器端：缓存所有配方的原始JSON（由RecipeManagerMixin在apply时填充）
    private static final Map<String, String> serverAllRecipeJsonCache = new ConcurrentHashMap<>();

    private RecipeJsonCache() {}

    /**
     * 服务器端：缓存所有配方JSON（由RecipeManagerMixin调用）
     */
    public static void serverCacheAllRecipeJson(Map<String, String> allRecipeJsonMap) {
        serverAllRecipeJsonCache.clear();
        serverAllRecipeJsonCache.putAll(allRecipeJsonMap);
        InGameRecipeEditor.LOGGER.info("服务器缓存了 {} 个配方JSON", serverAllRecipeJsonCache.size());
    }

    /**
     * 服务器端：清除缓存
     */
    public static void serverClearCache() {
        serverAllRecipeJsonCache.clear();
    }

    /**
     * 获取配方JSON
     * 优先从服务器缓存获取（单人游戏时客户端和服务器共享JVM）
     * 
     * @param recipeId 配方ID（如 minecraft:coal_block）
     * @return 配方JSON，如果未找到返回empty
     */
    public static Optional<JsonObject> getRecipeJson(String recipeId) {
        // 1. 从服务器缓存获取（单人游戏时可用）
        String jsonStr = serverAllRecipeJsonCache.get(recipeId);
        if (jsonStr != null) {
            try {
                JsonElement element = JsonParser.parseString(jsonStr);
                if (element.isJsonObject()) {
                    return Optional.of(element.getAsJsonObject());
                }
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.warn("解析缓存的配方JSON失败: {}", recipeId);
            }
        }

        return Optional.empty();
    }

    /**
     * 获取缓存大小
     */
    public static int getCacheSize() {
        return serverAllRecipeJsonCache.size();
    }
}
