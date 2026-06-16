package dev.whisperlyric.ingamerecipeeditor.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * JEI配方工具类 - 正确获取配方ID和配方类型
 */
public class JeiRecipeHelper {

    /**
     * 从JEI配方布局获取正确的配方ID
     * 对于Recipe<?>类型的配方，使用recipe.getId()
     * 对于其他类型，使用getRegistryName
     */
    @SuppressWarnings("unchecked")
    public static String getRecipeId(IRecipeLayoutDrawable<?> recipeLayout) {
        Object recipe = recipeLayout.getRecipe();
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();

        // 如果是Minecraft Recipe，使用recipe.getId()
        if (recipe instanceof Recipe<?> mcRecipe) {
            ResourceLocation id = mcRecipe.getId();
            return id != null ? id.toString() : null;
        }

        // 其他类型使用JEI的getRegistryName
        ResourceLocation registryName = ((IRecipeCategory<Object>) category).getRegistryName(recipe);
        return registryName != null ? registryName.toString() : null;
    }

    /**
     * 从JEI配方布局获取正确的配方类型
     * 从配方JSON的type字段或配方序列化器获取
     */
    public static String getRecipeType(IRecipeLayoutDrawable<?> recipeLayout) {
        Object recipe = recipeLayout.getRecipe();

        // 如果是Minecraft Recipe，从序列化器获取类型
        if (recipe instanceof Recipe<?> mcRecipe) {
            try {
                ResourceLocation serializerId = ForgeRegistries.RECIPE_SERIALIZERS.getKey(mcRecipe.getSerializer());
                if (serializerId != null) {
                    return serializerId.toString();
                }
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.warn("无法从序列化器获取配方类型", e);
            }

            // 备选方案：从RecipeType获取
            ResourceLocation recipeTypeId = ForgeRegistries.RECIPE_TYPES.getKey(mcRecipe.getType());
            if (recipeTypeId != null) {
                return recipeTypeId.toString();
            }
        }

        // 其他类型使用JEI的RecipeType UID
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        ResourceLocation uid = category.getRecipeType().getUid();
        return uid != null ? uid.toString() : null;
    }

    /**
     * 从配方JSON文件获取配方类型
     */
    public static Optional<String> getRecipeTypeFromJson(String recipeId) {
        Optional<JsonObject> jsonOpt = loadRecipeJson(recipeId);
        if (jsonOpt.isPresent()) {
            JsonObject json = jsonOpt.get();
            if (json.has("type")) {
                return Optional.of(json.get("type").getAsString());
            }
        }
        return Optional.empty();
    }

    /**
     * 加载配方JSON文件
     */
    public static Optional<JsonObject> loadRecipeJson(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) {
            return Optional.empty();
        }

        ResourceLocation rl = ResourceLocation.tryParse(recipeId);
        if (rl == null) {
            return Optional.empty();
        }

        // 尝试从多个路径加载配方JSON
        Path[] searchPaths = {
            Path.of("config/ingamerecipeeditor/recipes"),
            Path.of("config/ingamerecipeeditor/custom_recipes")
        };

        String namespace = rl.getNamespace();
        String path = rl.getPath();

        for (Path basePath : searchPaths) {
            Path recipePath = findRecipeFile(basePath, namespace, path);
            if (recipePath != null && Files.exists(recipePath)) {
                try (BufferedReader reader = Files.newBufferedReader(recipePath)) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (element.isJsonObject()) {
                        return Optional.of(element.getAsJsonObject());
                    }
                } catch (IOException e) {
                    InGameRecipeEditor.LOGGER.warn("加载配方JSON失败: {}", recipePath, e);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * 在目录中查找配方文件
     */
    private static Path findRecipeFile(Path basePath, String namespace, String recipePath) {
        // 尝试namespace目录
        Path namespaceDir = basePath.resolve(namespace);
        if (Files.exists(namespaceDir)) {
            // 将路径分隔符替换为下划线
            String fileName = recipePath.replace('/', '_') + ".json";
            Path filePath = namespaceDir.resolve(fileName);
            if (Files.exists(filePath)) {
                return filePath;
            }

            // 递归搜索
            return searchRecursively(namespaceDir, recipePath);
        }
        return null;
    }

    /**
     * 递归搜索配方文件
     */
    private static Path searchRecursively(Path dir, String targetPath) {
        if (!Files.isDirectory(dir)) {
            return null;
        }

        try {
            for (Path file : Files.list(dir).toList()) {
                if (Files.isDirectory(file)) {
                    Path found = searchRecursively(file, targetPath);
                    if (found != null) {
                        return found;
                    }
                } else if (file.getFileName().toString().endsWith(".json")) {
                    // 检查文件名是否匹配（去掉.json后缀，替换分隔符）
                    String fileName = file.getFileName().toString();
                    fileName = fileName.substring(0, fileName.length() - 5);
                    fileName = fileName.replace('_', '/');

                    // 简单匹配：文件名包含目标路径
                    if (fileName.contains(targetPath.replace('_', '/'))) {
                        return file;
                    }
                }
            }
        } catch (IOException e) {
            InGameRecipeEditor.LOGGER.warn("搜索配方文件失败: {}", dir, e);
        }

        return null;
    }

    /**
     * 检查配方是否为有序合成
     */
    public static boolean isShapedCrafting(String recipeType) {
        if (recipeType == null) return false;
        return recipeType.contains("crafting_shaped") ||
               recipeType.equals("minecraft:crafting_shaped");
    }

    /**
     * 检查配方是否为无序合成
     */
    public static boolean isShapelessCrafting(String recipeType) {
        if (recipeType == null) return false;
        return recipeType.contains("crafting_shapeless") ||
               recipeType.equals("minecraft:crafting_shapeless");
    }

    /**
     * 检查配方是否为工作台配方（有序或无序）
     */
    public static boolean isCraftingRecipe(String recipeType) {
        return isShapedCrafting(recipeType) || isShapelessCrafting(recipeType);
    }
}