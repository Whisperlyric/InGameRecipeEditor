package dev.whisperlyric.ingamerecipeeditor.jei;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.whisperlyric.ingamerecipeeditor.InGameRecipeEditor;
import dev.whisperlyric.ingamerecipeeditor.disabled.DisabledRecipesManager;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.Internal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JEI配方可见性管理
 * 控制禁用配方在JEI中的隐藏/显示
 */
public class JeiRecipeVisibility {

    // 是否在JEI中显示被禁用的配方（默认false，即禁用的配方在JEI中隐藏）
    private static boolean showDisabledInJei;

    // 已注入到JEI中的禁用配方（按JEI RecipeType分组），用于在切换回false时移除
    private static final Map<RecipeType<?>, List<Object>> injectedRecipes = new HashMap<>();

    // 待执行的延迟更新任务
    private static int pendingUpdateTicks;
    private static boolean hasPendingUpdate;

    /**
     * 延迟更新JEI可见性（用于/reload后等待JEI完成重载）
     */
    public static void scheduleUpdateVisibility() {
        pendingUpdateTicks = 20;
        hasPendingUpdate = true;
    }

    /**
     * 每tick调用，处理延迟更新任务
     * 需要在客户端tick事件中调用
     */
    public static void clientTick() {
        if (!hasPendingUpdate) return;
        pendingUpdateTicks--;
        if (pendingUpdateTicks <= 0) {
            hasPendingUpdate = false;
            updateVisibility();
        }
    }

    /**
     * 设置禁用配方是否在JEI中可见
     */
    public static void setShowDisabledInJei(boolean show) {
        showDisabledInJei = show;
        updateVisibility();
    }

    /**
     * 获取禁用配方是否在JEI中可见
     */
    public static boolean isShowDisabledInJei() {
        return showDisabledInJei;
    }

