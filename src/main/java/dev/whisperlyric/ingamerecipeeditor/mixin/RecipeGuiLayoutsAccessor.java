package dev.whisperlyric.ingamerecipeeditor.mixin;

import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * RecipeGuiLayouts 访问器 - 访问 private 字段
 */
@Mixin(RecipeGuiLayouts.class)
public interface RecipeGuiLayoutsAccessor {
    /**
     * 获取配方布局列表
     * RecipeGuiLayouts.recipeLayoutsWithButtons 字段类型是 List<RecipeLayoutWithButtons<?>>
     */
    @Accessor(value = "recipeLayoutsWithButtons", remap = false)
    List<RecipeLayoutWithButtons<?>> getRecipeLayoutsWithButtons();
}