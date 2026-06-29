package dev.whisperlyric.ingamerecipeeditor.button;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 配方按钮抽象基类 - 提供统一的 JEI 风格按钮渲染
 */
public abstract class AbstractRecipeButton extends AbstractWidget {

    protected AbstractRecipeButton(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        // 更新状态（子类可覆盖以实现动态状态）
        updateState();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        boolean hovered = isMouseOver(mouseX, mouseY);

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        );
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        // 使用 JEI 的按钮背景
        Textures textures = Internal.getTextures();
        DrawableNineSliceTexture buttonTexture = textures.getButtonForState(isPressed(), this.active, hovered);
        buttonTexture.draw(guiGraphics, getX(), getY(), this.width, this.height);

        // 设置图标颜色
        int color = getIconColor(hovered);
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;
        float alpha = (color >> 24 & 255) / 255.0F;
        RenderSystem.setShaderColor(red, green, blue, alpha);

        // 绘制图标
        renderIcon(guiGraphics, hovered);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (hovered) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, getTooltipLines(), mouseX, mouseY);
        }
    }

    /**
     * 更新按钮状态（子类可覆盖以实现动态状态更新）
     */
    protected void updateState() {
        // 默认无操作
    }

    /**
     * 是否处于按下状态（影响 JEI 按钮背景）
     */
    protected boolean isPressed() {
        return false;
    }

    /**
     * 获取图标颜色
     */
    protected int getIconColor(boolean hovered) {
        if (!this.active) {
            return 0xFFA0A0A0;
        } else if (hovered) {
            return 0xFFFFFFFF;
        }
        return 0xFFE0E0E0;
    }

    /**
     * 获取图标尺寸
     */
    protected int getIconSize() {
        return 8;
    }

    /**
     * 绘制图标
     */
    protected void renderIcon(GuiGraphics guiGraphics, boolean hovered) {
        int iconSize = getIconSize();
        double xOffset = getX() + (width - iconSize) / 2.0;
        double yOffset = getY() + (height - iconSize) / 2.0;

        // 应用图标偏移（子类可覆盖）
        var offset = getIconOffset(hovered);
        xOffset += offset.x();
        yOffset += offset.y();

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(xOffset, yOffset, 0);
        guiGraphics.blit(getIconTexture(), 0, 0, 0, 0, iconSize, iconSize, iconSize, iconSize);
        poseStack.popPose();
    }

    /**
     * 获取图标偏移
     */
    protected IconOffset getIconOffset(boolean hovered) {
        return IconOffset.ZERO;
    }

    /**
     * 获取图标纹理
     */
    protected abstract ResourceLocation getIconTexture();

    /**
     * 获取提示文本
     */
    protected abstract List<Component> getTooltipLines();

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narration) {
    }

    /**
     * 图标偏移记录
     */
    protected record IconOffset(double x, double y) {
        public static final IconOffset ZERO = new IconOffset(0, 0);
    }
}