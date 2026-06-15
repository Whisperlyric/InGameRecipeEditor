package dev.whisperlyric.ingamerecipeeditor.mixin;

import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * RecipesGui 访问器 - 访问 private 字段
 */
@Mixin(RecipesGui.class)
public interface RecipesGuiAccessor {
    /**
     * 获取配方布局管理器
     * RecipesGui.layouts 字段类型是 RecipeGuiLayouts
     */
    @Accessor(value = "layouts", remap = false)
    RecipeGuiLayouts getLayouts();
}