    /**
     * 获取JEI的IRecipeManager
     */
    private static @Nullable IRecipeManager getRecipeManager() {
        try {
            var runtime = Internal.getJeiRuntime();
            return runtime.getRecipeManager();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 更新JEI中禁用配方的可见性
     */
    public static void updateVisibility() {
        IRecipeManager recipeManager = getRecipeManager();
        if (recipeManager == null) return;

        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();

        if (showDisabledInJei) {
            // 禁用配方在JEI中保持可见：
            // 1. 先移除之前注入的配方（避免重复注入）
            removeInjectedRecipesFromJei(recipeManager);
            // 2. 将禁用配方注入到JEI中
            injectDisabledRecipesIntoJei(recipeManager, disabledRecipes);
            // 3. 取消隐藏所有配方
            unhideAllRecipes(recipeManager);
            InGameRecipeEditor.LOGGER.debug("更新JEI配方可见性：禁用配方在JEI中保持可见");
        } else {
            // 1. 移除之前注入到JEI中的禁用配方
            removeInjectedRecipesFromJei(recipeManager);
            // 2. 取消隐藏所有配方（处理之前被禁用但现已启用的配方）
            unhideAllRecipes(recipeManager);
            // 3. 隐藏被禁用的配方
            if (!disabledRecipes.isEmpty()) {
                hideDisabledRecipes(recipeManager, disabledRecipes);
            }
            InGameRecipeEditor.LOGGER.debug("更新JEI配方可见性：隐藏 {} 个禁用配方", disabledRecipes.size());
        }
    }

    /**
     * 将禁用配方注入到JEI中
     * 因为Mixin在配方加载时移除了禁用配方，JEI初始化时看不到它们，
     * 所以需要通过addRecipes()将它们重新注入到JEI中
     */
    @SuppressWarnings({"rawtypes", "deprecation"})
    private static void injectDisabledRecipesIntoJei(IRecipeManager recipeManager, Set<String> disabledRecipes) {
        if (disabledRecipes.isEmpty()) return;

        Map<String, String> jsonCache = DisabledRecipesManager.getClientRecipeJsonCache();
        if (jsonCache.isEmpty()) return;

        // 解析禁用配方的JSON，按JEI RecipeType分组
        Map<RecipeType<?>, List<Object>> recipesByType = new HashMap<>();

        for (var entry : jsonCache.entrySet()) {
            String recipeId = entry.getKey();
            String recipeJson = entry.getValue();

            // 只处理禁用列表中的配方
            if (!disabledRecipes.contains(recipeId)) continue;

            ResourceLocation id = ResourceLocation.tryParse(recipeId);
            if (id == null) continue;

            try {
                JsonElement jsonElement = JsonParser.parseString(recipeJson);
                Recipe<?> recipe = RecipeManager.fromJson(id, jsonElement.getAsJsonObject());

                // 查找该配方对应的JEI RecipeType
                List<RecipeType<?>> jeiTypes = findJeiRecipeTypes(recipeManager, recipe);
                if (jeiTypes.isEmpty()) {
                    InGameRecipeEditor.LOGGER.debug("未找到配方的JEI分类: {}", recipeId);
                    continue;
                }

                for (RecipeType<?> jeiType : jeiTypes) {
                    recipesByType.computeIfAbsent(jeiType, k -> new ArrayList<>()).add(recipe);
                }
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.debug("解析禁用配方JSON失败 {}: {}", recipeId, e.getMessage());
            }
        }

        // 通过addRecipes注入到JEI
        injectedRecipes.clear();
        for (var entry : recipesByType.entrySet()) {
            RecipeType recipeType = entry.getKey();
            List<Object> recipes = entry.getValue();
            try {
                callAddRecipes(recipeManager, recipeType, recipes);
                injectedRecipes.put(recipeType, new ArrayList<>(recipes));
                InGameRecipeEditor.LOGGER.info("注入 {} 个禁用配方到JEI分类 {}", recipes.size(), recipeType.getUid());
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.warn("注入配方到JEI分类 {} 失败: {}", recipeType.getUid(), e.getMessage());
            }
        }
    }

    /**
     * 从JEI中移除之前注入的禁用配方
     */
    @SuppressWarnings({"rawtypes"})
    private static void removeInjectedRecipesFromJei(IRecipeManager recipeManager) {
        if (injectedRecipes.isEmpty()) return;

        for (var entry : injectedRecipes.entrySet()) {
            RecipeType recipeType = entry.getKey();
            List<Object> recipes = entry.getValue();
            try {
                callHideRecipes(recipeManager, recipeType, recipes);
                InGameRecipeEditor.LOGGER.debug("从JEI分类 {} 移除 {} 个注入的配方", recipeType.getUid(), recipes.size());
            } catch (Exception e) {
                InGameRecipeEditor.LOGGER.warn("从JEI分类 {} 移除注入配方失败: {}", recipeType.getUid(), e.getMessage());
            }
        }
        injectedRecipes.clear();
    }

    /**
     * 查找配方对应的JEI RecipeType
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeType<?>> findJeiRecipeTypes(IRecipeManager recipeManager, Recipe<?> recipe) {
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        List<RecipeType<?>> result = new ArrayList<>();

        net.minecraft.world.item.crafting.RecipeType<?> mcType = recipe.getType();

        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();

            // 检查该分类是否接受此Recipe
            if (!recipeType.getRecipeClass().isInstance(recipe)) {
                continue;
            }

            try {
                if (!((IRecipeCategory) category).isHandled(recipe)) {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }

            // 检查该分类是否包含相同MC类型的配方
            boolean hasMatchingType;
            try {
                hasMatchingType = recipeManager.createRecipeLookup((RecipeType) recipeType)
                    .includeHidden()
                    .get()
                    .anyMatch(existing -> ((Recipe<?>) existing).getType() == mcType);
            } catch (Exception e) {
                // 如果查询失败，仍然尝试添加
                hasMatchingType = true;
            }

            if (hasMatchingType) {
                result.add(recipeType);
            }
        }

        return result;
    }

    /**
     * 隐藏指定的配方
     */
    public static void hideRecipe(String recipeId) {
        if (showDisabledInJei) return; // 禁用配方在JEI中保持可见时，不执行隐藏
        IRecipeManager recipeManager = getRecipeManager();
        if (recipeManager == null) return;
        hideSingleRecipe(recipeManager, recipeId);
    }

    /**
     * 显示指定的配方（取消隐藏）
     */
    public static void unhideRecipe(String recipeId) {
        IRecipeManager recipeManager = getRecipeManager();
        if (recipeManager == null) return;
        unhideSingleRecipe(recipeManager, recipeId);
    }

    /**
     * 隐藏所有被禁用的配方
     */
    private static void hideDisabledRecipes(IRecipeManager recipeManager, Set<String> disabledRecipes) {
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();

        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> recipesToHide = new ArrayList<>();

            recipeManager.createRecipeLookup(recipeType).get().forEach(recipe -> {
                ResourceLocation registryName = getRegistryName(category, recipe);
                if (registryName != null && disabledRecipes.contains(registryName.toString())) {
                    recipesToHide.add(recipe);
                }
            });

            if (!recipesToHide.isEmpty()) {
                callHideRecipes(recipeManager, recipeType, recipesToHide);
            }
        }
    }

    /**
     * 取消隐藏所有配方
     */
    private static void unhideAllRecipes(IRecipeManager recipeManager) {
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();

        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> allRecipes = new ArrayList<>();

            recipeManager.createRecipeLookup(recipeType).includeHidden().get().forEach(allRecipes::add);

            if (!allRecipes.isEmpty()) {
                callUnhideRecipes(recipeManager, recipeType, allRecipes);
            }
        }
    }

