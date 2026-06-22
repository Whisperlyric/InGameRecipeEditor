package dev.whisperlyric.ingamerecipeeditor.customrecipes;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义配方管理器 - 从 config 目录加载自定义配方
 * 配方保存路径: config/ingamerecipeeditor/recipes/<namespace>/<recipe_path>.json
 */
public class CustomRecipesManager {
    private static final Gson GSON = new Gson();
    private static final Path RECIPES_DIR = FMLPaths.CONFIGDIR.get()
        .resolve("ingamerecipeeditor")
        .resolve("recipes");

    /**
     * 从 config 目录加载所有自定义配方
     * @return 配方ID -> 配方JSON 的映射
     */
    public static Map<ResourceLocation, JsonElement> loadCustomRecipes() {
        Map<ResourceLocation, JsonElement> recipes = new HashMap<>();

        File recipesDir = RECIPES_DIR.toFile();
        if (!recipesDir.exists()) {
            InGameRecipeEditor.LOGGER.debug("自定义配方目录不存在: {}", RECIPES_DIR);
            return recipes;
        }

        // 扫描所有 namespace 目录
        File[] namespaceDirs = recipesDir.listFiles(File::isDirectory);
        if (namespaceDirs == null) {
            return recipes;
        }

        for (File namespaceDir : namespaceDirs) {
            String namespace = namespaceDir.getName();
            loadNamespaceRecipes(namespace, namespaceDir, recipes);
        }

        if (!recipes.isEmpty()) {
            InGameRecipeEditor.LOGGER.info("从 config 目录加载 {} 个自定义配方", recipes.size());
        }

        return recipes;
    }

    /**
     * 加载特定 namespace 下的所有配方
     */
    private static void loadNamespaceRecipes(String namespace, File namespaceDir, 
            Map<ResourceLocation, JsonElement> recipes) {
        List<File> recipeFiles = scanRecipeFiles(namespaceDir);

        InGameRecipeEditor.LOGGER.debug("扫描到 {} 个 {} 配方文件", recipeFiles.size(), namespace);

        for (File recipeFile : recipeFiles) {
            try {
                // 从文件路径获取配方 path
                String recipePath = getRecipePathFromFile(namespaceDir, recipeFile);
                ResourceLocation recipeId = ResourceLocation.parse(namespace + ":" + recipePath);

                // 读取配方 JSON
                try (FileReader reader = new FileReader(recipeFile)) {
                    JsonElement jsonElement = GSON.fromJson(reader, JsonElement.class);

                    if (jsonElement != null && jsonElement.isJsonObject()) {
                        JsonObject recipeJson = jsonElement.getAsJsonObject();
                        
                        // 确保 JSON 中有 type 字段
                        if (!recipeJson.has("type")) {
                            InGameRecipeEditor.LOGGER.warn("配方缺少 type 字段: {}", recipeId);
                            continue;
                        }

                        recipes.put(recipeId, jsonElement);
                        InGameRecipeEditor.LOGGER.debug("加载自定义配方: {}", recipeId);
                    } else {
                        InGameRecipeEditor.LOGGER.warn("无效的配方 JSON: {}", recipeFile.getPath());
                    }
                }

            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.error("加载配方文件失败: {}", recipeFile.getPath(), e);
            }
        }
    }

    /**
     * 递归扫描配方文件
     */
    private static List<File> scanRecipeFiles(File directory) {
        List<File> files = new java.util.ArrayList<>();

        File[] children = directory.listFiles();
        if (children == null) {
            return files;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                files.addAll(scanRecipeFiles(child));
            } else if (child.getName().endsWith(".json")) {
                files.add(child);
            }
        }

        return files;
    }

    /**
     * 从文件路径获取配方 path
     * 文件路径格式: <namespace_dir>/<type_subdir>/<file>.json 或 <namespace_dir>/<file>.json
     * 如果中间目录名与配方JSON的type路径部分一致，则跳过该目录层级
     * 例如: minecraft/crafting_shaped/oak_planks.json -> oak_planks
     *       minecraft/iron_ingot.json -> iron_ingot
     */
    private static String getRecipePathFromFile(File namespaceDir, File recipeFile) {
        String namespacePath = namespaceDir.getAbsolutePath();
        String filePath = recipeFile.getAbsolutePath();

        // 获取相对路径
        String relativePath = filePath.substring(namespacePath.length() + 1);

        // 移除 .json 扩展名
        if (relativePath.endsWith(".json")) {
            relativePath = relativePath.substring(0, relativePath.length() - 5);
        }

        // 检查是否有type子目录：如果路径包含分隔符，且目录名与配方type匹配，则只取文件名部分
        int sepIndex = relativePath.indexOf(File.separatorChar);
        if (sepIndex > 0 && sepIndex < relativePath.length() - 1) {
            // 有子目录，检查是否为type子目录
            // type子目录的判断：读取JSON的type字段，如果路径部分与目录名一致，则跳过目录名
            try (FileReader reader = new FileReader(recipeFile)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element != null && element.isJsonObject()) {
                    JsonObject json = element.getAsJsonObject();
                    if (json.has("type")) {
                        String type = json.get("type").getAsString();
                        String typePath = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
                        String dirName = relativePath.substring(0, sepIndex);
                        if (dirName.equals(typePath)) {
                            // 目录名与type路径部分一致，跳过目录名
                            return relativePath.substring(sepIndex + 1).replace(File.separatorChar, '_');
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // 将路径分隔符替换为下划线（符合 ResourceLocation path 规范）
        return relativePath.replace(File.separatorChar, '_');
    }

    /**
     * 获取自定义配方目录路径
     */
    public static Path getRecipesDir() {
        return RECIPES_DIR;
    }
}