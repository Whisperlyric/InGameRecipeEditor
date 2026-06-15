package dev.whisperlyric.ingamerecipeeditor.mixin;

import dev.whisperlyric.ingamerecipeeditor.button.RecipeLayoutButtonManager;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * RecipesGui 鼠标点击 Mixin - 处理自定义按钮点击
 */
@Mixin(RecipesGui.class)
public class RecipesGuiMouseMixin {
    /**
     * 处理鼠标点击事件，检查是否点击了自定义按钮
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ingamerecipeeditor_handleButtonClick(double mouseX, double mouseY, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        // 使用访问器获取配方布局管理器
        RecipesGuiAccessor guiAccessor = (RecipesGuiAccessor) (Object) this;
        RecipeGuiLayouts layouts = guiAccessor.getLayouts();
        
        // 使用访问器获取配方布局列表
        RecipeGuiLayoutsAccessor layoutAccessor = (RecipeGuiLayoutsAccessor) layouts;
        List<RecipeLayoutWithButtons<?>> layoutList = layoutAccessor.getRecipeLayoutsWithButtons();
        
        if (layoutList == null || layoutList.isEmpty()) {
            return;
        }
        
        for (RecipeLayoutWithButtons<?> layoutWithButtons : layoutList) {
            List<AbstractWidget> customButtons = RecipeLayoutButtonManager.getButtons(layoutWithButtons);
            for (AbstractWidget customButton : customButtons) {
                if (customButton.isMouseOver(mouseX, mouseY)) {
                    customButton.onClick(mouseX, mouseY);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}