    /**
     * 隐藏单个配方
     */
    private static void hideSingleRecipe(IRecipeManager recipeManager, String recipeId) {
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();

        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> recipesToHide = new ArrayList<>();

            recipeManager.createRecipeLookup(recipeType).includeHidden().get().forEach(recipe -> {
                ResourceLocation registryName = getRegistryName(category, recipe);
                if (registryName != null && recipeId.equals(registryName.toString())) {
                    recipesToHide.add(recipe);
                }
            });

            if (!recipesToHide.isEmpty()) {
                callHideRecipes(recipeManager, recipeType, recipesToHide);
                return;
            }
        }
    }

    /**
     * 取消隐藏单个配方
     */
    private static void unhideSingleRecipe(IRecipeManager recipeManager, String recipeId) {
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();

        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> recipesToUnhide = new ArrayList<>();

            recipeManager.createRecipeLookup(recipeType).includeHidden().get().forEach(recipe -> {
                ResourceLocation registryName = getRegistryName(category, recipe);
                if (registryName != null && recipeId.equals(registryName.toString())) {
                    recipesToUnhide.add(recipe);
                }
            });

            if (!recipesToUnhide.isEmpty()) {
                callUnhideRecipes(recipeManager, recipeType, recipesToUnhide);
                return;
            }
        }
    }

    /**
     * 通过反射调用addRecipes，将配方注入到JEI中
     * 注：addRecipes是JEI内部API，可能不存在于公开接口中
     */
    @SuppressWarnings("all")
    private static void callAddRecipes(IRecipeManager recipeManager, RecipeType<?> recipeType, Collection<?> recipes) {
        try {
            Method method = IRecipeManager.class.getDeclaredMethod("addRecipes", RecipeType.class, Collection.class);
            method.setAccessible(true);
            method.invoke(recipeManager, recipeType, recipes);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("添加配方到JEI失败: {}", e.getMessage());
        }
    }

    /**
     * 通过反射调用hideRecipes，绕过泛型类型检查
     * 注意：hideRecipes是JEI内部API
     */
    @SuppressWarnings("all")
    private static void callHideRecipes(IRecipeManager recipeManager, RecipeType<?> recipeType, Collection<?> recipes) {
        try {
            Method method = IRecipeManager.class.getDeclaredMethod("hideRecipes", RecipeType.class, Collection.class);
            method.setAccessible(true);
            method.invoke(recipeManager, recipeType, recipes);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("隐藏配方失败: {}", e.getMessage());
        }
    }

    /**
     * 通过反射调用unhideRecipes，绕过泛型类型检查
     * 注意：unhideRecipes是JEI内部API
     */
    @SuppressWarnings("all")
    private static void callUnhideRecipes(IRecipeManager recipeManager, RecipeType<?> recipeType, Collection<?> recipes) {
        try {
            Method method = IRecipeManager.class.getDeclaredMethod("unhideRecipes", RecipeType.class, Collection.class);
            method.setAccessible(true);
            method.invoke(recipeManager, recipeType, recipes);
        } catch (Exception e) {
            InGameRecipeEditor.LOGGER.warn("取消隐藏配方失败: {}", e.getMessage());
        }
    }

    /**
     * 获取配方的注册名
     */
    private static @Nullable ResourceLocation getRegistryName(IRecipeCategory<?> category, Object recipe) {
        try {
            var method = category.getClass().getMethod("getRegistryName", Object.class);
            Object id = method.invoke(category, recipe);
            if (id instanceof ResourceLocation rl) {
                return rl;
            }
        } catch (Exception ignored) {}

        if (recipe instanceof Recipe<?> mcRecipe) {
            return mcRecipe.getId();
        }

        return null;
    }
}
