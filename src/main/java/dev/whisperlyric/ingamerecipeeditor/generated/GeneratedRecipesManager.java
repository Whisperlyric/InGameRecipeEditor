package dev.whisperlyric.ingamerecipeeditor.generated;

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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 生成配方管理器 - 管理动态生成的配方
 * 用于标识和删除模组创建的配方
 */
public class GeneratedRecipesManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();
    private static final String GENERATED_NAMESPACE = "ingamerecipeeditor";
    private static final String GENERATED_PATH_PREFIX = "generated/";
    
    // 待删除的生成配方
    private static final CopyOnWriteArraySet<String> pendingGeneratedRecipeDeletes = new CopyOnWriteArraySet<>();
    private static Path pendingDeletesConfigPath;
    private static boolean serverInitialized = false;

    /**
     * 服务器初始化
     */
    public static void serverInit() {
        if (!serverInitialized) {
            pendingDeletesConfigPath = FMLPaths.GAMEDIR.get().resolve("config")
                .resolve("ingamerecipeeditor")
                .resolve("pending_generated_recipe_deletes.json");
            loadPendingDeletes();
            serverInitialized = true;
            InGameRecipeEditor.LOGGER.info("服务器初始化生成配方管理器，共 {} 个待删除配方", 
                pendingGeneratedRecipeDeletes.size());
        }
    }

    /**
     * 加载待删除配置
     */
    private static void loadPendingDeletes() {
        if (pendingDeletesConfigPath != null && Files.exists(pendingDeletesConfigPath)) {
            try {
                String json = Files.readString(pendingDeletesConfigPath);
                Set<String> loaded = GSON.fromJson(json, SET_TYPE);
                pendingGeneratedRecipeDeletes.clear();
                if (loaded != null) {
                    pendingGeneratedRecipeDeletes.addAll(loaded);
                }
                InGameRecipeEditor.LOGGER.info("加载 {} 个待删除生成配方", pendingGeneratedRecipeDeletes.size());
            } catch (IOException e) {
                InGameRecipeEditor.LOGGER.error("加载待删除生成配方配置失败", e);
            }
        }
    }

    /**
     * 保存待删除配置
     */
    private static void savePendingDeletes() {
        if (pendingDeletesConfigPath == null) {
            InGameRecipeEditor.LOGGER.error("无法保存待删除生成配方：配置路径为空");
            return;
        }
        try {
            String json = GSON.toJson(pendingGeneratedRecipeDeletes);
            Files.createDirectories(pendingDeletesConfigPath.getParent());
            Files.writeString(pendingDeletesConfigPath, json);
            InGameRecipeEditor.LOGGER.info("保存 {} 个待删除生成配方", pendingGeneratedRecipeDeletes.size());
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.error("保存待删除生成配方配置失败", e);
        }
    }

    /**
     * 检查是否是生成配方ID
     */
    public static boolean isGeneratedRecipeId(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) return false;
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        return id != null && GENERATED_NAMESPACE.equals(id.getNamespace()) && id.getPath().startsWith(GENERATED_PATH_PREFIX);
    }

    /**
     * 服务器设置生成配方待删除状态
     */
    public static boolean serverSetGeneratedRecipeDeletionPending(String recipeId, boolean pending) {
        serverInit();
        if (!isGeneratedRecipeId(recipeId)) {
            return false;
        }

        boolean changed = pending
            ? pendingGeneratedRecipeDeletes.add(recipeId)
            : pendingGeneratedRecipeDeletes.remove(recipeId);
        if (changed) {
            savePendingDeletes();
        }
        InGameRecipeEditor.LOGGER.info("{} 生成配方删除: {}", pending ? "标记" : "取消标记", recipeId);
        return true;
    }

    /**
     * 获取待删除生成配方集合
     */
    public static Set<String> getPendingGeneratedRecipeDeletes() {
        return new HashSet<>(pendingGeneratedRecipeDeletes);
    }

    /**
     * 检查生成配方是否待删除
     */
    public static boolean isGeneratedRecipeDeletionPending(String recipeId) {
        return pendingGeneratedRecipeDeletes.contains(recipeId);
    }

    /**
     * 客户端更新待删除生成配方列表
     */
    public static void clientUpdatePendingGeneratedRecipeDeletes(List<String> recipeIds) {
        pendingGeneratedRecipeDeletes.clear();
        pendingGeneratedRecipeDeletes.addAll(recipeIds);
        InGameRecipeEditor.LOGGER.info("客户端从服务器接收 {} 个待删除生成配方", 
            pendingGeneratedRecipeDeletes.size());
    }

    /**
     * 客户端设置生成配方待删除状态
     */
    public static void clientSetGeneratedRecipeDeletionPending(String recipeId, boolean pending) {
        if (!isGeneratedRecipeId(recipeId)) {
            return;
        }
        if (pending) {
            pendingGeneratedRecipeDeletes.add(recipeId);
        } else {
            pendingGeneratedRecipeDeletes.remove(recipeId);
        }
    }

    /**
     * 清除所有客户端状态
     */
    public static void clearClientState() {
        pendingGeneratedRecipeDeletes.clear();
    }
}