package dev.whisperlyric.ingamerecipeeditor.mixin;

import dev.whisperlyric.ingamerecipeeditor.button.RecipeLayoutButtonManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 配方布局按钮 Mixin
 * 向 JEI 配方界面添加禁用/编辑按钮
 * 使用 RecipeLayoutButtonManager 管理按钮
 */
@Mixin(RecipeLayoutWithButtons.class)
public class RecipeLayoutWithButtonsMixin {
    @Shadow(remap = false)
    @Final
    private IRecipeLayoutDrawable<?> recipeLayout;

    /**
     * 在构造函数完成后创建按钮
     * 由于 RecipeLayoutWithButtons 是 record，构造函数是隐式的
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void ingamerecipeeditor_afterInit(CallbackInfo ci) {
        // 使用管理类创建和存储按钮
        RecipeLayoutButtonManager.createButtons(
            (RecipeLayoutWithButtons<?>) (Object) this,
            recipeLayout
        );
    }
}