package dev.whisperlyric.ingamerecipeeditor.mixin;

import dev.whisperlyric.ingamerecipeeditor.button.RecipeLayoutButtonManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * RecipeGuiLayouts Mixin - 渲染和处理自定义按钮
 */
@Mixin(RecipeGuiLayouts.class)
public class RecipeGuiLayoutsMixin {
    @Shadow(remap = false)
    @Final
    private List<RecipeLayoutWithButtons<?>> recipeLayoutsWithButtons;

    // 按钮尺寸和间距（和src-old一致）
    @Unique
    private static final int BUTTON_WIDTH = 9;
    @Unique
    private static final int BUTTON_HEIGHT = 9;
    @Unique
    private static final int BUTTON_SPACING = 1;

    /**
     * 在绘制配方布局后绘制自定义按钮
     */
    @Inject(method = "draw", at = @At("RETURN"), remap = false)
    private void ingamerecipeeditor_drawButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfoReturnable<?> cir) {
        float partialTicks = Minecraft.getInstance().getDeltaFrameTime();
        
        for (RecipeLayoutWithButtons<?> layoutWithButtons : recipeLayoutsWithButtons) {
            List<AbstractWidget> buttons = RecipeLayoutButtonManager.getButtons(layoutWithButtons);
            for (AbstractWidget button : buttons) {
                button.render(guiGraphics, mouseX, mouseY, partialTicks);
            }
        }
    }

    /**
     * 在绘制工具提示时绘制按钮工具提示
     */
    @Inject(method = "drawTooltips", at = @At("RETURN"), remap = false)
    private void ingamerecipeeditor_drawButtonTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        // 按钮工具提示已在按钮渲染中处理
    }

    /**
     * 在更新布局时更新按钮位置
     * 这是关键：配方布局位置更新后，按钮位置也需要同步更新
     */
    @Inject(method = "updateRecipeButtonPositions", at = @At("RETURN"), remap = false)
    private void ingamerecipeeditor_updateCustomButtonPositions(CallbackInfo ci) {
        for (RecipeLayoutWithButtons<?> layoutWithButtons : recipeLayoutsWithButtons) {
            IRecipeLayoutDrawable<?> recipeLayout = layoutWithButtons.recipeLayout();
            Rect2i rect = recipeLayout.getRect();
            
            // 按钮位置：在配方布局右侧
            int startX = rect.getX() + rect.getWidth() + 2;
            int startY = rect.getY();
            int row2Y = startY + BUTTON_HEIGHT + BUTTON_SPACING;
            
            List<AbstractWidget> buttons = RecipeLayoutButtonManager.getButtons(layoutWithButtons);
            for (int i = 0; i < buttons.size(); i++) {
                AbstractWidget button = buttons.get(i);
                switch (i) {
                    case 0: // 第一行左侧：启/禁用
                        button.setX(startX);
                        button.setY(startY);
                        break;
                    case 1: // 第一行右侧：编辑
                        button.setX(startX + BUTTON_WIDTH + BUTTON_SPACING);
                        button.setY(startY);
                        break;
                    case 2: // 第二行左侧：添加
                        button.setX(startX);
                        button.setY(row2Y);
                        break;
                    case 3: // 第二行右侧：复制
                        button.setX(startX + BUTTON_WIDTH + BUTTON_SPACING);
                        button.setY(row2Y);
                        break;
                }
            }
        }
    }
}