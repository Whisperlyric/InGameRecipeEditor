package dev.whisperlyric.ingamerecipeeditor.mixin;

import dev.whisperlyric.ingamerecipeeditor.workspace.RecipeWorkspaceScreen;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.library.gui.recipes.RecipeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin拦截RecipeLayout的drawOverlays方法
 * 在工作区界面中，只渲染非槽位的叠加层（箭头、进度条等），不渲染槽位高亮和tooltip
 */
@Mixin(RecipeLayout.class)
public class RecipeLayoutDrawOverlaysMixin {
    
    @Inject(
        method = "drawOverlays",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void onDrawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        // 检查当前界面是否为工作区界面
        if (Minecraft.getInstance().screen instanceof RecipeWorkspaceScreen) {
            // 在工作区界面中，取消JEI的drawOverlays调用
            // 因为工作区有自己的槽位高亮和tooltip渲染
            ci.cancel();
            
            // 只渲染非槽位的叠加层（箭头、进度条等）
            // 这些是配方类型的特定动画效果，不是槽位相关的
            drawNonSlotOverlays(guiGraphics, mouseX, mouseY);
        }
    }
    
    /**
     * 只渲染非槽位的叠加层
     * 包括：箭头动画、进度条、火焰动画等配方类型特定的效果
     */
    private void drawNonSlotOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 获取RecipeLayout实例
        IRecipeLayoutDrawable<?> layout = (IRecipeLayoutDrawable<?>) (Object) this;
        
        // JEI的drawOverlays实际上分为两部分：
        // 1. 槽位相关的渲染（高亮、tooltip）- 我们不需要
        // 2. 配方类型特定的动画效果（箭头、进度条等）- 我们需要
        
        // 由于JEI没有提供分离的方法，我们需要手动调用配方类型的draw方法
        // 但这需要访问RecipeLayout的内部字段，可能需要更多的Accessor
        
        // 暂时不渲染任何叠加层，因为：
        // 1. 槽位高亮和tooltip由工作区自定义渲染
        // 2. 箭头、进度条等动画效果在某些配方类型中可能不重要
        // 如果需要这些动画效果，可以后续通过Accessor访问并手动渲染
    }
}