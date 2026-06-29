package dev.whisperlyric.ingamerecipeeditor.disabled;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 禁用配方管理器 - 管理被禁用的配方
 * 支持客户端和服务器端
 */
public class DisabledRecipesManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    // 禁用配方ID集合
    private static final CopyOnWriteArraySet<String> disabledRecipes = new CopyOnWriteArraySet<>();
    
    // 配置文件路径
    private static Path configPath;
    private static boolean serverInitialized = false;

    // 客户端缓存
    private static final Map<String, String> clientRecipeJsonCache = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<ResourceLocation> clientDisabledRecipeOutputs = new CopyOnWriteArraySet<>();
    private static volatile boolean clientRecipeStateSynced = false;

    // 服务器端配方JSON缓存（在Mixin移除配方时捕获原始JSON）
    private static final Map<String, String> serverRecipeJsonCache = new ConcurrentHashMap<>();

    /**
     * 服务器初始化
     */
    public static void serverInit() {
        if (!serverInitialized) {
            configPath = FMLPaths.GAMEDIR.get()
                .resolve("igredata")
                .resolve("disabled_recipes.json");
            load();
            serverInitialized = true;
            InGameRecipeEditor.LOGGER.info("服务器初始化禁用配方管理器，共 {} 个禁用配方", disabledRecipes.size());
        }
    }

    /**
     * 从配置文件加载禁用配方
     */
    private static void load() {
        if (configPath != null && Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Set<String> loaded = GSON.fromJson(json, SET_TYPE);
                if (loaded != null) {
                    disabledRecipes.clear();
                    disabledRecipes.addAll(loaded);
                }
                InGameRecipeEditor.LOGGER.info("加载 {} 个禁用配方", disabledRecipes.size());
            } catch (IOException e) {
                InGameRecipeEditor.LOGGER.error("加载禁用配方配置失败", e);
            }
        }
    }

    /**
     * 保存禁用配方到配置文件
     */
    public static void save() {
        if (configPath == null) {
            InGameRecipeEditor.LOGGER.error("无法保存禁用配方：配置路径为空");
            return;
        }
        try {
            String json = GSON.toJson(disabledRecipes);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, json);
            InGameRecipeEditor.LOGGER.info("保存 {} 个禁用配方", disabledRecipes.size());
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.error("保存禁用配方配置失败", e);
        }
    }

    /**
     * 服务器重载配置
     */
    public static void serverReload() {
        if (serverInitialized) {
            load();
            InGameRecipeEditor.LOGGER.info("重载配置：{} 个禁用配方", disabledRecipes.size());
        }
    }

    /**
     * 检查配方是否被禁用
     */
    public static boolean isRecipeDisabled(String recipeId) {
        return disabledRecipes.contains(recipeId);
    }

    /**
     * 服务器禁用配方
     */
    public static void serverDisableRecipe(String recipeId) {
        disabledRecipes.add(recipeId);
        save();
        InGameRecipeEditor.LOGGER.info("禁用配方: {}", recipeId);
    }

    /**
     * 服务器启用配方
     */
    public static void serverEnableRecipe(String recipeId) {
        disabledRecipes.remove(recipeId);
        save();
        InGameRecipeEditor.LOGGER.info("启用配方: {}", recipeId);
    }

    /**
     * 获取所有禁用配方ID
     */
    public static Set<String> getDisabledRecipes() {
        return new HashSet<>(disabledRecipes);
    }

    /**
     * 客户端更新禁用配方列表（从服务器接收）
     */
    public static void clientUpdateDisabledRecipes(List<String> recipes, Map<String, String> recipeJsonMap) {
        disabledRecipes.clear();
        disabledRecipes.addAll(recipes);
        clientRecipeJsonCache.clear();
        if (recipeJsonMap != null) {
            clientRecipeJsonCache.putAll(recipeJsonMap);
        }
        clientRecipeStateSynced = true;
        InGameRecipeEditor.LOGGER.info("客户端从服务器接收 {} 个禁用配方", disabledRecipes.size());
    }

    /**
     * 客户端是否已同步配方状态
     */
    public static boolean hasClientRecipeStateSynced() {
        return clientRecipeStateSynced;
    }

    /**
     * 获取客户端配方JSON缓存
     */
    public static Map<String, String> getClientRecipeJsonCache() {
        return new ConcurrentHashMap<>(clientRecipeJsonCache);
    }

    /**
     * 服务器端：缓存被移除配方的原始JSON（由RecipeManagerMixin调用）
     */
    public static void serverCacheRecipeJson(String recipeId, String recipeJson) {
        serverRecipeJsonCache.put(recipeId, recipeJson);
    }

    /**
     * 服务器端：获取缓存的配方JSON
     */
    public static Map<String, String> getServerRecipeJsonCache() {
        return new ConcurrentHashMap<>(serverRecipeJsonCache);
    }

    /**
     * 服务器端：清除缓存的配方JSON（在配置重载前调用）
     */
    public static void serverClearRecipeJsonCache() {
        serverRecipeJsonCache.clear();
    }

    /**
     * 设置客户端禁用配方输出物品
     */
    public static void setClientDisabledRecipeOutputs(Set<ResourceLocation> outputs) {
        clientDisabledRecipeOutputs.clear();
        clientDisabledRecipeOutputs.addAll(outputs);
    }

    /**
     * 获取客户端禁用配方输出物品
     */
    public static Set<ResourceLocation> getClientDisabledRecipeOutputs() {
        return new HashSet<>(clientDisabledRecipeOutputs);
    }

    /**
     * 检查客户端输出物品是否被禁用
     */
    public static boolean isClientDisabledRecipeOutput(ResourceLocation output) {
        return clientDisabledRecipeOutputs.contains(output);
    }

    /**
     * 清除所有客户端状态
     */
    public static void clearClientState() {
        disabledRecipes.clear();
        clientRecipeJsonCache.clear();
        clientDisabledRecipeOutputs.clear();
        clientRecipeStateSynced = false;
    }

    /**
     * 禁用配方记录（用于网络传输）
     */
    public record DisabledRecipeEntry(String recipeId, String recipeJson) {}
}